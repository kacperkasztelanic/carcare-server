---
project: "CarCare Server"
context_type: brownfield
change_id: security-baseline
created: 2026-08-30
updated: 2026-08-30
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "scope of the four evidenced problems"
      decision: "IN: (a) JWT signing-key rotation with fail-fast startup validation, (b) image write-ordering plus size and content verification, (c) removing test-data generation from the production surface. OUT: dev CORS tightening, deselected by the user; carried to Non-Goals in Phase 6."
    - topic: "key-rotation strategy"
      decision: "One forced re-login is acceptable. No dual-key / grace-window machinery. Matches the foundation PRD's single NFR."
    - topic: "test-data production surface"
      decision: "Profile-gate TestDataResource so it does not register under the prod profile. The class and its class-level ADMIN check stay as they are. Verified in-session that TestDataResourceIT exercises all three paths under the test profile, so a !prod gate costs no integration coverage."
    - topic: "auth model"
      decision: "Mechanism only. Same two roles, same permit-list, same @PreAuthorize placement, same repository-level ownership filtering. Only the signing key's provenance and a startup validation change. Token validity and revocation were both offered and declined."
    - topic: "key delivery mechanism"
      decision: "Environment variable sourced from an uncommitted .env on the host. Follows the pattern src/main/docker/app.yml already uses for MARIADB_PASSWORD_ENV and MAIL_PASSWORD_ENV, so no new delivery mechanism is introduced. Docker secrets and generate-on-first-boot were both offered and declined."
    - topic: "delivery order"
      decision: "Key path end-to-end first, as its own slice; image write-ordering and test-data profile-gating follow as separate slices. Rationale accepted: a failed verify must stay attributable to one area."
    - topic: "timeline"
      decision: "Comfortably under three weeks of after-hours work. No sustained-effort acknowledgment required."
    - topic: "secondary success criterion"
      decision: "Startup logs state the security posture — the app reports at boot which secrets came from the environment versus a default. Nice-to-have, explicitly not sufficient on its own. A committed-secrets sweep and a written rotation runbook were both offered and declined."
    - topic: "guardrails"
      decision: "Two selected: the client 1.2.5 REST contract, and the survival of every existing vehicle image file. The green verify baseline was offered as a third and not selected; it remains a Phase-1 must-preserve rather than a formal guardrail. \"At most one forced re-login\" was offered and not selected as a guardrail because it is already the accepted cost recorded under Access Control."
    - topic: "image content verification"
      decision: "Sniff the actual bytes and accept only a fixed image allowlist, ignoring the client-declared imageContentType. Match-declared-type and size-limit-only were both offered and declined."
    - topic: "oversize image behaviour"
      decision: "PARTLY RESOLVED by the Socrates round. The user endorsed moving the bound to the HTTP layer, so FR-005 now bounds the request body rather than the image, and no image-specific rejection path is introduced. What remains unresolved is how client 1.2.5 displays a container-level rejection; that still needs a browser check before the PRD locks."
    - topic: "prepareImagePath containment"
      decision: "In scope. Assert the resolved path stays under the configured data directory before any read, write, or delete. Not currently exploitable — filenames are server-generated UUIDs — but nothing else enforces the invariant."
    - topic: "FR-002 fail-fast severity (Socrates)"
      decision: "Stands as written: refuse to start. Three counter-arguments were offered and declined. The deploy-outage cost is explicitly accepted."
    - topic: "boot-time security-posture logging (Socrates)"
      decision: "DROPPED, along with the Phase-3 Secondary success criterion it was. The user endorsed the dilution counter-argument; keeping it and narrowing it were both offered and declined. The change is now primary-only."
    - topic: "FR-008 path containment (Socrates)"
      decision: "Kept as must-have with the counter-argument accepted as true. The user endorsed 'it guards an unreachable path' and kept the FR regardless. Recorded as knowingly speculative hardening."
    - topic: "FR-005 revision (Socrates)"
      decision: "REVISED. Originally an image-specific size cap; the user endorsed the counter-argument that the bound belongs at the HTTP layer, so it now bounds the request body. Removes the need for a new image-specific error path."
    - topic: "non-functional requirements"
      decision: "Two asserted: no image that was reachable before becomes unreachable, and deployment stays a single-step operation. 'Exactly one forced re-login' was offered and not selected as an NFR because it is already recorded as the accepted rotation cost. 'None at all' was offered and declined."
    - topic: "non-goals"
      decision: "Two selected: dev CORS tightening, and token revocation / shortened validity. Two further items were offered as non-goals and NOT selected — platform and dependency upgrades, and moving images off the filesystem. They are outside this change's FR set by construction and belong to dependency-alignment and minio-object-storage respectively; recorded as observation, not as user-declared non-goals."
    - topic: "product framing"
      decision: "No change. product_type: web-app; target_scale.users: small; hard_deadline: null; after_hours_only: true; delivery_weeks: 3 or under."
    - topic: "pager event / blast radius"
      decision: "User's stated position: none — the whole change is treated as low-risk routine hardening, with no named pager event. Recorded as a deliberate position, not inferred. See Open Questions for the one tension this leaves unresolved."
  frs_drafted: 9
  quality_check_status: accepted
