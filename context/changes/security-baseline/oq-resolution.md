---
change_id: security-baseline
purpose: resolve blocking open questions OQ1 (FR-005) and OQ2 (FR-006/FR-007)
date: 2026-08-30
vps_session_completed: true
browser_session_completed: true
oq1_resolved: partial
corrections_applied: 2026-08-30 — proxy scope, deployed-proxy identity, app version drift, false-success framing
oq2_resolved: yes
---

## Part A — Production volume inventory (VPS)

Reached `vps` (`kacper@193.33.111.50`, host `vps-webh-v1`). Volume present, `file(1)`
available. All commands read-only (`find`, `file`, `du`, `wc`, `sort`, `uniq`, `awk`).

```
ssh vps 'test -d ~/carcare/data/data && echo PRESENT || echo MISSING'   -> PRESENT
resolved path: /home/kacper/carcare/data/data
```

### Counts and total size

| Metric | Value | Command |
|---|---|---|
| Regular files, depth 1 | **9** | `find "$DATA" -maxdepth 1 -type f \| wc -l` |
| Regular files, any depth | 9 (no sub-dirs) | `find "$DATA" -type f \| wc -l` |
| Total size | **388K** | `du -sh "$DATA"` |
| Sum of file sizes | 368,592 bytes | `find … -printf '%s\n' \| awk '{s+=$1}…'` |

### Extension distribution — what filenames claim

```
find "$DATA" -maxdepth 1 -type f -printf '%f\n' | awk -F. 'NF>1{print "."$NF} NF==1{print "(no extension)"}' | sort | uniq -c | sort -rn
      4 .jpg
      4 .bin
      1 .png
```

### Byte-level format distribution — what the files actually are

```
find "$DATA" -maxdepth 1 -type f -exec file --mime-type -b {} \; | sort | uniq -c | sort -rn
      5 image/png
      4 image/jpeg
```

Full `file -b` detail per file:

| filename | ext | detected MIME | `file -b` description |
|---|---|---|---|
| 8eaf8e93-fc9a-4a90-a4c8-fe9d5395d63a.bin | .bin | image/png | PNG image data, 300 x 300, 8-bit gray+alpha, non-interlaced |
| 9b73df1e-edd5-4293-81e0-4a2616b35294.bin | .bin | image/png | PNG image data, 300 x 300, 8-bit gray+alpha, non-interlaced |
| e233f90c-9e64-4b3b-90b7-2640da35897a.bin | .bin | image/png | PNG image data, 300 x 300, 8-bit gray+alpha, non-interlaced |
| 7a705225-6cca-4f26-9f08-e72bd3896144.bin | .bin | image/png | PNG image data, 300 x 300, 8-bit gray+alpha, non-interlaced |
| 3f2d692c-865e-4854-bd43-3b4969fa1490.png | .png | image/png | PNG image data, 1330 x 816, 8-bit/color RGB, non-interlaced |
| e8d28af0-a9b9-441a-b95d-75110d480a35.jpg | .jpg | image/jpeg | JPEG … Exif … model=SM-G991B … 450x300 |
| adea3b21-595d-4826-8276-898749fbf18c.jpg | .jpg | image/jpeg | JPEG … Exif … software=Picasa … 480x270 |
| 7f4dea20-4057-4e28-8af5-2260ce462be4.jpg | .jpg | image/jpeg | JPEG … Exif … software=Picasa … 480x300 |
| 883a7d3c-0540-4a96-9c94-2dd4eec517e2.jpg | .jpg | image/jpeg | JPEG … Exif … 450x300 |

### Extension / format mismatches — listed individually

Four disagreements, all the same shape:

| filename | claimed by extension | actual bytes |
|---|---|---|
| 8eaf8e93-fc9a-4a90-a4c8-fe9d5395d63a.bin | `.bin` (application/octet-stream) | **image/png** |
| 9b73df1e-edd5-4293-81e0-4a2616b35294.bin | `.bin` (application/octet-stream) | **image/png** |
| e233f90c-9e64-4b3b-90b7-2640da35897a.bin | `.bin` (application/octet-stream) | **image/png** |
| 7a705225-6cca-4f26-9f08-e72bd3896144.bin | `.bin` (application/octet-stream) | **image/png** |

