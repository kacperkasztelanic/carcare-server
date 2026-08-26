# S-01 `session-parity` Implementation Plan

## Overview

Prove that an existing owner's whole session — log in, list and open vehicles, and create, read,
update, and delete all five event types — is indistinguishable through the unmodified React client
1.2.5 after the Jakarta / Spring Boot 3 migration, while reaching no other user's data on any path.

Three things stand between here and that proof, in order: the test profile cannot execute a single
query against any event table; the client-visible alert header was renamed during F-03 and no longer
matches; and the project has no business-behaviour tests and no fixture layer to write them against.
This plan lands all three, then adds the suite, then fixes three pre-existing 500s the suite will
otherwise trip over.

## Current State Analysis

**The migration never textually touched this surface.** `git diff 6e19b96 HEAD -- src/main/` is empty
for all nine in-scope controllers and for the entire `repository/` package, including all 13
`?#{principal.username}` ownership queries. Domain entities differ by import line only. Every parity
risk here is therefore *behavioural* — Hibernate 6 over unchanged JPQL, the Spring Security 6 filter
chain, Spring MVC 6 / Jackson serialization, the `ProblemDetail` error contract — which means a
diff audit would find nothing and only tests against a running context can detect what moved.

**Owner isolation is structurally sound but rests on an undeclared bean.** All 13 queries constrain
on the principal; `VehicleDto` has no owner field, so create cannot be hijacked; five of six entities
declare `private final Long id`, so create cannot become overwrite. But `?#{principal.username}`
requires `SecurityEvaluationContextExtension`, which is not declared anywhere in `src/main` — it
arrives implicitly through Boot's `SecurityAutoConfiguration` with `spring-security-data` on the
classpath (`pom.xml:203`). If that chain ever breaks, all 13 queries throw and nothing in the suite
would notice today.

**The test profile is blocked.** `hibernate.auto_quote_keyword: true` (added by F-04 for
`jhi_persistent_audit_evt_data.value`) makes every query against the five event tables fail with
`Column "R1_0.date" not found`. The physical H2 schema is inconsistent because Liquibase quotes
reserved words and leaves others bare, and Hibernate's flag is global.

**There is no fixture infrastructure at all.** 25 test classes, none covering `Vehicle` or any event
type. `src/test/resources` holds only config, i18n, logback, and a mail template. The single
`context="test"` Liquibase changeset creates `jhi_date_time_wrapper` and nothing else. The
`fuel_types` / `insurance_types` lookups are empty in tests — `GET /api/fuel-type` returns `[]` —
and `vehicles.fuel_type_id` is `NOT NULL`, so no vehicle can be created until they are seeded.

## Desired End State

`./mvnw verify` runs the existing 115 integration tests plus a new S-01 suite, all green, and that
suite asserts:

- Every CRUD path on `/api/vehicle` and the five event types returns its baseline status code, its
  `Location` header where applicable, and the restored `X-carcareApp-alert` / `-params` header names
  carrying `carcareApp.<entity>.<created|updated|deleted>` values.
- Every cross-user access returns 404 (or `200 []` on the two list endpoints) — never 403 — across
  all six resources plus the statistics, report, and events read paths.
- A real JWT minted through `POST /api/authenticate` and replayed as `Bearer` against a protected
  endpoint succeeds.
- The four wire invariants whose violation crashes or dead-ends client 1.2.5.

And a human has run one manual session against the real client with the app booted on MariaDB,
confirming toasts appear and the console is clean.

### Key Discoveries

- **Only ten resources regressed on the header, not twelve.** `UserResource.java:75` and
  `ExceptionTranslator.java:44-45` use the *explicit-`applicationName`* `HeaderUtil` overloads,
  seeded from `spring.application.name: carcare`. At baseline `6e19b96` those same two were seeded
  from `jhipster.clientApp.name: carcare` (`6e19b96:application.yml:119-120`) — byte-identical
  output. The restoration touches `HeaderUtil`'s internal constant only, and S-02's stated
  `X-carcare-*` admin contract survives untouched.
- **The `fuelType` 400 cannot be raised in the mapper.** `ArchTest` forbids `service` → `web`
  dependencies, so `BadRequestAlertException` (in `web/rest/errors`) is unreachable from
  `FuelTypeMapper`. The established pattern is a `service/exception` type plus an `@ExceptionHandler`
  in `ExceptionTranslator` — exactly how `EmailAlreadyUsedException` and `InvalidPasswordException`
  already work.
