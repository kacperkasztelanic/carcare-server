---
project: "CarCare Server"
context_type: brownfield
created: 2026-08-24
updated: 2026-08-24
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "change scope boundary"
      decision: "Foundation slice, narrowed in Phase 3. IN: green build, Jakarta/Spring Security 6 migration, de-JHipster (TODO 1), dependency versions (TODO 2), EN e-mail fix (TODO 7), vehicle deletion/cascade policy (TODO 8), and regression-test safety nets. DEFERRED: TODO 3 (MariaDB->PostgreSQL) and TODO 6 (Liquibase->Flyway) cut in Phase 3 to remove the irreversible failure mode; also TODO 4 (Vavr/FP sweep), 5 (entity restructure + Postgres JSON), 9 (i18n rethink), 10 (impersonation), 11 (Maven->Gradle), 12 (UUID PKs / non-leaking IDs)."
    - topic: "user base / tenancy"
      decision: "Personal-scale: the owner plus a handful of known people. Not a multi-tenant fleet product. Coordination cost of a client release is low."
    - topic: "change motivation"
      decision: "Three drivers, all active: (1) hard blocker - the branch does not build; (2) cost-of-change - platform drift makes any feature work expensive; (3) craft - a modern, internally consistent codebase is itself a goal."
    - topic: "must-preserve contracts"
      decision: "REST API contract with React client 1.2.5, and existing production data. Deployment shape (Docker/NGINX/WAR) is NOT declared preserved and may change."
    - topic: "access-control model"
      decision: "No model change. Same two roles, same public-path list, same @PreAuthorize placement, same repository-level ownership filtering. Spring Security 6 rewrite is mechanism-only. In-scope tightening limited to deduplicating the repeated session/header config blocks and adding missing @Valid on business request bodies."
    - topic: "security defects surfaced by analysis"
      decision: "Deferred to a separate security pass: committed prod JWT secret, dev CORS allow-all-with-credentials, frameOptions deny/disable contradiction. Recorded in Open Questions. Note: the frame-options item collides with the in-scope header-block deduplication and cannot be fully deferred."
    - topic: "scope-cost decision"
      decision: "Scoped down: PostgreSQL + Flyway cut. De-JHipster kept in after a corrected 4-6 week estimate was surfaced. delivery_weeks: 5, after-hours only, no hard deadline. See '## Timeline acknowledgment'."
    - topic: "secondary success criterion"
      decision: "Eliminating the N+1 fan-out in VehicleRichMapper. Nice-to-have, explicitly not sufficient on its own."
    - topic: "CODEBASE_ANALYSIS.md refuel-edit defect"
      decision: "False positive, verified against source. RefuelServiceImpl.editRefuel uses the correct repository; all five event services are correct. No FR written. The analysis's 'Functional correctness: High' rating is discounted and its sequencing step 3 is void. See '## Correction to CODEBASE_ANALYSIS.md'."
    - topic: "vehicle deletion policy"
      decision: "Soft-delete / archive: vehicle disappears from the owner's list, all event history retained. Verified net-new - no archived/deleted flag exists in domain/ or any Liquibase changelog. Sequenced to land only AFTER the migration is green and test-covered, so archive-filter edits never interleave with namespace edits in the same JPQL."
    - topic: "Socratic descopes"
      decision: "FR-010 (@Valid on business payloads) removed - hardening was deferred in Phase 2 and it conflicts with FR-008 compatibility. FR-014 (VehicleRichMapper N+1) removed - the real fix is the deferred TODO 5 restructure."
    - topic: "migration verification baseline"
      decision: "Reconstruct golden output from commit 2a20e8a (2023-03-22, Boot 2.7.3 / JHipster 7.9.3) - the last coherent stack, three commits back. The break was introduced by a single commit, 5b78f3f. Buildability of 2a20e8a is plausible but unverified."
    - topic: "secondary success criterion (replacement)"
      decision: "CI compile/test/verify gating on merge requests, not only on tags. Replaced the dropped N+1 criterion."
    - topic: "archive domain rule"
      decision: "Archived vehicles still count toward cost reports and statistics; they disappear only from the owner's vehicle list. Archive filter is therefore NOT uniform - it applies to the list but not to cost/statistic queries. Derived but unconfirmed: forward-looking features (upcoming events, reminders) exclude archived vehicles."
    - topic: "non-functional requirements"
      decision: "One NFR only: existing accounts keep working with at most one forced re-login. Performance, reminder-timing, and resource-envelope NFRs were offered and declined - recorded as deliberate non-adoptions."
    - topic: "rollback strategy"
      decision: "Parallel run against a restored copy of production data, compared to the FR-016 golden baseline before cutover. Shares setup with FR-016."
    - topic: "non-goals"
      decision: "Functional: TODO 3, 4, 5, 6, 9, 10, 11, 12; descoped FR-010 and FR-014; test-data endpoint removal. Non-functional: observability, vulnerability scanning, CI coverage thresholds, performance targets. Left open rather than ruled out: reminder robustness, API versioning, lookup service extraction, image-storage hardening."
    - topic: "product framing"
      decision: "No change. product_type: web-app; target_scale.users: small; delivery_weeks: 5; hard_deadline: null; after_hours_only: true."
    - topic: "archive scope over forward-looking features"
      decision: "Confirmed at cross-check: backward-looking features (costs, statistics, historical reports) INCLUDE archived vehicles; forward-looking features (/api/events upcoming events, daily reminder job) EXCLUDE them."
    - topic: "baseline commit verification"
      decision: "VERIFIED in-session: 2a20e8a compiles clean on JDK 17 (186 files, offline) but all 102 integration tests fail to load a Spring context. Two stacked pre-existing defects: test config references tech.jhipster.domain.util.FixedH2Dialect which does not exist in jhipster-framework 7.9.3, and an H2 schema-validation mismatch on inspections.details that survives substituting other dialects. FR-016 should capture its baseline against restored production data on MariaDB instead of via H2."
    - topic: "earlier-commit baseline search"
      decision: "No commit in reachable history has a green ./mvnw verify. IT suite broke at 63d72ef (2022-08-01) when jhipster-framework 7.9.0 dropped FixedH2Dialect. Best baseline: 6e19b96 (2022-05-20) at 94/102, or 3e91ed4 (identical). Older commits cannot build - client artifact 1.1.0 is unavailable. Critically: src/main/java and the Liquibase changelogs are BYTE-IDENTICAL between 3e91ed4 and HEAD, so the baseline's behaviour is HEAD's behaviour."
    - topic: "LoginVm deserialization"
      decision: "NOT a production bug. Proven empirically: the Spring Boot ObjectMapper deserializes LoginVm fine (parameter-names module + -parameters); only a plain ObjectMapper fails. The 3 UserJwtControllerIT failures are artifacts of MockMvcBuilders.standaloneSetup. FR-015 should use full-context MockMvc."
    - topic: "blast radius / pager event"
      decision: "The named pager event is: client cannot authenticate or read vehicles. Data loss was not selected as a pager event despite production data being declared must-preserve - flagged for Phase 3 guardrails."
  frs_drafted: 15
  quality_check_status: accepted
---

# Shape Notes — CarCare Server modernization (foundation slice)

Source material supplied by the user: `carcare-server-todo.txt` (12 items) and
`CODEBASE_ANALYSIS.md` (branch `refactor`, commit `16eb931`). Both were read in
full. Content below is captured from the user's own statements and those two
documents; nothing here is invented.