All four are byte-identical in size (14,366 bytes) and identical in geometry
(PNG 300×300, 8-bit gray+alpha) — almost certainly the same placeholder/avatar PNG saved
four times. `.bin` is what Tika returns for `MimeTypes.forName("application/octet-stream")
.getExtension()`, so these came from `VehicleDetailsMapper` → `ImageStorageServiceImpl.save()`
calls where the **client-declared** `imageContentType` was a generic non-image type
(`application/octet-stream` or similar) while the actual bytes were PNG. This is the exact
failure mode FR-006 targets: the current code trusts the client-declared content type.

The five JPEG/PNG files whose extension matches their bytes carry normal camera/edit
metadata (Samsung SM-G991B, Picasa, Shotwell) — ordinary user photos.

### Extensionless files

**None.** Every file has an extension. (A `save()` that hits `MimeTypeException` returns
`""` and writes no file at all, so a truly extensionless artifact was never expected; the
`.bin` group is the closest thing — content type resolved, but to a non-image type.)

### Size distribution

Ascending bytes (`find … -printf '%s\n' | sort -n`):

```
14366  14366  14366  14366  26465  53392  54693  65885  110693
```

| Statistic | Value (bytes) | Note |
|---|---|---|
| min | 14,366 | the placeholder PNG ×4 |
| median | 26,465 | |
| mean | 40,955 | |
| p90 | 74,847 (linear interp) / 110,693 (nearest-rank) | n=9 — see caveat |
| p99 | 107,108 (linear interp) / 110,693 (nearest-rank) | effectively = max |
| max | 110,693 | `3f2d692c-…png`, PNG 1330×816 |

Five largest (`find … -printf '%s\t%f\n' | sort -rn | head -5`):

```
110693  3f2d692c-865e-4854-bd43-3b4969fa1490.png
 65885  e8d28af0-a9b9-441a-b95d-75110d480a35.jpg
 54693  adea3b21-595d-4826-8276-898749fbf18c.jpg
 53392  7f4dea20-4057-4e28-8af5-2260ce462be4.jpg
 26465  883a7d3c-0540-4a96-9c94-2dd4eec517e2.jpg
```

**Caveat:** n=9. Percentiles here are barely meaningful; treat "max ≈ 108 KB, everything
else ≤ 65 KB" as the real signal, not the p90/p99 figures.

### Anomalies

- Non-regular entries under the volume: none (`find "$DATA" -maxdepth 1 ! -type f` returned
  only the directory itself).
- Zero-byte files: **none** (`find … -type f -size 0` empty).
- No sub-directories, no symlinks, no sockets.

## OQ2 answer

**Proposed FR-006 allowlist:** `image/png`, `image/jpeg`.

| Format | Files depending on it | Notes |
|---|---|---|
| `image/png` | **5** | 4 are the 14,366-byte placeholder saved as `.bin`; 1 is a real 1330×816 PNG |
| `image/jpeg` | **4** | ordinary camera/edited photos, `.jpg` extension correct |

**Does any stored file fall outside that allowlist?** **No.** Byte-level detection over all
9 files yields only PNG and JPEG. The four `.bin` files are PNG by content and are therefore
*inside* the allowlist despite their misleading name.

**FR-006 / FR-007 collision verdict:** **No collision, as of today's volume state
(2026-08-30).** An allowlist of {PNG, JPEG} excludes nothing currently stored, so
"reject non-allowlisted formats on upload" (FR-006) and "every stored file stays loadable"
(FR-007) do not contradict each other on this volume. Two qualifications:

1. The load path (`ImageStorageServiceImpl.load`) is a pure byte-passthrough keyed on the
   stored filename; it performs no type check. FR-007 holds for the four `.bin` files
   **only if the implementation keeps load unfiltered** — do not add allowlist enforcement
   to the read path, or the `.bin` files (and any future out-of-allowlist file) would stop
   loading.
