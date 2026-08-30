---
project: "CarCare Server"
version: 1
status: draft
created: 2026-08-30
updated: 2026-08-30
prd_version: 1
main_goal: quality
top_blocker: none
---

# Roadmap: CarCare Server — Security Baseline

> Derived from `context/foundation/prd.md` (v1), the measurement session recorded in
> `context/changes/security-baseline/oq-resolution.md`, and a probed codebase baseline.
> Edit-in-place; archive when superseded.
> Items are listed in dependency order. "At a glance" is the index.
>
> The roadmap this replaces covered the platform-foundation change, all twelve of whose
> items are `done`; it is archived at
> `context/foundation/archive/roadmap-2026-08-30-foundation.md`.

## Vision recap

The production JWT signing key is committed to this repository and the deployment does not
override it: the production configuration carries a hard-coded base64 default behind an
environment-variable placeholder, the compose file never sets that variable, and the token
provider decodes whatever it receives without checking it differs from the shipped value.
Anyone with read access to this repository can forge a token for any login, including an
administrator — and because authentication is stateless, there is no session to revoke once
a forged token is in circulation. Three lower-severity defects sit around it in the image
and production-surface code. This change closes all of them without moving the client
contract, migrating data, or changing who may do what.

## North star

**S-01: the server refuses to run on a key that exists in version control** — the exposure
is closed only at the moment the committed default is gone *and* the application will not
boot without an externally supplied key; everything else in this change is hardening around
that fact.

> *North star* here means the smallest end-to-end slice whose successful delivery would
> prove the change worked — placed as early as its prerequisites allow, because the rest of
> the roadmap only matters if this holds. The PRD names it directly: "This is the north
> star. Everything else in the change matters only if this holds."

## At a glance

| ID   | Change ID                      | Outcome (user can …)                                          | Prerequisites | PRD refs                       | Status   |
| ---- | ------------------------------ | ------------------------------------------------------------- | ------------- | ------------------------------ | -------- |
| F-01 | `deployment-key-delivery`      | (foundation) deployment supplies the signing key from the host | —             | FR-001, US-01                  | ready    |
| S-01 | `external-signing-key`         | operator deploys with no usable key in version control         | F-01          | FR-001, FR-002, FR-003, US-01, US-02 | proposed |
| S-02 | `image-write-ordering`         | owner's stored image survives a failed vehicle update          | —             | FR-004, FR-007                 | ready    |
| S-03 | `request-body-limit`           | server refuses an oversized request body before buffering it   | —             | FR-005                         | ready    |
| S-04 | `image-format-allowlist`       | server stores only genuine PNG/JPEG uploads                    | S-02          | FR-006, FR-007                 | proposed |
| S-05 | `image-path-containment`       | every image path resolves inside the data directory or refuses | —             | FR-008                         | ready    |
| S-06 | `production-surface-reduction` | production build ships without test-data generation            | —             | FR-009                         | ready    |

## Streams

Navigation aid — groups items sharing a Prerequisites chain. Canonical ordering lives in the
dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                    | Chain             | Note                                                                                  |
| ------ | ------------------------ | ----------------- | ------------------------------------------------------------------------------------- |
| A      | Signing key              | `F-01` → `S-01`   | The north-star chain. F-01 exists to make S-01's fail-fast safe to deploy.             |
| B      | Image write path         | `S-02` → `S-04`   | Sequential because both rewrite the same write path; parallelising them would conflict. |
| C      | Request boundary         | `S-03`            | Standalone — a new filter ahead of the controllers, touching no image code.            |
| D      | Hardening & surface      | `S-05` / `S-06`   | Two small independent slices, parallel with each other and with every other stream.    |

## Baseline

What is already in place as of 2026-08-30 (probed, then confirmed). Foundations below assume
these are present and do **not** re-scaffold them.

- **Frontend:** present — React client 1.2.5 consumed as a prebuilt Maven artifact into
  `target/www/`; frozen, no node build in this project.
- **Backend / API:** present — Spring Boot 3.1.5 layered monolith, controllers under
  `web/rest/`.
- **Data:** present — MariaDB in dev/prod, H2 in tests, Liquibase 4.20 with dated changelogs.
  This change adds no schema work.