---

# Shape Notes — CarCare Server: security-baseline

Second change shaped for this project. The **foundation slice is complete and closed** —
all twelve of its roadmap items (F-01…F-04, S-01…S-08) are `done` and archived under
`context/archive/`. Its shape notes were archived to
`context/foundation/archive/shape-notes-2026-08-30-foundation.md` at the start of this
session. Nothing in that slice is being resumed.

Seed supplied by the user, plus read-only source inspection performed in-session. Every
line-referenced claim below was verified against the working tree at branch `refactor`;
nothing here is inferred from the previous change's documents.

## Current System

**Purpose.** CarCare is a vehicle-fleet management backend. Owners record five kinds of
lifecycle event (refuel, repair, routine service, inspection, insurance) against their
vehicles; the server derives costs and statistics, generates XLSX/PDF reports, and mails
reminders for upcoming obligations.

**Tech stack, unchanged by this work.** Java 17, Spring Boot 3.1.5, Hibernate 6.2.13,
Maven 3.9.6, MariaDB in dev/prod with H2 in tests, Liquibase 4.20, Ehcache, stateless JWT
via `jjwt` 0.12.3. Dockerized behind an NGINX reverse proxy. The React client is external
(`../client`), consumed as the prebuilt Maven artifact
`com.kasztelanic.carcare:client` 1.2.5.

**Users.** Personal scale — the owner plus a handful of known people, with real accounts
and real data in a live deployment. Two roles: `ROLE_USER` and `ROLE_ADMIN`.

**Verification baseline.** `./mvnw verify` is green: 38 unit tests, 217 integration tests.

## Vision & Problem Statement

### The gap

The production JWT signing key is committed to the repository, and the deployment does
not override it.

- `src/main/resources/config/application-prod.yml:105` supplies a hard-coded base64
  default for `application.security.authentication.jwt.base64-secret`, behind an
  environment-variable placeholder.
- `src/main/docker/app.yml:6-14` sets `SPRING_PROFILES_ACTIVE`, datasource, and mail
  variables, and `JHIPSTER_SLEEP`. It does **not** set
  `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`, so the running container falls
  through to the committed value.
- `security/jwt/TokenProvider.afterPropertiesSet()` decodes that value straight into the
  HMAC key with no check that it differs from the shipped default.

Consequence: anyone with read access to this repository can forge a valid token for any
login, including an administrator. Stateless JWT means there is no server-side session to
revoke.

Three lower-severity defects sit around it, two of which are in scope:

- **Image write ordering.** `service/impl/VehicleServiceImpl.updateVehicle():76` deletes
  the old image file as its first statement — before the entity is mutated and before the
  transaction commits. A rollback after that point leaves the persisted row pointing at a
  filename that no longer exists. `FileUtils.deleteQuietly` swallows the failure silently.
- **Unverified, unbounded image input.** `service/impl/ImageStorageServiceImpl.save()`
  takes a client-supplied `imageContentType` (`service/dto/VehicleDetailsDto:19-20`) and
  asks Tika only for the matching file *extension*; the bytes are never inspected. No size
  cap exists anywhere — the image arrives base64-encoded inside the vehicle JSON body,
  not as multipart, so `spring.servlet.multipart.*` limits would not apply, and none are
  configured in any profile.