- **There are two `fuelType` failure modes, not one.** `FuelTypeMapper.java:25-28`: a null
  `FuelTypeDto` NPEs, and an unknown `type` throws `IllegalStateException`. Neither has a handler;
  both are 500 today.
- **Seeded credentials work.** `user`/`user` and `admin`/`admin` both verify against their
  `user.csv` bcrypt hashes (checked with `BCryptPasswordEncoder` 6.1.5) and both rows are
  `activated=true`. This resolves research open question 3: the JWT test needs no user creation, and
  `user` + `admin` are the two owners for isolation tests.
- **The shared read paths are POST-with-body**, not GET: `/api/stats/consumption/per-period`,
  `/api/stats/consumption/per-refuel`, `/api/stats/mileage`, `/api/stats/cost`, `/api/reports/costs`,
  and `/api/events` all take a request body. Only `GET /api/reports/vehicle/{id}` is a path variable.
- **`TestUtil`'s ObjectMapper sets `NON_EMPTY`** (`TestUtil.java:42`), so empty strings vanish from
  serialized request bodies and then NPE in `VehicleDetailsMapper`'s `.trim()` chain. S-01 request
  bodies must not go through it.

## What We're NOT Doing

- **Not fixing `DELETE /api/vehicle/{id}` for vehicles with history.** The six FKs into `vehicles`
  are non-cascading (`20190922082653_changelog.xml:238,247,250,253,256`) and `VehicleServiceImpl.java:63`
  issues a bare `deleteById`, so it 500s. S-05 `vehicle-archiving` exists precisely to replace hard
  delete; fixing it here pre-empts that design. Covered by a `@Disabled` placeholder instead.
- **Not asserting any computed value** — no consumption figure, cost total, mileage stat, or XLSX
  cell content. That is S-03's mandate and it needs F-02's golden baseline, which does not exist yet.
  S-01 asserts only that these endpoints resolve and isolate.
- **Not consolidating ownership enforcement** into a single auditable boundary. Roadmap Open Question
  8; preserved as-is and deferred to the domain restructure.
- **Not touching `Insurance.java:44`'s mutable id.** It is the one entity that opted out of
  `private final Long id`. Not exploitable today (no mapper sets it); recorded, not changed.
- **Not adding Bean Validation** to business request bodies. Explicitly parked by the roadmap — entity
  constraints were authored for persistence-time checking and could reject payloads client 1.2.5
  legitimately sends.
- **Not adding CI wiring.** S-06 `merge-request-ci` owns that; note that `.gitlab/gitlab-ci.yml:20`
  runs `./mvnw test` (Surefire, excludes `*IT*`), so none of this suite runs in CI until S-06 lands.

## Implementation Approach

Sequence the two blocking changes first, each with its own gate, so a failure is attributable. Then
build the fixture layer, then the tests, and only then change behaviour — parity is proven against
the tree as it stands before any 500 is fixed, so the suite documents what actually shipped rather
than what we wished had.

