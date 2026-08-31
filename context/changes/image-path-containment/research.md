---
date: 2026-08-31T13:41:58+02:00
researcher: Kacper Kasztelanic
git_commit: 87e7e435bb073d38a0a1ddb864901b11c799ac85
branch: refactor
repository: carcare-server
topic: "Vehicle image write path: containment (S-05), write ordering (S-02), format allowlist (S-04)"
tags: [research, codebase, image-storage, path-containment, tika, transactions, security-baseline]
status: complete
last_updated: 2026-08-31
last_updated_by: Kacper Kasztelanic
---

# Research: Vehicle image write path — containment, write ordering, format allowlist

**Date**: 2026-08-31T13:41:58+02:00
**Researcher**: Kacper Kasztelanic
**Git Commit**: `87e7e435bb073d38a0a1ddb864901b11c799ac85`
**Branch**: `refactor`
**Repository**: `carcare-server`

## Research Question

Invoked as `/10x-research image-path-containment` (roadmap **S-05**, FR-008). Scope was widened
at the clarification step to cover the whole vehicle image path as one subject — **S-02
`image-write-ordering`**, **S-04 `image-format-allowlist`**, **S-05 `image-path-containment`** —
because all three rewrite the same class and its callers.

A second instruction was given: the PRD's accepted premise for FR-008 — *"it guards a path no code
can reach, since filenames are server-generated UUIDs with no client influence"* — was to be
**verified against the code**, not inherited from the shaping record.

## Summary

**The FR-008 premise holds.** Client-controlled input cannot reach the `name` parameter of
`load`/`delete`. That value is always read from the persisted `VehicleDetails.image` column, which
is only ever written with the server-generated `UUID + extension` string that `save()` returns.
Verified across all four call sites; no MapStruct generation is involved (these "mappers" are
hand-written `@Service` classes), so there is no hidden mapping that writes `image` from request
JSON.

**But the premise is narrower than the shaping record states, in a way that matters.** The claim
is "no client influence." In fact one client-controlled, entirely unvalidated string —
`VehicleDetailsDto.imageContentType` — *does* reach `save()` and *does* participate in choosing the
stored filename's extension. Containment survives only because of a property of a third-party
data file, not because of anything this application enforces: Tika's MIME registry rejects
traversal-shaped strings as invalid media-type names, and none of its 1,320 glob patterns contains
`/` or `..`. That is a true statement about tika-core 2.7.0, not an invariant the code holds. The
requirement is better characterised as *"currently unreachable, on a dependency's guarantee"*
rather than *"unreachable by construction."*

**Three findings beyond the slice definitions:**

1. **`prepareImagePath` has an ordering defect independent of containment.** It calls
   `.normalize()` *before* `.toAbsolutePath()`, so for an escaping input the path it returns is
   not even normalised — `../../../../etc/passwd` comes back still containing `..` segments.
   Any containment check written on top of the current expression would be checking an
   unnormalised path. The fix is `.toAbsolutePath().normalize()` and then a `startsWith(root)`
   test — the order matters as much as the check.
2. **A new (minor, authenticated-only) defect: unbounded MIME-registry growth.** `MimeTypes`
   caches a JVM-lifetime static singleton, and `forName()` on a *syntactically valid but unknown*
   media type constructs and permanently registers a new entry. Since `imageContentType` is
   client-supplied and unvalidated, each distinct value an authenticated caller sends adds a
   permanent entry. Measured: registry grew 1613 → 1618 over five probes. S-04 closes this
   incidentally by no longer passing client input to `forName`.
3. **The roadmap's sequencing constraint is weaker than assumed.** It records that S-02 and S-04
   "rewrite the same write path" and that parallelising them would conflict. In fact the three
   slices land in **three disjoint methods across two files**. See "Sequencing" below — this may
   let S-04 come off its S-02 prerequisite.

**Test coverage for this path is close to zero**, which is the single largest risk to all three
slices. `ImageStorageServiceImpl` has no direct test of any kind; `load()` is not referenced
anywhere in `src/test`; no test exercises a create-or-update *with* an image through REST; and no
test anywhere asserts filesystem state after a rollback.

## Detailed Findings

### 1. The storage surface is one class, and it has not changed since 2019

Everything reachable under `application.data-directory.location` goes through
`service/impl/ImageStorageServiceImpl.java` — 72 lines, three public methods, one private path
helper. A sweep of `src/main/java` for `Files.`, `FileUtils.`, `new File(`, `Paths.get(` finds
hits in exactly one other file, `config/WebConfigurer.java:72,90`, and both resolve the static
asset root `target/www/` — neither touches the data directory.

