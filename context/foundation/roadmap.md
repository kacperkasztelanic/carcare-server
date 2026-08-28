---
project: "CarCare Server"
version: 1
status: draft
created: 2026-08-24
updated: 2026-08-28
prd_version: 1
main_goal: speed
top_blocker: none
---

# Roadmap: CarCare Server — Platform Foundation Change

> Derived from `context/foundation/prd.md` (v1) + an auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Items below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

CarCare is a vehicle-fleet backend: owners record five kinds of lifecycle event against
their vehicles, and the server derives costs and statistics, generates XLSX reports, and
mails reminders for upcoming obligations. The `refactor` branch declares Spring Boot 3.1.5
but its sources are still Jakarta EE 8, and the build fails during Maven model construction
before a compiler ever runs — so nothing ships, and no test result means anything.

This change is the foundation slice of a twelve-item modernization programme: restore a
green build, complete the Jakarta and Spring Security 6 migration, remove JHipster, fix
English e-mail, add vehicle archiving, and build the regression coverage that none of the
rest of the programme can safely proceed without.

## North star

**S-01: A user's whole session is indistinguishable through client 1.2.5** — an existing
owner logs in, lists and opens vehicles, and creates, reads, updates, and deletes all five
event types against the same paths, payloads, and status codes as before.

> "North star" here means the smallest end-to-end slice whose successful delivery would
> prove the change actually worked — placed as early as its Prerequisites allow, because
> everything else only matters if this holds. The PRD names client 1.2.5 breakage as the
> pager event, which makes proving the opposite the thing worth proving first.

## At a glance

| ID | Change ID | Outcome (user can …) | Prerequisites | PRD refs | Status |
| --- | --- | --- | --- | --- | --- |
| F-01 | `resolvable-build` | (foundation) Maven resolves every dependency; the compiler runs | — | FR-001, FR-002 | done |
| F-02 | `golden-baseline-capture` | (foundation) reference output exists from the last runnable commit | — | FR-016 | done |
| F-03 | `jakarta-platform-migration` | (foundation) `src/main` compiles on Jakarta EE 9+ and Spring Security 6, JHipster-free | F-01 | FR-001, FR-002, FR-003, FR-004 | done |
| F-04 | `test-context-restored` | (foundation) `./mvnw verify` boots a Spring context and runs the suite | F-03 | FR-001, FR-002, FR-003, FR-015 | done |
| S-01 | `session-parity` | log in and run a full vehicle + event session, unchanged, seeing only their own data | F-04 | US-01, FR-004, FR-005, FR-006, FR-008, FR-015 | done |
| S-07 | `client-server-contract-trial` | trial the real client against the server and fix confirmed compatibility failures | S-01 | FR-004, FR-005, FR-006, FR-008, FR-015 | done |
| S-02 | `admin-surface-parity` | administer users, authorities, audits, lookups, test data, and reminder dispatch with four explicit API corrections | F-04 | FR-002, FR-007, FR-015 | proposed |
| S-03 | `report-parity` | request statistics and both XLSX reports and get baseline-matching values | F-02, F-04 | FR-013, FR-015, FR-016 | done |
| S-04 | `english-reminder-fix` | receive a correctly rendered English reminder | F-02, F-04 | US-03, FR-011, FR-012, FR-015, FR-016 | done |
| S-05 | `vehicle-archiving` | archive a vehicle with history and keep its costs in reporting | S-01, S-03, S-04 | US-02, FR-009, FR-012, FR-015 | proposed |
| S-06 | `merge-request-ci` | (developer) get compile, test, and verify feedback on a merge request | S-01 | FR-015, FR-017 | proposed |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still
lives in the dependency graph below; this table is the proposed reading order across
parallel tracks.

| Stream | Theme | Chain | Note |
| --- | --- | --- | --- |
| A | Platform restoration | `F-01` → `F-03` → `F-04` | The critical path. Every slice waits on it. `F-01` and `F-03` are delivered, so `src/main` now compiles and packages on the declared platform; `F-04` is the next item and the last foundation on this chain. |
| B | Reference capture | `F-02` | Runs entirely on commit `6e19b96`, so it is unaffected by the broken build and parallel with all of Stream A. Feeds `S-03` and `S-04`. |
| C | Parity proof | `S-01` → `S-07` (next); `S-02` / `S-03` / `S-04` follow | Joins Stream A at `F-04`; `S-03` and `S-04` also consume Stream B. S-07 turns the real-client findings from S-01 into focused compatibility work before the remaining parity slices proceed. |
| D | New behaviour and feedback | `S-05` / `S-06`, parallel | Joins Stream C. The only two items that change what anyone sees; both deliberately last. |

## Baseline

What is already in place in the codebase as of 2026-08-24 (auto-researched, user-confirmed).
Foundations below assume these are present and do **not** re-scaffold them.

