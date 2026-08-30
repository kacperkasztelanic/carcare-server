---
project: "CarCare Server"
version: 1
status: draft
created: 2026-08-30
context_type: brownfield
change_id: security-baseline
product_type: web-app
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  delivery_weeks: 3
  hard_deadline: null
  after_hours_only: true
---

# CarCare Server — Security Baseline

Second change shaped for this project. The platform-foundation change that preceded it is
complete and closed — all twelve of its roadmap items are `done` and archived — and its PRD
was moved to `context/foundation/archive/prd-2026-08-24-foundation.md` when this one was
written. Nothing in that change is being resumed.

Source: `context/foundation/shape-notes.md` (`checkpoint.current_phase: 8`,
`quality_check_status: accepted`, 9 FRs). Every claim below was verified by read-only
inspection of the working tree at branch `refactor` during shaping.

## Current System Overview

**Purpose.** CarCare is a vehicle-fleet management backend. Owners record five kinds of
lifecycle event — refuel, repair, routine service, inspection, insurance — against their
vehicles; the server derives costs and statistics, generates XLSX/PDF reports, and mails
reminders for upcoming obligations.

**Architecture.** A layered monolith, originally JHipster-generated and now hand-maintained.
`Vehicle` is the aggregate root, owned by a `User`; each event type embeds a `VehicleEvent`
value object carrying mileage and date. Per-owner data isolation is enforced at the
persistence boundary rather than in controllers.

**Tech stack — unchanged by this work.** Java 17, Spring Boot 3.1.5, Hibernate 6.2.13,
Maven 3.9.6, MariaDB in dev and prod with H2 in tests, Liquibase 4.20, Ehcache, stateless
JWT via `jjwt` 0.12.3. Dockerized behind an NGINX reverse proxy. The React client is
external (`../client`) and consumed here as the prebuilt Maven artifact
`com.kasztelanic.carcare:client` version 1.2.5.

**User base.** Personal scale — the owner plus a handful of known people, with real accounts
and real data in a live deployment. Two roles: `ROLE_USER` and `ROLE_ADMIN`.

**Verification baseline.** `./mvnw verify` is green: 38 unit tests, 249 integration tests.

**Deployment.** Docker containers on a VPS behind a shared NGINX reverse proxy. See
`## Constraints & Compatibility` for the topology, which differs from what this repository's
`src/main/docker/` files suggest.

**Where the defects live** (evidence gathered in-session; recorded here rather than in the
delta sections so the later sections stay behaviour-framed):

| Evidence | Observation |
| --- | --- |
| `src/main/resources/config/application-prod.yml:105` | Hard-coded base64 default for the JWT signing key, behind an environment-variable placeholder. Identical default at `application-dev.yml:88`. |
| `src/main/docker/app.yml:6-14` | Sets profiles, datasource, mail and sleep variables — but not the signing-key variable, so the container falls through to the committed value. |
| `security/jwt/TokenProvider.afterPropertiesSet()` | Decodes that value straight into the HMAC key with no check that it differs from the shipped default. |
| `service/impl/VehicleServiceImpl.updateVehicle():76` | Deletes the old image file as its first statement — before the entity is mutated and before commit. `FileUtils.deleteQuietly` swallows failure silently. |
| `service/impl/ImageStorageServiceImpl.save()` + `service/dto/VehicleDetailsDto:19-20` | Trusts the client-supplied `imageContentType` and asks Tika only for a matching extension; the bytes are never inspected. The image arrives base64-encoded inside the vehicle JSON body, so `spring.servlet.multipart.*` limits would not apply — and none are configured in any profile. |
| `service/impl/ImageStorageServiceImpl.prepareImagePath()` | `resolve(fileName)` on an absolute name or one containing `..` escapes the configured data directory; the trailing `normalize()` does not restore containment. Filenames are server-generated UUIDs today, so this is an unguarded invariant, not a live vulnerability. |
| `web/rest/TestDataResource` | Ships three test-data generation endpoints in the production build. |

**Two seed claims that inspection contradicted**, recorded because they changed scope and
because overstating a finding is itself a defect:

- **Development CORS is not a production exposure.** `application-dev.yml:74-80` does set
  `allowed-origins: *` with `allow-credentials: true`, but `application-prod.yml` carries no
  `cors:` block at all, and `config/WebConfigurer.corsFilter():103` registers no
  configuration when the origin list is empty. The weakness is confined to the dev profile.
- **The test-data endpoints are not anonymously reachable.** `TestDataResource` carries a
  class-level `@PreAuthorize(hasRole(ADMIN))`, and `SecurityConfiguration` requires
  authentication for all `/api/**`. The residual concern is narrower than "unprotected
  endpoint": generated data can be injected into the production database by an
  administrator, and the surface ships in the production artifact at all.

## Problem Statement & Motivation

**The gap.** The production JWT signing key is committed to this repository, and the
deployment does not override it. The production configuration supplies a hard-coded base64
default behind an environment-variable placeholder; the compose file that runs the
container never sets that variable; and the token provider decodes whatever it receives
into the signing key without checking that it differs from the shipped default. The three
facts compose into a single consequence: anyone with read access to this repository can
forge a valid token for any login, including an administrator. Because authentication is
stateless, there is no session to revoke once a forged token is in circulation.

**Why now.** The platform-foundation change is complete and closed, so the tree is green and
stable enough to change safely for the first time since the migration began. Nothing else
is in flight against it. The exposure is not new, but it has never been addressed, and every
subsequent change on the modernization roadmap inherits it.

**The current workaround.** There is none. The deployment runs on the committed key today.

**Three lower-severity defects sit around it**, two of which are in scope:

- **Image write ordering.** Replacing a vehicle image deletes the previous file before the
  transaction commits. A rollback after that point leaves the persisted record pointing at a
  filename that no longer exists, and the deletion failure is swallowed silently.
- **Unverified, unbounded image input.** The declared content type is taken on trust and the
  bytes are never inspected, and no size ceiling exists anywhere on the request path.
- **Test-data generation in the production artifact.** Endpoints that populate lookup tables
  and generate random vehicles ship in the production build.

## User & Persona

**Primary persona — the operator.** The system's owner, who is also its deployer and its
administrator. They hold host access to the deployment machine, run the container by hand,
and are the only person who can inject a secret into the running environment. They reach for
this change at the moment they realize the repository they push to also contains the key
that signs every token their live system trusts. Their cost today is an exposure they cannot
close from the repository alone.

**Affected existing users — vehicle owners.** The handful of known people with real accounts
and real data. This change is intended to be almost invisible to them: their contract with
the server does not move, and their data does not migrate. The one thing they will notice is
a single forced re-login at the moment the key rotates.

# TODO: secondary persona and the vehicle owner's own framing of the change — see Open Questions

## Success Criteria

### Primary

An operator can deploy the server with the signing key supplied from outside the repository,
and the application refuses to run in any state where it would sign tokens with a key that
is present in version control. Concretely, end to end:

1. The committed base64 default is gone from the production configuration.
2. The deployment reads the key from the host environment, the same way it already reads
   the database and mail passwords, sourced from an uncommitted local environment file.
3. Booting without that variable set, or with a key too short for the signing algorithm, fails
   fast with a message naming the missing configuration — rather than starting and signing
   tokens anyone can forge. (Narrowed 2026-08-30 from "or with the previously-committed value";
   see FR-002.)
4. An existing owner logs in once through the unmodified client 1.2.5 and continues a normal
   session: lists vehicles, opens one, records an event.

This is the north star. Everything else in the change matters only if this holds.

### Secondary

**None — this change is primary-only.** A boot-time security-posture criterion was selected
during shaping and then dropped during the challenge round, when the user endorsed the
argument that a lone nice-to-have dilutes a security change and touches boot code for
observability value that a stated non-goal already rules out. Recorded as a deliberate
position, not an omission.

### Guardrails

- **The client 1.2.5 contract.** Paths, payloads, and status codes unchanged. This change
  requires no client release and none is coordinated.
