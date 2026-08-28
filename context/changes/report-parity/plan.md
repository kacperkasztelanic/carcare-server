# Report Parity (S-03) Implementation Plan

## Overview

Assert, permanently, that HEAD's two XLSX reports and four statistics endpoints reproduce the F-02
golden baseline at value level. Research already measured the answer — ten of eleven goldens match
and the eleventh is the documented intentional divergence — so this plan does not hunt a parity bug.
It clears the two harness defects that make golden assertions impossible today, converts the
throwaway probe into a permanent suite, and then removes a latent non-determinism from the report
sort path, using the new suite to prove that change moved nothing.

## Current State Analysis

Eleven golden references for reports and statistics sit in `src/test/resources/golden/`, captured
from pre-migration `6e19b96` under `libfaketime`. **None of them is consumed by any integration
test.** `GoldenReference.load` appears only in `GoldenReferenceTest`, a unit test that builds its own
small handle maps. `GoldenDatasetMirrorIT` seeds the fixture but asserts the fixture's own definition
and never calls `GoldenReference`. The producer and the consumer halves of F-02 were never wired
together.

The four `/api/stats/*` and two `/api/reports/*` endpoints have owner-isolation coverage only
(`src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java:186-233`) — status codes,
empty results for foreign callers, one license-plate leak check. No test asserts a single computed
value.

Two defects block any golden assertion:

1. `GoldenReference.validateHandleMap` (`GoldenReference.java:201-217`) requires the handle map to be
   injective over ids, but `SessionFixtures.seedGoldenDataset()` returns 28 handles across ten
   tables and H2 assigns identity values per table. The first assertion throws
   `Golden handle map resolves id 2 to both fuel-type:diesel and insurance-type:oc`. The baseline
   capture avoided this only because `golden-dataset.sql` hand-assigned globally distinct ids in the
   `900000+` range.
2. `src/test/resources/i18n/messages_en.properties` and `messages_pl.properties` share a classpath
   location with the production bundles, and `target/test-classes` precedes `target/classes`, so
   they shadow the real bundles for the whole test JVM. Polish reports render in English.

Green baseline this plan builds on, measured at `2e6da14` with `./mvnw -o -B verify`:
**BUILD SUCCESS, 33 unit tests (1 skipped), 177 integration tests (1 skipped).**

## Desired End State

`./mvnw -o -B verify` is green and a new `ReportParityIT` asserts all eleven golden references:
ten as exact value-level matches, and `stats/consumption-period-zero.json` as an explicitly named
divergence that records the captured 500 and asserts HEAD's deliberate 200. The i18n stubs are gone,
a guard test prevents a third instance of the shadowing defect, and the report sort path is
deterministic under a total comparator rather than relying on distinct fixture dates.

Verification: run the full suite, confirm `ReportParityIT` contributes eleven passing tests, and
confirm Phase 4's production change leaves every Phase 3 assertion untouched and passing.

### Key Discoveries:

- **The handle map is consulted for two different jobs.** `normalizeJsonById`
  (`GoldenReference.java:244-255`) consults it only for `vehicleId` fields; `normalizeRawHandles`
  (`GoldenReference.java:284-292`) does a descending-magnitude blind textual replace across the whole
  raw body. The raw path is live: `consumption-period-zero.json` is the one golden whose `body` is a
  string, so it is exactly where a colliding id would silently corrupt a comparison. Narrowing the
  map to `vehicle:*` is a correctness fix on the `vehicleId` path — but on the raw path it is only
  half the story, and the plan must not claim otherwise.