- **Frontend:** present, but external — no node/npm build in this repository. The React
  client is consumed as the Maven artifact `com.kasztelanic.carcare:client` 1.2.5
  (`pom.xml:13`, `pom.xml:118`) from a private GitLab registry and served from the WAR.
  Changing the UI means working in `../client`, which is outside this roadmap.
- **Backend / API:** present — 19 REST controllers, 16 service implementations, 16 base
  paths under `/api` plus `/management/audits`. Layering `web/rest` → `service` →
  `repository` → `domain` is mechanically enforced by an ArchUnit test. Does not compile.
- **Data:** present — 16 JPA entities, 14 Spring Data repositories, Liquibase (3 changelogs,
  2 includes in `master.xml`), MariaDB in dev and prod with H2 in tests, Ehcache L2. No
  `archived` or `deleted` flag exists anywhere in the domain or in any changelog.
- **Auth:** present, but pre-Spring-Security-6 — JWT stack in `security/jwt/`
  (`TokenProvider`, `JwtFilter`, `JwtConfigurer`). `SecurityConfiguration.java:30` still
  extends `WebSecurityConfigurerAdapter`; lines 45–96 are `antMatchers`. Owner isolation is
  enforced by `principal.username` in 13 queries across 6 repositories.
- **Deploy / infra:** present — `src/main/docker/` (Dockerfile, `app.yml`, `mariadb.yml`,
  `reverseproxy`, `deploy.sh`) and GitLab CI at `.gitlab/gitlab-ci.yml`. All four CI jobs
  are `only: tags` / `except: branches`, and the test job runs `./mvnw test`, which is
  Surefire only and excludes `*IT*`.
- **Observability:** partial — Actuator with `micrometer-registry-prometheus`,
  `logback-spring.xml`, a `LoggingAspect`, and a `PersistentAuditEvent` audit trail. No
  tracing, error tracking, alerting, or job monitoring. Deepening this is a stated non-goal.

**Verification baseline, stated plainly because it shapes several slices below:** all 21
test files in the tree are JHipster-generated scaffolding — account, user, audit, mail, JWT,
ArchUnit, timezone, web-configurer. Not one covers `Vehicle`, any of the five event types,
reports, statistics, or reminders. Business-behaviour coverage is not merely broken; it was
never written.

## Foundations

### F-01: Resolvable dependency graph

- **Outcome:** (foundation) Maven model construction succeeds — the 11 versionless
  dependencies carry explicit managed versions, the `jhipster-dependencies` BOM is gone, and
  `./mvnw compile` reaches the compiler and emits a real error list.
- **Change ID:** `resolvable-build`
- **PRD refs:** FR-001, FR-002
- **Unlocks:** `F-03` — the PRD is explicit that dependency resolution must be settled before
  a single import is touched, because an unresolvable graph means the compiler cannot give
  trustworthy feedback and the real migration surface stays hidden. Also reduces the standing
  unknown "how large is the migration surface actually?" from an estimate to a compiler output.
- **Prerequisites:** — (Temurin 17.0.20 is installed and verified against the enforcer)
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Dropping the BOM makes roughly 40 dependency versions hand-managed in one move,
  and the PRD accepts that nothing will watch them afterwards. Sequenced first because it is
  the cheapest step that converts guesswork into compiler output. Build health also depends
  on the private GitLab registry continuing to serve `client:1.2.5` — a dependency outside
  this repository, currently working.
  **Measured during implementation: the risk was overstated.** Swapping in
  `spring-boot-dependencies` 3.1.5 left only **three** additional dependencies unversioned
  (`logstash-logback-encoder`, `spring-cloud-starter-bootstrap`, `jhipster-framework`), not
  forty — Boot's nested BOMs cover the rest. Total hand-managed surface is 14.
- **Status:** done
- **Delivered:** commits `7cd2e41` (p1), `fae090b` (p2), `b8b191e` (p3), `d7985d7` (epilogue),
  `e626a7f` (review triage fixes). Scope was widened at the owner's direction beyond the
  pom-only framing above: `org.zalando:problem-spring-web` was removed and error handling
  rewritten onto Spring 6 `ProblemDetail`, and jjwt moved to 0.12.3 with the corresponding
  `TokenProvider` rewrite. `./mvnw validate` now exits 0 on the default, `prod`, and `IDE`
  profiles, and `./mvnw compile` reaches javac and reports **398 unique diagnostics** — the
  measured migration surface F-03 starts from. Handoff:
  `context/archive/resolvable-build/migration-surface.md`; wire contract:
  `error-contract.md`; review: `reviews/impl-review.md`.

### F-02: Golden reference output captured