## Current System Overview

**Purpose.** CarCare is a vehicle/fleet-management backend: each user owns
vehicles and records five kinds of lifecycle event against them; the server
derives costs and fuel/mileage statistics, generates Excel reports, and sends
e-mail reminders for upcoming obligations.

**Architecture.** Single-deployment layered monolith, originally generated by
JHipster 5.5.0. `web/rest` → `service` → `repository` → `domain`, with an
ArchUnit test forbidding `service`/`repository` from depending on `web`.
`Vehicle` is the aggregate root, owned by a `User`. Five independent event
tables (`Refuel`, `Repair`, `RoutineService`, `Inspection`, `Insurance`) each
embed a shared `VehicleEvent` value object holding mileage + date. `FuelType`,
`InsuranceType`, and `ReminderAdvance` are global lookup/config tables. Data
isolation is enforced in the persistence boundary: repository JPQL embeds
`vehicle.owner.login = ?#{principal.username}`.

**Tech stack.** Java 17. Declared platform is Spring Boot 3.1.5 with the
`jhipster-dependencies` 8.0.0 BOM. Spring MVC, Spring Security with stateless
JWT (`jjwt`), Zalando Problem. Spring Data JPA + Hibernate 6.2.13, HikariCP,
MariaDB in dev/prod and H2 in tests. Liquibase 4.20 XML for schema. Ehcache
second-level cache. Lombok 1.18.30 + MapStruct 1.5.3. Apache POI 5.2.5 for
XLSX, Apache Tika, Commons IO. Vavr 0.10.3, Guava. JUnit 5, ArchUnit, JaCoCo.
Maven Wrapper 3.9.6, GitLab CI, Docker/DinD, NGINX reverse proxy, WAR
packaging. The React/TypeScript client is a separate repository, consumed here
as the prebuilt Maven artifact `com.kasztelanic.carcare:client` version 1.2.5
from a private GitLab Maven registry; its static assets are served from the WAR.

**Current user base.** Personal scale — the owner plus a handful of known
people. Real accounts and real data exist in a live deployment. Two roles:
`ROLE_USER` (owns vehicles, records events) and `ROLE_ADMIN` (user/authority
administration, audit access, lookup-table maintenance, test-data generation,
manual reminder dispatch).

**Core functionality today.** Registration → e-mail activation → JWT
authentication → account/password management and reset. Owned-vehicle CRUD with
server-assigned ownership and local-filesystem image storage. Owned-event CRUD
across all five event types. `/api/events` upcoming-event planning across
caller-selected vehicles and date ranges. A daily 08:00 server-local scheduled
job that reads global reminder-day offsets and queues localized reminder e-mail
to vehicle owners. Consumption/mileage/cost statistics and two in-memory XLSX
reports (per-vehicle detail, multi-vehicle cost). 86 mapped REST handlers across
18 resource classes, all under `/api`, with Actuator under `/management`. i18n
for English and Polish across messages and mail templates.

## Problem Statement & Motivation

**The gap.** The `refactor` branch does not build. `./mvnw compile` fails during
Maven model construction because eleven dependencies receive no version from the
declared JHipster 8.0.0 BOM. Even past that, 184 legacy `javax.*` and
pre-Spring-Security-6 usages (`WebSecurityConfigurerAdapter`, `antMatchers`)
contradict the declared Boot 3 / Hibernate 6 / Spring Security 6 platform. There
is no green baseline, so no test result on this branch means anything and the
`target/carcare-*.war` present in the tree is a stale artifact from the old
Boot 2.x build.

Beneath the build failure sits accumulated platform and design drift that makes
ordinary change expensive: JHipster coupling the project no longer wants,
dependency versions that have gone stale, MariaDB and Liquibase where the owner
wants PostgreSQL and Flyway, vehicle deletion that the API advertises but that
fails against non-cascading foreign keys, and English e-mail templates that are
broken.

**Why now.** Three drivers, all active simultaneously. First, this is a hard
blocker rather than an improvement — nothing ships in any form until the build
is restored. Second, the cost of change is itself the problem: platform drift
means any feature work means fighting a half-migrated framework. Third, a
modern and internally consistent codebase is an explicit goal in its own right,
not only a means to feature velocity.

**Current workaround and its cost.** There is none. The branch cannot compile,
so the work is stalled outright; the deployed system runs from an older build
that no longer corresponds to the source on this branch.

**Scope decision.** The user's `carcare-server-todo.txt` describes a twelve-item
modernization programme. This change deliberately takes the foundation slice
only — the work that unblocks everything else — and defers the rest to later
PRDs once a green, tested baseline exists. In scope: TODO 1 (de-JHipster),
2 (dependency versions), 7 (EN e-mails), 8 (vehicle deletion/cascades), plus the
implicit green-build and Jakarta/Spring Security 6 migration work that the
codebase analysis identifies as prerequisite, plus regression-test safety nets.
Deferred: TODO 3, 4, 5, 6, 9, 10, 11, 12.

The database migration (TODO 3 MariaDB→PostgreSQL, TODO 6 Liquibase→Flyway) was
cut from this slice during Phase 3, after the scope-cost was surfaced. The
reasoning is recorded under `## Timeline acknowledgment`: migrating live
production data is the only irreversible operation in the whole programme, and
doing it before any behavioural test coverage exists inverts the sequencing the
codebase analysis recommends. It becomes its own change, against a tested
baseline, with its own rollback plan.