Git history is the useful part here:

| Commit | Date | Effect on this class |
|---|---|---|
| `7169569` | 2019-01-03 | created |
| `2fdd23a` | 2019-01-04 | added the Tika extension lookup + try/catch in `save()` |
| `2584ae8` | 2019-01-04 | **last logic change** — null/empty guards on `load`/`delete` |
| `7966291` | 2020-05-20 | comment only (`//TODO refactor to be like in hrtool`) |
| `1818100` | 2022-05-15 | Lombok annotations / import reformatting, zero logic change |

`prepareImagePath`'s containment behaviour and `save`'s trust in the client content type are
**as originally written in January 2019**. Neither has ever been revisited. The `//TODO refactor`
comment is not explained anywhere in `context/**`.

### 2. Reachability — the FR-008 premise, verified

Four call sites, all injected as a field named `imageStorageService`:

| # | Call site | Argument | Origin |
|---|---|---|---|
| 1 | `service/mapper/VehicleDetailsMapper.java:34` | `load(vehicleDetails.getImage())` | persisted entity column |
| 2 | `service/mapper/VehicleDetailsMapper.java:51-52` | `save(dto.getImage(), dto.getImageContentType())` | **request body** |
| 3 | `service/impl/VehicleServiceImpl.java:76` | `delete(vehicle.getVehicleDetails().getImage())` | persisted entity column |
| 4 | `service/impl/AdminVehicleServiceImpl.java:132` | `delete(image)` | persisted entity column, captured at `:104` |

Call sites 1, 3 and 4 all pass a value obtained via `getVehicleDetails().getImage()` on an entity
fetched by numeric id (`vehicleScopeService.findActiveOwnedVehicle(id)` /
`vehicleRepository.findById(id)`). The client supplies only the `id` used for the lookup — never
the filename string. The column itself (`domain/VehicleDetails.java:71-73`,
`@Column(name = "image", length = 45)`) is written in exactly one place,
`VehicleDetailsMapper.java:51-53`, always from `save()`'s return value, which is
`UuidProvider.newUuid() + extension` (`ImageStorageServiceImpl.java:35`).

`AdminVehicleMapper` never references the image field at all. Confirmed there are **no MapStruct
interfaces** on the vehicle path — `VehicleMapper`, `VehicleDetailsMapper` and `AdminVehicleMapper`
are hand-written `@Service` classes. (This matters given `AGENTS.md`'s warning about generated
code: here there is none to miss.)

**Verdict: client input cannot reach `load`/`delete`'s `name`. FR-008 guards an unreachable path,
exactly as the PRD records.**

### 3. …with one qualification the shaping record does not capture

Call site 2 passes `vehicleDetailsDto.getImageContentType()` straight into `save()`. That field
(`service/dto/VehicleDetailsDto.java:19-20`) carries **no validation annotation**, and
`VehicleDto.vehicleDetails` (`service/dto/VehicleDto.java:35`) carries **no `@Valid`**, so the
controller's `@Valid @RequestBody VehicleDto` (`web/rest/VehicleResource.java:54,63`) does not
cascade into it. The value is fully client-controlled and unchecked when it reaches:

```java
// ImageStorageServiceImpl.java:34
String extension = MimeTypes.getDefaultMimeTypes().forName(fileType).getExtension();
```

So a client *does* influence the filename — it selects the extension. Whether that influence is
path-safe was measured directly against tika-core 2.7.0 (the pinned version), not assumed:

- `tika-mimetypes.xml` holds **1,320 glob patterns; 0 contain `/` or `..`**.
- `getExtension()` returns `extensions.get(0)`, a value built only from those globs (verified by
  `javap` on `MimeType.class`).
- Traversal-shaped strings are rejected before lookup — `forName("../../etc/passwd")` throws
  `MimeTypeException: Invalid media type name`, caught at `:39` and turned into `""`.

Containment therefore holds today, but it holds **because of the contents of a third-party data
file**, not because this application checks anything. Recording this because it changes how S-05
should be justified in its plan: it is not redundant belt-and-braces over a server-generated UUID,
it is the only thing that would still hold if that dependency's data changed or if a future caller
passes something else.

### 4. New finding — unbounded MIME-registry growth (minor, authenticated-only)

