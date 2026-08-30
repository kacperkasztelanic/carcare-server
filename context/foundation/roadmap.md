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
| S-01 | `external-signing-key`         | operator deploys with no usable key in version control         | —             | FR-001, FR-002, US-01 (FR-003, US-02 → S-07) | done     |
| S-02 | `image-write-ordering`         | owner's stored image survives a failed vehicle update          | —             | FR-004, FR-007                 | ready    |
| S-03 | `request-body-limit`           | server refuses an oversized request body before buffering it   | —             | FR-005                         | done     |
| S-04 | `image-format-allowlist`       | server stores only genuine PNG/JPEG uploads                    | S-02          | FR-006, FR-007                 | proposed |
| S-05 | `image-path-containment`       | every image path resolves inside the data directory or refuses | —             | FR-008                         | ready    |
| S-06 | `production-surface-reduction` | production build ships without test-data generation            | —             | FR-009                         | ready    |
| S-07 | `signing-key-release`         | operator runs the fail-fast key server in production, verified end to end | S-01          | FR-003, US-02                   | ready    |

## Streams

Navigation aid — groups items sharing a Prerequisites chain. Canonical ordering lives in the
dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                    | Chain             | Note                                                                                  |
| ------ | ------------------------ | ----------------- | ------------------------------------------------------------------------------------- |
| A      | Signing key              | `S-01` → `S-07`   | The north star. S-01 is the repo + host work (its two-step rollout is internal — see its Risk); S-07 merges and deploys it and carries S-01's FR-003 verification. |
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
  existing mechanism is what S-01 extends. This repository's `src/main/docker/app.yml`,
  `env-template` and `deploy.sh` describe a **superseded** path — `deploy.sh` `sed -i`s
  placeholders into `app.yml` destructively and starts a `carcare-app` container that is not
  running. Do not plan against them.
- **Observability:** present — Actuator plus Prometheus under `/management`.

## Foundations

**None.** The only candidate was a separate slice delivering the signing key to the host
environment ahead of the fail-fast check. It was folded into S-01 by owner decision on
2026-08-30: once the deployment was verified, the delivery turned out to be one variable in a
`.env` that already holds three, plus one line in a compose file that already resolves three
others — not enough work to justify its own planning cycle.

The ordering constraint it existed to encode has **not** been dropped. It now lives inside
S-01 as a mandatory two-step rollout; see that slice's Risk field. Any plan for S-01 that does
not sequence key delivery before default removal has reintroduced the boot-outage the PRD
explicitly refuses.

## Slices

### S-01: The server refuses to run on a key that exists in version control

- **Outcome:** an operator deploys the server with no usable signing key anywhere in version
  control, and the application fails fast — naming the missing configuration — if the key is
  absent, empty, or too short for the signing algorithm. (Narrowed 2026-08-30: the
  previously-committed-value blocklist was dropped; see FR-002.)
- **Change ID:** `external-signing-key`
- **PRD refs:** FR-001, FR-002, US-01 — FR-003 and US-02 (the end-to-end verification) moved to S-07
- **Prerequisites:** —
- **Parallel with:** S-02, S-03, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - Which environment variable name does Spring's relaxed binding actually require to populate
    `application.security.authentication.jwt.base64-secret`? The placeholder at
    `application-prod.yml:105` is `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`, but
    `AGENTS.md` records that stale `jhipster.*` naming was purged from this tree once already.
    Verify empirically before the default is removed. — Owner: user. Block: no.
  - Does the deployed 1.3.10 image differ from this branch in any way that affects the boot
    path? Production runs app tag 1.3.10 while the repository is at 1.3.11. — Owner: user.
    Block: no.