2. `VehicleDetailsMapper` line 35 computes the response `imageContentType` with
   `tika.detect(filename)` — a filename-only detect. For the `.bin` files this returns
   `application/octet-stream`, so the client currently receives those images tagged as a
   non-image type. That is pre-existing behavior, not caused by FR-006; if FR-006 also
   switches this to byte-based detection it would be an improvement, not a regression.

**Size figures Part C needs:** largest stored image raw = **110,693 bytes (~108 KB)**;
as a base64 JSON body ≈ 110,693 × 4/3 ≈ **147,591 bytes (~144 KB)** plus the small vehicle
JSON envelope.

## Part B — Trial environment

Local, `/Users/kacper/Dev/carcare/server`, method inherited from
`context/archive/2026-08-27-client-server-contract-trial/change.md`.

| Component | Value |
|---|---|
| WAR | `target/carcare-1.3.11.war`, rebuilt this session with `./mvnw -Pprod -DskipTests clean package` (BUILD SUCCESS) under `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` |
| Bundled client | `WEB-INF/lib/client-1.2.5.jar`; served bundle `app/vendors.aeca5ce7ffebc06fb0ac.chunk.js`; nav shows "CarCare 1.2.5" — matches the archived contract-trial hash |
| DB | `mariadb:10.11.6`, container `carcare-trial-db`, tmpfs `/var/lib/mysql`, host port 13306 — exact args from the archived setup |
| App | `java -Dspring.profiles.active=prod -Dserver.port=18080 -Duser.timezone=UTC -Dapplication.data-directory.location=/tmp/carcare-trial-data …` |
| Data dir | `/tmp/carcare-trial-data` (local scratch — **never** the VPS path) |
| Seeding | `populate-fuel-types` / `populate-insurance-types` under admin JWT → both `true` (7 fuel, 3 insurance types) |
| Reverse proxy (added) | `nginx:alpine` (resolved to **nginx/1.31.4**), container `carcare-trial-proxy`, host port 18090, config copied in = the repo's `src/main/docker/reverseproxy/nginx.conf` semantics: `client_max_body_size 4M`, `proxy_pass` to the app |

**Difference from the archived setup:** this trial added a throwaway NGINX container in
front of the app, because no in-process property bounds the request body (see OQ1) and the
only production body bound is at the proxy. The proxy config mirrors the committed
`nginx.conf` (`client_max_body_size 4M`) but points `proxy_pass` at
`host.docker.internal:18080` and drops the unrelated MariaDB `stream {}` block. NGINX
version is whatever `nginx:alpine` resolved to today (1.31.4); production's Dockerfile is
`FROM nginx:alpine` **unpinned**, so the production version is not known to match.

**Teardown (confirmed):**

```
docker rm -f carcare-trial-proxy carcare-trial-db   -> both removed
kill -9 <pid on :18080>                              -> app stopped
rm -rf /tmp/carcare-trial-data                       -> removed
rm -rf .playwright-mcp/trial-img                     -> removed
ports 18080 / 13306 / 18090                          -> free
git status --porcelain                               -> clean (only this report is new)
```

Screenshots kept for reference under the git-ignored `.playwright-mcp/` (same convention as
the archived trial):
`.playwright-mcp/oq1-justover-after-save.png`, `.playwright-mcp/oq1-farover-after-save.png`
(byte-identical — the client-visible state is the same for "just over" and "far over").
Browser console log: `.playwright-mcp/console-2026-08-30T12-23-29-774Z.log`.

## Part C — Client 1.2.5 under an oversized-body rejection

### Which server property bounds a JSON body

The image travels as a base64 string inside a JSON `@RequestBody` (`VehicleDto` →
`vehicleDetails.image: byte[]`, `POST /api/vehicle`, `PUT /api/vehicle/{id}`). Probed each
candidate on the disposable app (command-line `-D` only, nothing on disk), 50 KB cap
against a 2 MB JSON body:

| Property tested | Value | 2 MB JSON body result |
|---|---|---|
| `server.tomcat.max-http-form-post-size` | `51200` | **HTTP 201** — not enforced (applies to `application/x-www-form-urlencoded` only) |
| `server.tomcat.max-swallow-size` | `51200` | **HTTP 201** — not enforced (governs discard of unread body, not admission) |
| `spring.servlet.multipart.max-request-size` (+ `max-file-size`) | `50KB` | **HTTP 201** — not enforced (multipart only; this is not multipart) |

Baseline, bare app, no cap: a **60 MB** decoded image (83,886,457-byte wire body) → **HTTP
201**, file written to the volume at full size. Jackson 2.15's `StreamReadConstraints`
(20 M-char string cap) did **not** fire on the base64→`byte[]` path at 60 MB.

**Conclusion: none of the obvious Spring/Tomcat properties bounds this request shape.** A
bare Spring Boot app accepts an unbounded base64 JSON body. The only bound in the
production topology is **NGINX `client_max_body_size 4M`** in
`src/main/docker/reverseproxy/nginx.conf`.

Verified through the trial NGINX (`:18090`):

| Body | Wire size | Result | `size_upload` | time |
|---|---|---|---|---|
| small | 1,745 B | 201, `X-carcareApp-alert` present | 1,745 | 0.05 s |
| under cap | 3,333,713 B (~3.3 MB) | 201 | 3,333,713 | 0.08 s |
| just over | 4,267,045 B (~4.3 MB) | **413** `text/html`, `Connection: close` | **0** | 0.0015 s |
| 10 MB | 13,981,393 B | **413** identical | **0** | 0.0016 s |
| 60 MB | 83,886,457 B | **413** identical | **0** | 0.0014 s |

`size_upload=0` on every rejection: NGINX rejects on the `Content-Length` header before
reading the body. The Spring app logs **nothing** for these — the request never reaches
Tomcat. This satisfies FR-005's "before it is buffered into memory or written to the
volume" **for traffic that passes through the proxy**.

413 response body is the stock NGINX page (`text/html`, ~185–585 bytes depending on
padding). It carries **no** `X-carcareApp-alert` / `X-carcareApp-error` header, so the
client's notification middleware (which keys on `app-alert` / `app-error` header suffixes)
has nothing to match, and there is no JSON `data.message` to fall back to either.

### Baseline through the real UI (client 1.2.5, via the proxy)

The **create-vehicle** modal (`#/app/new`) has only Make / Model / License plate / Fuel
type — **no image field**. Images are attached only through the **edit** modal
(`#/app/details/{id}/edit`), which does `GET /api/vehicle/{id}` then
`PUT /api/vehicle/{id}` with the full DTO including the base64 image.

- Created vehicle `BASE0001` via the create modal → `POST /api/vehicle` **201**.
- Edited it, attached `small.png` (308 B real PNG) via the "Image" file picker, Save →
  `PUT /api/vehicle/3` **200**. Modal closed, navigated to `#/app/details/3`.
- Volume: one new file `db3aed3c-…png` (308 B, `file` → `PNG image data, 120 x 120`).
- `GET /api/vehicle/3` returns `imageContentType: "image/png"` and the image round-trips.
- Success toast: react-toastify container present; the toast is short-lived and had already
  auto-dismissed by the time the details view settled. (The archived contract trial already
  recorded the vehicle create/update success toasts — "added" / "updated" — via a
  MutationObserver; not re-instrumented here.)

### Oversized image through the real UI

**Just over the cap** — attached `justover.jpg` (3,416,340 B → ~4.55 MB JSON body), Save:

- `PUT /api/vehicle/3` → **413**, `content-type: text/html`, `connection: close`,
  response body = NGINX's `413 Request Entity Too Large` HTML page (585 B with padding).
- Browser console gained exactly two lines:
  ```
  [ERROR] Failed to load resource: the server responded with a status of 413 (Request Entity Too Large) @ .../api/vehicle/3
  Error: Request failed with status code 413
      at e.exports (.../vendors.aeca5ce7ffebc06fb0ac.chunk.js:90:35372)
  ```
  i.e. an axios rejection surfaced to the console; no application-level handling.