`MimeTypes.getDefaultMimeTypes()` is `static synchronized` and caches into a static
`DEFAULT_TYPES` / `CLASSLOADER_SPECIFIC_DEFAULT_TYPES` — a JVM-lifetime singleton. `forName()` on a
media type that parses but is not registered **constructs a new `MimeType` and adds it to that
singleton's map**. Measured against tika-core 2.7.0:

```
registry size at start: 1613
  forName("image/png")                 -> extension=".png"
  forName("image/jpeg")                -> extension=".jpg"
  forName("application/octet-stream")  -> extension=".bin"
  forName("not a mime type")           -> MimeTypeException: Invalid media type name
  forName("../../etc/passwd")          -> MimeTypeException: Invalid media type name
  forName("application/x-carcare-probe-0..4") -> extension=""   (x5)
registry size after 5 unknown-but-valid types: 1618
```

Because `imageContentType` is unvalidated, an authenticated owner sending N distinct valid-syntax
content types on vehicle create/update permanently adds N entries to a process-wide map. It is
slow, bounded by request volume, and requires authentication — low severity — but it is a real
unbounded-growth path reachable from a request field, and it is not recorded anywhere in
`context/**`. **S-04 removes it as a side effect** by ceasing to call `forName` with client input;
worth noting in that slice's plan so the benefit is not lost if S-04 is descoped.

Note also the `extension=""` result: an unknown-but-valid type yields a **bare UUID filename with
no extension**. The production measurement found no extensionless files, so this branch appears
never to have been exercised in production.

### 5. Containment behaviour, measured

`prepareImagePath` (`ImageStorageServiceImpl.java:68-71`) was extracted verbatim and run against
representative inputs (`location = "data"`):

| Input `fileName` | Resulting path | Contained? |
|---|---|---|
| `3f2b1c9e-….png` | `<cwd>/data/3f2b1c9e-….png` | yes |
| `../../../../etc/passwd` | `<cwd>/../../../etc/passwd` | **no** |
| `/etc/passwd` | `/etc/passwd` | **no** |
| `..` | `<cwd>` (the parent of `data`) | **no** |
| `sub/../ok.png` | `<cwd>/data/ok.png` | yes |
| `""` | `<cwd>/data` (the directory itself) | yes, but is a directory |

Two things to carry into the S-05 plan:

**The ordering is wrong, independently of the missing check.** The expression is
`Paths.get(location).normalize().resolve(fileName).normalize().toAbsolutePath()`. Because
`normalize()` runs before `toAbsolutePath()`, leading `..` segments cannot be collapsed and
survive into the returned path — see row 2, which still contains `..`. The correct form is
`.toAbsolutePath().normalize()`, *then* `startsWith(root)`. A containment check bolted onto the
current expression would be testing an unnormalised path.

**An absolute `fileName` bypasses containment entirely** (row 3) — `resolve` on an absolute path
discards the base. A `..`-only check would not catch this; the `startsWith(root)` test does.

Also worth deciding explicitly in the plan: `""` resolves to the data directory itself, and today
`load`/`delete` guard it with their own `isEmpty()` checks (`:50`, `:62`) rather than the path
helper doing so. Whether containment treats "resolves to the root itself" as contained or refused
should be a stated decision, not an accident — it interacts with the `""` sentinel below.

### 6. S-02 — write ordering, and an existing precedent to copy

`VehicleServiceImpl.editVehicle` (`:55-61`) is `@Transactional`, and inside it:

```java
private Vehicle updateVehicle(Vehicle vehicle, Vehicle updatedVehicle) {   // :75
    imageStorageService.delete(vehicle.getVehicleDetails().getImage());     // :76
    ...
```

Order of operations, which is worse than "delete before commit":

1. `vehicleMapper.vehicleDtoToVehicle(vehicleDto)` is evaluated **first** — this is what calls
   `save()`, so the **new** file is written to disk before anything else happens.
2. `updateVehicle(...)` runs and deletes the **old** file, synchronously.
3. Only then does `vehicleRepository::save` run; the flush/commit is later still.

A rollback after step 2 therefore leaves the old file destroyed *and* the new file orphaned, with
the DB row restored to a filename that no longer exists. `FileUtils.deleteQuietly` swallows any
failure silently. `addVehicle` has no old file, so only `editVehicle` is in scope.

**The fix pattern already exists in this codebase**, from the archived `archived-vehicle-purge`
change — `AdminVehicleServiceImpl.java:122-141` captures the filename before mutation, registers a
`TransactionSynchronization`, and deletes only in `afterCompletion(STATUS_COMMITTED)`, logging
rather than throwing on failure (a throw there would surface as a misleading 500 for an already
committed write). `TransactionSynchronizationManager` appears **nowhere else** in `src/main/java`
— that one usage is the whole precedent, and S-02 should mirror it rather than invent a variant.