- **Auth:** present — stateless JWT (`security/jwt/TokenProvider`, `JwtFilter`), two roles,
  ownership enforced at the persistence boundary. This change touches the *provenance* of
  the signing key, not the model.
- **Deploy / infra:** present — but **not** where this repository suggests. The live
  deployment is compose project `services`, config file `/home/kacper/services/carcare.yml`,
  in a separate private git repository, with secrets supplied by native Compose substitution
  from a gitignored `~/services/.env` (encrypted counterpart `.env.gpg` is committed). That
  existing mechanism is what F-01 extends. This repository's `src/main/docker/app.yml`,
  `env-template` and `deploy.sh` describe a **superseded** path — `deploy.sh` `sed -i`s
  placeholders into `app.yml` destructively and starts a `carcare-app` container that is not
  running. Do not plan against them.
- **Observability:** present — Actuator plus Prometheus under `/management`.

## Foundations

### F-01: Deployment supplies the signing key from the host environment

- **Outcome:** (foundation) the deployment reads the signing key from an uncommitted host
  environment file, through the same mechanism it already uses for the database and mail
  passwords — while the committed default is still in place, so nothing can fail to boot.
- **Change ID:** `deployment-key-delivery`
- **PRD refs:** FR-001 (delivery half), US-01
- **Unlocks:** S-01. This foundation exists solely to make S-01's fail-fast check safe to
  deploy. It also discharges the rollout-ordering constraint the PRD records under Open
  Question 3 and `shape-notes.md` parks under `## Forward: technical-roadmap`: "deployment
  key injection must land before or with the fail-fast check."
- **Prerequisites:** —
- **Parallel with:** S-02, S-03, S-05, S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Sequenced first because the reverse order is the one failure the PRD explicitly
  refuses to accept — shipping the fail-fast check before the host supplies a key converts a
  forgotten environment line into a boot outage. Deployed on its own this slice is inert: the
  committed default remains as a fallback, so a mistake here cannot take production down.
  **The delivery mechanism already exists and works** (verified on the host 2026-08-30): the
  live compose file `/home/kacper/services/carcare.yml` already resolves
  `${CARCARE_MYSQL_USER}`, `${CARCARE_MYSQL_PASSWORD}` and `${CARCARE_MAIL_PASSWORD}` from a
  gitignored `~/services/.env`, so this slice adds one variable to a file that already has
  three. The main risk is therefore not the mechanism but the **variable name**: whether
  Spring's relaxed binding actually populates `application.security.authentication.jwt.base64-secret`
  from the `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` placeholder in
  `application-prod.yml:105`, or whether that name is a legacy leftover that would silently
  fail to bind. Verify empirically — if the name is wrong, F-01 ships a variable nothing reads
  and S-01 then removes the default from an application with no other key source.
- **Status:** ready

## Slices

### S-01: The server refuses to run on a key that exists in version control

- **Outcome:** an operator deploys the server with no usable signing key anywhere in version
  control, and the application fails fast — naming the missing configuration — if the key is
  absent or still equals the previously-committed value.
- **Change ID:** `external-signing-key`
- **PRD refs:** FR-001 (removal half), FR-002, FR-003, US-01, US-02
- **Prerequisites:** F-01
- **Parallel with:** S-02, S-03, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - Does the deployed 1.3.10 image differ from this branch in any way that affects the boot
    path? Production runs app tag 1.3.10 while the repository is at 1.3.11. — Owner: user.
    Block: no.
- **Risk:** This is the north star and the only slice with a rollout step outside the
  repository — the PRD is explicit that "the change is not complete when the branch merges."
  Rotation invalidates every token in flight; the accepted cost is exactly one forced
  re-login, with no dual-key grace window. FR-003 is the verification half of this slice, not
  a separate concern: the client-1.2.5 session must be exercised end to end after rotation.
- **Status:** proposed

### S-02: A failed vehicle update leaves the stored image intact

- **Outcome:** when a vehicle's image is replaced, the previous file is deleted only after
  the transaction commits; a rollback leaves the stored file intact and reachable.