- **Client render: nothing.** The edit modal closed and the app navigated to
  `#/app/details/3` — the exact same transition as a successful save. `react-toastify`'s
  `Toastify__toast-container` stayed empty (`childCount: 0`). No toast, no inline error, no
  banner, no alert, no modal left open, no spinner. The details view rendered fully and
  correctly, still showing the **previous** (308 B) image.
- No crash, no wedged form, no stuck spinner, no full-page error.

**Far over the cap** — attached `big.jpg` (26,258,092 B → ~35 MB JSON body), Save:

- `PUT /api/vehicle/3` → **413**, identical `text/html` / `connection: close` response.
  XHR duration 183 ms (vs 15 ms for "just over") — the browser transmitted more of the body
  before NGINX cut it, but the outcome is unchanged.
- Console: same two lines again (413 resource + axios `Error: Request failed with status
  code 413`).
- Client render: **identical to "just over"** — silent navigation to the details view, no
  toast, no error. The two result screenshots are byte-for-byte identical.

**Recovery:** immediately after the two 413s, reopened the edit modal (fields pre-filled
fine from `GET /api/vehicle/3` 200), attached `small.png` again, Save → `PUT /api/vehicle/3`
**200**. The client was never wedged; a normal-size retry succeeds with no intervening
reload.

**Volume side-effect:** after both rejected submits, `/tmp/carcare-trial-data` was
unchanged — 3 files, none newer than the last *successful* PUT, no partial writes, and the
app log had no entry for the rejected requests. Final state: `224fe90a-…png` (2.5 MB) and
`71363357-…png` (1 KB) from earlier curl probes, plus `cd5605a6-…png` (308 B) from the
recovery PUT. FR-005's "rejection precedes the write" holds for the proxied path.

## OQ1 answer

**Effective server property for this request shape:** *none of the obvious ones.*
`server.tomcat.max-http-form-post-size`, `server.tomcat.max-swallow-size`, and
`spring.servlet.multipart.max-request-size` were each verified not to bound a JSON
`@RequestBody`. The only bound in the production topology is the reverse proxy's
`client_max_body_size 4M` (`src/main/docker/reverseproxy/nginx.conf`), which returns a
`text/html` **413** on the `Content-Length` header alone, before the body is read or the
request reaches Tomcat. Implementing FR-005 as an in-application guarantee therefore
requires **new code** — a servlet `Filter` / `OncePerRequestFilter` that checks
`Content-Length` (and defensively caps the stream) and returns 413 before the body is
consumed — not a configuration property.

> **Corrected after the report was filed (verified over SSH, read-only).** Two facts about
> the proxy were assumed rather than measured, and both are wrong in ways that matter:
>
> 1. **The deployed proxy is not this repository's.** The running container is `nginx-proxy`
>    from stock `nginx:alpine` (nginx **1.29.0**), mounting `/home/kacper/services/nginx/`.
>    `src/main/docker/reverseproxy/` is **not deployed at all** — nothing keeps it in sync
>    with the live config. That both files happen to say `4M` is a coincidence, not
>    corroboration.
> 2. **The 4M limit is global, not CarCare's.** `client_max_body_size 4M` sits at
>    `/home/kacper/services/nginx/nginx.conf:18`, in the **`http` block**, and
>    `conf.d/default.conf` carries no per-server override. It is therefore shared by every
>    service behind that proxy — `metube`, `keycloak`, `oauth2-proxy` and CarCare alike.
>
> The recommendation below to "align NGINX to the same value" would consequently tighten the
> limit for three unrelated services. Any proxy-side change for this work must go in a
> CarCare-specific `server`/`location` block, leaving the `http`-level default untouched.
>
> Also noted while verifying: production runs
> `registry.gitlab.com/kkasztel_carcare/server/app:**1.3.10**` while this repository is at
> **1.3.11** — production is one release behind the branch baseline.

**Client-visible failure mode (client 1.2.5, cannot be changed):** a **silent failure that
renders as a false success**. On the 413 the client logs an axios error to the console, then closes the
edit modal and navigates to the vehicle details view exactly as if the save had succeeded.
There is **no toast, no error banner, no message of any kind**; the previously stored image
simply remains. The app does not crash, the form does not wedge, no spinner hangs, and the
user can retry immediately — a normal-size image then saves cleanly. The only symptom a
user could notice is that their new image silently didn't take.