Note the impl-review of that change already flagged the pattern's weak spot
(`context/archive/2026-08-29-archived-vehicle-purge/reviews/impl-review.md:81-87`): it ignores
`delete()`'s boolean return, so a permission or lock failure silently orphans the file. S-02 copies
the pattern; it should decide deliberately whether to copy that weakness too.

### 7. S-04 — the allowlist, and the `.bin` files explained mechanically

The production measurement (2026-08-30) found nine files: five PNG and four JPEG by byte content,
but four of them named `*.bin`. The mechanism is now confirmed rather than inferred —
`application/octet-stream`'s first glob in Tika's registry is `*.bin`, and the live probe above
returns `.bin` for it. So those four files are PNG bytes written under a `.bin` name because the
client declared a generic content type. That is precisely the defect S-04 fixes.

**No new dependency is needed.** Only `tika-core` 2.7.0 is declared (`pom.xml:50,162-166`; pinned
in `dependencyManagement` to override the transitive version from `poi-ooxml`). `tika-core` ships
its own magic-byte database and `DefaultDetector`, so `Tika#detect(byte[])` byte-sniffing is
already available — no `tika-parsers`.

One nuance the read/write split needs care on: `VehicleDetailsMapper.java:7,15,35` already holds a
`private final Tika tika = new Tika()` and calls `tika.detect(vehicleDetails.getImage())` on the
**read** path — but that argument is the *filename*, so this is extension-based detection. It is
why the four `.bin` files are reported to clients as `application/octet-stream` today. The
measurement session recorded switching it to a byte-sniff as *optional*. Flagging it because it is
easy to misread as an FR-007 violation: FR-007 forbids adding **allowlist enforcement** to the read
path (which would make those four files unloadable). Improving read-path *detection* is a
different thing and would not break FR-007 — but it is a client-visible contract change to
`imageContentType`, so it should be an explicit decision, not a drive-by.

### 8. The empty-string sentinel is invisible end to end

`save()` returns `""` on both `MimeTypeException` (`:39-41`) and `IOException` (`:41-43`). The
caller does not check it — `VehicleDetailsMapper.java:51-53` feeds the return value straight into
`.image(...)` on the entity builder, and the column has no `@NotNull`. So `""` is persisted
silently and the vehicle create/update **returns success**. On the way back out, `load("")` hits
the `isEmpty()` guard at `:50` and serves `default.png`.

A failed image save is therefore indistinguishable from "this vehicle never had an image", at every
layer, with only a log line (`"Could not save file."`, no filename, no exception) as evidence. This
is the mechanism behind S-02's open Unknown ("are any `vehicle_details.image` values the
empty-string sentinel?"). The question is still unmeasured — the measurement session had no DB
access — but note it cannot be answered from the volume, only from the column.

### 9. Test coverage — effectively none

| Area | Coverage |
|---|---|
| `ImageStorageServiceImpl` direct unit test | **none** — no such file exists |
| `load()` | **zero references in `src/test`**, including the `default.png` fallback |
| `save()` | only indirectly, via `SessionFixtures.imageFor` (`:159`), used by one test |
| Real file written and deleted on disk | one test: `AdminVehiclePurgeIT:67` |
| Create/update *with an image* through REST | **none** — `VehicleResourceIT`, `OwnerIsolationIT`, `AdminVehicleResourceIT` have no image assertions at all |
| Content-type / extension resolution, and its error branches | **none** |
| Filesystem state after a rollback | **none** anywhere in the suite |

`RequestBodyLimitIT:114,133` asserts image side effects are *skipped* on an oversized request
(`verify(imageStorageService, never()).save(...)`), which is the inverse of what these slices need.

Test data-directory configuration is worth a decision in the plan. `application-test.yml:76-78`
sets `location: data` — a **relative** path resolving to the repo root's gitignored `data/`
directory, shared by the whole suite, with no `@TempDir` and no per-class isolation. The one test
that writes real files (`AdminVehiclePurgeIT`) opts out of the class-level `@Transactional` with
`@Transactional(propagation = Propagation.NOT_SUPPORTED)` and cleans up in `try/finally`, computing
paths with its own private copy of `prepareImagePath`'s logic (`:145-148`) — note that copy will
need updating in lockstep with S-05, or it will silently stop matching the production helper.