- **Test-data generation in the production artifact.** `web/rest/TestDataResource` exposes
  `/api/test-data/populate-fuel-types`, `/populate-insurance-types`, and
  `/random-vehicles/{n}` in the production build.

### Corrections to the seed framing

Recorded because they changed scope decisions, and because overstating a finding is itself
a defect:

- **Dev CORS is not a production exposure.** `application-dev.yml:74-80` does set
  `allowed-origins: *` with `allow-credentials: true`, but `application-prod.yml` has no
  `cors:` block at all, and `config/WebConfigurer.corsFilter():103` registers no CORS
  configuration when `allowedOrigins` is empty. The weakness is confined to the dev
  profile. Deselected from scope; carried to Non-Goals.
- **`/api/test-data` is not anonymously reachable.** `TestDataResource` carries a
  class-level `@PreAuthorize(hasRole(ADMIN))`, and `SecurityConfiguration` requires
  authentication for all `/api/**`. The residual concern is narrower than "unprotected
  endpoint": an administrator can inject generated data into the production database, and
  the surface ships in the production artifact at all.

### Additional observation, not yet ruled in or out

`ImageStorageServiceImpl.prepareImagePath()` builds
`Paths.get(location).resolve(fileName).normalize()`. `resolve` on an absolute path or a
name containing `..` escapes the configured data directory, and the trailing `normalize()`
does not restore containment. Filenames are server-generated UUIDs today, so this is an
unguarded invariant rather than a live vulnerability. Raised for an explicit decision
during FR capture, not assumed into scope.

### What is changing

The deployed system stops trusting anything committed to the repository for token signing,
and refuses to run in a state where it would. The image write path stops destroying data
it cannot restore and stops accepting input it has not checked. Test-data generation stops
being part of what production ships.

### Must preserve

- The REST contract with client 1.2.5 — paths, payloads, and status codes.
- Existing production data, including the vehicle image files on the Docker volume.
- The green `./mvnw verify` baseline: 38 unit tests, 217 integration tests.

## Access Control

**Current model — preserved, no changes planned.** Stateless JWT bearer tokens signed with
an HMAC key. Two roles in `security/AuthoritiesConstants`: `ROLE_USER` and `ROLE_ADMIN`.
`config/SecurityConfiguration` permits exactly five paths anonymously — `/api/register`,
`/api/activate`, `/api/authenticate`, `/api/account/reset-password/init`, and
`/api/account/reset-password/finish` — gates `/api/admin/**` and `/management/**` (except
`health`, `info`, `prometheus`) to `ADMIN`, requires authentication for all other
`/api/**`, and permits everything else so the SPA's static assets load. Per-owner data
isolation is enforced in the persistence boundary: repository JPQL embeds
`vehicle.owner.login = ?#{principal.username}`.

**What this change touches.** The provenance of the signing key and a startup validation
over it — not who may do what. No role is added, no permit-list entry moves, no
`@PreAuthorize` placement changes, no ownership query is rewritten.

**Rotation semantics.** Rotating the key invalidates every token in flight. The accepted
cost is **one forced re-login** for every active user. No dual-key grace window, no
overlap period. This matches the single NFR the foundation PRD carried.

**Deliberate non-adoptions.** Shortening token validity and adding server-side token
revocation were both offered and declined. Neither is a defect being tolerated: the first
is a client-facing behavior change without a stated justification, and the second would
undercut the statelessness the rest of the system is built on.

**Test-data surface.** `web/rest/TestDataResource` will be profile-gated so it does not
register under the `prod` profile. Its class-level `@PreAuthorize(hasRole(ADMIN))` stays.
This is a *surface* reduction, not an access-model change — the endpoint was already
admin-only and authenticated.

## Success Criteria

### Primary

An operator can deploy the server with the signing key supplied from outside the
repository, and the application refuses to run in any state where it would sign tokens
with a key that is present in version control. Concretely, end to end:

1. The committed base64 default is gone from `application-prod.yml`.
2. `src/main/docker/app.yml` reads the key from the host environment, the same way it
   already reads `MARIADB_PASSWORD_ENV` and `MAIL_PASSWORD_ENV`, sourced from an
   uncommitted `.env`.