- **Existing vehicle image files.** Every image currently on the data volume is still
  loadable afterwards. This is the guardrail that constrains the write-ordering fix, whose
  whole failure mode is destroying a file it cannot restore.

Offered during shaping and **not** selected, recorded so the omission is deliberate rather
than lost: the green `./mvnw verify` baseline was not elevated to a formal guardrail — it
remains a preserved behaviour under Constraints & Compatibility. "At most one forced
re-login" was not selected because it is already recorded as the accepted cost of rotation.

## User Stories

Shaping captured no Given/When/Then blocks. The two stories below are direct conversions of
the numbered Primary success criterion into the schema's required shape — the user's own
words, reformatted, not new content. No further stories were captured.

### US-01: Operator deploys with an externally supplied signing key

- **Given** an operator with host access to the deployment machine and an uncommitted local
  environment file
- **When** they deploy the server with the signing key set in that file
- **Then** the application starts and signs tokens with a key that exists nowhere in version
  control

**Before this change:** the variable was absent from the deployment, and the application
silently fell back to the key committed in the production configuration.

#### Acceptance Criteria
- No usable signing key remains anywhere in version control.
- Deployment remains a single step — one command, as today.
- Booting with the variable unset fails immediately, naming the missing configuration.
- Booting with a key too short for the signing algorithm fails immediately, naming the required
  minimum. (Narrowed 2026-08-30: this replaces "booting with the previously-committed value
  fails" — see FR-002.)

### US-02: Existing owner continues working after the key rotates

- **Given** an existing vehicle owner holding a token issued under the previous key
- **When** the key is rotated and they next use the unmodified client 1.2.5
- **Then** they are asked to log in exactly once, and then complete a full session — list
  vehicles, open one, record an event — with the same paths, payloads, and status codes as
  before

**Before this change:** no rotation had ever occurred, so no re-login was ever forced.

#### Acceptance Criteria
- Exactly one forced re-login; no repeated prompts.
- No client release is required.
- No stored vehicle image becomes unreachable as a result.

# TODO: user stories for the image-handling and production-surface changes — see Open Questions

## Scope of Change

Each item carries its shaping challenge verbatim. FR-001, FR-003, FR-005 and FR-008 were not
independently challenged: each restates a decision already taken under challenge in an
earlier phase, and manufacturing an objection to a restatement would be theatre rather than
scrutiny.

### Signing key

- **[modified] FR-001** — An operator can supply the JWT signing key from the deployment
  environment, and no usable signing key remains anywhere in version control.
  Priority: must-have.
  > Socrates: Not independently challenged — restates the scope decision and the
  > key-delivery decision, both taken under challenge.

- **[new] FR-002** — The application refuses to start when the signing key is absent or empty,
  or when it is too short to sign with the algorithm the application uses. Priority: must-have.
  > Socrates: Counter-arguments offered: that it contradicts the "no pager event" position by
  > turning a forgotten environment line into a deploy outage; that blocklisting one known
  > value is theatre against any other weak key; that it fails closed on the wrong thing for
  > a personal-scale deployment. Resolution: **refuse to start stands.** The user's stated
  > reason: an application that boots and signs with a repository key is exactly the state
  > this change exists to eliminate, and a loud deploy-time failure is preferable to a quiet
  > runtime weakness. The deploy-outage cost is accepted, and is the reason the rollout
  > ordering in Open Questions must hold.
  >
  > **Narrowed 2026-08-30 (owner decision).** The value blocklist — "or when it equals the
  > value previously committed to this repository" — is **dropped**. It does not earn its cost
  > for a family-and-friends deployment: rotation, not detection, is what closes the exposure,
  > and Phase 1 of `external-signing-key` rotates the key before the committed default is
  > removed, so the blocklisted value can never be the one in use. The second counter-argument
  > above is thereby conceded. A **length** check replaces it, which the blocklist never
  > provided: `TokenProvider` pins `Jwts.SIG.HS512` and jjwt 0.12.3 requires 512 bits, but key
  > construction accepts 256, so a short key boots healthy and then fails every login.
  > Rationale and residual risk: `context/changes/external-signing-key/plan.md` § "Deviation
  > from FR-002".

### Image handling

- **[preserved] FR-003** — An existing owner can log in through the unmodified client 1.2.5
  after the key is rotated and continue a full session — list vehicles, open one, record an
  event — with the same paths, payloads, and status codes as before. Priority: must-have.
  > Socrates: Not independently challenged — restates the primary success criterion and the
  > client-contract guardrail.

- **[modified] FR-004** — When a vehicle's image is replaced, the previous file is deleted
  only after the transaction commits successfully; a rollback leaves the stored file intact
  and reachable. Priority: must-have.
  > Socrates: Not independently challenged — restates the scope decision to fix the
  > delete-before-commit data-loss path, taken with the defect's evidence in hand.

- **[new] FR-005** — The server rejects an oversized request body before it is buffered into
  memory or written to the volume. Priority: must-have.
  > Socrates: **REVISED by the challenge.** Counter-arguments offered: that the limit belongs
  > at the request boundary rather than in the image code; that no honest limit can be chosen
  > without first looking at real upload sizes; that it creates a failure client 1.2.5 cannot
  > display. Resolution: the user endorsed the first. The requirement was originally "enforce
  > an upper bound on accepted image size" and is rewritten to bound the request body instead
  > — the real exposure is an unbounded base64 payload, not the image specifically, and one
  > ceiling at the boundary covers every oversized payload without touching the image code.
  > The unresolved failure-behaviour question narrows accordingly but does not disappear: the
  > rejection is still client-visible and its handling in client 1.2.5 is still unverified.

- **[new] FR-006** — The server determines an uploaded image's type from its actual bytes and
  accepts only a fixed allowlist of image formats, disregarding the client-declared content
  type. Priority: must-have.
  > Socrates: Counter-arguments offered: that it can render already-stored files unloadable
  > and so collides with FR-007; that the threat does not exist because the file is only
  > served back to its own owner; that a fixed allowlist will reject HEIC/AVIF from a phone
  > tomorrow. Resolution: **stands as written, no counter-argument accepted** — trusting a
  > client-declared content type to choose a stored file's extension is indefensible
  > regardless of current exploitability. The FR-007 collision remains an Open Question.

- **[preserved] FR-007** — Every image file currently stored on the data volume remains
  loadable after this change. Priority: must-have.
  > Socrates: Not independently challenged — restates the guardrail selected by the user,
  > which exists precisely to constrain FR-004 and FR-006.

- **[new] FR-008** — Every image read, write, and delete resolves to a path under the
  configured data directory, or is refused. Priority: must-have.
  > Socrates: Counter-arguments offered: that it guards a path no code can reach, since
  > filenames are server-generated UUIDs with no client influence; and that a later
  > object-storage change will delete this path logic entirely. Resolution: **kept as
  > must-have with the objection accepted as true** — the user endorsed "it guards an
  > unreachable path" and kept the requirement anyway, on the basis that it is a few lines
  > guarding an invariant nothing else enforces across read, write, and delete alike.
  > Recorded as knowingly speculative hardening, not as a defect anyone is currently
  > exposed to.

### Production surface

- **[modified] FR-009** — Test-data generation endpoints do not register under the production
  profile, and remain available under development and test with their existing admin-only
  behaviour. Priority: must-have.
  > Socrates: Counter-arguments offered: that it breaks the admin-surface-parity contract
  > without any test catching it; that the endpoints are already authenticated and
  > ADMIN-gated so this is not the fix; that it is surface reduction dressed as security and
  > makes production and development artifacts diverge. Resolution: **stands as written, no
  > counter-argument accepted** — random-data generation has no business in a production
  > build, and profile-gating costs no coverage, because the existing integration test
  > exercises all three paths under the test profile.

### Dropped during the challenge round

- **[removed from scope] Boot-time secret-provenance reporting** — originally "an operator can
  see, in the boot log, which secrets were supplied by the environment and which fell back to
  a default." Dropped. The user endorsed the counter-argument that a lone nice-to-have
  dilutes a security change and touches boot code for observability value a stated non-goal
  already rules out. Two alternatives — keeping it with the objection recorded, and narrowing
  it to the signing key alone — were offered and declined. The Secondary success criterion
  was this requirement and is dropped with it. Remaining requirements were renumbered.

## Constraints & Compatibility

**Backward compatibility.** The client 1.2.5 contract is fixed: paths, payloads and status
codes must not move. No client release is available — the client is frozen and consumed as a
prebuilt artifact from a private registry — so anything requiring a client change and a
version bump is out of reach for this change. That is precisely why FR-005's unverified
rejection rendering is an open question rather than a fixable item.

**Data migration.** None. No schema change, no backfill, no rollback plan required. The
production data volume holding live vehicle images must survive untouched; no backup story
for it has been verified, so every image-path change must work against the files already
there.

**Existing integrations.** The mail path, the reverse proxy, and the database container are
untouched. The deployment reads the new key through the same host-environment mechanism it
already uses for the database and mail passwords, so no new delivery mechanism is
introduced.

**Rollout dependency.** Key rotation requires host access. The change is not complete when
the branch merges — someone must edit a file on the deployment host. The repository alone
cannot finish it.

**Deployment topology — verified on the host 2026-08-30.** The live deployment is *not* driven
by this repository's `src/main/docker/app.yml`. The running container's Compose labels name
compose project `services`, config file `/home/kacper/services/carcare.yml`, working directory
`/home/kacper/services` — a separate private git repository holding one compose file per
service. Secrets reach it through a gitignored `~/services/.env` (with an encrypted committed
counterpart `.env.gpg`) via **native Compose variable substitution**: `carcare.yml` already
references `${CARCARE_MYSQL_USER}`, `${CARCARE_MYSQL_PASSWORD}` and `${CARCARE_MAIL_PASSWORD}`,
and no secret literal is committed there. This confirms the premise above rather than
contradicting it — the mechanism the signing key will use already exists and demonstrably
works; the change adds one variable to it.

Two superseded deployment paths remain on the host and in this repository, and must not be
mistaken for the live one: `~/carcare/artifacts/deploy.sh` (this repo's `src/main/docker/
deploy.sh`) `sed -i`-substitutes placeholders into `app.yml` destructively — one run consumes
the template permanently — and brings up a `carcare-app` container that is not running; and
`~/carcare/misc/env` carries three `*_ENV` keys matching this repo's `env-template` that
nothing live references. The repository's `src/main/docker/app.yml` and `env-template` are
therefore documentation of intent, not the operative delivery mechanism.

**Verification is advisory, not gating.** Merges are not gated on a green pipeline by owner
decision, so a passing pipeline cannot be relied on as a merge gate here.

**Explicitly preserved:** the client 1.2.5 contract; the existing two-role access model and
its anonymous permit-list; and the `./mvnw verify` baseline of 38 unit and 249 integration
tests.

## Business Logic Changes

**No domain logic change. This is an infrastructure and security change.**

The rule of operation the system applies — deriving costs, consumption and mileage
statistics from the five event types recorded against a vehicle, and selecting which
upcoming obligations warrant a reminder — is untouched. No requirement in this change alters
what the application decides for the user.

## Access Control Changes

**No access control changes — the current model is preserved.**

The model that stays as it is: stateless JWT bearer tokens signed with an HMAC key; two
roles, `ROLE_USER` and `ROLE_ADMIN`; exactly five anonymously permitted paths (register,
activate, authenticate, and the two password-reset steps); administrator gating on the admin
and management surfaces except health, info and metrics; authentication required everywhere
else under `/api`; and everything else permitted so the client's static assets load.
Per-owner data isolation stays enforced at the persistence boundary, where the ownership
predicate is embedded in the queries themselves.

**What this change touches** is the provenance of the signing key and a startup validation
over it — not who may do what. No role is added, no permit-list entry moves, no
authorization annotation changes placement, and no ownership query is rewritten.

**Rotation semantics.** Rotating the key invalidates every token in flight. The accepted cost
is **one forced re-login** for every active user. No dual-key grace window, no overlap
period.

**Surface reduction, not model change.** The test-data endpoints will stop registering under
the production profile; their existing administrator-only check stays exactly as it is. The
endpoints were already authenticated and admin-only, so nothing about who may call them
changes — only whether they exist in the production build.

**Deliberate non-adoptions.** Shortening token validity and making issued tokens revocable
were both offered and declined. Neither is a tolerated defect: the first is a client-facing
behaviour change without a stated justification, and the second would undercut the
statelessness the rest of the system is built on.

## Non-Goals

- **Development-profile origin policy.** The permissive origin configuration in the
  development profile stays as it is. Ruled out once inspection showed the production
  profile carries no such block and the filter registers nothing when the origin list is
  empty — it is a development-profile weakness, not a production exposure.
- **Token revocation and shortened token validity.** Both offered during shaping and
  declined. The authentication model stays mechanism-only; statelessness is preserved.

Two further items were offered as non-goals and **not** selected by the user: platform and
dependency upgrades — the tree stays on its current platform and dependency versions throughout,
despite that line being out of community support — and moving image storage off the
filesystem. Neither appears in any requirement here, and both are separate changes on the
modernization roadmap. Recorded as an observation about scope boundaries rather than as a
user-declared non-goal.

## Open Questions

1. ~~**How does client 1.2.5 render an oversized-body rejection? (FR-005)**~~ — **RESOLVED
   2026-08-30**, measured in a browser against a disposable stack; see
   `context/changes/security-baseline/oq-resolution.md`. The client renders the rejection as a
   **silent false success**: it logs an axios error to the console, then closes the edit modal
   and navigates to the details view exactly as after a successful save. No toast, no crash,
   no wedged form; retry works immediately. FR-005 is therefore implementable — but the false
   success is an accepted defect, not a clean pass, and the client is frozen so it cannot be
   fixed here. Separately, **no Spring or Tomcat property bounds a JSON request body** — all
   three candidates were verified to pass 2 MB through, and the bare application accepted
   60 MB — so FR-005 requires a pre-buffer `Content-Length` filter written for this change.
   The limit's value is recommended at 2 MiB from a nine-file sample and carried as a
   non-blocking unknown. Owner: user. Block: **no** (was yes).

2. ~~**Do any stored image files fall outside the FR-006 allowlist?**~~ — **RESOLVED
   2026-08-30**, inventoried read-only on the production volume. Nine files, 388 KB total:
   five PNG and four JPEG by byte-level detection. The allowlist is `{image/png, image/jpeg}`
   and **nothing stored today falls outside it**, so FR-006 and FR-007 do not collide. One
   constraint follows and must survive into implementation: four of those files are named
   `*.bin` (PNG content stored when the client declared a generic content type — the exact
   defect FR-006 fixes), so allowlist enforcement belongs on the **write path only**. Adding
   it to the read path would make those four unloadable and break FR-007. Owner: user.
   Block: **no** (was yes).

3. **Fail-fast versus the stated "no pager event" position.** — The user recorded that no
   failure of this change would page them. Refusing to start on a missing or default signing
   key does, by construction, convert a deployment-configuration omission into a boot
   failure. The two positions are compatible only if the deployment key injection lands
   before or with the fail-fast check. Flagged rather than resolved by assumption; belongs
   to the rollout ordering the roadmap will define. Owner: user, at roadmap time. Block: no.

4. **Secondary persona and the vehicle owner's own framing.** — Shaping captured the operator
   persona in detail and the affected owners only as an aggregate ("a handful of known
   people"). No secondary persona was elicited. Owner: user. Block: no — the change is
   operator-facing and the owner-facing surface is explicitly unchanged.

5. **User stories for the image-handling and production-surface work.** — Shaping captured no
   Given/When/Then blocks at all; US-01 and US-02 above are conversions of the primary
   success criterion. FR-004 through FR-009 have no stories, because none of them has a
   user-visible flow the user described. Owner: user. Block: no — the requirements are
   individually testable without stories.