> Socrates (why hasn't this been done already?): the change is non-obvious
> because the ordering is counter-intuitive under pressure. The instinct with
> 184 legacy imports is to mass-convert them first, but an unresolvable
> dependency graph means the compiler cannot give trustworthy feedback, so the
> real migration surface stays hidden. Dependency resolution has to be settled
> before a single import is touched.

## Correction to CODEBASE_ANALYSIS.md

Verified against source at `src/main/java/com/kasztelanic/carcare/service/impl/`
during Phase 4 of this shape session.

`CODEBASE_ANALYSIS.md` rates "Functional correctness" as **High** on the basis
that `RefuelServiceImpl.editRefuel` "queries
`VehicleRepository.findByIdAndOwnerIsCurrentUser(id)` using a refuel ID, maps a
`Vehicle` through a refuel updater, and passes it to `refuelRepository.save`".
It repeats this as step 3 of its recommended sequencing ("Fix the refuel edit
defect").

**This defect does not exist.** `RefuelServiceImpl.editRefuel` (line 51) calls
`refuelRepository.findByIdAndOwnerIsCurrentUser(id)` — the correct repository,
correctly typed. All five event services (`Refuel`, `Repair`, `RoutineService`,
`Inspection`, `Insurance`) are structurally identical and all five are correct.
The `vehicleRepository.findByIdAndOwnerIsCurrentUser(vehicleId)` call that the
analysis flagged sits in each service's **add** method (~line 43), where looking
up the parent vehicle to attach a new event to is the intended behaviour.

Consequences for this change:

- No FR is written for a refuel-edit fix; there is nothing to fix.
- The analysis's "Functional correctness: High" rating should be discounted.
- Remaining findings in that document were not individually re-verified. The
  build failure, the `javax.*` count, and the deletion/cascade issue are
  structural claims that the roadmap depends on; a spot-check of those is
  recommended before planning. Recorded as an Open Question.

## User & Persona

**Primary persona — the vehicle owner (existing user, `ROLE_USER`).** A private
individual tracking their own small number of vehicles. They register, log in
through the React client, record refuels, repairs, routine services,
inspections, and insurance policies against their vehicles, and rely on the
system to tell them what is coming due and what it has all cost. They reach for
the product at two moments: immediately after a real-world event (a refuel, a
service visit) to record it, and passively when a reminder e-mail arrives about
an expiring inspection or insurance policy.

For this change their experience should be indistinguishable from today. They
are affected only in that the system continues to work, on a modern platform,
with vehicle deletion and English e-mail now behaving correctly.

**Secondary persona — the administrator (`ROLE_ADMIN`).** Manages users and
authorities, reads audit history, maintains lookup tables, and can trigger
reminder dispatch manually. Unchanged by this work.

**Third party — the React client 1.2.5.** Not a person, but a consumer whose
contract is load-bearing: it is released separately from a private registry and
must keep working against the modernized server.

## Success Criteria

### Primary

- `./mvnw compile`, `./mvnw test`, and `./mvnw verify` all run green from a
  clean checkout on Java 17, with no legacy-API compilation escape hatches
  remaining — zero `javax.persistence`/`javax.validation`/`javax.servlet`
  imports, no `WebSecurityConfigurerAdapter`, no `antMatchers`.
- No JHipster dependency remains: the `jhipster-dependencies` BOM is gone, every
  dependency carries an explicit managed version, and `tech.jhipster.*` usages
  are replaced.
- An existing user, through the unmodified React client 1.2.5, can log in, list
  and fetch their vehicles, and create/read/update/delete every one of the five
  event types — with the same paths, payloads, and status codes as before.
- Deleting a vehicle that has event history succeeds according to an explicitly
  decided and documented retention policy, rather than failing against a
  non-cascading foreign key.
- The English reminder and account e-mail templates render correctly.
- Regression tests exist and pass for: owner isolation including negative cases,
  each of the five event CRUD paths, `RefuelServiceImpl.editRefuel`, vehicle
  deletion under the chosen policy, reminder selection, and report
  output/content type.

### Secondary

- Merge requests get automated compile/test/verify feedback, rather than
  verification running only on tags as it does today. Desirable, not sufficient:
  a green pipeline over a suite that does not yet exist proves nothing, so this
  has value only once the regression suite (FR-015) lands. Replaced the original
  secondary criterion (eliminating the `VehicleRichMapper` N+1), which the
  Socratic round moved to the deferred TODO 5 restructure.

### Guardrails

Failure on any of these is a regression even if every Primary criterion holds.

- **Owner isolation never weakens.** No user can reach another user's vehicles
  or events through any path. The repository-level
  `vehicle.owner.login = ?#{principal.username}` filtering survives the Jakarta
  and Hibernate 6 rewrite intact on every query. This is the highest-severity
  guardrail: it fails silently, it touches every query the migration edits, and
  there are currently no negative-case tests for it.
- **Production data survives intact and verifiably.** No vehicle, event, user
  account, or audit record is lost or mangled. "Verifiably" is load-bearing —
  there must be a way to demonstrate this after the fact, not an assumption.
- **Client 1.2.5 keeps working unchanged.** Paths, payloads, status codes, and
  JWT behaviour stay compatible. This is the named pager event: if the client
  cannot authenticate or read vehicles, the change has failed.

## Timeline acknowledgment

Acknowledged on 2026-08-24: the scope-cost of this change was surfaced
explicitly and the user responded by cutting scope, then accepted the remaining
estimate.

- Initial full-slice estimate was 6–10 weeks of after-hours work.
- The user cut MariaDB→PostgreSQL (TODO 3) and Liquibase→Flyway (TODO 6) out of
  this change, removing the only irreversible failure mode — a partially
  completed database migration against live production data with no behavioural
  test coverage.
- A corrected estimate of 4–6 weeks was then put to the user, because
  de-JHipster (TODO 1) is itself a 1–2 week item touching the same files as the
  Jakarta migration. The user chose to keep it in and accepted 4–6 weeks.
- Recorded `delivery_weeks: 5`. After-hours only; no hard deadline.

The user accepted that this requires sustained effort across several weeks of
evenings and weekends, including stretches where progress is invisible.

## User Stories

### US-01: An existing user's session is indistinguishable after the migration

- **Given** an existing account with vehicles and event history, and the unmodified React client 1.2.5
- **When** the user logs in, lists vehicles, opens one, and records a refuel
- **Then** every response matches what the pre-migration server returned — same paths, same payload shape, same status codes
- **Before:** the same flow ran on Spring Boot 2.x / `javax.*` / MariaDB from a stale WAR, because the current branch does not build at all.

#### Acceptance Criteria
- No client-side change is required for any of these operations.
- A second user's data is not reachable through any of them.
- The JWT issued before the migration is not required to survive it; one re-login is acceptable.

### US-02: An owner deletes a vehicle that has history

- **Given** a vehicle owned by the user with at least one refuel, repair, service, inspection, and insurance record
- **When** the owner deletes that vehicle
- **Then** the operation completes according to the decided retention policy, and the outcome is the one the policy specifies
- **Before:** the API advertised the delete, but it failed at the database layer against non-cascading foreign keys.

#### Acceptance Criteria
- The retention policy is written down before the code implements it.
- The behaviour is covered by an integration test running against a real schema, not a mock.
- No other user's data is affected.

### US-03: An English-language user receives a correct reminder

- **Given** an account whose language is English, with an inspection expiring inside the configured reminder window
- **When** the daily 08:00 job runs
- **Then** the user receives a correctly rendered English reminder naming the vehicle and the expiry
- **Before:** the English templates were broken (TODO 7); Polish was unaffected.

#### Acceptance Criteria
- Both English and Polish templates render without placeholder leakage.
- Reminder selection is unchanged — only rendering is fixed.
4. **Are the remaining CODEBASE_ANALYSIS.md findings accurate?** One High-rated
   finding (the refuel-edit defect) was verified as a false positive during this
   session. The build failure, the 184-usage `javax.*` count, and the
   non-cascading foreign keys are load-bearing for the whole plan and were not
   individually re-verified here. Owner: user / planning step. Recommended
   before `/10x-plan`.
5. **Do forward-looking features exclude archived vehicles?** The archive rule
   (FR-009) was decided for costs and statistics — they include archived
   vehicles. Whether `/api/events` upcoming-events and the daily reminder job
   exclude them was derived, not stated. Owner: user. Blocks: FR-009 and FR-012
   acceptance criteria.
6. **Is commit `2a20e8a` actually buildable?** FR-016's golden baseline depends
   on it. The commit was identified as the last coherent stack (Boot 2.7.3 /
   JHipster 7.9.3) but was not built during this session. Owner: planning step.
   Blocks: FR-016, and therefore FR-013.
7. **Reminder delivery robustness.** Idempotency, retry, distributed lock, and an
   explicit timezone decision. Offered as a non-goal and not ruled out. Owner:
   user. Not blocking this change.
8. **API versioning and path normalization.** Offered as a non-goal and not ruled
   out. Blocks TODO 12 whenever it is taken up. Owner: user.
9. **Extracting services behind the lookup and reminder-advance controllers.**
   Source carries "extract service" TODOs. Offered as a non-goal and not ruled
   out. Owner: user. Not blocking this change.
10. **Consolidating ownership enforcement.** FR-005 preserves SpEL principal
    filtering scattered across every query, enforced by convention. Moving it to
    a single auditable boundary was raised in the Socratic round and deferred to
    the TODO 5 restructure. Owner: user.
11. **Should test-data generation remain a production REST surface?** Ruled out
    of this change, but the source TODO stands. Owner: user.
12. **Image-storage hardening.** No size limit, no content verification, and the
    old file is deleted before the transaction commits. Not ruled out, not in
    scope. Owner: user.

## Scope of Change

Category tokens follow the brownfield schema: `[new]` / `[modified]` /
`[removed]` / `[preserved]`. `[preserved]` entries are defensive — they exist so
that preservation is explicit and testable rather than assumed.

Every FR below carries a `> Socrates:` blockquote recording the strongest
counter-argument the user considered and how it resolved. Three FRs were revised
and two were descoped as a direct result of that round.

### Build & platform

- FR-001: [modified] A developer can run `./mvnw compile`, `./mvnw test`, and `./mvnw verify` green from a clean checkout on Java 17, with every dependency resolving to an explicit managed version. Priority: must-have
  > Socrates: Counter-argument considered — "a compiling build is table stakes,
  > a precondition for work rather than an outcome worth tracking." Resolution:
  > kept as written. On this branch it is not table stakes; it is the blocking
  > condition, and it is the only criterion whose failure is currently certain.

- FR-002: [removed] The project carries no JHipster dependency — neither the `jhipster-dependencies` BOM nor any `tech.jhipster.*` class. Priority: must-have
  > Socrates: Counter-argument considered — "it's craft, not need: the build can
  > go green on the JHipster 8 BOM, and de-JHipster consumes 1–2 weeks of a 4–6
  > week budget while unblocking nothing." Resolution: kept. The user accepted
  > the corrected 4–6 week estimate specifically to retain this item, and
  > removing JHipster is a stated goal of the work rather than a means to it.
  > Consequence accepted: ~40 dependency versions become hand-managed.

- FR-003: [modified] The codebase compiles against Jakarta EE 9+ namespaces throughout, with no `javax.persistence`, `javax.validation`, or `javax.servlet` import remaining. Priority: must-have
  > Socrates: Counter-argument considered — "it's a consequence, not a
  > requirement: Boot 3 mandates the Jakarta namespace, so this states a
  > platform constraint rather than a decision." Resolution: kept, because the
  > *absolute* form is the decision. 'No escape hatches' rules out the common
  > failure mode of leaving a compatibility shim in place and calling the
  > migration done.

- FR-004: [modified] Security is configured through a bean-based `SecurityFilterChain` with `requestMatchers`, producing authorization outcomes that are deliberate and documented — with any divergence from the previous configuration recorded as an explicit decision rather than an accident. Priority: must-have
  > Socrates: Counter-argument considered — "Spring Security 6 defaults
  > genuinely differ (authorization on forward/error dispatch, permitAll
  > semantics, CSRF), so reproducing old behaviour exactly may mean deliberately
  > re-creating weaker defaults." **FR revised**: the original wording demanded
  > "identical" outcomes; it now demands deliberate and documented ones. Where
  > Spring Security 6's default is safer, the default wins and the difference is
  > written down.

### Access & isolation

- FR-005: [preserved] A vehicle owner can reach only their own vehicles and events, on every path, with the principal constraint still enforced at the repository boundary. Priority: must-have
  > Socrates: Counter-argument considered — "preserving it entrenches a fragile
  > pattern: the constraint is SpEL scattered across every query, enforced by
  > convention, and nothing stops a new query from omitting it." Resolution:
  > preserved for this change; the fragility is real but consolidating
  > enforcement into a single auditable boundary is a design change that belongs
  > with the deferred TODO 5 restructure. Recorded as an Open Question so it is
  > not lost.

- FR-006: [preserved] A visitor can register, activate an account via e-mail key, authenticate for a JWT, and complete a password reset, unchanged. Priority: must-have
  > Socrates: No counter-argument; it stands as written. Note for planning: this
  > is the same generated code FR-002 deletes, so it is the largest single piece
  > of work wearing a `[preserved]` label.

- FR-007: [preserved] An administrator can manage users and authorities, read audit history, maintain lookup tables, generate test data, and trigger reminder dispatch, unchanged. Priority: must-have
  > Socrates: Counter-argument considered — "it preserves debt the code itself
  > flags: lookup and reminder-advance resources bypass the service layer and
  > carry 'extract service' TODOs." Resolution: preserved unchanged. Extracting
  > those services is a refactor, and this change's purpose is to prove nothing
  > moved. Recorded as an Open Question, alongside whether test-data generation
  > should remain a production REST surface at all.

### Vehicle & event management

- FR-008: [preserved] A vehicle owner can create, read, update, and delete vehicles and all five event types through the same REST paths, payloads, and status codes that client 1.2.5 uses today. Priority: must-have
  > Socrates: Counter-argument considered — "you control the client, so a
  > coordinated client release may be cheaper than preserving a contract you
  > don't like across a large migration." Resolution: preservation stands, per
  > the Phase 1 must-preserve decision and because client breakage is the named
  > pager event. A coordinated client release is retained as an escape hatch if
  > preservation proves disproportionately expensive on a specific endpoint —
  > used deliberately, not as a fallback for surprise.

- FR-009: [new] A vehicle owner can archive a vehicle that has event history: the vehicle disappears from their vehicle list while all of its events, costs, and report history are retained. Archiving is implemented only after FR-001 and FR-015 are satisfied, so archive-filter changes are never interleaved with namespace changes in the same query. Priority: must-have
  > Socrates: Counter-argument considered — "the FR promises an outcome it
  > doesn't name; cascade, soft-delete, archive, and block are four different
  > products." **FR revised twice.** First, the policy was decided: soft-delete /
  > archive, retaining history. Second, verification against source showed no
  > `archived`/`deleted` flag exists anywhere in `domain/` or in any Liquibase
  > changelog — so this is `[new]`, not `[modified]`, and it adds a column plus a
  > filter on every ownership-constrained query. Because those are the same
  > queries FR-003 and FR-005 touch, the user sequenced it to land only after the
  > migration is green and test-covered.

- **FR-010 — DESCOPED.** Bean Validation (`@Valid`) on business resource request bodies.
  > Socrates: Counter-argument considered — "it's hardening, and hardening was
  > deferred to a separate security pass in Phase 2." Resolution: **removed from
  > this change** for consistency with that boundary, and because it sat in
  > direct tension with FR-008's compatibility promise — entity constraints were
  > authored for persistence-time checking and could reject payloads client 1.2.5
  > legitimately sends today. Moves to the deferred security pass, which should
  > first establish what the client actually sends. This reverses part of the
  > Phase 2 "tighten what's broken" decision; the header/session-block
  > deduplication remains in scope.

### Notifications

- FR-011: [modified] A vehicle owner receiving English-language reminder and account e-mail gets a correctly rendered message. Priority: must-have
  > Socrates: No counter-argument; it stands as written. Flagged for planning:
  > the cause is undiagnosed. If it turns out to be `MessageSource`
  > configuration rather than template syntax, the fix touches TODO 9 (rethink
  > internationalization), which is deferred — treat that as a stop-and-reassess
  > point rather than scope creep.

- FR-012: [preserved] The daily 08:00 scheduled job selects and queues the same reminders for the same owners as it does today. Priority: must-have
  > Socrates: Counter-argument considered — "preserving it preserves
  > at-most-once-by-luck: no idempotency record, no retry, no distributed lock,
  > fires on exact configured offsets, so a restart across 08:00 silently skips a
  > day." Resolution: preserved *as-is, knowingly*. The current behaviour is
  > explicitly accepted as imperfect and preserved so the migration remains
  > verifiable. Reminder robustness — idempotency, retry, an explicit timezone
  > decision — is recorded as an Open Question for its own change.

### Analysis & reporting

- FR-013: [preserved] A vehicle owner can request consumption, mileage, and cost statistics and both XLSX reports, and receive output that matches the golden baseline captured under FR-016 at the value level — cell values, computed figures, and content type — rather than byte-for-byte. Priority: must-have
  > Socrates: Counter-argument considered — "there is no way to capture the
  > 'today' baseline, because the branch doesn't build." **FR revised**: it now
  > names FR-016's reconstructed baseline as the reference, and specifies
  > value-level rather than byte-level equivalence, since Apache POI writes
  > version-dependent bytes and costs are widened from integer cents to double
  > on output.

- **FR-014 — DESCOPED.** Eliminating the `VehicleRichMapper` N+1 fan-out.
  > Socrates: Counter-argument considered — "doing it properly means read models
  > and projections, which is TODO 5 and explicitly deferred; anything cheaper is
  > a patch on a twelve-dependency class you plan to redesign anyway."
  > Resolution: **removed from this change** and moved to the TODO 5 restructure.
  > This vacated the Phase 3 secondary success criterion, which was replaced with
  > FR-017.

### Verification

- FR-015: [new] A developer can run a regression suite covering owner-isolation negative cases, all five event CRUD paths, vehicle archiving under FR-009, reminder selection, and report output and content type. Priority: must-have
  > Socrates: Counter-argument considered — "the safety net can only be built
  > after the thing it protects: the branch doesn't compile, so any test written
  > necessarily encodes post-migration behaviour, locking in whatever the
  > migration produced including its bugs." **Resolution drove a new FR.** The
  > user chose to reconstruct a real baseline first rather than accept the
  > limitation — see FR-016.

- FR-016: [new] A developer can produce reference output — report values, statistics figures, and reminder selection — from the last coherent pre-migration build, and compare post-migration output against it. Priority: must-have
  > Socrates: challenge inherited from FR-015, which this FR resolves. Verified
  > during this session: commit `2a20e8a` (2023-03-22, Spring Boot 2.7.3 /
  > JHipster 7.9.3) is the last commit with a coherent stack; the break was
  > introduced by a single later commit, `5b78f3f` (2026-06-22), which jumped to
  > Boot 3.1.5 / JHipster 8.0.0. Open risk: `2a20e8a` has not been built, so its
  > buildability is plausible but unproven — see Open Questions.

- FR-017: [new] A developer opening a merge request gets automated compile, test, and verify feedback, rather than verification running only on tags. Priority: nice-to-have
  > Socrates: challenge inherited from FR-014, which this FR replaced as the
  > secondary success criterion. Explicitly not sufficient on its own: a green
  > pipeline over a suite that doesn't exist yet proves nothing, so FR-017 has
  > value only once FR-015 lands.

## Constraints & Compatibility

**Backward compatibility.** The REST contract that client 1.2.5 consumes is
preserved: paths, payloads, and status codes. Archiving is deliberately designed
to be invisible to the client — an archived vehicle simply stops appearing in
the vehicle list, exactly as a deleted one would have. No new request parameter
is introduced, which is why the "user chooses per report" archive option was
rejected. There is no API version prefix and path naming is inconsistent
(`/api/vehicle`, `/api/fuel-type`); both are frozen as-is by this change.

**Data migration.** No database engine change in this slice — MariaDB and
Liquibase remain, since PostgreSQL and Flyway were cut in Phase 3. The only
schema change is the additive archive column introduced by FR-009, added as a
new timestamped Liquibase changelog referenced from `master.xml`; existing
changesets are never rewritten.

**Rollback plan — parallel run.** The new build is stood up against a restored
copy of production data and compared against the FR-016 golden baseline before
any cutover. This is the strongest available verification of the Phase 3
guardrails, and it shares most of its setup with FR-016 — the parallel
environment is the vehicle for the baseline comparison. The backup/restore
helpers already in `src/main/scripts/` are the starting point.

**Existing integrations that must keep working.**

- React client 1.2.5, consumed as a Maven artifact from a private GitLab
  registry. Build health depends on that registry being reachable and that
  artifact still existing — a dependency outside this repository.
- SMTP for account and reminder mail.
- The local filesystem image volume bind-mounted into the container; image bytes
  live on disk with only a generated filename in the database.
- NGINX reverse proxy and the Docker Compose runtime files.
- GitLab CI, which today runs only on tags.

**Explicitly preserved behaviour.** Owner isolation on every path; the account
lifecycle; all admin functions; vehicle and five-event CRUD contracts; reminder
selection semantics including their known imperfections; and statistics and
report output values.

## Non-Functional Requirements

- Every account that exists before this change continues to work after it, with
  at most one forced re-login and no password reset, re-registration, or account
  recreation.

**Considered and deliberately not adopted**, recorded so the absence is a
decision rather than a gap:

- *No performance NFR.* The user declined a "no perceptible slowdown" property.
  This is consistent with descoping FR-014: performance is a settled non-concern
  at a handful of users with small fleets, not an oversight. Note for planning
  that Hibernate 6 changes fetch and type-mapping behaviour, so a regression here
  would go unmeasured by choice.
- *No reminder-timing NFR.* Reminder delivery timing is preserved as behaviour
  (FR-012) but not asserted as a measurable outer-boundary property. The
  timezone question remains open.
- *No resource-envelope NFR.* Startup and heap characteristics change between
  Boot 2.7 and Boot 3.1, and this is not being asserted against the container's
  current limits.

## Business Logic Changes

**Existing rule, unchanged:** given a vehicle's dated, mileage-stamped event
history, the system derives what is due next and what it has cost.

That rule drives everything the product does beyond storage — consumption per
period and per refill, cost breakdowns by category and date range, and the
forward-looking expiry and next-service dates that feed both the upcoming-events
view and the daily reminder job. It is not touched by this change.

**This change is infrastructure-only, with one exception.** The platform
migration, JHipster removal, e-mail fix, and test work introduce no domain rule.
FR-009 does.

**New rule — archiving:** an archived vehicle is no longer active, but it did
still happen. It disappears from the owner's vehicle list, and its history
continues to count toward costs and statistics.

The user's decision was explicit: archived vehicles still count in cost reports
and statistics. A car sold last year still appears in last year's total cost of
ownership. This is what makes archiving worth building rather than simply
refusing deletion with a 409.

Mechanically this means the archive filter is **not** uniform — it applies to
the vehicle list but not to cost and statistic queries. Two different filters
over the same ownership-constrained JPQL that FR-003 and FR-005 are already
rewriting, which is why the user sequenced FR-009 to land only after the
migration is green and test-covered.

**Forward-looking features exclude archived vehicles.** Confirmed by the user
during the closing cross-check. Archiving means "no longer active", and the rule
splits on direction of time:

- *Backward-looking* — cost reports, statistics, historical totals: archived
  vehicles are **included**. A car sold last year still appears in last year's
  cost of ownership.
- *Forward-looking* — the `/api/events` upcoming-events view and the daily
  reminder job: archived vehicles are **excluded**. There is no value in being
  reminded that a sold car's insurance is expiring.

This gives FR-009 and FR-012 unambiguous acceptance criteria, and it is why the
archive filter cannot be applied uniformly across the ownership-constrained
queries.

## Access Control Changes

**No access-control model changes — current model preserved.** The Spring
Security 6 migration is mechanism-only and must be provably behaviour-identical
for any legitimate caller.

Current model, preserved as-is:

- Stateless JWT bearer authentication (`jjwt`); no server-side session.
- Two roles in `AuthoritiesConstants`: `ROLE_USER` and `ROLE_ADMIN`.
- Public paths, narrowly listed: `/api/register`, `/api/activate`,
  `/api/authenticate`, `/api/account/reset-password/*`, plus management
  `health`/`info`/`prometheus`.
- `/api/admin/**` and the rest of `/management/**` gated to `ROLE_ADMIN`.
- All other `/api/**` requires authentication.
- Method-level `@PreAuthorize` additionally guards user/authority
  administration, lookup-table mutation, reminder dispatch, and test-data
  endpoints.
- **Data isolation lives in the persistence boundary**, not in controllers:
  repository JPQL embeds `vehicle.owner.login = ?#{principal.username}`. This is
  the single most important preserved property in the change — it is what
  prevents cross-user access, and the Jakarta/Hibernate 6 migration touches
  every one of those queries.
- Vehicle creation assigns the current user as owner regardless of client input.

Mechanism changes required by the platform migration (no model delta):

- `WebSecurityConfigurerAdapter` + chained `antMatchers` → bean-based
  `SecurityFilterChain` + `requestMatchers`.
- Duplicated session-management/header configuration blocks are deduplicated.

In-scope tightening that does not change the model:

- Deduplicate the repeated session-management/header configuration blocks.
- Add missing `@Valid` on business resource request bodies. Today only
  account/user/auth paths carry Bean Validation at the entry point; entity
  annotations alone do not validate an unannotated controller DTO payload. This
  is a hardening fix, not a behaviour change for well-formed requests — but it
  does change the response for malformed ones.

Explicitly deferred to a separate security pass (see Open Questions):

- Rotating the production JWT signing secret committed in `application-prod.yml`.
- Dev-profile CORS permitting all origins while allowing credentials.
- The `frameOptions().deny()` followed by `frameOptions().disable()` sequence.
- User impersonation (TODO 10).
- Ceasing to leak database IDs to the browser (TODO 12).

> Socrates (smallest access change that still makes the work useful without
> disrupting existing users?): none at all. The value here is that the model
> survives a framework rewrite unchanged. Every access-control *improvement*
> was deliberately pushed out so that any behavioural difference observed after
> the migration is unambiguously a bug rather than an intended change.

## Product Framing

No product-framing changes. Recorded for the PRD frontmatter:

- **Product type:** `web-app` — no change. A Spring Boot WAR serving both the
  `/api` REST surface and the React client's static assets from `target/www/`.
- **Target scale:** `small` — no change. A handful of known users, small fleets,
  low query volume, small data volume.
- **Timeline:** `delivery_weeks: 5` (4–6 week range accepted in Phase 3),
  `hard_deadline: null`, `after_hours_only: true`.

## Non-Goals

### Functional non-goals

- **Database engine migration (TODO 3) and Liquibase→Flyway (TODO 6).** Cut in
  Phase 3. Migrating live production data is the only irreversible operation in
  the programme; doing it before behavioural test coverage exists inverts the
  recommended sequencing. Becomes its own change against a tested baseline.
- **Entity restructure and PostgreSQL JSON storage (TODO 5).** Extracting the
  common event shape and moving query-irrelevant detail into JSON is a domain
  redesign, not platform work, and it depends on TODO 3 having landed.
- **UUID primary keys and non-leaking IDs (TODO 12).** Breaks the client
  contract FR-008 preserves; needs an API versioning story first.
- **Maven → Gradle (TODO 11).** Changes nothing the product does while
  invalidating every build assumption in CI, Docker, and the client-artifact
  dependency.
- **User impersonation (TODO 10).** A new access-control capability; Phase 2
  locked the access model as unchanged.
- **Internationalization rethink (TODO 9).** FR-011 fixes English template
  rendering only. If the root cause turns out to be `MessageSource`
  configuration, that is a stop-and-reassess point, not licence to start TODO 9.
- **Functional-programming / Vavr sweep (TODO 4).** A style migration across the
  same files the Jakarta migration touches; combining them would make the diff
  impossible to review as behaviour-preserving.
- **Bean Validation on business request bodies.** Descoped from this change
  during the Socratic round (was FR-010) — hardening was deferred in Phase 2,
  and it conflicts with FR-008's compatibility promise.
- **Eliminating the `VehicleRichMapper` N+1.** Descoped during the Socratic
  round (was FR-014); the real fix is the TODO 5 restructure.
- **Removing test-data generation from the production REST surface.** Explicitly
  ruled out of this change, despite the source TODO, to keep the migration
  focused.

### Non-functional non-goals

- **Observability — tracing, alerting, job monitoring.** Actuator and audit stay
  as they are; production continues to disable Prometheus export. Accepted
  consequence: if the reminder job stops after the migration, nothing will say so.
- **Dependency and container vulnerability scanning.** Accepted consequence:
  FR-002 makes ~40 dependency versions hand-managed, and nothing will watch them.
- **Coverage thresholds enforced in CI.** FR-015's suite is built and FR-017 runs
  it on merge requests, but no floor blocks a merge. Avoids a threshold that
  would block work before the suite is complete.
- **Performance targets.** No latency, throughput, or resource-envelope property
  is asserted (see Non-Functional Requirements).

### Offered as non-goals and deliberately left open

These were put to the user as candidate non-goals and not ruled out. They remain
live Open Questions rather than hard exclusions:

- Reminder delivery robustness (idempotency, retry, distributed lock, timezone).
- API versioning and path-name normalization.
- Extracting services behind the lookup and reminder-advance controllers.

Also not ruled out, and not in the FR set: image-storage hardening — size
limits, content verification, and the file-deleted-before-commit inconsistency
window. Recorded as an Open Question.

## Open Questions

1. **Frame-options policy.** Deduplicating the header-configuration blocks
   forces a decision on `frameOptions()`, because the current
   `deny()`-then-`disable()` sequence has no coherent intent — the second call
   wins, so clickjacking protection is effectively off today. The security pass
   was deferred, but this item cannot be deferred while the deduplication is in
   scope. Owner: user. **Blocks:** the security-configuration rewrite (FR-004).
2. ~~**Is commit `2a20e8a` actually buildable?**~~ **RESOLVED** during this
   session — see `## Baseline verification result`. It compiles cleanly but its
   102 integration tests have been broken since 2023-03-22. FR-016 should
   capture its baseline by running the build against restored production data on
   MariaDB rather than through the H2 test suite. **Superseded by Q12.**
3. **Are the remaining `CODEBASE_ANALYSIS.md` findings accurate?** One
   High-rated finding — the refuel-edit defect — was verified as a false
   positive during this session. The build failure, the 184-usage `javax.*`
   count, and the non-cascading foreign keys are load-bearing for the whole plan
   and were not individually re-verified. Owner: planning step. Recommended
   before `/10x-plan`.
4. **Production JWT secret rotation.** A base64 signing secret is committed in
   `application-prod.yml`, contrary to its own comment. Deferred to a separate
   security pass by explicit decision. Owner: user. Not blocking.
5. **Dev CORS allow-all with credentials.** The dev profile permits all origins
   while allowing credentials. Deferred to the same security pass. Owner: user.
   Not blocking.
6. **Reminder delivery robustness.** Idempotency, retry, distributed lock, and
   an explicit timezone decision. FR-012 preserves the current behaviour
   knowingly. Offered as a non-goal and not ruled out. Owner: user. Not blocking.
7. **API versioning and path normalization.** No version prefix exists and path
   naming is inconsistent. Offered as a non-goal and not ruled out. Blocks
   TODO 12 whenever it is taken up. Owner: user.
8. **Extracting services behind the lookup and reminder-advance controllers.**
   Source carries "extract service" TODOs. Offered as a non-goal and not ruled
   out. Owner: user. Not blocking.
9. **Consolidating ownership enforcement.** FR-005 preserves SpEL principal
   filtering scattered across every query, enforced by convention. Moving it to
   a single auditable boundary was raised in the Socratic round and deferred to
   the TODO 5 restructure. Owner: user.
10. **Should test-data generation remain a production REST surface?** Ruled out
    of this change, but the source TODO stands. Owner: user.
11. **Image-storage hardening.** No size limit, no content verification, and the
    old file is deleted before the transaction commits. Not ruled out, not in
    scope. Owner: user.

## Quality cross-check

Run at the close of the shaping session. All six elements present; no gaps.
`quality_check_status: accepted`.

| Element | Result |
| --- | --- |
| Access Control | present — model preserved; mechanism delta, tightening, and deferrals enumerated |
| Business Logic | present — one-sentence existing rule, plus one new domain rule (archiving) |
| Project artifacts | present — valid frontmatter checkpoint |
| Timeline-cost acknowledged | present — `delivery_weeks: 5` exceeds the three-week default and carries an explicit acknowledgment |
| Non-Goals | present — 10 functional, 4 non-functional, 4 deliberately left open |
| Preserved behaviour (brownfield) | present — backward compatibility, data migration, rollback, integrations, and an explicit preserved list |

No gaps were carried into `/10x-prd` as warnings. Three Open Questions are
marked blocking; the archive-scope question raised at cross-check was resolved
during the session and removed from the list.

## Baseline verification result (commit `2a20e8a`)

Executed during this session, in a throwaway git worktree, on Temurin JDK 17.
Resolves what was Open Question 2. The working tree and `refactor` branch were
not touched.

**`./mvnw compile` succeeds.** 186 source files, `BUILD SUCCESS`, fully offline —
every dependency resolves from the local `~/.m2`, with no versionless
coordinates. The client artifact `1.2.4` is cached, so the private GitLab
registry is not a blocker. This confirms the POM at `2a20e8a` constructs cleanly,
unlike HEAD, which fails at exactly that step.

**`./mvnw verify` fails.** All 102 integration tests error with
`Failed to load ApplicationContext`. Two stacked defects, both pre-existing:

1. `src/test/resources/config/application.yml:29` sets
   `database-platform: tech.jhipster.domain.util.FixedH2Dialect`. **That class
   does not exist in `jhipster-framework` 7.9.3** — inspecting the cached jar
   shows only `FixedPostgreSQL10/82/95Dialect` under `tech/jhipster/domain/util/`.
   JHipster removed the H2 variant upstream, and the `2a20e8a` commit ("Update
   dependency versions") bumped to 7.9.3 without updating this reference.
2. Underneath it, an H2 schema-validation mismatch:
   `wrong column type encountered in column [details] in table [inspections];
   found [character (Types#CLOB)]`, under `hibernate.hbm2ddl.auto: validate`.
   This survived substituting a stock `org.hibernate.dialect.H2Dialect` and a
   hand-restored `FixedH2Dialect`, so it is not a one-line fix. Most likely an
   H2 2.x versus Liquibase type-rendering mismatch.

**Conclusion: `2a20e8a` is coherent at compile level only. Its integration-test
suite has been broken since it was committed on 2023-03-22.**

### Consequences

- **Open Question 2 is resolved** — and the answer costs more than assumed.
- **FR-016 is not dead, but its route changes.** The golden baseline cannot be
  captured by running the existing IT suite at `2a20e8a`. Two options:
  - *(a)* Repair the H2 test-schema mismatch at the baseline first. Bounded but
    unknown effort, and it means debugging a 2023 test configuration before the
    real work starts.
  - *(b)* **Preferred:** capture the baseline by running the `2a20e8a` build
    against a restored copy of production data on MariaDB, bypassing H2
    entirely. This is already the Phase 5 rollback plan (parallel run), so it
    reuses infrastructure the change needs anyway, and it exercises the real
    dialect rather than a test surrogate.
- **FR-013 inherits this**, since it compares against FR-016's baseline.
- **`CODEBASE_ANALYSIS.md`'s "Test confidence: Low" is understated.** It reports
  21 test files and describes results as "stale/unverified". In fact the 102
  integration tests have not executed successfully since March 2023. The
  practical starting coverage for this change is zero at integration level, not
  low — which raises the value of FR-015 and lowers the value of any assumption
  that existing tests protect existing behaviour.
- **A further question is now open:** whether any earlier commit has a green IT
  suite, which would give FR-016 a cleaner baseline. Only `jhipster-framework`
  7.9.3 and 8.0.0 are cached locally, so checking earlier commits requires
  network downloads and was not attempted.

### Follow-up: earlier commits checked (resolves Q12)

Every reachable candidate was tested on Temurin JDK 17 in throwaway worktrees.

| Commit | Date | Boot / JHipster | Result |
| --- | --- | --- | --- |
| `16eb931` HEAD | 2026 | 3.1.5 / 8.0.0 | POM does not resolve — 11 versionless deps |
| `5b78f3f` | 2026-06-22 | 3.1.5 / 8.0.0 | same — this is the commit that broke the build |
| `2a20e8a` | 2023-03-22 | 2.7.3 / 7.9.3 | compiles; **0/102** ITs — no Spring context |
| `bfaf1a0` / `8605045` / `63d72ef` | 2022-08 | 2.7.2 / 7.9.x | same dialect defect as `2a20e8a` |
| **`6e19b96`** | **2022-05-20** | **2.7.0 / 7.8.1** | **compiles; 22/22 unit; 94/102 ITs pass** |
| **`3e91ed4`** | **2022-05-17** | **2.6.6 / 7.8.1** | **identical: 94/102, same 8 failures** |
| `1818100` and older | ≤2022-05-15 | 2.6.3 / 7.6.0 | cannot build — needs client artifact `1.1.0`, absent from `~/.m2` and Maven Central |

**The IT suite broke at `63d72ef` (2022-08-01, "Update JHipster to 7.9.0").**
`FixedH2Dialect` was removed upstream in `jhipster-framework` 7.9.0 — verified by
inspecting the published jars: present in 7.8.1 and earlier, absent from 7.9.0,
7.9.1, 7.9.2, 7.9.3, and 8.0.0. The test config was never updated. `2a20e8a`
merely inherited the defect; the suite has been dead for four years, not one.

**No commit in reachable history has a fully green `./mvnw verify`.** The best
available is 94/102.

### The 8 pre-existing IT failures are not product bugs

Three of the eight (`UserJwtControllerIT`) were traced to `LoginVm`, which is
`@Value(staticConstructor = "of")` — a private all-args constructor, no
`@JsonCreator`, no no-args constructor. The tests use
`MockMvcBuilders.standaloneSetup(...)`, which does not use the application's
configured `ObjectMapper`.

This was resolved empirically rather than by reasoning. A probe test autowiring
the real Spring Boot `ObjectMapper` was run against `2a20e8a`-era code:

```
>>> PROBE app-ObjectMapper:   OK -> u/true
>>> PROBE plain-ObjectMapper: FAILED -> InvalidDefinitionException
```

**`/api/authenticate` works in production.** Spring Boot registers
`jackson-module-parameter-names`, and the project compiles with `-parameters`,
so Jackson resolves the private constructor as an implicit creator. The failures
are artifacts of an unrepresentative test harness. A `UserResourceIT` failure is
likewise a `application/json` vs `application/json;charset=UTF-8` mismatch.

Consequence: these tests assert against a Jackson configuration the application
never uses. FR-015 should replace `standaloneSetup` with a full-context MockMvc
so the suite exercises real serialization behaviour.

### Business logic has not changed since 2022-05-17

```
git diff --stat 3e91ed4 16eb931 -- src/main/java                        → empty
git diff --stat 3e91ed4 16eb931 -- src/main/resources/config/liquibase  → empty
```

**Zero source changes and zero schema changes between `3e91ed4` and HEAD.**
Every commit in that span touched only the POM, Docker files, or documentation.

This materially improves FR-016. `6e19b96` is not merely "an old commit that
runs" — its Java sources and database schema are **byte-identical to HEAD**. Any
behavioural difference observed after the migration is therefore attributable to
the platform change alone, which is exactly the property a golden baseline needs.

### Revised recommendation for FR-016

Capture the golden baseline from **`6e19b96`** (newest commit that runs; sources
identical to HEAD). Two complementary routes, both now viable:

- *Test-suite route:* 94/102 integration tests pass there, giving an immediate
  executable reference. The 8 known failures are documented above and should be
  treated as harness defects to fix in FR-015, not as behaviour to reproduce.
- *Data route:* run that build against a restored copy of production data on
  MariaDB to capture report, statistics, and reminder-selection output — the
  Phase 5 parallel-run plan, unchanged.

Note the toolchain constraint discovered while testing: **no JDK 17 is installed
on this machine** (SDKMAN has 21 and 25; Zulu 26 is on disk), and the enforcer
plugin hard-fails anything above 17. A JDK 17 is a prerequisite for any baseline
work.

## Forward: tech-stack

Informational only — NOT part of the PRD schema. Captured for the downstream
stack-assessment step.

- **PostgreSQL is the intended destination** (TODO 3), deferred from this change.
  MariaDB 10.11.2 is current.
- **Flyway is the intended migration tool** (TODO 6), replacing Liquibase 4.20
  XML. Until then, new schema changes go in new timestamped Liquibase changelogs
  referenced from `master.xml`; deployed changesets are never rewritten.
- **Gradle (Groovy DSL) is the intended build tool** (TODO 11), replacing Maven.
- **Vavr is already a dependency** (0.10.3) and the user wants functional style
  used more consistently (TODO 4). Current usage of `Option`/`Either`/`Try` is
  inconsistent.
- Java 17 is current; no language-version change was requested.
- The deployment shape (Docker, NGINX reverse proxy, WAR) was explicitly NOT
  declared must-preserve in Phase 1 and is available to change.

## Forward: technical-roadmap

Informational only — NOT part of the PRD schema. The user's twelve-item TODO
list implies a programme; this change is its first slice. A plausible ordering,
derived from the dependencies surfaced during this session:

1. **This change** — green build, Jakarta/Spring Security 6, de-JHipster,
   dependency versions, EN e-mails, archiving, regression suite, golden baseline.
2. **Security pass** — production JWT secret rotation, dev CORS, frame-options
   policy, Bean Validation on business payloads (descoped FR-010).
3. **Database migration** — MariaDB→PostgreSQL (TODO 3) and Liquibase→Flyway
   (TODO 6), verified against the FR-016 golden-baseline harness this change
   builds.
4. **Domain restructure** — extract the common event shape, move
   query-irrelevant detail to JSON (TODO 5), fix the `VehicleRichMapper` N+1
   (descoped FR-014), consolidate ownership enforcement into a single auditable
   boundary, extract lookup services.
5. **API evolution** — versioning story, then UUID primary keys and non-leaking
   IDs (TODO 12), coordinated with a client release.
6. **Remaining items** — Vavr/FP sweep (TODO 4), i18n rethink (TODO 9),
   impersonation (TODO 10), Maven→Gradle (TODO 11), reminder robustness,
   observability, image-storage hardening.
12. ~~**Does any earlier commit have a green integration-test suite?**~~
    **RESOLVED** — no commit in reachable history is fully green. Best available
    is `6e19b96` at 94/102, with sources byte-identical to HEAD. See
    `### Follow-up: earlier commits checked`.
13. ~~**JDK 17 must be installed before any baseline work.**~~ **RESOLVED**
    2026-08-24 — Temurin `17.0.20-tem` installed via SDKMAN and verified to drive
    the build past the enforcer. The SDKMAN default remains a newer JDK, so
    `JAVA_HOME` must be exported per build; documented in `AGENTS.md`.
14. **Should FR-015 replace `standaloneSetup` with full-context MockMvc?** The
    existing ITs assert against a Jackson configuration the application never
    uses, which produced three false failures during this session. Owner:
    planning step. Recommended.

### Follow-up: codebase-analysis claims verified (resolves Q4)

Checked against source at head on 2026-08-24, with a JDK 17 available.

| Claim in `CODEBASE_ANALYSIS.md` | Verdict |
| --- | --- |
| 11 dependencies receive no version from the BOM | **Confirmed exactly** — `commons-io`, `jjwt-api`/`impl`/`jackson`, `jakarta.cache:cache-api`, `hibernate-core`/`envers`/`jcache`/`jpamodelgen`, `springdoc-openapi-webmvc-core`, `problem-spring-web` |
| 184 legacy `javax.*` / old-security usages | **Corrected to 193** — 172 `javax.*` imports (113 persistence, 37 validation, 10 servlet, 5 mail, 4 transaction, 2 annotation, 1 sql) plus 21 legacy security usages |
| Vehicle FKs have no cascade specified | **Confirmed** — no `onDelete` attribute appears anywhere in the Liquibase changelogs |
| `RefuelServiceImpl.editRefuel` is type-inconsistent | **False** (found during Phase 4) |
| Test confidence "Low" | **Understated** — integration coverage is zero and has been since 2022-08-01 |

**Migration trap surfaced by the count.** `javax.sql` is JDK-owned
(`javax.sql.DataSource`), not Jakarta EE, and must **not** be rewritten. A blanket
`javax.*` → `jakarta.*` replacement breaks it. `javax.mail` is also a separate
decision from the Jakarta EE core packages.

**Standing judgement.** The analysis has now been wrong on two of five checked
claims. Treat its unquantified assertions as leads to verify, not as facts.