- **The raw replace is unsafe with generated ids, independently of the collision fix.**
  `normalizeRawHandles` calls `String.replace(Long.toString(id), handle)` with no delimiter. The
  capture survived this only because `golden-dataset.sql` hand-assigned ids in the `900000+` range;
  the mirror fixture uses H2 identity values, and research observed `vehicle:en-primary=1`.
  Replacing the bare digit `1` across a raw body would corrupt `"2026-03-01"`, `"error.http.500"`,
  and the problem document's `path`. Phase 1 therefore makes the replace delimiter-aware as well as
  namespace-scoped. Note also that Phase 3's divergence test asserts fields directly rather than
  routing `consumption-period-zero.json` through `compareJson`, so nothing in this slice currently
  exercises the raw path — the fix is for the next golden with a textual body.
- **Only two resource paths exist in both `src/main/resources` and `src/test/resources`**, and they
  are the two i18n stubs being deleted. The Phase 2 guard test lands with an empty exception list.
- **`templates/mail/testEmail.html` is already a test-only resource with no main counterpart** — the
  precedent that test fixtures are fine as long as they occupy a distinct classpath location.
- **All five report DTOs carry `Long id`** (`InsuranceDto`, `InspectionDto`, `RoutineServiceDto`,
  `RepairDto`, `RefuelDto`), and all five `VehicleReport` sorts are the identical one-line shape
  (`VehicleReport.java:126, 165, 198, 235, 265`), so the tiebreaker is uniform.
- **`VehicleRichDto` is never serialized over REST, but its iteration order still reaches the wire.**
  `VehicleRichMapper` has exactly three consumers — `ReportServiceImpl`, `StatisticServiceImpl`, and
  `EventServiceImpl` — and the third feeds `EventResource` `/api/events`
  (`EventServiceImpl.java:40-45`), which *is* serialized. `ForthcomingEvent.compareTo`
  (`ForthcomingEvent.java:31-38`) returns `0` for equal `dateThru` whenever either `mileageThru` is
  zero, and `findForthcomingInsurances` always builds `.mileageThru(0)`, so same-date ties are
  resolved by the stable sort — that is, by exactly the collection order Phase 4 changes. "No
  wire-visible effect" is therefore not derivable from "the DTO is not serialized"; Phase 4 asserts
  it directly instead. `ReminderService` does not consume the mapper, so the two S-04 reminder
  goldens are genuinely untouched.
- **The langKey cache hazard is a fixture bug, not a production bug.** `UserService` calls
  `clearUserCaches` on every mutation path (`UserService.java:294-296`), but `SessionFixtures` writes
  langKeys through `userRepository.save()` directly, bypassing it. The baseline capture hit the same
  wall and worked around it by restarting the app after the fixture load
  (`reference.md` Phase 3 capture record).
- **The captured request matrix is fully documented** (`reference.md:150-175`): all captures use
  `2026-03-01` to `2026-03-31`; the shared cost request lists all three vehicles; the EN period
  request uses `vehicle:en-primary`, the zero request `vehicle:zero-consumption`, the unowned mileage
  request `vehicle:pl-primary`; every call is made as `admin` except `vehicle-pl.json`, made as
  `user`.

## What We're NOT Doing

- **Not adding `order by vehicle.id` to `findAllByIdAndOwnerIsCurrentUser`** (research D3). The
  unordered query keeps feeding an index-exact array comparison in `cost-en.json`. Accepted risk,
  documented in Phase 4.
- **Not adding a Polish cost-report golden** (research D4), and therefore not touching
  `WorkbookValues.shouldSort`'s literal `"Costs"` key.
- **Not changing the `averageConsumption` contract.** Returning `null` or omitting the field when
  mileage is zero is client-visible and belongs to the frozen-client discussion (S-07).
- **Not consuming `golden/reminders/full-path.json` or `typed-seam.json`.** Reminder selection is
  S-04's outcome.
- **Not judging the English mail text.** Phase 2 repairs `MailServiceIT` to assert whatever the real
  production bundle produces; whether that English is correct is S-04's starting position.
- **Not fixing `messages_pl.properties`'s missing `email.reset.greeting`.** Handed to S-04 untouched.
- **Not updating `AGENTS.md` or `golden-baseline-capture/reference.md`.**