- **Risk:** This is the north star and the only slice with a rollout step outside the
  repository — the PRD is explicit that "the change is not complete when the branch merges."

  **Mandatory two-step rollout, in this order.** This ordering was originally a separate
  foundation slice; folding it in did not make it optional.

  1. **Deliver the key, default still in place.** Add the signing-key variable to the
     gitignored `~/services/.env`, reference it from `/home/kacper/services/carcare.yml` the
     way `${CARCARE_MYSQL_PASSWORD}` and `${CARCARE_MAIL_PASSWORD}` already are, regenerate
     `.env.gpg`, and confirm the running container actually picks the value up. At this point
     the committed default is untouched and still functions as a fallback, so this step cannot
     take production down — which is exactly why it goes first.
  2. **Remove the default and add the fail-fast check.** Only after step 1 is verified on the
     host. Reversing these two converts a forgotten environment line into a boot outage, which
     the PRD refuses; it is the tension recorded in Open Question 3.

  Note the fail-fast check must cover **both** key fields: `ApplicationProperties.Jwt` binds
  `secret` (plain UTF-8) and `base64Secret` independently, and `TokenProvider` prefers the
  former when non-empty. Guarding only `base64-secret` leaves the other path open.

  Rotation invalidates every token in flight; the accepted cost is exactly one forced re-login,
  with no dual-key grace window. FR-003 is the verification half of this slice — but the merge,
  tag, production deploy and that end-to-end client session were **split out to S-07
  (`signing-key-release`) on 2026-08-30** at the owner's request, so they run on a separate cadence.
- **Progress (2026-08-30):** rollout step 1 (host key delivery) done and verified by token
  invalidation on the running `1.3.10` container — **the live exposure is closed**. Rollout step 2
  (remove the committed default, add the fail-fast guard covering both key fields, plus a length
  check, plus marking the superseded deploy files) landed on branch `refactor`, unpushed:
  `./mvnw verify` green at 42 unit / 249 integration. See
  `context/changes/external-signing-key/plan.md` Phases 1–4.
- **Status:** done (implementation landed and archived; production release tracked by S-07)

### S-02: A failed vehicle update leaves the stored image intact

- **Outcome:** when a vehicle's image is replaced, the previous file is deleted only after
  the transaction commits; a rollback leaves the stored file intact and reachable.
- **Change ID:** `image-write-ordering`
- **PRD refs:** FR-004, FR-007
- **Prerequisites:** —
- **Parallel with:** S-01, S-03, S-05, S-06
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
- **Parallel with:** S-01, S-02, S-05, S-06
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
- **Status:** done

### S-04: The server stores only genuine PNG and JPEG uploads

- **Outcome:** an uploaded image's type is determined from its actual bytes and only PNG and
  JPEG are accepted, regardless of the content type the client declares — while every image
  already on the volume stays loadable.
- **Change ID:** `image-format-allowlist`
- **PRD refs:** FR-006, FR-007
- **Prerequisites:** S-02
- **Parallel with:** S-01, S-03, S-05, S-06
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
- **Parallel with:** S-01, S-02, S-03, S-06
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
- **Parallel with:** S-01, S-02, S-03, S-05
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Costs no test coverage — the existing integration test exercises all three
  endpoints under the test profile, which stays unchanged. The objection recorded and
  overruled during shaping was that it makes production and development artifacts diverge;
  that divergence is the point. Watch that the admin-surface-parity contract is not broken
  silently, since no test currently covers the production profile's registration.
- **Status:** ready

### S-07: The fail-fast signing-key server runs in production

- **Outcome:** the repository hardening from S-01 (empty prod default, fail-fast guard on both
  key fields plus a length check, superseded-deploy-file headers) is merged, tagged, built into
  a production image, and deployed to the host — and a client-1.2.5 session runs end to end
  against it (list vehicles, open one, record an event) with **no** login prompt beyond the one
  already spent in S-01's host rotation.
- **Change ID:** `signing-key-release`
- **PRD refs:** FR-003, US-02
- **Prerequisites:** S-01 (its Phases 1–4 are already done on branch `refactor`)
- **Parallel with:** may batch with the merge/release of any other slice — it is a release gate,
  not code
- **Blockers:** —
- **Unknowns:**
  - **Branch and tag strategy.** `master` is ~130 commits behind `refactor`, which is the de facto
    mainline; `.gitlab/gitlab-ci.yml`'s release path fires on `$CI_COMMIT_TAG`, and `verify` also
    runs on `$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH`. Whether to fast-forward `master`, retarget
    the default branch, or tag from `refactor` directly is an owner decision and gates the release.
    — Owner: user. Block: yes.
  - **Tag value.** `pom.xml` is at `1.3.11`, production runs `1.3.10`; tag `1.3.11` unless the
    owner bumps first. — Owner: user. Block: no.