- **Change ID:** `image-write-ordering`
- **PRD refs:** FR-004, FR-007
- **Prerequisites:** —
- **Parallel with:** F-01, S-01, S-03, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - Are any `vehicle_details.image` values the empty-string sentinel that `save()` returns on
    failure? The volume has no zero-byte or extensionless files, but the column was not read
    (the measurement session had no database access). — Owner: user. Block: no.
- **Risk:** Sequenced before S-04 because both rewrite the same write path and doing them in
  parallel would conflict. The failure mode being fixed destroys a file that cannot be
  restored, and no backup story for the data volume has been verified — so this slice must be
  built and tested against a scratch directory, never the production volume.
- **Status:** ready

### S-03: The server refuses an oversized request body before buffering it

- **Outcome:** a request body above a fixed ceiling is rejected before it is buffered into
  memory or written to the volume.
- **Change ID:** `request-body-limit`
- **PRD refs:** FR-005
- **Prerequisites:** —
- **Parallel with:** F-01, S-01, S-02, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - The recommended 2 MiB ceiling rests on a nine-file sample whose largest image is 108 KB.
    Ratify or adjust before freezing it. — Owner: user. Block: no.
  - Should a proxy-side ceiling accompany the in-application one? The live 4M limit is an
    `http`-level default shared with three unrelated services, so any proxy change must be
    scoped to a CarCare `server`/`location` block. — Owner: user. Block: no.
- **Risk:** The only slice whose implementation surface grew during measurement. No Spring or
  Tomcat property bounds a JSON request body — all three candidates were verified to let
  2 MB through, and the bare application accepted 60 MB — so this needs a pre-buffer
  `Content-Length` filter written for the change, not a configuration line. Shipping it also
  means knowingly accepting a defect: client 1.2.5 renders the rejection as a **false
  success**, closing the edit modal and navigating exactly as it does after a successful
  save. The client is frozen, so this cannot be fixed here; it is an accepted limitation, not
  a clean pass.
- **Status:** ready

### S-04: The server stores only genuine PNG and JPEG uploads

- **Outcome:** an uploaded image's type is determined from its actual bytes and only PNG and
  JPEG are accepted, regardless of the content type the client declares — while every image
  already on the volume stays loadable.
- **Change ID:** `image-format-allowlist`
- **PRD refs:** FR-006, FR-007
- **Prerequisites:** S-02
- **Parallel with:** F-01, S-01, S-03, S-05, S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** The one place in this change where two must-have requirements could contradict
  each other — and measurement has cleared it: the production volume holds nine files, five
  PNG and four JPEG by byte-level detection, so nothing stored today falls outside the
  allowlist. **One hard constraint follows and must survive into implementation:** four of
  those files are named `*.bin` (PNG content saved when the client declared a generic content
  type — the exact defect this slice fixes). Enforcement therefore belongs on the **write
  path only**. Adding allowlist checks to the read path would make those four files
  unloadable and break FR-007.
- **Status:** proposed

### S-05: Image paths cannot escape the data directory

- **Outcome:** every image read, write, and delete resolves to a path under the configured
  data directory, or is refused.
- **Change ID:** `image-path-containment`
- **PRD refs:** FR-008
- **Prerequisites:** —
- **Parallel with:** F-01, S-01, S-02, S-03, S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Recorded in the PRD as knowingly speculative hardening rather than a live
  vulnerability: filenames are server-generated UUIDs with no client influence, so no current
  code path reaches the escape. The user kept it as must-have with that objection accepted,
  on the basis that it guards an invariant nothing else enforces across read, write, and
  delete alike. Small and self-contained; if anything in this change gets dropped for time,
  this is the candidate.
- **Status:** ready

### S-06: The production build ships without test-data generation

- **Outcome:** test-data generation endpoints do not register under the production profile,
  and remain available under development and test with their existing admin-only behaviour.
- **Change ID:** `production-surface-reduction`
- **PRD refs:** FR-009
- **Prerequisites:** —
- **Parallel with:** F-01, S-01, S-02, S-03, S-05
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Costs no test coverage — the existing integration test exercises all three
  endpoints under the test profile, which stays unchanged. The objection recorded and
  overruled during shaping was that it makes production and development artifacts diverge;
  that divergence is the point. Watch that the admin-surface-parity contract is not broken
  silently, since no test currently covers the production profile's registration.
- **Status:** ready