## Implementation Approach

Four phases, sequenced so that verification capability exists before the production change that needs
verifying. Phases 1 and 2 are mutually independent harness repairs, each separately verifiable, and
are kept apart because their failure signatures are unrelated. Phase 3 depends on both. Phase 4 lands
last specifically so Phase 3's suite can serve as its proof of inertness — running it unchanged
against the modified sort path is the whole verification argument.

## Critical Implementation Details

**Stale `target/test-classes` will produce a false negative in Phase 2.** Maven does not delete
removed resources from `target/` without a `clean`. Deleting `src/test/resources/i18n/` and re-running
will appear to change nothing, because the stale copies in `target/test-classes/i18n/` keep shadowing.
Run `./mvnw clean` (or delete `target/test-classes/i18n/` explicitly) as part of verifying that phase.

**Seed before authenticating, or the Polish report silently renders English.** `@Cacheable` on
`findOneWithAuthoritiesByLogin` (`UserRepository.java:38-39`) means a cached pre-fixture `user` entry
carries the old langKey. Phase 1's eviction inside `seedGoldenDataset` is what makes Phase 3's PL test
order-independent; without it the failure surfaces as a wrong-locale sheet name, which reads as an
i18n bug rather than a caching one.

---

## Phase 1: Harness repair — handle-map scoping and fixture cache eviction

### Overview

Make `GoldenReference` accept the map `seedGoldenDataset()` actually returns, and make the fixture
safe against the user cache. Close the producer/consumer seam that had no coverage.

### Changes Required:

#### 1. Handle-map scoping

**File**: `src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java`

**Intent**: Restrict both the uniqueness validation and the id-rewriting to the `vehicle:` handle
namespace, so a caller can pass the full 28-handle map from `seedGoldenDataset()` unchanged. Handles
outside that namespace are filtered out before validation rather than rejected — they are not
consulted by either rewrite path.

**Contract**: `validateHandleMap` gains a namespace filter applied before the injectivity check; the
filtered map is what flows into both `normalizeJson`/`reverse` and `normalizeRawHandles`. The public
signatures of `compareJson`, `compareWorkbook`, `assertJsonMatches`, and `assertWorkbookMatches` are
unchanged — callers still pass `Map<String, Long>`. Null keys and values remain rejected. The
injectivity check must still fire for two distinct `vehicle:` handles resolving to one id. The
namespace prefix belongs in a named constant, not inline.

`normalizeRawHandles` additionally becomes delimiter-aware: it must replace an id only when the
match is not flanked by digits, so a two-digit vehicle id cannot be rewritten inside a date, a
status code, or a longer number. A plain `String.replace` is safe only for the capture's `900000+`
ids and is wrong for the generated ids the mirror fixture produces. The descending-magnitude
ordering stays — it is still needed so a longer id is tried before a shorter one that prefixes it.

One accessor is added: `status()`, returning the captured envelope's `status` as an `int`. Today
`expected` is private with no getter and only `resourceName()` is exposed, so Phase 3's divergence
test — which must assert that the captured record still says 500 — has no way to read it. This is
the only public-surface addition in this phase.