3. Booting without that variable set, or with the previously-committed value, fails fast
   with a message naming the missing configuration — rather than starting and signing
   tokens anyone can forge.
4. An existing owner logs in once through the unmodified client 1.2.5 and continues a
   normal session: lists vehicles, opens one, records an event.

This is the north star. Everything else in the change matters only if this holds.

### Secondary

**None — primary only.** A boot-time security-posture log line was selected here during
Phase 3 and then dropped during the Phase 4.5 Socrates round, when the user endorsed the
argument that a lone nice-to-have dilutes a security change and touches boot code for
observability value that a stated non-goal already rules out. Recorded as a deliberate
position: this change has no secondary criterion.

### Guardrails

- **Client 1.2.5 REST contract.** Paths, payloads, and status codes unchanged. This change
  requires no client release and none is coordinated.
- **Existing vehicle image files.** Every image currently on the Docker data volume is
  still loadable afterwards. This is the guardrail that constrains the image write-ordering
  fix, whose whole failure mode is destroying a file it cannot restore.

Offered and not selected, recorded so the omission is deliberate rather than lost: the
green `./mvnw verify` baseline (38 unit + 217 integration tests) was not elevated to a
formal guardrail — it remains listed under Phase 1 must-preserve. "At most one forced
re-login" was not selected as a guardrail because it is already recorded under Access
Control as the accepted cost of rotation.

### Timeline

`delivery_weeks: 3` or under, after-hours only. Three narrow slices against existing test
infrastructure with no data migration. No sustained-effort acknowledgment is required.

## Functional Requirements

Every FR below carries its Socrates challenge. FR-001, FR-003, FR-005, and FR-008 were not
independently challenged: each is a literal restatement of a decision the user already made
under challenge in Phases 1–3, and manufacturing an objection to a restatement would be
theatre rather than scrutiny.

### Signing key

- FR-001: An operator can supply the JWT signing key from the deployment environment, and
  no usable signing key remains anywhere in version control. Priority: must-have.
  Change: modified.
  > Socrates: Not independently challenged — restates the Phase-1 scope decision and the
  > Phase-3 key-delivery decision, both taken under challenge.
- FR-002: The application refuses to start when the signing key is absent, or when it
  equals the value previously committed to this repository. Priority: must-have.
  Change: new.
  > Socrates: Counter-arguments offered: that it contradicts the "no pager event" position
  > by turning a forgotten `.env` line into a deploy outage; that blocklisting one known
  > value is theatre against any other weak key; that it fails closed on the wrong thing
  > for a personal-scale deployment. Resolution: **stands as written, refuse to start**.
  > The user's stated reason: an app that boots and signs with a repo key is exactly the
  > state this change exists to eliminate, and a loud deploy-time failure is preferable to
  > a quiet runtime weakness. The deploy-outage cost is accepted, and is the reason the
  > rollout ordering in Open Questions must hold.

### Image handling

- FR-003: An existing owner can log in through the unmodified client 1.2.5 after the key
  is rotated and continue a full session — list vehicles, open one, record an event — with
  the same paths, payloads, and status codes as before. Priority: must-have.
  Change: preserved.
  > Socrates: Not independently challenged — restates the Phase-3 primary success criterion
  > and the client-contract guardrail.
- FR-004: When a vehicle's image is replaced, the previous file is deleted only after the
  transaction commits successfully; a rollback leaves the stored file intact and reachable.
  Priority: must-have. Change: modified.
  > Socrates: Not independently challenged — restates the Phase-1 scope decision to fix the
  > delete-before-commit data-loss path, taken with the defect's evidence in hand.
- FR-005: The server rejects an oversized request body at the HTTP layer, before it is
  buffered into memory or written to the volume. Priority: must-have. Change: new.
  > Socrates: **REVISED by the challenge.** Counter-arguments offered: that the limit
  > belongs at the HTTP layer rather than in the image code; that no honest limit can be
  > chosen without first looking at real upload sizes; that it creates a failure client
  > 1.2.5 cannot display. Resolution: the user endorsed the first. The FR was originally
  > "enforce an upper bound on accepted image size" and is rewritten to bound the request
  > body instead — the real exposure is an unbounded base64 JSON payload, not the image
  > specifically, and one container-level ceiling covers every oversized payload without
  > touching the image code. The unresolved failure-behaviour question narrows accordingly
  > but does not disappear: a container-level rejection is still client-visible and its
  > handling in client 1.2.5 is still unverified.