The suite is one `*ResourceIT` per resource (matching F-04's convention) plus one cross-cutting
`OwnerIsolationIT` that concentrates every cross-user negative in a single auditable file — the
PRD's highest-severity guardrail should be readable in one place rather than scattered across six.

Fixtures are a test-only Spring component seeding through the repositories. Component-scanned from
`src/test/java` like the existing `TestUserIdentitySequenceFixup`, so no production changelog gains
test-only rows and the shared Spring context is not forked.

## Critical Implementation Details

**H2 is shared JVM-wide** (`DB_CLOSE_DELAY=-1`) and every IT must stay in the same Spring context —
no `@MockBean`, no `@DirtiesContext`. A second context re-enters the JCache territory F-04 fixed in
`CacheConfiguration.createCache()`. Use class-level `@Transactional` everywhere **except** the
delete-with-events placeholder: under `@Transactional` the delete flushes at *test rollback*, not
during the request, so the FK violation never surfaces as a 500 in the `MvcResult`.

**Ordering within Phase 6 matters.** The three 500 fixes must land *after* the parity suite is green,
not before. Their tests are new; no existing S-01 assertion should change when they land, and if one
does, that is a signal the fix reached further than intended.

## Phase 1: Unblock the test profile

### Overview

Apply the validated identifier-quoting fix so queries against the five event tables execute. Nothing
else in this plan can be written until this lands.

### Changes Required:

#### 1. Test datasource and Hibernate identifier handling

**File**: `src/test/resources/config/application-test.yml`

**Intent**: Make the single global `auto_quote_keyword` flag unnecessary by teaching H2 to treat
`VALUE` as a non-keyword and to fold identifiers case-insensitively, so both the bare `date` columns
and the Liquibase-quoted `value` column resolve. Replace F-04's comment at lines 31–34 with one that
explains the new tri-part arrangement and why it is test-only.

**Contract**: Two settings change (`application-test.yml:15,34`). This exact pair was applied,
verified green against all 115 existing ITs, and reverted during research:

```yaml
url: jdbc:h2:mem:carcare;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
hibernate.auto_quote_keyword: false
```

Neither setting exists in MariaDB, so `src/main` is unaffected. Note in the comment that this is the
*third* documented divergence of the test profile from production on identifier handling, on top of
the two F-04 recorded.

#### 2. Guard the fix

**File**: `src/test/java/com/kasztelanic/carcare/config/TestConfigurationIT.java`

**Intent**: Extend F-04's existing resource-layering guard with an assertion that a query against an
event table executes, so a future edit to `application-test.yml` cannot silently re-break a third of
the API the way F-04's did.

**Contract**: One new test method issuing a repository call against `RefuelRepository` (or the
equivalent) and asserting it does not throw. Not a REST assertion — this guards configuration, not
the contract.

### Success Criteria:

#### Automated Verification:

- Full existing suite green: `./mvnw verify` — 22 unit tests, 115 integration tests, 0 failures
- The new `TestConfigurationIT` guard passes
- No file under `src/main/` is modified in this phase

#### Manual Verification:

- The comment in `application-test.yml` explains the three-way divergence clearly enough that a
  future reader will not undo it

**Implementation Note**: After completing this phase and all automated verification passes, pause
for manual confirmation before proceeding.

---

## Phase 2: Restore the `X-carcareApp-*` alert header

### Overview

Undo the client-breaking half of F-03's header rename. Client 1.2.5 matches alert headers by
case-insensitive suffix (`../client/.../notification-middleware.ts:27-33,59-65`): `X-carcareApp-alert`
lowercases to `x-carcareapp-alert`, ends with `app-alert`, matches. `X-carcare-alert` ends with
`care-alert` and does not. Every create, update, and delete on the session surface silently loses its
toast today.

### Changes Required:

#### 1. Decouple the header name from the application name

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java`

**Intent**: Replace the mutable `applicationName` field with a fixed constant carrying the
client-facing header prefix, restoring the baseline emitted names on the two-argument `createAlert`
and the three-argument `createFailureAlert`. Keep `TRANSLATION_KEY_NAMESPACE` exactly as it is — the
*value* namespace is already correct and must not change. Rewrite the class javadoc so the two
independent `carcareApp` contracts (header-name prefix vs i18n key root) are named as distinct, since
conflating them is what caused this regression.

**Contract**: `applicationName` (`:23`) and `setApplicationName` (`:34-36`) are removed; the header
names emitted at `:40-41` and `:81-82` become `X-carcareApp-alert` / `-params` / `-error`, matching
`6e19b96:HeaderUtil.java:15,19-20`. The overloads that take an explicit `applicationName` parameter
(`:45-54`, `:56-64`) are **unchanged** — they are how `UserResource` and `ExceptionTranslator` emit
`X-carcare-*`, which is what those two emitted at baseline too.

#### 2. Remove the now-dead initializer

**File**: `src/main/java/com/kasztelanic/carcare/config/HeaderUtilInitializer.java`

**Intent**: Delete the file. Its sole purpose was seeding the static field Phase 2 removes.

**Contract**: File deleted. `spring.application.name: carcare` (`application.yml:47-48`) stays as it
is — it feeds logging and metrics, and nothing else reads it for header purposes after this change.

#### 3. Pin the contract

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/util/HeaderUtilTest.java` (new)

**Intent**: A fast unit test asserting the exact emitted header names and value shapes for creation,
update, deletion, and failure alerts, so the rename cannot recur without a red test.

**Contract**: Asserts literal strings `X-carcareApp-alert`, `X-carcareApp-params`,
`X-carcareApp-error`, and value `carcareApp.vehicle.created`. A `*Test`, not `*IT` — Surefire runs it,
so it fails fast.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green — existing suite plus the new `HeaderUtilTest`
- `HeaderUtilInitializer` no longer exists and nothing references it
- Grep confirms `UserResource` and `ExceptionTranslator` still emit `X-carcare-*`

#### Manual Verification:

- The rewritten javadoc distinguishes the header prefix from the i18n namespace unambiguously

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 3: Build the fixture layer

### Overview

Create the test-only infrastructure every subsequent phase depends on: idempotent lookup seeding, a
two-owner dataset builder, and a shared IT base carrying the annotations and the non-`NON_EMPTY`
ObjectMapper.

### Changes Required:

#### 1. Session fixtures component

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java` (new)

**Intent**: A `@Component` (test classpath only, so it is scanned into the same shared context as
`TestUserIdentitySequenceFixup` without forking it) that seeds the `fuel_types` and
`insurance_types` lookups idempotently, and builds vehicles and each of the five event types for a
named owner. Idempotency matters because all three columns on both lookup tables carry unique
constraints and the context is shared JVM-wide.

**Contract**: Seeding is find-or-create per row, not `deleteAll` + insert — another IT's data must
survive. Vehicle and event builders take an owner login and return the persisted entity, resolving
the `User` by login rather than by id. Owners are the Liquibase-seeded `user` and `admin`; **no test
creates a user**.

#### 2. Shared IT base

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AbstractSessionIT.java` (new)

**Intent**: Carry `@SpringBootTest(classes = CarcareApp.class)`, `@AutoConfigureMockMvc`,
`@Transactional`, the injected `MockMvc` and `SessionFixtures`, and a JSON-writing helper — matching
`AuditResourceIT.java:29-32`'s established shape.

**Contract**: The JSON helper must **not** use `TestUtil.convertObjectToJsonBytes`. `TestUtil.java:42`
configures `NON_EMPTY`, which strips empty strings from request bodies and then NPEs in
`VehicleDetailsMapper`'s `.trim()` chain. Use a locally configured `ObjectMapper` with default
inclusion, registering `JavaTimeModule` for the `LocalDate` fields on `InsuranceDto` and
`VehicleEventDto`.

#### 3. Fixture smoke test

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixturesIT.java` (new)

**Intent**: Prove the fixtures seed and read back, and — critically — that running them twice does
not violate a unique constraint.

**Contract**: Seeds lookups twice, then builds a vehicle with events for both owners and asserts row
counts. This is also the first test that would fail if `SecurityEvaluationContextExtension` ever
stopped resolving.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green including `SessionFixturesIT`
- Seeding twice in one context does not throw
- Still exactly one Spring context — no `@MockBean` or `@DirtiesContext` introduced

#### Manual Verification:

- Fixture builders read clearly enough that Phase 4's six IT classes will be short

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 4: CRUD parity integration tests

### Overview

Six `*ResourceIT` classes proving the positive session paths. This is the bulk of the slice.

### Changes Required:

#### 1. Vehicle CRUD

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java` (new)

**Intent**: Cover `GET /api/vehicle/{id}`, `GET /api/vehicle/all`, `POST /api/vehicle`,
`PUT /api/vehicle/{id}`, `DELETE /api/vehicle/{id}` for the owner, asserting status, the restored
header names, `Location` on create, and `X-Total-Count` on the list.

**Contract**: `POST` asserts 201 + `Location: /api/vehicle/{id}` + `X-carcareApp-alert:
carcareApp.vehicle.created`. `DELETE` on an **event-free** vehicle asserts 200 + `carcareApp.vehicle.deleted`
+ the row gone. `GET /api/vehicle/{unknown}` asserts **404 with an empty body and no Content-Type** —
it is not a ProblemDetail, and that is baseline behaviour worth pinning. Note
`POST /api/vehicle` calls `getUserWithAuthoritiesOrFail()` (`VehicleResource.java:54`), so
`@WithMockUser("user")` works only because `user` is a real seeded row.

#### 2. Delete-with-history placeholder

**File**: `VehicleResourceIT.java` (same file)

**Intent**: A `@Disabled` test recording that deleting a vehicle carrying events 500s against the six
non-cascading FKs, naming S-05 `vehicle-archiving` as the owner of the real fix, so the defect is
discoverable from the suite rather than only from prose.

**Contract**: The `@Disabled` reason string must name S-05. This test must **not** be `@Transactional`
— under class-level `@Transactional` the delete flushes at test rollback rather than inside the
request, so the FK violation never reaches the `MvcResult`. Since the class is `@Transactional`,
this method needs `@Transactional(propagation = NOT_SUPPORTED)` or equivalent and must clean up after
itself.

#### 3. Five event-type CRUD classes

**Files**: `RefuelResourceIT.java`, `RepairResourceIT.java`, `RoutineServiceResourceIT.java`,
`InspectionResourceIT.java`, `InsuranceResourceIT.java` (all new)

**Intent**: Cover the regular five-endpoint shape each event type exposes, with the same header,
status, and `Location` assertions. `InsuranceResourceIT` additionally covers the POST-vs-PUT
`insuranceType` shape asymmetry the client sends (POST object-wraps, PUT does not) — if both shapes
do not round-trip, the client's edit flow breaks.

**Contract**: Per type — `POST /api/{type}/{vehicleId}` → 201 + `Location: /api/{type}/{vehicleId}/{id}`
+ `carcareApp.{type}.created`; `PUT /api/{type}/{id}` → 200 + `.updated`; `DELETE /api/{type}/{id}` →
200 + `.deleted` **and the deleted DTO in the body**; `GET /api/{type}/{id}` → 200 or bodyless 404;
`GET /api/{type}/all/{vehicleId}` → 200 + `X-Total-Count`. Entity names for the alert values come from
each resource's `ENTITY_NAME` constant.

#### 4. Client wire invariants

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/ClientWireContractIT.java` (new)

**Intent**: Assert the four invariants whose violation crashes or dead-ends client 1.2.5, kept in one
file so the coupling to `../client` is documented in one place with its `file:line` justification.

**Contract**: (a) every string field the client `.trim()`s unconditionally — `station`, `details`,
`insurer`, `number`, `make`, `model`, `licensePlate`, and the five `vehicleDetails` strings — is
non-null in a GET response when stored empty; (b) `GET /api/{type}/{id}` returns `vehicleId`, since
the client's delete flow reads it off a preceding GET; (c) `X-Total-Count` is present on both list
endpoints; (d) the `Authorization` response header on `POST /api/authenticate` carries a literal
`Bearer ` prefix — see Phase 5, which may host this one instead.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green with all six `*ResourceIT` classes plus `ClientWireContractIT`
- Every create/update/delete assertion checks the literal header name `X-carcareApp-alert`
- The `@Disabled` placeholder is present, named, and skipped — not failing
- Test count increases by roughly 40–50 integration tests

#### Manual Verification:

- Reading `InsuranceResourceIT` makes the POST/PUT shape asymmetry obvious to a future maintainer
- No test asserts a computed value — spot-check that S-03's boundary was respected

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 5: Owner isolation and the real-JWT session

### Overview

The highest-severity guardrail, concentrated in one auditable file, plus the one test that proves the
actual login path works rather than the mock.

### Changes Required:

#### 1. Cross-cutting isolation

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java` (new)

**Intent**: For every read, update, and delete path across all six resources, assert that owner B
cannot reach owner A's row — and assert the *uniform failure mode*, because ownership here is a query
predicate rather than an authorization decision, so a foreign id is indistinguishable from a missing
one.

**Contract**: Cross-user access asserts **404, never 403** — except the two list endpoints
(`GET /api/vehicle/all`, `GET /api/{type}/all/{vehicleId}`), which return `200` with an empty array
even for a foreign vehicle id. Also asserts that owner B cannot create an event against owner A's
`vehicleId` (each event service resolves the parent through `findByIdAndOwnerIsCurrentUser` first —
`RefuelServiceImpl.java:43-46` and four siblings) and that a `POST /api/vehicle` body cannot set an
owner (`VehicleDto` has no owner field; `VehicleServiceImpl.java:44` sets it unconditionally).

#### 2. Shared read-path isolation smoke

**File**: `OwnerIsolationIT.java` (same file)

**Intent**: Close the coverage gap on the six ownership queries reachable *only* through statistics,
reports, and events — the collection-valued `findAllByIdAndOwnerIsCurrentUser` and the five unfiltered
`findByVehicleId` variants that `VehicleRichMapper.java:64-80` calls. Without this nobody covers the
collection-valued SpEL variant until S-03 lands, and S-03 is blocked on F-02.

**Contract**: Owner gets 200, non-owner gets 404 or empty, on `POST /api/stats/consumption/per-period`,
`POST /api/stats/consumption/per-refuel`, `POST /api/stats/mileage`, `POST /api/stats/cost`,
`POST /api/events`, `POST /api/reports/costs`, and `GET /api/reports/vehicle/{id}`. All except the
last take a request body. **No computed value is asserted** — shape and isolation only.

#### 3. Real-JWT end-to-end

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/JwtSessionIT.java` (new)

**Intent**: Prove the actual login path — the one thing `@WithMockUser` structurally cannot. Post
credentials, read the token off the **response header**, replay it as `Bearer` against a protected
endpoint.

**Contract**: `POST /api/authenticate` with `user`/`user` (verified: the `user.csv` bcrypt hash matches
and the row is `activated=true`) returns 200; the token is read from the `Authorization` *response
header*, not the body (`../client/.../authentication.ts:111-113`), and must carry a literal `Bearer `
prefix; replaying it against `GET /api/vehicle/all` returns 200. Also asserts anonymous access to the
same endpoint returns 401 `application/problem+json`, and that `GET /` returns **200** — the runtime
guard for F-03's `.anyRequest().permitAll()` fix, which was reasoned from bytecode.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green with `OwnerIsolationIT` and `JwtSessionIT`
- Every cross-user assertion checks 404 or empty-list, and none accepts 403
- All six stats/report/events paths have an owner-positive and a non-owner-negative case
- `GET /` returns 200 and anonymous `GET /api/vehicle/all` returns 401

#### Manual Verification:

- `OwnerIsolationIT` can be read top to bottom as a complete statement of the guarantee — a reviewer
  should be able to confirm no path is missing without opening the other six files

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 6: Fix the three pre-existing 500s

### Overview

Three server errors that predate the migration (all call sites byte-identical to `6e19b96`) and that
the suite trips over. Owner decision: fix them. This deliberately widens past strict parity, so it
lands *after* the parity suite is green — if any Phase 4 or 5 assertion changes here, the fix reached
further than intended.

### Changes Required:

#### 1. Missing or unknown `fuelType` → 400

**Files**: `src/main/java/com/kasztelanic/carcare/service/exception/` (new exception type),
`service/mapper/FuelTypeMapper.java`, `web/rest/errors/ExceptionTranslator.java`

**Intent**: Turn both mapper failure modes into a 400 ProblemDetail instead of a 500. A null
`FuelTypeDto` and an unknown `type` are both unsatisfiable — `vehicles.fuel_type_id` is `NOT NULL`
with an FK to `fuel_types` — so there is no valid interpretation of either.

**Contract**: The exception must live in `service/exception`, **not** `web/rest/errors`: `ArchTest`
forbids `service` → `web` dependencies, so `BadRequestAlertException` is unreachable from the mapper.
Follow the existing pattern — `EmailAlreadyUsedException`, `InvalidPasswordException`, and
`ReportGenerationException` all live there with `@ExceptionHandler` methods in `ExceptionTranslator`.
The handler returns 400 and routes through `handleExceptionInternal` so `path` and `message` are added
like every other error body. `FuelTypeMapper.java:25-28` replaces the bare
`orElseThrow(IllegalStateException::new)` and adds a null check.

#### 2. Duplicate `vehicleId` in `POST /api/events` → tolerated

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java`

**Intent**: Absorb a redundant duplicate rather than failing. The request is satisfiable — the client
has simply named the same vehicle twice.

**Contract**: `EventServiceImpl.java:34-35`'s `Collectors.toMap(PeriodVehicle::getVehicleId, identity())`
gains a merge function keeping the **first** occurrence. Note in a comment that "keep first" is an
arbitrary tiebreak: if two genuinely different periods are ever sent for one vehicle, the second is
silently dropped.

#### 3. Zero mileage consumption → `0.0`

**File**: `src/main/java/com/kasztelanic/carcare/service/dto/AverageConsumptionResult.java`

**Intent**: Guard the division so zero mileage yields `0.0` instead of throwing. At `mileage == 0`,
`volume * 100.0 / mileage` is NaN or Infinity and `BigDecimal.valueOf(...)` throws
`NumberFormatException` → 500.

**Contract**: `getAverageConsumption()` (`:19-23`) returns `0.0` when `mileage <= 0`; **every non-zero
input must produce a bit-identical result to today**. The return type stays primitive `double`, so the
JSON shape and nullability are unchanged and no client arithmetic path can break. Record the known
semantic cost: "unknown" is now reported as "zero", indistinguishable from a real 0.0 — flag it for
S-03, which owns this surface at value level and has the golden baseline to judge it against.

#### 4. Tests for all three

**Files**: `VehicleResourceIT.java`, `OwnerIsolationIT.java` or a new `EventResourceIT.java`, and a
unit test for the DTO

**Intent**: Assert the new behaviour: 400 for both `fuelType` cases, 200 with merged results for the
duplicate `vehicleId`, and `0.0` for zero mileage.

**Contract**: The `fuelType` test asserts 400 **and** that the response body is a ProblemDetail
carrying `path` and `message` — the two fields client 1.2.5 actually reads. The DTO test is a plain
unit test asserting both the guard and that a representative non-zero case is unchanged.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green
- No Phase 4 or Phase 5 assertion changed to accommodate these fixes
- All three formerly-500 requests now return their intended status
- The `AverageConsumptionResult` unit test proves a non-zero case is bit-identical

#### Manual Verification:

- The zero-mileage semantic cost is written down where S-03 will find it

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 7: Manual client session and record correction

### Overview

The gate no integration test can supply, plus the bookkeeping that keeps the project record from
contradicting itself.

### Changes Required:

#### 1. Manual session against the real client

**File**: none — a documented procedure, recorded in `change.md`

**Intent**: Boot the app on MariaDB with the real client WAR and walk a full owner session in a
browser. This is the only step that proves the *client* is satisfied by the server contract, as
opposed to proving the server matches our reading of the client. The header regression is precisely a
defect every server-side assertion could have passed while the client silently lost every toast.

**Contract**: Log in; list and open vehicles; create, edit, and delete one of each of the five event
types; confirm a toast appears on every create/edit/delete; confirm the browser console is clean;
confirm the delete flow completes. Record the result — including the client version and the date — in
`change.md`.

#### 2. Name the two `carcareApp` contracts

**File**: `AGENTS.md`

**Intent**: Document that `carcareApp` names two independent contracts — the HTTP header-name prefix
and the i18n message-key root — and that they must not be folded together. Conflating them is what
produced this regression and what caused F-03's review to clear it.

**Contract**: A short subsection under the existing header/contract material, naming both with their
file references and the client-side suffix-match that depends on the header prefix.

#### 3. Epilogue and record correction

**File**: `context/changes/session-parity/change.md`

**Intent**: State plainly that S-01 reverses F-03 implementation-review finding F2
(`context/archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md`), which accepted the
header rename on reasoning that conflated the header name with the i18n namespace — true as far as it
went, but a different contract from the one that broke. Without this the archived record reads as
contradicting the current code.

**Contract**: Also record: the three fixed 500s and their new behaviour; the `@Disabled` delete case
and S-05 as its owner; the test profile's third identifier divergence from production; and that
research open question 3 is resolved (seeded credentials verified working). Set `status: implemented`.

#### 4. Roadmap note

**File**: `context/foundation/roadmap.md`

**Intent**: Correct S-02's risk paragraph, which states that F-03 unified alert headers into a single
`X-carcare-*` namespace and instructs S-02 to verify against it. That is now only half true — the ten
business resources are back on `X-carcareApp-*` while `UserResource` remains on `X-carcare-*` (as it
was at baseline).

**Contract**: Edit S-02's Risk paragraph only. Do not change its Status — `/10x-archive` owns that.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` green, final run
- `AGENTS.md` and `change.md` contain the required sections
- `git status` clean after commit

#### Manual Verification:

- The manual client session completed with toasts appearing and a clean console
- The archived F-03 review no longer contradicts the code without explanation

---

## Testing Strategy

### Unit Tests

- `HeaderUtilTest` — exact emitted header names and alert value shapes (Phase 2)
- `AverageConsumptionResult` zero-mileage guard, plus a non-zero regression case (Phase 6)

### Integration Tests

- Six `*ResourceIT` classes — full CRUD, status codes, header names, `Location`, `X-Total-Count`
- `OwnerIsolationIT` — every cross-user negative across six resources plus seven shared read paths
- `JwtSessionIT` — real login, `Bearer` replay, anonymous 401, `GET /` 200
- `ClientWireContractIT` — non-null strings, `vehicleId` on GET, `X-Total-Count`
- `SessionFixturesIT` — fixture idempotency
- `TestConfigurationIT` — extended with the event-table query guard

### Manual Testing Steps

1. Boot the app on MariaDB with the real client WAR: `./mvnw` with a running `localhost:3306/carcare`
2. Log in through the client as an existing owner
3. List vehicles, open one, edit it — confirm a toast appears on save
4. Create, edit, and delete one of each of the five event types — confirm a toast each time
5. Delete an event-free vehicle — confirm the toast and that it leaves the list
6. Check the browser console is free of errors throughout

## Performance Considerations

None asserted — the PRD deliberately states no latency, throughput, or fetch-behaviour property. Worth
noting that `VehicleRichMapper.java:64-80` fans out five queries per vehicle and Phase 5's shared
read-path smoke will exercise it; if suite runtime becomes a problem, that is the cause, and the fix
belongs to the parked read-models work, not here.

## Migration Notes

No schema change. No Liquibase changelog is added or edited. `src/main` changes are confined to
`HeaderUtil` (header names), the deleted `HeaderUtilInitializer`, and the three Phase 6 fixes. The
only production-visible behaviour changes are the restored header names — which restore baseline —
and the three 500s becoming 400 / 200 / `0.0`.

## References

- Research: `context/changes/session-parity/research.md`
- Decided inputs: `context/changes/session-parity/change.md`
- F-03 review that accepted the header rename:
  `context/archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md` (finding F2)
- F-04's test-context work this builds on: `context/archive/2026-08-25-test-context-restored/`
- IT pattern to follow: `src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java:29-32`
- Client suffix match: `../client/src/main/webapp/app/config/notification-middleware.ts:27-33,59-65`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Unblock the test profile

#### Automated

- [ ] 1.1 Full existing suite green: `./mvnw verify` — 22 unit tests, 115 integration tests, 0 failures
- [ ] 1.2 The new `TestConfigurationIT` guard passes
- [ ] 1.3 No file under `src/main/` is modified in this phase

#### Manual

- [ ] 1.4 The comment in `application-test.yml` explains the three-way divergence clearly

### Phase 2: Restore the `X-carcareApp-*` alert header

#### Automated

- [ ] 2.1 `./mvnw verify` green — existing suite plus the new `HeaderUtilTest`
- [ ] 2.2 `HeaderUtilInitializer` no longer exists and nothing references it
- [ ] 2.3 Grep confirms `UserResource` and `ExceptionTranslator` still emit `X-carcare-*`

#### Manual

- [ ] 2.4 The rewritten javadoc distinguishes the header prefix from the i18n namespace

### Phase 3: Build the fixture layer

#### Automated

- [ ] 3.1 `./mvnw verify` green including `SessionFixturesIT`
- [ ] 3.2 Seeding twice in one context does not throw
- [ ] 3.3 Still exactly one Spring context — no `@MockBean` or `@DirtiesContext` introduced

#### Manual

- [ ] 3.4 Fixture builders read clearly enough that Phase 4's six IT classes will be short

### Phase 4: CRUD parity integration tests

#### Automated

- [ ] 4.1 `./mvnw verify` green with all six `*ResourceIT` classes plus `ClientWireContractIT`
- [ ] 4.2 Every create/update/delete assertion checks the literal header name `X-carcareApp-alert`
- [ ] 4.3 The `@Disabled` placeholder is present, named, and skipped — not failing
- [ ] 4.4 Test count increases by roughly 40–50 integration tests

#### Manual

- [ ] 4.5 `InsuranceResourceIT` makes the POST/PUT shape asymmetry obvious
- [ ] 4.6 No test asserts a computed value — S-03's boundary respected

### Phase 5: Owner isolation and the real-JWT session

#### Automated

- [ ] 5.1 `./mvnw verify` green with `OwnerIsolationIT` and `JwtSessionIT`
- [ ] 5.2 Every cross-user assertion checks 404 or empty-list, and none accepts 403
- [ ] 5.3 All six stats/report/events paths have an owner-positive and a non-owner-negative case
- [ ] 5.4 `GET /` returns 200 and anonymous `GET /api/vehicle/all` returns 401

#### Manual

- [ ] 5.5 `OwnerIsolationIT` reads as a complete statement of the guarantee

### Phase 6: Fix the three pre-existing 500s

#### Automated

- [ ] 6.1 `./mvnw verify` green
- [ ] 6.2 No Phase 4 or Phase 5 assertion changed to accommodate these fixes
- [ ] 6.3 All three formerly-500 requests now return their intended status
- [ ] 6.4 The `AverageConsumptionResult` unit test proves a non-zero case is bit-identical

#### Manual

- [ ] 6.5 The zero-mileage semantic cost is written down where S-03 will find it

### Phase 7: Manual client session and record correction

#### Automated

- [ ] 7.1 `./mvnw verify` green, final run
- [ ] 7.2 `AGENTS.md` and `change.md` contain the required sections
- [ ] 7.3 `git status` clean after commit

#### Manual

- [ ] 7.4 The manual client session completed with toasts appearing and a clean console
- [ ] 7.5 The archived F-03 review no longer contradicts the code without explanation