- **Risk:** The only slice that touches production. S-01's host step already rotated the signing
  key on the running `1.3.10` container, so this deploy must cost **no** further forced re-login:
  a second re-login means the key did not survive the `1.3.10 → 1.3.11` image swap and the deploy
  must roll back to `1.3.10` (which still binds `JHIPSTER_*`, so rollback itself forces no
  re-login). The continuity token must be minted minutes before the swap — not carried from S-01,
  whose 24-hour token would expire on its own and produce a false rollback trigger. Merges are
  not gated on a green pipeline (`only_allow_merge_if_pipeline_succeeds` is `false`), and the
  tag-only release path (`test`, `build`, `app`, `proxy`) has not executed since the Phase 4
  header edits — check it deliberately.
- **Status:** ready

## Backlog Handoff

| Roadmap ID | Change ID                      | Suggested issue title                                        | Ready for `/10x-plan` | Notes                                    |
| ---------- | ------------------------------ | ------------------------------------------------------------ | --------------------- | ---------------------------------------- |
| S-01       | `external-signing-key`         | Supply the signing key from the host, then fail fast without it | yes                 | Start here — the north star; two-step rollout inside |
| S-02       | `image-write-ordering`         | Delete a replaced vehicle image only after commit             | yes                   | Parallel-safe                            |
| S-03       | `request-body-limit`           | Reject oversized request bodies before buffering              | yes                   | Needs new filter code; see Risk          |
| S-04       | `image-format-allowlist`       | Accept only byte-verified PNG and JPEG uploads                | no                    | Needs S-02; write path only              |
| S-05       | `image-path-containment`       | Contain every image path under the data directory             | yes                   | Speculative hardening, by owner decision |
| S-06       | `production-surface-reduction` | Exclude test-data endpoints from the production profile       | yes                   | Parallel-safe                            |
| S-07       | `signing-key-release`         | Merge, tag and deploy the external-signing-key hardening       | no                    | Needs S-01; branch/tag strategy is an open owner decision |

## Open Roadmap Questions

1. ~~**How does client 1.2.5 render an oversized-body rejection? (FR-005)**~~ — **RESOLVED
   2026-08-30.** Measured in a browser against a disposable stack. The client renders the
   413 as a silent false success; it does not crash or wedge, and retry works. See
   `context/changes/security-baseline/oq-resolution.md`. The residual — the limit's exact
   value — is carried as a non-blocking Unknown on S-03.
2. ~~**Do any stored image files fall outside the FR-006 allowlist?**~~ — **RESOLVED
   2026-08-30.** No. Nine files, PNG and JPEG only. FR-006 and FR-007 do not collide, subject
   to the write-path-only constraint recorded on S-04.
3. **Fail-fast versus the stated "no pager event" position.** — **Discharged by sequencing,
   now internal to S-01.** The two positions are compatible only if key injection lands before
   or with the fail-fast check. This was originally encoded as an `F-01 → S-01` dependency;
   when that foundation was folded into S-01 on 2026-08-30, the constraint became the
   mandatory two-step rollout in S-01's Risk field. It is a plan-level obligation now rather
   than a graph-level one, which makes it easier to lose — the plan review should check for it
   explicitly. Owner: user, at plan time. Block: no.
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
   S-01, since that slice is where the divergence bites.
7. **Does production's 1.3.10 image differ from this branch in ways that affect rollout?** —
   New, surfaced while verifying the proxy configuration. Production runs app tag 1.3.10;
   this repository is at 1.3.11. **Answered for S-01 (2026-08-30):** `1.3.10` binds the key only
   under the legacy `JHIPSTER_*` prefix, so the host now sets both spellings; S-01's Phases 1–4
   shipped against that. The residual — proving the key survives the `1.3.10 → 1.3.11` image swap
   with no second re-login — moves to **S-07** along with the branch/tag strategy. Owner: user.
   Block: no for S-01; yes for S-07.

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

- **S-01: an operator deploys the server with no usable signing key anywhere in version control,
  and the application fails fast — naming the missing configuration — if the key is absent, empty,
  or too short for the signing algorithm. (Narrowed 2026-08-30: the previously-committed-value
  blocklist was dropped; see FR-002.)** — Archived 2026-08-30 →
  `context/archive/2026-08-30-external-signing-key/`. Lesson: —.
- **S-03: server refuses an oversized request body before buffering it** — Archived 2026-08-30 →
  `context/archive/2026-08-30-request-body-limit/`. Lesson: —.