- FR-006: The server determines an uploaded image's type from its actual bytes and accepts
  only a fixed allowlist of image formats, disregarding the client-declared
  `imageContentType`. Priority: must-have. Change: new.
  > Socrates: Counter-arguments offered: that it can render already-stored files unloadable
  > and so collides with FR-007; that the threat does not exist because the file is only
  > served back to its own owner; that a fixed allowlist will reject HEIC/AVIF from a phone
  > tomorrow. Resolution: **stands as written, no counter-argument accepted** — trusting a
  > client-declared content type to choose a stored file's extension is indefensible
  > regardless of current exploitability. The FR-007 collision remains an Open Question.
- FR-007: Every image file currently stored on the data volume remains loadable after this
  change. Priority: must-have. Change: preserved.
  > Socrates: Not independently challenged — restates the Phase-3 guardrail selected by the
  > user, which exists precisely to constrain FR-004 and FR-006.
- FR-008: Every image read, write, and delete resolves to a path under the configured data
  directory, or is refused. Priority: must-have. Change: new.
  > Socrates: Counter-arguments offered: that it guards a path no code can reach, since
  > filenames are server-generated UUIDs with no client influence; and that
  > `minio-object-storage` will delete this path logic entirely. Resolution: **kept as
  > must-have with the objection accepted as true** — the user endorsed "it guards an
  > unreachable path" and kept the FR anyway, on the basis that it is a few lines guarding
  > an invariant nothing else enforces across read, write, and delete alike. Recorded as
  > knowingly speculative hardening, not as a defect anyone is currently exposed to.

### Production surface

- FR-009: Test-data generation endpoints do not register under the `prod` profile, and
  remain available under `dev` and `test` with their existing admin-only behaviour.
  Priority: must-have. Change: modified.
  > Socrates: Counter-arguments offered: that it breaks the S-02 admin-surface-parity
  > contract without any test catching it; that the endpoints are already authenticated and
  > ADMIN-gated so this is not the fix; that it is surface reduction dressed as security and
  > makes prod and dev artifacts diverge. Resolution: **stands as written, no counter-argument
  > accepted** — random-data generation has no business in a production build, and
  > profile-gating costs no coverage because `TestDataResourceIT` runs under the test
  > profile.

### Dropped during Phase 4.5

- **FR-004 (original numbering): "an operator can see, in the boot log, which secrets were
  supplied by the environment and which fell back to a default."** Dropped. The user
  endorsed the counter-argument that a lone nice-to-have dilutes a security change and
  touches boot code for observability value a stated non-goal already rules out. Two
  alternatives — keeping it with the objection recorded, and narrowing it to the signing key
  alone — were offered and declined. The Phase-3 Secondary success criterion was this FR and
  is dropped with it; the change is now primary-only. Remaining FRs were renumbered.

## Business Logic

**No domain logic change. This is an infrastructure and security change.**

The rule of operation the system applies — deriving costs, consumption, and mileage
statistics from the five event types embedded against a vehicle, and selecting which
upcoming obligations warrant a reminder — is untouched. No FR in this change alters what
the application decides for the user. The empty-CRUD check does not apply to brownfield
infrastructure work and was not run.

## Non-Functional Requirements

- **No image that was reachable before the change becomes unreachable after it**, including
  across a rolled-back vehicle update. Binary commitment, checkable from outside by loading
  each vehicle's image.
- **Deployment remains a single-step operation.** Injecting the signing key does not turn
  deploying into a multi-stage procedure — one `compose up`, as today. This constrains how
  FR-001 and FR-002 may be implemented: any design requiring an ordered sequence of
  operator actions violates it.

"Exactly one forced re-login" was offered as a third NFR and not selected — it is already
recorded under Access Control as the accepted cost of rotation rather than an asserted
property. "No NFRs at all" was offered and declined.

## Constraints & Preserved Behavior

- **No client release is available.** Client 1.2.5 is frozen and consumed as a prebuilt
  Maven artifact from a private registry. Anything requiring a `../client` change and a
  `carcare-client.version` bump is out of reach for this change — which is why FR-005's
  unverified rejection rendering is an open question rather than a fixable item.