Two conclusions follow, and they must not be collapsed into one. **For FR-005 this is a
positive finding**: the frozen client tolerates a boundary-level 413 without breaking, so
FR-005 can be implemented without fear of a wedged or crashed UI. **But the behaviour itself
is a defect, not a benign outcome** — the modal-close-and-navigate transition is
*byte-for-byte identical* to a successful save, so the user is affirmatively told their image
saved when it did not. Silent-and-recoverable would be benign; silent-and-indistinguishable-
from-success is a false success. The client cannot be changed, so shipping FR-005 means
knowingly accepting this failure mode. Record it in the roadmap as a **known limitation of
FR-005 against client 1.2.5**, not as a clean pass.

**Recommended FR-005 limit:** **2 MiB (2,097,152 bytes) on the request body**, enforced in
the application by a pre-buffer `Content-Length` filter. A matching proxy-side value is
optional and, per the correction above, must be scoped to a CarCare `server`/`location`
block rather than applied to the shared `http`-level default.

Justification from Part A:
- Largest image on the volume today: 110,693 B raw ≈ **147,591 B (~144 KB)** as a base64
  JSON body. Everything else is ≤ 65,885 B raw (~86 KB as a body).
- 2 MiB is **~14×** the largest real payload's body size — a legitimate ~1 MB photo
  (→ ~1.4 MB body) still passes with room to spare, while the current *unbounded* exposure
  is cut to a fixed 2 MiB ceiling.
- 2 MiB is small enough to be a meaningful bound: it caps a single request's heap/described
  base64 buffer at ~2 MB rather than the tens of MB shown to be accepted today.
- If a single cross-layer number is preferred over headroom, **4 MiB** (the value already
  in `nginx.conf`, ~28× the largest real payload) is also defensible and requires no proxy
  change — but it leaves twice the necessary slack.

Marked **partial**, not resolved, because: (a) the number rests on an n=9 sample and should
be ratified against a larger/rolling sample before it is frozen into a requirement;
(b) FR-005 needs new code, since no property delivers the in-app guarantee. The production
proxy value — originally listed here as unverified — has since been confirmed at 4M, but as
a *shared* `http`-level default on a proxy this repository does not own; see the correction
under "Effective server property" above.

## Not measured

- ~~**Production NGINX config as deployed.**~~ **RESOLVED after filing** — verified over
  SSH. `client_max_body_size 4M` at `/home/kacper/services/nginx/nginx.conf:18`, in the
  `http` block, with no per-server override. The deployed proxy is `nginx-proxy` from stock
  `nginx:alpine`, *not* the repository's `src/main/docker/reverseproxy/` image. The `4M`
  figure used throughout Part C is correct, but for a different config file than assumed,
  and it is shared with three other services.
- ~~**Production NGINX version.**~~ **RESOLVED after filing** — nginx **1.29.0** in the
  deployed `nginx-proxy` container, against 1.31.4 in the trial. Error-page bytes were not
  compared, but the 413-on-`Content-Length` behaviour is the same on both.
- **Deployed application version.** Production runs app image tag **1.3.10**; this
  repository is at **1.3.11**. What differs between them was not established, and it means
  the trial exercised a *newer* server than production runs.
- **Direct-to-Tomcat path in production.** Whether anything can reach the app container
  bypassing the proxy (and would thus hit no body bound at all) was not established — it
  would require inspecting the prod compose/network, which is out of scope.
- **Behavior exactly at the 4 MiB boundary.** Tested clearly-under (3.3 MB) and
  clearly-over (4.3 / 14 / 60 MB wire, plus ~4.55 MB and ~35 MB via the UI). A body within
  a few KB of the limit was not probed.
- **Chunked transfer-encoding / no `Content-Length`.** Browser XHR of a JSON string always
  sets `Content-Length`, so the real-world shape always carries one; a chunked upload with
  no length header (where NGINX would have to count bytes as they stream) was not tested.