- **Outcome:** (foundation) reference report values, statistics figures, and reminder
  selections exist, produced from commit `6e19b96` — the newest commit that builds and runs —
  and are comparable against post-migration output.
- **Change ID:** `golden-baseline-capture`
- **PRD refs:** FR-016
- **Unlocks:** `S-03` and `S-04`, neither of which can assert correctness without a reference;
  and the value-level comparison FR-013 depends on. It is also the verification path behind
  the rollback plan, since the parallel-run environment and the baseline share their setup.
- **Prerequisites:** —
- **Parallel with:** F-01, F-03, F-04
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Runs entirely on an old commit, so it is immune to the state of HEAD. (It was the
  only workable item while the build was broken; since F-03 that is no longer the constraint,
  but running on `6e19b96` remains the point — HEAD compiles and packages, yet still cannot
  boot a context, so it cannot produce a reference.) Its value rests on the verified
  fact that `src/main/java` and the Liquibase changelogs are byte-identical between
  `3e91ed4` and HEAD, so any post-migration difference is attributable to the migration
  alone. Client artifacts below 1.2.3 are no longer retrievable, which bounds how far back
  a baseline could ever be taken; `6e19b96` is inside that bound.
- **Status:** done

### F-03: `src/main` compiles on the declared platform

- **Outcome:** (foundation) `./mvnw compile` runs green — the `javax.*` imports in `src/main`
  are on Jakarta EE 9+ namespaces (150 as of F-01's close, of which 148 are convertible and 2
  are JDK-owned; was 152 before F-01's `ExceptionTranslator` rewrite), security is configured
  through a bean-based
  `SecurityFilterChain` with `requestMatchers`, and no `tech.jhipster.*` class remains in
  main sources. No compatibility shims.
- **Change ID:** `jakarta-platform-migration`
- **PRD refs:** FR-001, FR-002, FR-003, FR-004
- **Unlocks:** `F-04`, and through it every slice — nothing can be written, run, or verified
  until the tree compiles. Also closes both failing quality criteria and the partial recorded
  in `stack-assessment.md`, all three of which trace to the JHipster layer.
- **Prerequisites:** F-01 — **satisfied** (F-01 delivered 2026-08-25)
- **Parallel with:** F-02
- **Blockers:** — (Open Roadmap Question 1 resolved 2026-08-25)
- **Unknowns:** — (the frame-options policy was the only one; see "Decided inputs" below)
- **Decided inputs:**
  - **Frame options: `DENY`.** Owner decision, 2026-08-25 (Open Roadmap Question 1). Keep the
    `.deny()` intent at `SecurityConfiguration.java:71-72` and drop the vestigial
    `.headers().frameOptions().disable()` at `:77-79`. The H2 console that normally justifies
    disabling is not enabled anywhere in this project. Fallback is `SAMEORIGIN` only if the
    client turns out to iframe its own views.
  - While deduplicating that block, also remove the `sessionManagement()
    .sessionCreationPolicy(STATELESS)` declared twice (`:74-75` and `:81-82`) and the stale
    `antMatchers("/h2-console/**")` ignore rule at `:49`.
- **Scope discovered during F-01** — measured against the now-working compiler, so these are
  observations rather than estimates. Full attribution in
  `context/archive/resolvable-build/migration-surface.md`:
  - The residual diagnostic set is **398 unique errors**, of which 393 are the unconverted
    Jakarta namespace and 2 are `WebSecurityConfigurerAdapter`.
  - **A fourth error category the original framing missed**: `MailService.java:16,30,39`
    imports `org.thymeleaf.spring5.SpringTemplateEngine`, which does not exist for Spring 6.
    A one-line import change to `org.thymeleaf.spring6` —
    `spring-boot-starter-thymeleaf` already pulls the correct jar transitively.
  - **`jackson-datatype-hibernate5` is a live runtime incompatibility**, not just a tidy-up.
    `pom.xml:186-188` and `JacksonConfiguration.hibernate5Module()` target Hibernate 5 /
    `javax.persistence` while the pom now resolves `org.hibernate.orm:hibernate-core` 6.2.13.
    It compiles and then fails at first context load, so it must land inside F-03 rather than
    after it.
  - `antMatchers(...)` at `SecurityConfiguration.java:45-51,86-96` is confirmed removed in
    Spring Security 6 (verified by `javap` on `spring-security-config-6.1.5`) but produces
    **no diagnostic today** — javac's error recovery suppresses member-level checking once
    the `WebSecurityConfigurerAdapter` supertype fails to resolve. Treat it as a known rename
    independent of what the compile output shows.
  - Two JDK-owned imports must **never** be converted: `javax.sql.DataSource`
    (`LiquibaseConfiguration.java:18`) and `javax.crypto.SecretKey`
    (`TokenProvider.java:24`, introduced by F-01's jjwt migration).
- **Ownership boundary (resolves the prior F-03/S-02 contradiction):** F-03 implements every
  `tech.jhipster.web.util` replacement — `HeaderUtil`, `PaginationUtil`, `ResponseUtil` — and
  renames alert/error headers on the ten non-`UserResource` endpoints from `X-carcareApp-*` to
  a single `X-carcare-*` namespace, unifying them with the names `UserResource` and
  `ExceptionTranslator` already used. S-02 no longer implements this removal; it verifies
  admin header, pagination, and response parity against the **renamed** `X-carcare-*`
  contract, and owns confirming the client at `../client` does not key on the old
  `carcareApp` prefix. F-04 owns the H2 dialect, the five CLOB/`TEXT` schema-validation pairs,
  remaining test-source `javax.*` imports, full-context MockMvc conversion, Liquibase-before-
  Hibernate-validation startup ordering, and runtime security assertions — none of which
  F-03's green main compile proves. `hibernate.hbm2ddl.auto: validate` must not be weakened to
  unblock any of this.
- **Risk:** This is the single largest item on the roadmap and it cannot be split further —
  a partially converted namespace does not compile, and `WebSecurityConfigurerAdapter` no
  longer exists in Spring Security 6, so FR-003 and FR-004 must land together. Removing
  `tech.jhipster.*` is folded in rather than sequenced separately because 12 of its 28 files
  are in `config/`, the same files both other changes edit; splitting them means touching
  `SecurityConfiguration`, `WebConfigurer`, and `CacheConfiguration` twice. Named traps: the
  two JDK-owned imports listed under "Scope discovered during F-01" must not be rewritten, and
  Spring Security 6 authorization defaults genuinely differ, so FR-004 asks for deliberate and
  documented outcomes rather than identical ones.
- **Delivered:** 2026-08-25, `context/archive/jakarta-platform-migration/`. `src/main` compiles
  under both the default and `IDE` profiles and packages a prod WAR; no `javax.*` EE 8 or
  `tech.jhipster.*` references remain in main sources. The implementation review
  (`reviews/impl-review.md`) found ten issues, all fixed — two of them critical: the Spring
  Security 6 chain had no terminal `anyRequest()` rule and denied every root SPA asset, and the
  alert-header rename had also silently renamed the client's i18n message keys. Two deliberate
  divergences are recorded there: `/test/**` was dropped from the security bypass despite being
  listed as preserved, and gate 2.5 (`! rg carcareApp`) no longer holds literally because
  `carcareApp` legitimately survives as an i18n key namespace. Runtime behaviour is still
  unproven — that is F-04's gate.
- **Status:** done

### F-04: Test context loads and the suite executes

- **Outcome:** (foundation) `./mvnw verify` boots a Spring context against H2 and the
  integration suite runs on its merits instead of erroring at startup — the 20 remaining
  `javax.*` imports in `src/test` are converted, `tech.jhipster.domain.util.FixedH2Dialect`
  is replaced, and the schema-validation mismatch beneath it is resolved.
- **Change ID:** `test-context-restored`
- **PRD refs:** FR-001, FR-002, FR-003, FR-015
- **Unlocks:** `S-01`, `S-02`, `S-03`, `S-04` — none of them can be verified, and therefore
  none can be honestly called done, until a Spring context loads under test. It is also the
  verification path the entire FR-015 regression suite is written against.
- **Prerequisites:** F-03
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - How is the `inspections.details` CLOB-versus-CHARACTER schema-validation mismatch
    resolved — a Liquibase column-type change, a Hibernate 6 type-mapping adjustment, or
    relaxed schema validation in tests? Owner: planning step. Block: no.
  - Should the existing `standaloneSetup` harness be replaced with full-context MockMvc?
    Owner: planning step. Block: no. Recommended: it asserts against a serialization
    configuration the application never uses and produced three false failures during
    baseline verification.
- **Risk:** Split from F-03 because its risk is independent and worse-understood.
  Substituting a stock `H2Dialect` is known to be insufficient — the schema mismatch surfaces
  underneath it, and a hand-restored `FixedH2Dialect` also failed during baseline
  verification. This is the one foundation where the fix is not yet known, which is why it
  is isolated rather than buried inside a larger item.
- **Carried in from F-03's implementation review** (`context/archive/jakarta-platform-migration/reviews/impl-review.md`):
  - Assert `GET /` returns 200. F-03's security chain gained `.anyRequest().permitAll()` to undo a
    Spring Security 6 fall-through inversion that denied every root SPA asset; the fix is reasoned
    from bytecode but has never served a request.
  - Two test-side landmines were already defused, so they should not resurface: `UserResourceIT:96`
    no longer reads the deleted `jhipster.clientApp.name`, and the JWT validity fields carry their
    pre-migration defaults again (remember-me tokens would otherwise have expired instantly under
    the test profile, which omits that key).
  - The remaining `javax.*` imports are confined to six files: `MailServiceIT`,
    `ExceptionTranslatorTestController`, `TestUtil`, `UserResourceIT`,
    `CustomAuditEventRepositoryIT`, `DateTimeWrapper`. `MailServiceIT` additionally still imports
    `org.thymeleaf.spring5`.
- **Status:** done

## Slices

### S-01: A user's whole session is indistinguishable through client 1.2.5

- **Outcome:** an existing owner can log in through the unmodified React client 1.2.5, list
  and open their vehicles, and create, read, update, and delete all five event types —
  receiving the same paths, payloads, and status codes as before — while reaching no other
  user's data on any path.
- **Change ID:** `session-parity`
- **PRD refs:** US-01, FR-004, FR-005, FR-006, FR-008, FR-015
- **Prerequisites:** F-04
- **Parallel with:** S-02, S-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Consolidating ownership enforcement into a single auditable boundary instead of 13
    scattered queries. Owner: user. Block: no — preserved as-is here and deferred to the
    domain restructure, recorded so it is not lost.
- **Risk:** Carries the guardrail the PRD rates highest severity: owner isolation fails
  silently, it touches every query the migration edits, and there are no negative-case tests
  for it today. The regression tests written here are the first business-behaviour tests the
  project has ever had, so they encode post-migration behaviour — F-02's reference output is
  what keeps that from silently locking in a bug. Sequenced first among slices because it is
  the shortest route to a shippable, verifiable claim; a coordinated client release remains a
  deliberate escape hatch if preserving one specific endpoint proves disproportionate.
- **Status:** done

### S-07: Trial and fix confirmed client-server compatibility failures

- **Outcome:** the frozen React client can be exercised against a clean MariaDB-backed server in a
  real browser; every observed client/server mismatch is either fixed with coverage or recorded as
  a deliberate compatibility decision.
- **Change ID:** `client-server-contract-trial`
- **PRD refs:** FR-004, FR-005, FR-006, FR-008, FR-015
- **Prerequisites:** S-01
- **Parallel with:** —
- **Blockers:** —
- **Risk:** The S-01 manual smoke exposed a normal pre-login `/api/account` 401 followed by a
  client-side `applicationProfile(...includes)` console error. Treat observations as evidence, not
  automatic server defects: reproduce against the real client, establish ownership, and avoid
  widening frozen-client compatibility work without a confirmed contract break.
- **Status:** done

### S-02: The administrator's surface is unchanged

- **Outcome:** an administrator can manage users and authorities, read audit history,
  maintain lookup tables, generate test data, and trigger reminder dispatch with the
  existing business, security, and response behavior. This slice explicitly restores four
  confirmed API contracts: canonical creation `Location` headers for the three lookup/config
  resources and a usable reminder-advance DELETE binding.
- **Change ID:** `admin-surface-parity`
- **PRD refs:** FR-002, FR-007, FR-015
- **Prerequisites:** F-04
- **Parallel with:** S-01, S-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Extracting services behind the lookup and reminder-advance controllers, which carry
    standing TODOs in source. Owner: user. Block: no — offered as a non-goal, not ruled out.
  - Should test-data generation remain a production surface at all? Owner: user. Block: no —
    ruled out of this change, but the source TODO remains.
- **Risk:** F-03 already completes the JHipster removal, replacing `HeaderUtil`,
  `PaginationUtil`, and `ResponseUtil`. Session-parity restored the business resources' baseline
  `X-carcareApp-*` alert names, while `UserResource` remains on its baseline `X-carcare-*`
  contract. This slice verifies the latter's admin header, pagination, and response parity; it
  must not assume one alert-header namespace for both surfaces. The main hazard is scope creep:
  the resources here bypass the service layer and advertise their own TODOs. The point of this
  change is to prove nothing moved except the four explicitly authorized API corrections above;
  resist unrelated fixes.
- **Status:** proposed

### S-03: A user's reports and statistics match the pre-migration baseline

- **Outcome:** an owner can request consumption, mileage, and cost statistics and both XLSX
  reports, and receive output matching the F-02 reference at value level — cell values,
  computed figures, and content type.
- **Change ID:** `report-parity`
- **PRD refs:** FR-013, FR-015, FR-016
- **Prerequisites:** F-02, F-04
- **Parallel with:** S-01, S-02, S-04
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Comparison must be at value level, not byte level: XLSX writers emit
  version-dependent bytes and costs widen from integer cents to floating point on output.
  Hibernate 6 changes type mapping and fetch behaviour, which is exactly where a silent
  statistics regression would hide — and the PRD deliberately asserts no performance or
  fetch-behaviour property, so such a regression would go unmeasured by choice.
  Session-parity now reports zero mileage as `0.0` rather than failing: this conflates unknown
  consumption with a real zero and must be judged against F-02's golden baseline at value level.
- **Status:** done

### S-04: An English-language user receives a correct reminder

- **Outcome:** an owner whose account language is English receives a correctly rendered
  reminder naming the vehicle and the expiry, with Polish unaffected and reminder selection
  semantics unchanged.
- **Change ID:** `english-reminder-fix`
- **PRD refs:** US-03, FR-011, FR-012, FR-015, FR-016
- **Prerequisites:** F-02, F-04
- **Parallel with:** S-01, S-02, S-03
- **Blockers:** —
- **Unknowns:**
  - Is the English rendering fault template syntax or message-source configuration? Owner:
    planning step. Block: no — but if it proves to be message-source configuration, the fix
    reaches into the deferred internationalization rework, and the PRD names that a
    stop-and-reassess point rather than licence to start it.
  - Reminder delivery robustness — idempotency, retry, a distributed lock, an explicit
    timezone decision. Owner: user. Block: no — current behaviour is preserved knowingly,
    including the fact that a restart across 08:00 silently skips a day.
- **Risk:** The only slice on the roadmap whose cause is undiagnosed, which is why it carries
  an explicit stop condition rather than an estimate. Reminder selection is compared against
  F-02's reference so that "only rendering changed" is a verified claim rather than an
  intention.
- **Status:** done

### S-05: An owner archives a vehicle that has history

- **Outcome:** an owner can archive a vehicle carrying refuel, repair, routine-service,
  inspection, and insurance records — it disappears from their vehicle list, from upcoming
  events, and from reminders, while its costs and statistics continue to count toward
  historical reporting.
- **Change ID:** `vehicle-archiving`
- **PRD refs:** US-02, FR-009, FR-012, FR-015
- **Prerequisites:** S-01, S-03, S-04
- **Parallel with:** S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** The only schema change in the whole roadmap (a new timestamped Liquibase
  changelog adding the archive column, referenced from `master.xml`; existing changesets are
  never rewritten) and the only new domain rule. The rule splits on direction of time, so the
  filter is deliberately non-uniform — applied to the vehicle list and forward-looking
  queries, withheld from cost and statistics queries. Applying it uniformly would silently
  erase sold vehicles from historical reports. Those are the same ownership-constrained
  queries F-03 rewrites, which is why this waits until all three parity slices have landed
  and are test-covered: archive-filter changes are never interleaved with namespace changes
  in the same query.
- **Status:** proposed

### S-06: A merge request gets automated compile, test, and verify feedback

- **Outcome:** a developer opening a merge request receives automated compile, test, and
  verify results, rather than verification running only on tags.
- **Change ID:** `merge-request-ci`
- **PRD refs:** FR-015, FR-017
- **Prerequisites:** S-01
- **Parallel with:** S-05
- **Blockers:** —
- **Unknowns:** —
- **Risk:** The only item here not required by a must-have FR, and the sequencing goal for
  this roadmap would normally park it. Kept because `.gitlab/gitlab-ci.yml:20` runs
  `./mvnw test` — Surefire, which excludes `*IT*` — and no job invokes `verify`, so the
  regression suite built across S-01 to S-05 would never execute in CI. That is the same
  mechanism by which a total suite outage went unnoticed from 2022-08-01 onward. Sequenced
  after S-01 because a green pipeline over a suite that does not yet exist proves nothing.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID | Suggested issue title | Ready for `/10x-plan` | Notes |
| --- | --- | --- | --- | --- |
| F-01 | `resolvable-build` | Restore Maven dependency resolution and drop the JHipster BOM | done | Delivered 2026-08-25; see `context/archive/resolvable-build/` |
| F-02 | `golden-baseline-capture` | Capture golden reference output from commit `6e19b96` | yes | Run `/10x-plan golden-baseline-capture`; runs on `6e19b96`, so it is independent of HEAD and parallel with F-04 |
| F-03 | `jakarta-platform-migration` | Migrate `src/main` to Jakarta EE 9+ and Spring Security 6, remove JHipster | done | Delivered 2026-08-25; see `context/archive/jakarta-platform-migration/`. Implementation review found and fixed 10 issues — read `reviews/impl-review.md` before F-04 |
| F-04 | `test-context-restored` | Restore the integration-test Spring context under H2 | yes | Unblocked 2026-08-25 — F-03 delivered. Run `/10x-plan test-context-restored`; start from the F-04 handoff in `context/archive/jakarta-platform-migration/change.md` |
| S-01 | `session-parity` | Prove a full user session is unchanged through client 1.2.5 | no | Waits on F-04 |
| S-02 | `admin-surface-parity` | Prove the administrator surface is unchanged apart from four explicit API corrections | no | Waits on F-04 |
| S-03 | `report-parity` | Verify statistics and XLSX reports against the golden baseline | no | Waits on F-02 and F-04 |
| S-04 | `english-reminder-fix` | Fix English reminder and account e-mail rendering | no | Waits on F-02 and F-04 |
| S-05 | `vehicle-archiving` | Add vehicle archiving with history retention | no | Waits on S-01, S-03, S-04 |
| S-06 | `merge-request-ci` | Run compile, test, and verify on merge requests | no | Waits on S-01 |

## Open Roadmap Questions

1. ~~**Frame-options policy.**~~ **RESOLVED 2026-08-25 — `DENY`.** Owner decision.
   **No longer blocks F-03.**

   The current configuration denies at `SecurityConfiguration.java:71-72` and then disables at
   `:77-79`; the second wins, so clickjacking protection is effectively off today.
   F-03's deduplicated `SecurityFilterChain` must emit **`X-Frame-Options: DENY`** — i.e. keep
   the `.deny()` intent and drop the `.disable()` call, which is vestigial.

   **Basis for the decision.** The usual reason a JHipster application disables frame options
   is the H2 console, which renders in frames. This project never enables it: there is no
   `spring.h2.console` key in any file under `src/main/resources/config/`, no
   `H2ConfigurationHelper` usage, and the only trace is a stale
   `antMatchers("/h2-console/**")` ignore rule at `SecurityConfiguration.java:49`. Dev runs on
   MariaDB and H2 is test-only and headless. No other framing requirement exists server-side —
   the reports are XLSX/PDF downloads, not embedded views.

   **Carried into F-03:** emit `DENY`; delete the duplicated
   `sessionManagement().sessionCreationPolicy(STATELESS)` (declared twice, at `:74-75` and
   `:81-82`); and drop the stale `/h2-console/**` ignore rule at `:49` along with it. If the
   client at `../client` turns out to iframe its own views, `SAMEORIGIN` is the fallback — but
   nothing found server-side suggests it does.
2. **Production JWT signing key.** The PRD defers rotation to a separate security pass and
   records it as not blocking. Two facts established after that decision change the picture:
   the key in `application-prod.yml` is byte-identical to the one in `application-dev.yml`,
   and `src/main/docker/app.yml` overrides the datasource and mail passwords at deploy time
   but not this key — so production signs tokens with a value committed to the repository
   since 2018. Owner: user. **Blocks:** nothing in this roadmap; raised because the
   deferral was made on weaker information.
3. **Should the regression suite replace the standalone test harness with full-context
   MockMvc?** The existing integration tests assert against a serialization configuration the
   application never uses, which produced three false failures during baseline verification.
   Owner: planning step. **Blocks:** advisory to F-04 and S-01. Recommended.
4. **Development CORS permitting all origins while allowing credentials.** Deferred to the
   separate security pass. Owner: user. **Blocks:** nothing.
5. **Reminder delivery robustness** — idempotency, retry, a distributed lock, and an explicit
   timezone decision. S-04 preserves current behaviour knowingly. Owner: user.
   **Blocks:** nothing.
6. **API versioning and path normalization.** No version prefix exists and path naming is
   inconsistent (`/api/vehicle`, `/api/fuel-type`); both are frozen by this change. Owner:
   user. **Blocks:** nothing here; blocks the deferred identifier rework whenever it is
   taken up.
7. **Extracting services behind the lookup and reminder-advance controllers.** Source carries
   standing TODOs; S-02 preserves them deliberately. Owner: user. **Blocks:** nothing.
8. **Consolidating ownership enforcement** into a single auditable boundary rather than 13
   principal-filtered queries enforced by convention. S-01 preserves the current pattern.
   Owner: user. **Blocks:** nothing here; belongs to the deferred domain restructure.
9. **Should test-data generation remain a production surface?** Ruled out of this change
   despite a standing source TODO. Owner: user. **Blocks:** nothing.
10. **Image-storage hardening** — no size limit, no content verification, and the old file is
    deleted before the transaction commits. Owner: user. **Blocks:** nothing.
11. **Who owns the client-side defects found by S-07?** The S-07 browser trial reproduced two
    faults in the frozen client 1.2.5 that no roadmap item covers. Every other S-07 finding has
    a home — the delete-with-history 500 belongs to S-05 — but these do not, because the client
    is explicitly out of scope for this repository (`../client`, consumed as a prebuilt Maven
    artifact).

    **C-2 is the one that matters.** The insurance edit form binds the raw `costInCents` into
    its "Cost (PLN)" input, so saving that form **without touching a field** sends a value 100×
    too large and silently corrupts the stored cost. It is isolated to that one form: the
    repair, routine-service, inspection, and refuel edit forms all convert correctly from the
    identical server field, and the server half of the path is sound. This destroys user data on
    a normal edit, so it is not a cosmetic backlog item. **C-1** is milder: cold-loading any
    route that mounts `VehicleDetails` (`#/app/details/:id`, `#/app/new`) white-screens the app,
    while in-app navigation is unaffected.

    Neither is fixable from this repository — both need a client change and a
    `carcare-client.version` bump. Three options: open a slice for a client fix + bump, file
    them in the client repo and track externally, or consciously accept them. Owner: user.
    **Blocks:** nothing in this roadmap; raised so the decision is made deliberately rather than
    lost inside an archived change record. Evidence:
    `context/changes/client-server-contract-trial/change.md` (findings C-1, C-2).

## Parked

- **MariaDB → PostgreSQL, and Liquibase → Flyway.** Migrating live production data is the
  only irreversible operation in the programme; doing it before behavioural coverage exists
  inverts the correct sequencing. Becomes its own change against the baseline this roadmap
  builds.
- **Entity restructure and JSON detail storage.** A domain redesign, not platform work, and
  it depends on the database migration having landed first.
- **UUID primary keys and non-leaking identifiers.** Breaks the client contract S-01
  preserves; needs an API versioning story first.
- **Maven → Gradle.** Changes nothing the product does while invalidating every build
  assumption in CI, containers, and the client-artifact dependency.
- **User impersonation.** A new access-control capability; the access model is explicitly
  unchanged by this work.
- **Internationalization rework.** S-04 fixes English template rendering only, with an
  explicit stop condition if the root cause reaches further.
- **Functional-programming and Vavr sweep.** A style migration across the same files F-03
  touches; combining them would make the diff impossible to review as behaviour-preserving.
- **Bean Validation on business request bodies.** Descoped during shaping (was FR-010) — it
  conflicts with the compatibility promise, since entity constraints were authored for
  persistence-time checking and could reject payloads client 1.2.5 legitimately sends today.
- **Eliminating the per-vehicle query fan-out in the rich vehicle mapper.** Descoped during
  shaping (was FR-014); the real fix needs read models and projections, which belong to the
  deferred domain restructure.
- **Removing test-data generation from the production surface.** Ruled out to keep this
  change focused, despite a standing source TODO.
- **Observability — tracing, alerting, job monitoring.** Accepted consequence: if the
  reminder job stops after the migration, nothing will say so.
- **Dependency and container vulnerability scanning.** Accepted consequence: F-01 makes
  roughly 40 dependency versions hand-managed and nothing will watch them.
- **Coverage thresholds enforced in CI.** S-06 runs the suite on merge requests, but no floor
  blocks a merge — a threshold would block work before the suite is complete.
- **Performance targets.** No latency, throughput, or resource-envelope property is asserted.
  A settled position at this scale, noted because Hibernate 6 changes fetch and type-mapping
  behaviour.

## Done

<!-- Populated by /10x-archive when a change whose Change ID matches an item above is archived. -->

- **F-04: (foundation) `./mvnw verify` boots a Spring context against H2 and the integration suite runs on its merits instead of erroring at startup — the 20 remaining `javax.*` imports in `src/test` are converted, `tech.jhipster.domain.util.FixedH2Dialect` is replaced, and the schema-validation mismatch beneath it is resolved.** — Archived 2026-08-26 → `context/archive/2026-08-25-test-context-restored/`. Lesson: —.
- **S-07: the frozen React client can be exercised against a clean MariaDB-backed server in a real browser; every observed client/server mismatch is either fixed with coverage or recorded as a deliberate compatibility decision.** — Archived 2026-08-27 → `context/archive/2026-08-27-client-server-contract-trial/`. Lesson: —.
- **S-04: an owner whose account language is English receives a correctly rendered reminder naming the vehicle and the expiry, with Polish unaffected and reminder selection semantics unchanged.** — Archived 2026-08-28 → `context/archive/2026-08-28-english-reminder-fix/`. Lesson: —.
- **S-01: an existing owner can log in through the unmodified React client 1.2.5, list and open their vehicles, and create, read, update, and delete all five event types — receiving the same paths, payloads, and status codes as before — while reaching no other user's data on any path.** — Archived 2026-08-28 → `context/archive/2026-08-26-session-parity/`. Lesson: —.
- **F-02: (foundation) reference report values, statistics figures, and reminder selections exist, produced from commit `6e19b96` — the newest commit that builds and runs — and are comparable against post-migration output.** — Archived 2026-08-28 → `context/archive/2026-08-27-golden-baseline-capture/`. Lesson: —.
- **S-03: an owner can request consumption, mileage, and cost statistics and both XLSX reports, and receive output matching the F-02 reference at value level — cell values, computed figures, and content type.** — Archived 2026-08-28 → `context/archive/2026-08-28-report-parity/`. Lesson: —.