The roadmap's S-02 risk note says the work must be built and tested "against a scratch directory,
never the production volume." That is satisfied today by construction, but only incidentally.

### 10. Sequencing — the roadmap constraint looks weaker than recorded

The roadmap makes S-04 depend on S-02 because both "rewrite the same write path," and warns that
parallelising them would conflict. Mapped against the actual code, the three slices land in three
**disjoint** methods across two files:

| Slice | File | Method |
|---|---|---|
| S-02 | `service/impl/VehicleServiceImpl.java` | `updateVehicle` (`:75-83`) |
| S-04 | `service/impl/ImageStorageServiceImpl.java` | `save` (`:28-45`) |
| S-05 | `service/impl/ImageStorageServiceImpl.java` | `prepareImagePath` (`:68-71`) |

S-02 does not touch `ImageStorageServiceImpl` at all. S-04 and S-05 share a file but not a method.
The textual-conflict argument for the S-02 → S-04 prerequisite does not appear to survive contact
with the code.

This is offered as evidence for a decision, not as the decision — there may be a semantic ordering
argument the roadmap is encoding that a diff-overlap analysis cannot see (for instance, wanting the
rollback-safety net in place before changing what gets written). Worth putting to the owner at
plan time, since dropping the prerequisite would move S-04 from `proposed` to independently
schedulable.

## Code References