- **`Expect: 100-continue` handling by the browser.** `curl` sent it (NGINX withheld
  `100 Continue` and returned 413); the browser XHR did not send it. Both still received a
  clean 413. Server-side 100-continue negotiation specifics were not probed further.
- **Success/error toast contents for the vehicle edit flow.** The success toast auto-
  dismissed before capture and was not re-instrumented; the *absence* of any toast on the
  413 was confirmed directly (empty `Toastify__toast-container`).
- **`GoldenDatasetMirror` / real production dataset.** The trial DB was freshly seeded with
  lookup data only; no production vehicles/images were imported (and must not be).
- **Jackson `StreamReadConstraints` upper limit.** Confirmed *not* triggered at 60 MB; the
  exact threshold (if any) for a base64→`byte[]` field was not bisected.
- **p90/p99 as stable statistics.** With n=9 these are nearly meaningless; only min /
  median / max are trustworthy.
- **Whether any stored image name is empty-string in the DB.** The volume has no
  extensionless or zero-byte files, but the `vehicle_details.image` column values were not
  read (no DB access), so a row pointing at `""` (the `save()` failure sentinel) cannot be
  ruled out.

## Implications for the roadmap

**FR-006 — implementable now.** Byte-level detection over the current volume yields exactly
{`image/png`, `image/jpeg`}. Adopt that as the allowlist. Apply the detection on the
**upload** path (replace the trust in `VehicleDetailsDto.imageContentType` with a
byte-sniff of the decoded `image`, reject anything not in the allowlist, and derive the
stored extension from the sniffed type). Consider also switching `VehicleDetailsMapper`
line 35 from `tika.detect(filename)` to a byte-sniff so responses stop mislabeling the
four `.bin` images as `application/octet-stream` — optional, but it removes the only
loose end.

**FR-005 — implementable, with new code and one accepted defect.** No Spring or Tomcat
property bounds a JSON `@RequestBody`; the in-app guarantee needs a pre-buffer
`Content-Length` filter written for this change. This is the only FR in the change whose
implementation surface *grew* during measurement — size it accordingly. Shipping it also
means accepting that client 1.2.5 renders the rejection as a false success (see the OQ1
answer); that limitation belongs in the roadmap's risk column, not buried as a UX footnote.
Any proxy-side alignment must be CarCare-scoped, since the live 4M ceiling is shared.

**FR-007 — implementable now, with one constraint.** No file currently on the volume falls
outside the {PNG, JPEG} allowlist, so nothing stored today becomes unloadable. **Constraint
introduced by this measurement:** keep the load/read path (`ImageStorageServiceImpl.load`)
a pure byte-passthrough keyed on filename — do **not** add allowlist enforcement there, or
the four legacy `.bin` files (PNG content, non-image name) would stop loading and FR-007
would break. FR-006 enforcement belongs on write only.

**FR-006 + FR-007 — no collision** on the 2026-08-30 volume state. This verdict is only as
current as the volume; if images are added before the change ships, re-run the Part A
byte-level distribution and re-check that the allowlist still covers everything.

**FR-005 — partially unblocked.**
- *Unblocked:* the frozen client 1.2.5 handles a boundary 413 gracefully (silent, no crash,
  no wedge, clean retry), so the rejection can be added without breaking the UI. A
  concrete, justified limit (2 MiB body) is proposed.
- *New constraint:* FR-005 cannot be satisfied by configuration. No Tomcat/Spring property
  bounds a JSON `@RequestBody`; the only existing bound is NGINX `client_max_body_size`,
  which is the *proxy*, not "the server". Implementing FR-005 as written ("the server
  rejects…") requires a new pre-buffer filter in the application that checks
  `Content-Length` (and caps the stream for the no-length case) and returns 413 before
  reading the body. The NGINX `client_max_body_size` should be aligned to the same value so
  the two layers agree.
- *Still open:* ratify the 2 MiB figure against a larger sample than n=9, and verify the
  production proxy's actual `client_max_body_size` and NGINX version on the host.