#### 2. Fixture cache eviction

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`

**Intent**: After `seedGoldenDataset()` writes `admin.langKey=en` and `user.langKey=pl` through
`userRepository.save()`, evict those two logins from the `usersByLogin` cache, mirroring what
`UserService.clearUserCaches` does on every production mutation path. This makes the fixture safe for
any consumer regardless of whether it authenticates before or after seeding.

The eviction is needed in **both** directions, and the second is the one that leaks outside the
class. A parity request calls `userService.getUserWithAuthoritiesOrFail()` →
`findOneWithAuthoritiesByLogin`, which is `@Cacheable` (`UserRepository.java:39`), so the request
*repopulates* `usersByLogin` with the seeded langKeys. `AbstractSessionIT` is `@Transactional`: the
database write rolls back, the cache entry does not. AGENTS.md records that JSR-107's
`CachingProvider` hands the same `javax.cache.CacheManager` to every Spring context in a JVM, so a
stale `admin=en` / `user=pl` entry outlives the test class. `ReportParityIT` is the first test in
the suite that both mutates a langKey and authenticates, so this is a new hazard, not a pre-existing
one.

**Contract**: `SessionFixtures` takes a `CacheManager` dependency and exposes a public
`evictGoldenOwnerCaches()` that evicts `UserRepository.USERS_BY_LOGIN_CACHE` by login for both
seeded owners. `seedGoldenDataset()` calls it after the two `save()` calls; Phase 3's
`ReportParityIT` calls it again from an `@AfterTransaction` hook, which runs after rollback so the
next reader repopulates from real database state. The method's return type and handle set are
unchanged, so `GoldenDatasetMirrorIT`'s assertions on `GOLDEN_HANDLES` still hold.

#### 3. Seam coverage

**File**: `src/test/java/com/kasztelanic/carcare/golden/GoldenReferenceTest.java`

**Intent**: Cover the exact defect that shipped — a map carrying cross-table id collisions outside
the `vehicle:` namespace must now be accepted, and a genuine `vehicle:` collision must still be
rejected. This is the unit-level half of closing the seam; `GoldenDatasetMirrorIT` gets the
integration half.

**Contract**: Two new tests against `GoldenReference`'s existing public comparison API — one passing
a map shaped like `seedGoldenDataset()`'s real output (duplicate ids across `fuel-type:` and
`insurance-type:`) and asserting a successful comparison, one passing two `vehicle:` handles on the
same id and asserting the `IllegalArgumentException`.

#### 4. Integration-level seam guard

**File**: `src/test/java/com/kasztelanic/carcare/golden/GoldenDatasetMirrorIT.java`

**Intent**: Assert that the real map returned by `seedGoldenDataset()` is accepted by
`GoldenReference` — the one assertion whose absence let this defect ship. Placed here rather than in
`ReportParityIT` because it is a property of the fixture, not of any endpoint.

**Contract**: One added test seeding the dataset and passing the returned map to a `GoldenReference`
comparison without expecting a throw. It does not need to assert a match, only that the map is
accepted.

### Success Criteria:

#### Automated Verification:

- Compiles: `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem && ./mvnw -o -B test-compile`
- Golden unit tests pass: `./mvnw -o -B test -Dtest=GoldenReferenceTest`
- Fixture IT passes: `./mvnw -o -B verify -Dit.test=GoldenDatasetMirrorIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false`
- Full suite still green: `./mvnw -o -B verify` — no test count regression against 33 unit / 177 integration

#### Manual Verification:

- The `vehicle:` namespace constant reads as a documented contract, not a magic string
- A colliding non-vehicle handle no longer aborts a comparison, confirmed by the new unit test's failure message if reverted

---

## Phase 2: i18n de-shadowing and a guard against recurrence

### Overview

Delete the two stub bundles so production i18n resolves in tests, give `MailServiceIT` its test-only
key over a distinct basename, and add a test that prevents any future main/test resource collision.

### Changes Required:

#### 1. Remove the shadowing stubs

**Files**: `src/test/resources/i18n/messages_en.properties`,
`src/test/resources/i18n/messages_pl.properties` (both deleted)

**Intent**: Stop overriding the production message bundles for the whole test JVM. This is what makes
`reports/vehicle-pl.json` render Polish.

**Contract**: `src/test/resources/i18n/` is empty and removed. No main resource changes.

#### 2. Test-only message bundle

**Files**: `src/test/resources/i18n/test-messages_en.properties`,
`src/test/resources/i18n/test-messages_pl.properties` (both new)

**Intent**: Hold `email.test.title` — a key that exists in no production bundle and should not ship
in one — at a classpath location that collides with nothing.

**Contract**: Each file carries exactly `email.test.title`, preserving the values the deleted stubs
used (`test title` for `en`, `Aktywacja konta carcare` for `pl`) so `MailServiceIT`'s assertions keep
their current meaning. `test-messages` must not be a prefix collision with `messages`.

#### 3. Repair MailServiceIT

**File**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`