## Backlog Handoff

| Roadmap ID | Change ID                      | Suggested issue title                                        | Ready for `/10x-plan` | Notes                                    |
| ---------- | ------------------------------ | ------------------------------------------------------------ | --------------------- | ---------------------------------------- |
| F-01       | `deployment-key-delivery`      | Deliver the JWT signing key from the host environment         | yes                   | Start here — unlocks the north star      |
| S-01       | `external-signing-key`         | Remove the committed signing key and fail fast without one    | no                    | Needs F-01 deployed first                |
| S-02       | `image-write-ordering`         | Delete a replaced vehicle image only after commit             | yes                   | Parallel-safe                            |
| S-03       | `request-body-limit`           | Reject oversized request bodies before buffering              | yes                   | Needs new filter code; see Risk          |
| S-04       | `image-format-allowlist`       | Accept only byte-verified PNG and JPEG uploads                | no                    | Needs S-02; write path only              |
| S-05       | `image-path-containment`       | Contain every image path under the data directory             | yes                   | Speculative hardening, by owner decision |
| S-06       | `production-surface-reduction` | Exclude test-data endpoints from the production profile       | yes                   | Parallel-safe                            |

## Open Roadmap Questions

1. ~~**How does client 1.2.5 render an oversized-body rejection? (FR-005)**~~ — **RESOLVED
   2026-08-30.** Measured in a browser against a disposable stack. The client renders the
   413 as a silent false success; it does not crash or wedge, and retry works. See
   `context/changes/security-baseline/oq-resolution.md`. The residual — the limit's exact
   value — is carried as a non-blocking Unknown on S-03.
2. ~~**Do any stored image files fall outside the FR-006 allowlist?**~~ — **RESOLVED
   2026-08-30.** No. Nine files, PNG and JPEG only. FR-006 and FR-007 do not collide, subject
   to the write-path-only constraint recorded on S-04.
3. **Fail-fast versus the stated "no pager event" position.** — **Discharged by sequencing.**
   The two positions are compatible only if key injection lands before or with the fail-fast
   check; the F-01 → S-01 dependency is exactly that ordering. Owner: user, at plan time.
   Block: no.
4. **Secondary persona and the vehicle owner's own framing.** — No secondary persona was
   elicited during shaping. Owner: user. Block: no — the change is operator-facing and the
   owner-facing surface is explicitly unchanged.
5. **User stories for the image-handling and production-surface work.** — S-02 through S-06
   carry no Given/When/Then; their requirements are individually testable without stories.
   Owner: user. Block: no.
6. **What becomes of the superseded deployment files in this repository?** —
   `src/main/docker/{app.yml,env-template,deploy.sh}` describe a deployment path that is no
   longer live. Leaving them is a standing trap for any future planner (it misled this
   roadmap's own first draft). Options: update them to mirror `~/services/carcare.yml`, mark
   them clearly as historical, or delete them. Owner: user. Block: no — but resolve it during
   F-01, since that slice is where the divergence bites.
7. **Does production's 1.3.10 image differ from this branch in ways that affect rollout?** —
   New, surfaced while verifying the proxy configuration. Production runs app tag 1.3.10;
   this repository is at 1.3.11. Owner: user. Block: no — gates S-01's rollout step only.

## Parked

- **Development-profile origin policy.** — Why parked: PRD §Non-Goals. Inspection showed the
  production profile carries no origin block and the filter registers nothing when the origin
  list is empty; it is a development-profile weakness, not a production exposure.
- **Token revocation and shortened token validity.** — Why parked: PRD §Non-Goals. Both
  offered during shaping and declined; statelessness is preserved deliberately.
- **Platform and dependency upgrades.** — Why parked: recorded in the PRD as an observation
  about scope boundaries rather than a declared non-goal. The health check found Spring Boot
  3.1.5 is seven patches behind its own end-of-life line's last release and no dependency
  scanning exists anywhere. A separate change on the modernization roadmap, and the strongest
  candidate to follow this one.
- **Moving image storage off the filesystem.** — Why parked: same observation. A later
  object-storage change would delete S-05's path logic entirely.

## Done

(Empty. `/10x-archive` appends here and flips the item's Status when a matching change is
archived.)