- **The production data volume must survive.** `/home/kacper/carcare/data/data` (mapped in
  `src/main/docker/app.yml`) holds live vehicle images. No backup story for it has been
  verified in-session. Every image-path change must work against the files already there.
- **Key rotation requires host access.** The `.env` delivery decision means this change is
  not complete when the branch merges — someone must edit a file on the deployment host.
  The repository alone cannot finish it.
- **CI is advisory, not gating.** `only_allow_merge_if_pipeline_succeeds` is `false` by
  owner decision, so a green pipeline cannot be relied on as a merge gate here.
- **Preserved:** the client 1.2.5 REST contract (paths, payloads, status codes); the
  existing two-role access model and its permit-list; the `./mvnw verify` baseline of 38
  unit and 217 integration tests.

## Non-Goals

- **Dev CORS tightening.** `application-dev.yml:74-80` keeps `allowed-origins: *` with
  `allow-credentials: true`. Ruled out once inspection showed `application-prod.yml` has no
  `cors:` block and `WebConfigurer.corsFilter():103` registers nothing when origins are
  empty — it is a dev-profile weakness, not a production exposure.
- **Token revocation and shortened token validity.** Both offered during Phase 2 and
  declined. The auth model stays mechanism-only; statelessness is preserved.

Two further items were offered as non-goals and **not** selected by the user: platform and
dependency upgrades (the tree stays on Spring Boot 3.1.5 and Java 17 throughout, despite
3.1.x being out of OSS support), and moving image storage off the filesystem. Neither
appears in any FR here, and both are separate PRDs — `dependency-alignment` and
`minio-object-storage`. Recorded as an observation about scope boundaries rather than as a
user-declared non-goal.

## Forward: technical-roadmap

Captured for downstream skills; not part of the PRD schema.

- The rollout ordering constraint from FR-002 (deployment key injection must land before or
  with the fail-fast check) is a sequencing input for `/10x-roadmap`, not an FR.
- Two open questions require a browser session against the real client 1.2.5, reproducing
  the S-07 method: how it renders a container-level oversized-body rejection, and whether
  any stored image file falls outside the FR-006 allowlist.

## Quality cross-check

All six brownfield elements present; no gaps recorded.

| Element | Status |
| --- | --- |
| Access Control | present — current model documented, explicitly unchanged, rotation semantics stated |
| Business Logic | present — "No domain logic change", valid for brownfield infrastructure work |
| Project artifacts | present — shape-notes.md with a valid checkpoint |
| Timeline-cost acknowledged | present — `delivery_weeks` ≤ 3, no sustained-effort acknowledgment needed |
| Non-Goals | present — 2 entries with rationale |
| Preserved behavior | present — 5 constraints and 3 preserved contracts named |

`quality_check_status: accepted`. The two entries under Open Questions below are **not**
cross-check gaps — they are substantive decisions the user deliberately deferred pending a
browser observation, and `/10x-prd` should mirror them rather than resolve them.

## Open Questions

- **Fail-fast versus the "no pager event" position.** The user recorded that no failure of
  this change would page them. Refusing to start on a missing or default signing key does,
  by construction, convert a deployment configuration omission into a boot failure. These
  two positions are compatible only if the deployment change lands before or with the
  fail-fast check. Flagged here rather than resolved by assumption; belongs in the rollout
  ordering the roadmap will define.
- **Path-containment guard in `prepareImagePath`** — RESOLVED in Phase 4: in scope, FR-009.
- **Oversize-body rejection and client 1.2.5 (FR-005).** The Socrates round moved the bound
  to the HTTP layer, so no image-specific error path is introduced — but a container-level
  rejection is still something the client has never seen. How it renders that must be
  observed in a browser, the way S-07 established, before the PRD locks. The limit's actual
  value is also unchosen; the "no honest limit without looking at real upload sizes"
  argument was offered and not endorsed, but it stands unanswered.
- **Existing stored image formats versus the FR-006 allowlist.** If any file already on the
  data volume falls outside the chosen allowlist, FR-006 and FR-007 conflict on the read
  path. Not yet checked against the production volume. This is the one place in the change
  where two must-have FRs can contradict each other.