**Intent**: Give the two tests that need `email.test.title` a message source that layers the test
bundle over the production one, so those tests keep working while every other test in the class
begins asserting against real shipped text.

**Contract**: `setup()` constructs the `MailService` with a `ResourceBundleMessageSource` whose
basenames are `i18n/test-messages` then `i18n/messages`, in that order, instead of the autowired
`MessageSource`. `testSendLocalizedEmailForAllSupportedLanguages` reads
`i18n/test-messages_<locale>.properties` off the classpath rather than `i18n/messages_<locale>.properties`
(`MailServiceIT.java:210-216`). Note the encoding contract: the deleted stubs and the production
bundles are read as UTF-8, so the new source must be configured to match or the Polish assertion
will compare mojibake.

#### 4. Shadowing guard

**File**: `src/test/java/com/kasztelanic/carcare/config/ResourceLayeringTest.java` (new)

**Intent**: Fail the build if any resource path ever exists in both `src/main/resources` and
`src/test/resources` again. F-04 fixed this defect class for `application.yml`, this phase fixes it
for `i18n/`; the guard is what stops the third instance costing another investigation.

**Contract**: A plain unit test walking both source trees and asserting the intersection of their
relative paths is empty. It walks the source directories, not `target/`, so a stale build directory
cannot mask a real collision. Verified today: after the Phase 2 deletions the intersection is empty,
so no exception list is needed — do not add one speculatively.

### Success Criteria:

#### Automated Verification:

- Stale classes cleared first: `./mvnw -o -B clean` (see Critical Implementation Details)
- Mail tests pass: `./mvnw -o -B verify -Dit.test=MailServiceIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — all 9 tests
- Guard test passes: `./mvnw -o -B test -Dtest=ResourceLayeringTest`
- Full suite green: `./mvnw -o -B verify`

#### Manual Verification:

- Reverting either deletion turns `ResourceLayeringTest` red, confirming the guard actually guards
- The Polish mail assertion compares real Polish characters, not mojibake

---

## Phase 3: The golden parity suite

### Overview

Convert the research probe into `ReportParityIT`: ten goldens asserted as exact value-level matches,
plus one explicitly named divergence test for the zero-consumption case.

### Changes Required:

#### 1. Parity integration test

**File**: `src/test/java/com/kasztelanic/carcare/golden/ReportParityIT.java` (new)

**Intent**: Assert every captured report and statistics response against its golden reference, so a
value regression in the report or statistics path fails the build with the golden file named.

**Contract**: Extends `AbstractSessionIT` (`@SpringBootTest` + `@AutoConfigureMockMvc` +
`@Transactional`), seeds via `sessionFixtures.seedGoldenDataset()`, and issues each request with
`SecurityMockMvcRequestPostProcessors.user(...)`. It also carries an `@AfterTransaction` hook
calling `sessionFixtures.evictGoldenOwnerCaches()`, so the langKeys this class caches during its
requests do not outlive the rolled-back transaction (Phase 1 §2). One test per golden, named for it.
The request matrix is fixed by `reference.md:150-175`:

| Golden | Call | As | Status |
| --- | --- | --- | --- |
| `reports/vehicle-en.json` | `GET /api/reports/vehicle/{en-primary}` | admin | 200 |
| `reports/vehicle-pl.json` | `GET /api/reports/vehicle/{pl-primary}` | user | 200 |
| `reports/costs-en.json` | `POST /api/reports/costs`, shared cost request | admin | 200 |
| `reports/vehicle-unowned.json` | `GET /api/reports/vehicle/{pl-primary}` | admin | 404 |
| `stats/consumption-period-en.json` | `POST /api/stats/consumption/per-period`, EN period | admin | 200 |
| `stats/consumption-refuel-en.json` | `POST /api/stats/consumption/per-refuel`, EN period | admin | 200 |
| `stats/consumption-refuel-zero.json` | `POST /api/stats/consumption/per-refuel`, zero period | admin | 200 |
| `stats/mileage-en.json` | `POST /api/stats/mileage`, EN period | admin | 200 |
| `stats/mileage-unowned.json` | `POST /api/stats/mileage`, unowned period | admin | 404 |
| `stats/cost-en.json` | `POST /api/stats/cost`, shared cost request | admin | 200 |

All requests use `2026-03-01` to `2026-03-31`. The shared cost request carries all three vehicle ids
in `en-primary, pl-primary, zero-consumption` order, matching the capture. Note that the request
list order does **not** determine the response order: `StatisticServiceImpl.calculate` passes the
ids into `findAllByIdAndOwnerIsCurrentUser` (`… where vehicle.id in :id`) and iterates the query
result, so `cost-en.json`'s index-exact array — two entries, `en-primary` then `zero-consumption`,
with `pl-primary` filtered out as unowned — is fixed by DB insertion order. That is the accepted
risk recorded in Phase 4 §3, not a property of the request. Reports use `assertWorkbookMatches`,
statistics use `assertJsonMatches`.

#### 2. Divergence test

**File**: same class

**Intent**: Record that the baseline returned 500 for zero-mileage consumption and assert that HEAD
deliberately returns 200, per research D1. The golden file is not modified — re-baselining it would
make it assert HEAD against HEAD and stop it detecting a regression back to the serialization
failure.

**Contract**: A test named for the divergence that loads
`golden/stats/consumption-period-zero.json`, asserts the loaded golden's status is 500 (proving the
captured record still says what this test claims), and asserts the live response is 200 with
`volume 0.0`, `mileage 0`, `averageConsumption 0.0`. Its comment cites
`AverageConsumptionResult.java:19-26` and `4ad88bd`, and states that the `0.0` conflation of unknown
with real zero is deferred to S-07, not resolved here.

### Success Criteria:

#### Automated Verification:

- Suite passes: `./mvnw -o -B verify -Dit.test=ReportParityIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — 11 tests, 0 failures
- Full suite green: `./mvnw -o -B verify` — integration count rises from 177 to 188 or higher

#### Manual Verification:

- Perturbing one computed value in a service produces a failure naming the golden file and the JSON path, not an opaque diff
- The PL test passes when run alone and when run after another test that authenticates first, confirming Phase 1's eviction works

---

## Phase 4: Ordering determinism

### Overview

Replace the report path's reliance on distinct fixture dates with a total comparator, and use Phase
3's golden assertions unchanged as the proof that nothing captured moved. One assertion is added
rather than reused: `/api/events` is the mapper consumer Phase 3 does not reach, so it gets its own
before/after ordering check.

### Changes Required:

#### 1. Ordered event collections

**File**: `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleRichMapper.java`

**Intent**: Replace the five `Collectors.toSet()` calls with an order-preserving set so the mapper
stops introducing `HashSet` iteration order into everything downstream.

**Contract**: All five collectors (`:64-83`, insurance/inspection/routineService/repair/refuel) move
to `LinkedHashSet`. `VehicleRichDto`'s field types stay `Set<...>`, so no consumer signature changes.

Blast radius: the mapper has three consumers, and `EventServiceImpl` is the one Phase 3's suite does
not cover — it feeds `/api/events`, whose ordering can shift on a `ForthcomingEvent.compareTo` tie
(see Key Discoveries). §4 adds the assertion that closes that gap.

#### 2. Total sort comparators

**File**: `src/main/java/com/kasztelanic/carcare/service/reports/VehicleReport.java`