- `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:28-45` — `save()`; client-content-type extension lookup, `""` on failure
- `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:47-58` — `load()`; `default.png` fallback, no test coverage
- `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:60-66` — `delete()`; `FileUtils.deleteQuietly`, silent failure
- `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:68-71` — `prepareImagePath()`; **the S-05 target**, normalize/toAbsolutePath ordering defect
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:75-83` — `updateVehicle`; **the S-02 target**, delete before commit
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:46-61` — `addVehicle` / `editVehicle` transaction boundaries
- `src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java:122-141` — **the pattern S-02 should copy**: post-commit file delete via `TransactionSynchronization`
- `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java:34` — read path `load()`
- `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java:35` — `tika.detect(filename)`, extension-based, source of the `.bin` → octet-stream reporting
- `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java:51-53` — the only write of `VehicleDetails.image`
- `src/main/java/com/kasztelanic/carcare/service/dto/VehicleDetailsDto.java:19-20` — `image` / `imageContentType`, unvalidated
- `src/main/java/com/kasztelanic/carcare/service/dto/VehicleDto.java:35` — `vehicleDetails` without `@Valid`, so validation does not cascade
- `src/main/java/com/kasztelanic/carcare/domain/VehicleDetails.java:71-73` — `@Column(name = "image", length = 45)`
- `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:17,36-41` — `DataDirectory.location` binding
- `src/main/resources/config/application-dev.yml:67-68` — `location: data`
- `src/main/resources/config/application-prod.yml:90-91` — `location: /home/jhipster/data`
- `src/test/resources/config/application-test.yml:76-78` — `location: data`, relative, shared
- `src/test/java/com/kasztelanic/carcare/web/rest/AdminVehiclePurgeIT.java:145-148` — duplicated copy of `prepareImagePath` logic that must track S-05
- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:159` — `imageFor(...)`, the only fixture writing real image bytes
- `pom.xml:50,162-166` — `tika-core` 2.7.0, pinned to override the `poi-ooxml` transitive

## Architecture Insights

- **The data directory has exactly one gateway.** `ImageStorageServiceImpl` is the sole reader and
  writer under `application.data-directory.location` in all of `src/main/java`. That is what makes
  S-05 genuinely cheap: one private helper is the complete enforcement point for read, write and
  delete alike. It is also what makes the duplicated copy of that logic in `AdminVehiclePurgeIT`
  worth eliminating rather than updating.
- **File deletion and transactions are already understood here, once.** The purge change
  established the `afterCompletion(STATUS_COMMITTED)` discipline with the reasoning written into a
  comment at the call site. That reasoning was never propagated back to the older
  `VehicleServiceImpl` path, which predates it by years. S-02 is less "design a fix" than "apply
  the decision already made in this repo to the one place that missed it."
- **Failure is silent by default along this whole path** — `save()` returns `""`, `delete()`
  returns an ignored boolean via `deleteQuietly`, `load()` falls back to `default.png`, and none of
  the three surfaces anything to the client. Every slice here should be checked against the
  question "does this fail loudly enough to notice?", because the surrounding code's answer is
  consistently no.
- **The client contract constrains the write path but not the read path**, and the distinction is
  subtle enough to be worth restating in each plan: FR-007 requires already-stored files to stay
  loadable, which forbids allowlist *rejection* on read, not improved *detection* on read.

## Historical Context (from prior changes)

- `context/changes/security-baseline/oq-resolution.md` — the 2026-08-30 production measurement.
  Nine files at `/home/kacper/carcare/data/data`, 388K total; byte-level types exactly
  {5 × `image/png`, 4 × `image/jpeg`}; four PNGs named `*.bin`, byte-identical at 14,366 bytes
  (a repeated placeholder); largest file 110,693 B. No extensionless, zero-byte, symlink or
  sub-directory entries. Explicitly **not** measured: the `vehicle_details.image` column (no DB
  access), so the empty-string sentinel remains an open question.
- `context/foundation/shape-notes.md:140-145` — the origin of S-05, recorded as "Additional
  observation, not yet ruled in or out": `resolve` on an absolute path or a `..` name escapes the
  data directory and the trailing `normalize()` does not restore containment. This research
  confirms that observation and adds the normalize/toAbsolutePath ordering detail it did not note.
- `context/foundation/shape-notes.md:108-116` — the origin of S-02 and S-04, both correctly
  identified at shaping time.
- `context/archive/2026-08-29-archived-vehicle-purge/change.md:56` and `plan.md:340-380` — owner
  decision P6 and its implementation: the post-commit image delete S-02 should mirror.
- `context/archive/2026-08-29-archived-vehicle-purge/reviews/impl-review.md:81-87` — finding F5,
  that the callback ignores `delete()`'s return value; relevant to how faithfully S-02 copies it.
- `context/archive/2026-08-29-archived-vehicle-purge/research.md:188-199` — "Images are already
  orphaned today"; orphaned files are a pre-existing condition none of these slices claims to fix.
- `context/archive/2026-08-30-request-body-limit/plan.md:89-246` — the reusable precedent from S-03:
  `ProblemDetail` error conventions for pre-`DispatcherServlet` rejections, and the three-tier test
  harness (raw unit / registration contract / full-context IT). The error-shape convention is
  reusable by S-04 if it starts rejecting uploads; the filter mechanics are not, since S-04 and
  S-05 are logic changes inside a service.
- `context/foundation/health-check.md:168` — already recorded that
  "`ImageStorageServiceImpl`'s error handling is existing uncovered branching."
- `context/foundation/roadmap.md:355-356` (Parked) — a future `minio-object-storage` change would
  delete S-05's path logic entirely. Known and accepted when FR-008 was kept.

## Related Research

- `context/changes/security-baseline/oq-resolution.md` — production volume measurement (primary
  source for the file inventory used throughout)
- `context/archive/2026-08-29-archived-vehicle-purge/research.md` — image lifecycle and storage
  layout
- `context/archive/2026-08-30-request-body-limit/research.md` — request-boundary analysis; the
  base64-in-JSON payload shape shared by these slices

## Open Questions

1. **Does any `vehicle_details.image` value hold the empty-string sentinel?** Carried from S-02;
   still unmeasured, and unanswerable from the volume — it needs a read of the column. Finding
   §8 explains why such rows are otherwise invisible. Owner: user. Block: no.
2. **Should the S-02 → S-04 prerequisite stand?** §10 shows the three slices touch disjoint
   methods, so the recorded textual-conflict rationale does not hold. If there is a semantic
   ordering reason, it should be recorded; if not, S-04 becomes independently schedulable.
   Owner: user, at plan time. Block: no.
3. **Does containment treat "resolves to the data directory itself" as contained or refused?**
   The `""` input reaches the root path; today `load`/`delete` guard it separately. Needs an
   explicit decision so the guard lives in one place. Owner: plan. Block: no.
4. **Should `VehicleDetailsMapper.java:35` switch from filename-based to byte-based detection?**
   Would make the four legacy `.bin` files report `image/png` instead of `application/octet-stream`
   to the client. Not an FR-007 violation, but a client-visible contract change against a frozen
   client 1.2.5. Recorded as optional by the measurement session. Owner: user. Block: no.
5. **Should S-04 also validate `imageContentType`, or just stop using it?** Simply ignoring it on
   the write path closes the registry-growth defect (§4) and is the smaller change; validating it
   additionally would keep the field meaningful for the response contract. Owner: plan. Block: no.
6. **Is the shared, non-isolated test `data/` directory acceptable for the new tests?** Three
   slices will add filesystem assertions to a suite that currently has one such test and no
   isolation. Owner: plan. Block: no.