**Intent**: Give each sheet's date sort an explicit secondary key so same-date events order
deterministically instead of inheriting collection iteration order. Without this the suite becomes
flaky the moment any fixture gains a same-date pair in one sheet.

**Contract**: All five sorts (`:126, :165, :198, :235, :265`) compare by
`vehicleEvent.date` then `id` ascending. Every DTO involved carries `Long id`. This cannot move a
captured value: the golden dataset gives every vehicle distinct dates within each sheet, so no
existing sheet has a tie for the new key to break — which is precisely what Phase 3's suite verifies.

#### 3. Record the accepted risk

**File**: `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java`

**Intent**: Leave `findAllByIdAndOwnerIsCurrentUser` unordered per research D3, but note why, so the
next reader does not mistake it for an oversight.

**Contract**: A comment on the query stating that result order feeds `cost-en.json`'s index-exact
array comparison, that it currently matches by insertion order on both H2 and MariaDB rather than by
contract, and that `order by vehicle.id` is the fix if that assertion ever flakes. No query change.

#### 4. Pin the `/api/events` ordering

**File**: `src/test/java/com/kasztelanic/carcare/golden/ReportParityIT.java`

**Intent**: Cover the one mapper consumer Phase 3's goldens do not reach. §1 changes the collection
order `EventServiceImpl` iterates, and `ForthcomingEvent.compareTo` ties on equal `dateThru` when a
`mileageThru` is zero, so `/api/events` ordering is genuinely reachable by this change. Without an
assertion, Phase 4's inertness claim rests on inspection alone for this path.

**Contract**: One added test posting the golden vehicles' periods to `/api/events` as `admin` and
asserting the full response sequence by `(eventType, dateThru)`. The fixture already supplies the
tie shape worth pinning — `inspection:en-reminder-plus-three`,
`routine-service:en-reminder-plus-three`, and `insurance:en-reminder-plus-three` all fall due on
`2026-04-18` for `vehicle:en-primary`. Capture the expected sequence by running the test before §1
and §2 land, so it records pre-change behaviour and then proves §1 did not move it. No golden file
is created — this assertion lives in the test source.

### Success Criteria:

#### Automated Verification:

- Parity suite unchanged and still green: `./mvnw -o -B verify -Dit.test=ReportParityIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — 11 golden tests, 0 failures, no golden file edited
- `/api/events` ordering test recorded before §1/§2 and still green after: same command, 12 tests total
- `git diff --stat src/test/resources/golden/` is empty across this phase
- Full suite green: `./mvnw -o -B verify`

#### Manual Verification:

- Temporarily adding a same-date event pair to one sheet in the fixture produces a stable report across repeated runs, then revert
- The `VehicleRepository` comment is specific enough that someone hitting a `cost-en.json` flake finds the cause from it

---

## Testing Strategy

### Unit Tests:

- `GoldenReferenceTest`: cross-table id collisions outside `vehicle:` are accepted; a genuine
  `vehicle:` collision is still rejected
- `ResourceLayeringTest`: no path exists in both main and test resource trees

### Integration Tests:

- `ReportParityIT`: ten value-level golden matches across both XLSX reports and all four statistics
  endpoints, in both locales, including both 404 paths — plus the named zero-consumption divergence,
  and (from Phase 4) an `/api/events` ordering assertion covering the third mapper consumer
- `GoldenDatasetMirrorIT`: the real fixture handle map is accepted by `GoldenReference`
- `MailServiceIT`: unchanged test count, now asserting production bundle text everywhere except the
  two tests using the test-only key

### Manual Testing Steps:

1. `./mvnw -o -B clean verify` from a clean `target/` and confirm the full suite is green
2. Revert one i18n stub deletion and confirm `ResourceLayeringTest` fails
3. Change one cost value in `CostCalculatorImpl` and confirm `ReportParityIT` names the golden and path
4. Run `ReportParityIT`'s PL test in isolation and again after a test that authenticates first

## Performance Considerations

`ReportParityIT` adds eleven full-context integration tests that each seed the golden dataset inside a
rolled-back transaction. This is the same cost profile as `GoldenDatasetMirrorIT` and
`OwnerIsolationIT`, and reuses the existing `AbstractSessionIT` context rather than creating a new
one, so no additional Spring context is started.

## Migration Notes

No database, schema, or Liquibase changes. Phase 4 is the only production change. The
`VehicleReport` tiebreaker is inert by construction — it only fires on ties the current fixture does
not contain. The `VehicleRichMapper` change reaches one serialized surface, `/api/events`, whose
ordering Phase 4 §4 pins with a before/after assertion rather than assuming inertness.

## References

- Related research: `context/changes/report-parity/research.md` (findings 1-6, decisions D1-D4)
- Capture provenance and request matrix: `context/changes/golden-baseline-capture/reference.md:150-175`
- Expected divergences: `context/changes/golden-baseline-capture/reference.md:338-344`
- Similar full-context IT: `src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java:186-233`
- Prior instance of the shadowing defect class: `context/archive/2026-08-25-test-context-restored/`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Harness repair — handle-map scoping and fixture cache eviction

#### Automated

- [x] 1.1 Compiles: `./mvnw -o -B test-compile` — cbd11f9
- [x] 1.2 Golden unit tests pass: `./mvnw -o -B test -Dtest=GoldenReferenceTest` — cbd11f9
- [x] 1.3 Fixture IT passes: `./mvnw -o -B verify -Dit.test=GoldenDatasetMirrorIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — cbd11f9
- [x] 1.4 Full suite still green with no test count regression — cbd11f9

#### Manual

- [x] 1.5 The `vehicle:` namespace constant reads as a documented contract, not a magic string — cbd11f9
- [x] 1.6 A colliding non-vehicle handle no longer aborts a comparison — cbd11f9

### Phase 2: i18n de-shadowing and a guard against recurrence

#### Automated

- [x] 2.1 Stale classes cleared: `./mvnw -o -B clean` — 0e50aa7
- [x] 2.2 Mail tests pass: `./mvnw -o -B verify -Dit.test=MailServiceIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — all 9 — 0e50aa7
- [x] 2.3 Guard test passes: `./mvnw -o -B test -Dtest=ResourceLayeringTest` — 0e50aa7
- [x] 2.4 Full suite green: `./mvnw -o -B verify` — 0e50aa7

#### Manual

- [x] 2.5 Reverting either deletion turns `ResourceLayeringTest` red — 0e50aa7
- [x] 2.6 The Polish mail assertion compares real Polish characters, not mojibake — 0e50aa7

### Phase 3: The golden parity suite

#### Automated

- [x] 3.1 Suite passes: `./mvnw -o -B verify -Dit.test=ReportParityIT -DskipUTs -Dsurefire.failIfNoSpecifiedTests=false` — 11 tests — a47d940
- [x] 3.2 Full suite green, integration count rises from 177 to 188 or higher — a47d940

#### Manual

- [x] 3.3 Perturbing a computed value produces a failure naming the golden file and JSON path — a47d940
- [x] 3.4 The PL test passes alone and after a test that authenticates first — a47d940

### Phase 4: Ordering determinism

#### Automated

- [x] 4.1 Parity suite unchanged and still green — 11 golden tests, 0 failures
- [x] 4.2 `/api/events` ordering test recorded before §1/§2 and still green after — 12 tests total
- [x] 4.3 `git diff --stat src/test/resources/golden/` is empty across this phase
- [x] 4.4 Full suite green: `./mvnw -o -B verify`

#### Manual

- [x] 4.5 A temporary same-date event pair produces a stable report across repeated runs
- [x] 4.6 The `VehicleRepository` comment is specific enough to diagnose a `cost-en.json` flake
