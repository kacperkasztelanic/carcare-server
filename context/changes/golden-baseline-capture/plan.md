# Golden Baseline Capture Implementation Plan

## Overview

Capture the report values, statistics figures, and reminder selections that commit `6e19b96` —
the newest commit that builds and runs — produces from a fixed dataset, and commit them as a
machine-readable reference plus a human-readable provenance record. Deliver alongside them the
XLSX→value extractor and comparison function that `report-parity` (S-03) and
`english-reminder-fix` (S-04) will use to assert parity at **value level, not byte level**.

This is a contract-capture change. **No `src/main` file is edited.** Its structural model is
`context/archive/resolvable-build/error-contract.md`: write the contract down, with the exact
command that produced it, before the thing it guards changes.

## Current State Analysis

- **No reference exists.** `src/test/resources/` holds only `config/`, `i18n/`, `logback.xml`,
  and `templates/mail/testEmail.html`. There is no `golden/`, no fixture resource layer, no
  snapshot or approval-testing library on the classpath.
- **`6e19b96` builds offline.** Verified: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem
  ./mvnw -B -DskipTests clean package` in a worktree at `6e19b96` produces
  `target/carcare-1.3.5.war` (executable, 98 MB) with no network access. The client artifact
  `1.2.3` and the full Spring Boot 2.6.6 tree are in `~/.m2`. The versionless-dependency failure
  that breaks HEAD does not occur — the `jhipster-dependencies` 7.8.1 BOM versions all eleven.
- **`6e19b96` context boot is asserted, not verified.** `AGENTS.md` states it "builds and runs",
  but no live boot has been exercised in this workstream. It requires MariaDB — no `h2` or
  `dev-h2` Spring profile exists at that commit, so running on H2 would be a source change.
- **Liquibase `dev` at `6e19b96` seeds only users.** `admin`/`admin` (both roles) and
  `user`/`user` (`ROLE_USER`) come from `data/user.csv`. `fuel_types`, `insurance_types`, and
  `reminder_advances` are created empty — there is no `loadData` for them.
- **No deterministic event generator exists.** `GET /api/test-data/random-vehicles/{n}` uses
  unseeded `Collections.shuffle` / `new Random()` and creates only `Vehicle` rows with **zero
  events**, so reports and statistics over it would be empty. The two lookup-population endpoints
  (`populate-fuel-types`, `populate-insurance-types`) *are* deterministic (static classpath JSON).
- **The test layer S-03/S-04 will extend already exists**, built by `session-parity`:
  `fixtures/SessionFixtures.java` (`@Profile("test")` `ApplicationRunner`, seeds lookups, exposes
  per-event builders), `web/rest/AbstractSessionIT.java` (`@SpringBootTest(classes =
  CarcareApp.class)` + `@AutoConfigureMockMvc` + `@Transactional`), and per-resource
  `*ResourceIT` classes. `OwnerIsolationIT` already parses the cost-report XLSX with POI.
  **One shared Spring context is a hard constraint** — no `@MockBean`, no `@DirtiesContext`.
- **`SessionFixtures`' builders are fixed-value.** Only `refuelFor` has an explicit
  `(vehicle, mileage, date)` overload; `repairFor`, `routineServiceFor`, `inspectionFor`, and
  `insuranceFor` take only a `Vehicle` and hard-code every field. A golden fixture cannot be
  mirrored through them as they stand.
- **`session-parity` deferred two computed-value judgements to S-03 against this baseline:**
  zero-mileage consumption now returning `0.0` rather than failing, and the duplicate-`vehicleId`
  merge tiebreak in `POST /api/events`. `reviews/impl-review.md:135-143` (F5) records the
  `$.averageConsumption` assertion being relaxed from `== 0.0` to `.exists()` precisely so S-03
  can re-judge it here.
- **HEAD's `src/main` is no longer the baseline's.** Commit `4ad88bd`
  ("fix(session-parity): fix four pre-existing 500s") changed five files after `6e19b96`:
  `AverageConsumptionResult` (the zero-mileage guard), `InsuranceTypeDto` (delegating
  `@JsonCreator`), `EventServiceImpl` (the duplicate-`vehicleId` merge
  `(first, ignored) -> first`), and `FuelTypeMapper` / `InsuranceTypeMapper` /
  `ExceptionTranslator` (`InvalidLookupTypeException`, 400 instead of 500). Only the first two are
  the deferred judgements above; all five are deliberate divergences from the baseline, so a
  HEAD-vs-reference value difference has **three** possible causes, not two.
  `AGENTS.md`'s "`src/main` is byte-identical between `3e91ed4` and HEAD" claim predates this
  commit and is stale — this change refreshes it.

## Desired End State

`context/changes/golden-baseline-capture/` contains a `reference.md` recording the exact capture
command, dataset, commit, profile, clock, timezone, and locale for every captured surface.
`src/test/resources/golden/` contains machine-readable JSON reference files — one per captured
surface — with numbers as fixed-precision decimal strings and every entity identity expressed as
a symbolic fixture handle rather than a raw id, so the files are comparable across the two
runtimes. `src/test/java/.../golden/` contains a workbook-to-values extractor, a reference
loader, and a handle-resolving comparison function. `SessionFixtures` carries explicit-value
overloads sufficient to re-seed the golden dataset under H2 and returns the handle→id map that
binds the reference to live output, and one integration test proves that mirroring is faithful.

Verified by: `./mvnw verify` green at HEAD; the mirroring test passing; and `reference.md`
being sufficient for a reader with no prior context to re-run the capture and get the same files.

### Key Discoveries:

- The `(Set<LocalDate> dates, LocalDate now)` methods on `ReminderService.java:8-14` are the
  deterministic seam — they bypass both `LocalDate.now()` (`ReminderServiceImpl.java:45`, no
  injectable `Clock`) and the `reminder_advances` table.
- Reminder selection has **no query surface**. It is observable only by capturing
  `MailService.send*ReminderEmail(owner, vehicle, event, diff)` invocations.
- `GET /api/reports/vehicle/{id}` returns `Content-Type: application/vnd.ms-excel` — the legacy
  `.xls` MIME on a genuine `.xlsx` body (`ReportResource.java:53-61`). A pre-existing quirk that
  must be asserted so it cannot silently change.
- The refuel unit-price cell computes `costInCents * 10.0 / volume` (`VehicleReport.java` ~279),
  producing `Infinity` when `volume == 0`.
- `MileageServiceImpl.java:22-37` collects to a `TreeMap` with merge `(v1,v2)->v2` — the
  **highest mileage on a shared date wins**, and that ordering depends on the preceding
  mileage-ascending sort.
- The only explicit rounding anywhere is `AverageConsumptionResult.java:19-26` —
  `BigDecimal.setScale(1, HALF_UP)`.
- Three queries have no `ORDER BY`: `VehicleRepository.findAllByIdAndOwnerIsCurrentUser`
  (`:22-23`, affecting `/api/reports/costs` row order and `/api/stats/cost` list order) and the
  reminder `findBy…In` queries.
- The English reminder fault is wrong positional argument indices in **one key** —
  `email.service.text1` at `messages_en.properties:35`, the only line differing from the base
  bundle, and already present at `6e19b96`.

## What We're NOT Doing

- **Not editing any `src/main` file.** Not fixing the `vnd.ms-excel` content type, the missing
  `=` in `reports.vehicle.main.certificate`, the broken `DELETE /api/reminder-advance/{type}`
  path-variable mismatch, the missing `ORDER BY` clauses, or `messages_en.properties:35`. Each is
  recorded as captured behaviour; the English fix belongs to S-04.
- **Not writing parity assertions.** F-02 delivers the reference and the harness; S-03 and S-04
  wire them into assertions against HEAD.
- **Not capturing at HEAD.** HEAD cannot boot a `dev` context, so a HEAD-side capture would have
  to run under H2/test — a different engine, which would muddy attribution.
- **Not building the restored-production-data parallel run.** That reuses this change's extractor
  and comparison function later; it is not in scope here, and it carries a prod-dump dependency
  and PII handling that this foundation item does not need.
- **Not committing anything to the `6e19b96` worktree.** All capture-side code there is
  throwaway; only its output is committed, on `refactor`.
- **Not adjudicating the deferred decisions.** F-02 captures what `6e19b96` does for
  zero-mileage consumption and the duplicate-`vehicleId` tiebreak; S-03 decides what HEAD should do.

## Implementation Approach

Five phases, each with a distinct failure mode.

Phase 1 settles the single ASSUMED fact — that `6e19b96` boots — before any authoring effort is
sunk. Phase 2 authors the fixture as SQL, designed backwards from the branch inventory so that
every code path in the report, statistics, and reminder surfaces is exercised. Phases 3 and 4
capture, using different mechanisms because the surfaces differ: reports and statistics are
driven over real HTTP against the booted WAR (capturing headers and status alongside bodies),
while reminder selection has no HTTP surface for its *selection* and is captured by a
worktree-local runner over the typed seam, supplemented by one full `GET /api/reminder/send`
run so the advance-set derivation is covered too. Phase 5 returns to HEAD and builds the
consumption side: the extractor, the loader, the comparison function, and the `SessionFixtures`
overloads that let the same rows exist under H2.

The dataset is the load-bearing decision. It has two consumers with different runtimes —
MariaDB at `6e19b96` for capture, H2 at HEAD for S-03/S-04 — so it is authored once as SQL with
explicit primary keys and mirrored as `SessionFixtures` calls, with a test proving the two agree.

## Critical Implementation Details

**Timing & lifecycle.** `SessionFixtures` is an `ApplicationRunner` that seeds lookups at context
start and is shared across every IT in one Spring context. The golden fixture must **not** be
seeded eagerly in `run(...)` — that would leak golden rows into every existing `*ResourceIT` and
change their query results. It must be an explicitly-invoked method that a test calls.

**State sequencing.** The dataset's dates are authored relative to one fixed reference date so
that reminder due-dates land exactly on and at ±1 day from a configured advance. Choose a
reference date away from month and year boundaries and never 29 February, because
`now.plusDays(N)` crossing a boundary is a source of off-by-one confusion when reading the
reference by hand.

**Debug & observability.** `MailService`'s reminder methods are `@Async`. A real bean behind a
proxy may not have completed when an `ArgumentCaptor` is read. The capture runner must use a
mock or spy `MailService` (no async proxy), or a synchronous executor.

---

## Phase 1: Boot spike at `6e19b96`

### Overview

Prove that `6e19b96` boots a `dev` context against a disposable MariaDB and authenticates, before
any fixture or capture work is written. If this fails, the whole approach is reconsidered here at
minimum cost.

### Changes Required:

#### 1. Disposable capture environment

**File**: none — procedure recorded in `context/changes/golden-baseline-capture/reference.md`
(created in this phase, extended in Phases 3-4)

**Intent**: Stand up a throwaway `git worktree` at `6e19b96` outside the repo working tree, build
its WAR offline with Temurin 17.0.20, start an isolated MariaDB container with the `carcare`
database, and boot the WAR under the `dev` profile with the JVM timezone pinned. Record every
command verbatim so the capture is reproducible by someone who was not present.

**Contract**: The worktree lives under the scratchpad or `/private/tmp`, never inside
`/Users/kacper/Dev/carcare/server`, and is removed at the end of Phase 4. The `refactor` branch is
never checked out away from HEAD and the working tree stays clean throughout. MariaDB runs on a
non-default host port so it cannot collide with anything the user has running. The capture JVM
runs with `-Duser.timezone=UTC`, matching the `argLine` HEAD's `pom.xml` already pins for tests,
and the golden reference is documented as a UTC artefact. The date-pinning mechanism Phase 4's
full-path run needs (`libfaketime` or a container date) is established and recorded here, and
its effect on Liquibase and JWT issuance is confirmed during the boot below.

#### 2. Boot and authentication verification

**File**: none — result recorded in `reference.md`

**Intent**: Confirm Liquibase applies the `dev` context cleanly, the application context starts,
and both seeded accounts authenticate. Confirm the three lookup tables are empty after migration,
since that is what makes fixture-side lookup seeding necessary.

**Contract**: `POST /api/authenticate` with `admin`/`admin` and with `user`/`user` each return a
JWT. `SELECT COUNT(*)` on `fuel_types`, `insurance_types`, and `reminder_advances` each return 0.
Both facts are recorded in `reference.md` as VERIFIED, replacing the ASSUMED entries carried over
from research.

### Success Criteria:

#### Automated Verification:

- The `6e19b96` worktree builds offline: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw -B -DskipTests clean package` exits 0
- MariaDB container is reachable and the `carcare` schema exists
- The WAR boots under the `dev` profile and `GET /management/health` returns `UP`
- `POST /api/authenticate` returns a token for both `admin` and `user`
- The repository working tree at `/Users/kacper/Dev/carcare/server` is unchanged: `git status --porcelain` reports no change under `src/` or `pom.xml`, and `git worktree list` shows only the intended capture worktree

#### Manual Verification:

- `reference.md` contains every command verbatim, in order, such that a reader can re-run the boot without reconstructing anything
- The empty-lookup-table finding is recorded, since Phase 2 depends on it

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual review was successful before
proceeding to the next phase.

---

## Phase 2: Author the golden fixture

### Overview

Author the deterministic dataset as SQL, designed backwards from the branch inventory so every
code path the capture must exercise has data behind it.

### Changes Required:

#### 1. Golden dataset SQL

**File**: `src/test/resources/golden/golden-dataset.sql`

**Intent**: Define the complete golden dataset — lookup rows, reminder advances, two owner
`langKey` updates, vehicles, and events of all five types — with explicit primary keys so that
every `vehicleId` and event id appearing in a captured reference is stable and readable.

**Contract**: Owners are the Liquibase-seeded `admin` (id 3) and `user` (id 4); the fixture
`UPDATE`s their `lang_key` to `en` and `pl` respectively and creates no users. All ids are
explicit and allocated from a high, clearly-reserved range so they cannot collide with rows any
other test creates. Dates are expressed relative to one documented reference date. The file is
idempotent-safe only in the sense that it is loaded into a freshly migrated database; it does not
attempt upserts.

**Every row also carries a stable symbolic handle** — `vehicle:en-primary`,
`refuel:zero-volume`, `routine-service:null-next-date`, and so on — declared alongside its
explicit id in the SQL comments and in the branch inventory. The handle, not the id, is the
identity the golden references use (see Phase 3 §2). Capture-side ids come from this file;
HEAD-side rows get JPA-generated ids off a shared H2 sequence whose values depend on how many
rows earlier ITs created, so raw ids can never agree between the two runtimes and are not a
usable comparison key.

The dataset must contain, at minimum:

- **Reports / statistics branches** — a refuel with `volume == 0` (the `Infinity` unit-price
  cell); a routine service with `nextByDate` null and one with it set (the created-vs-not cell);
  a routine service with `nextByMileage` null; a vehicle with ≥3 in-range refuels (a real
  consumption figure after `skip(1)`); a vehicle with exactly 1 in-range refuel (empty
  per-refuel list, zero-mileage per-period) — **this vehicle is captured only in its own
  single-vehicle request**, because at `6e19b96` `AverageConsumptionResult.getAverageConsumption`
  has no `mileage == 0` guard and `BigDecimal.valueOf(Infinity)` throws during serialization,
  failing the whole response and taking every other vehicle's figure with it; two events of
  different types sharing a date with
  differing mileages (the `TreeMap` merge tiebreak); at least one vehicle with zero events of
  some type (empty sheet with header only); events straddling a date-range boundary so the
  inclusive filter is proven at both ends.
- **Locale dimension** — at least one vehicle owned by the `en` owner and one by the `pl` owner,
  so both report locales can be captured.
- **Ownership dimension** — vehicles owned by both users, so a captured cost report proves no
  foreign vehicle leaks in.
- **Reminder branches** — insurance `validThru`, inspection `validThru`, and routine-service
  `nextByDate` rows landing exactly on `reference_date + advance` for at least two different
  configured advances; rows at `+1` and `-1` day from an advance (must not fire); a
  routine-service row with `nextByDate` null (must not fire); matching rows across both owners.

#### 2. Branch inventory record

**File**: `context/changes/golden-baseline-capture/reference.md`

**Intent**: Record, as a table, each branch the fixture is designed to exercise and which rows
exercise it, so a later reader can tell whether a newly-discovered branch is covered.

**Contract**: One row per branch, naming the code location and the fixture handles (with their
capture-side ids) that cover it. This is the artefact that makes "full edge-case matrix"
auditable rather than asserted, and it doubles as the handle→id map Phase 3 and Phase 4 write
their references against.

### Success Criteria:

#### Automated Verification:

- `golden-dataset.sql` loads into the freshly migrated `6e19b96` MariaDB without error
- Row counts per table after load match the counts stated in `reference.md`
- No id in the fixture collides with a Liquibase-seeded id

#### Manual Verification:

- Every branch listed in the Phase 2 contract above has at least one covering row in the branch
  inventory table
- The reference date is away from month/year boundaries and is not 29 February
- Reading `reference.md`'s inventory alongside the SQL is enough to understand why each row exists
- Every fixture row has a unique symbolic handle, and the inventory's handle→id map covers all of
  them — this is what Phases 3-5 compare on instead of raw entity ids

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual review was successful before
proceeding to the next phase.

---

## Phase 3: Capture reports and statistics

### Overview

Drive all six endpoints against the booted `6e19b96` instance over real HTTP, and reduce each
response to a stable, machine-readable reference.

### Changes Required:

#### 1. Capture driver

**File**: throwaway script in the scratchpad — not committed

**Intent**: Authenticate as each owner, call every report and statistics endpoint with documented
request bodies, and write both the response metadata and the reduced body to the golden files.

**Contract**: Captures `GET /api/reports/vehicle/{id}` for at least one vehicle per owner (both
locales), `POST /api/reports/costs`, and all four of
`POST /api/stats/{consumption/per-period,consumption/per-refuel,mileage,cost}`. The zero-mileage
vehicle is excluded from the main consumption vehicle lists and driven in its own single-vehicle
request against both consumption endpoints, so its failure mode is captured in isolation rather
than destroying the healthy figures. For each call it
records HTTP status, `Content-Type`, `Content-Disposition`, `Cache-Control`, and `X-Total-Count`
where present, alongside the body. Only headers the controller sets explicitly are captured for
comparison; container-supplied headers (`Content-Length`, `Transfer-Encoding`, `Date`) are
excluded, because HEAD-side consumption runs through MockMvc, which does not produce them.
The 404 paths are captured too: an unowned vehicle id against
`GET /api/reports/vehicle/{id}` and against `POST /api/stats/mileage`.

#### 2. Golden reference files

**File**: `src/test/resources/golden/reports/*.json`, `src/test/resources/golden/stats/*.json`

**Intent**: Store one reference file per captured call, holding the response metadata and the
reduced body, in a form that is stable across JVM and library versions.

**Contract**: XLSX bodies are reduced to a per-sheet ordered list of rows, each row a list of
cell entries carrying the cell's type, its value, and its data-format string; sheets appear in
workbook order. Row sorting is **per-sheet, not global**: only the cost report's per-vehicle rows
are sorted by a stable composite key, because `generateCostReport` iterates the unordered
`VehicleRepository.findAllByIdAndOwnerIsCurrentUser` (`:22-23`). Every other sheet is stored in
natural order — the vehicle report is single-vehicle (`findByIdAndOwnerIsCurrentUser`) and its
sheets are a laid-out form, so sorting would discard both the layout and any genuine row-order
regression, such as Hibernate 6 changing the event collections' iteration order. `reference.md`
records which sheets are sorted and why. All numbers — in both XLSX and JSON references — are stored as fixed-precision
decimal strings: money at 2 decimal places, other doubles at 6. `Infinity` and `NaN` are stored as
explicit string sentinels rather than numbers. JSON bodies retain their literal key order and
literal date strings, because a Jackson date-format change is a target regression, not noise.

**Entity ids are normalised to handles before storage.** Every statistics response embeds
`periodVehicle.vehicleId` (`PeriodVehicle.java:12`, reached through `CostResult`,
`MileageResult`, and `AverageConsumptionResult`), and the requests are keyed by vehicle id too.
The capture driver rewrites each such id to its Phase 2 handle using the branch inventory's
handle→id map, so no golden file contains a raw entity id. Requests are recorded in
`reference.md` by handle as well, and the driver resolves them to capture-side ids at call time.

#### 3. Capture record

**File**: `context/changes/golden-baseline-capture/reference.md`

**Intent**: Extend the record with the exact request bodies, the vehicle ids and owners used per
call, and the resulting file names, so each golden file's provenance is traceable.

**Contract**: Every file under `src/test/resources/golden/` is named in `reference.md` with the
call that produced it. The `application/vnd.ms-excel` content type and the
`reports.vehicle.main.certificate` parse quirk are each called out explicitly as captured
pre-existing behaviour, not defects to fix.

### Success Criteria:

#### Automated Verification:

- Every endpoint call returns the status recorded in `reference.md` — including any 5xx, which is
  captured behaviour, not a capture failure
- A golden file exists for every call listed in `reference.md`
- Re-running the capture against a freshly reloaded database produces byte-identical golden files
- Every number in every golden file parses as a fixed-precision decimal or is a documented sentinel
- No golden file contains a raw entity id: every vehicle and event identity is a Phase 2 handle

#### Manual Verification:

- Both report locales are captured and their sheet names differ as expected
- The zero-volume refuel's unit-price cell shows the `Infinity` sentinel
- The zero-mileage consumption case and the same-date mileage merge case are both visible in the
  captured values, so S-03 has something concrete to adjudicate against
- Spot-checking three captured figures by hand against the fixture rows confirms the arithmetic

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 4: Capture reminder selection

### Overview

Capture which reminders `6e19b96` selects, for which owners, on a fixed reference date — the
proof S-04 needs that its one-line bundle fix changed only rendering.

### Changes Required:

#### 1. Selection capture runner

**File**: throwaway test class in the `6e19b96` worktree — not committed

**Intent**: Invoke the three typed `ReminderService` methods with an explicit fixed `now` and an
explicit `dates` set, capturing every `MailService.send*ReminderEmail(owner, vehicle, event,
diff)` invocation; then separately invoke `sendReminders()` (or `GET /api/reminder/send`) once so
the `reminder_advances` → `plusDays` → `Set` derivation is exercised too.

**Contract**: `MailService` is a mock or spy so the `@Async` proxy cannot swallow or delay
invocations. The captured set is written under a total sort by `(eventType, ownerLogin,
vehicleHandle, eventHandle)` — handles, not raw ids, so the order is identical under both
runtimes — neutralising the missing `ORDER BY` on the `findBy…In` queries. The fixed
`now` is the reference date from Phase 2 and the `dates` set is derived from it and the
configured advances, both recorded verbatim.

`sendReminders()` reads `LocalDate.now()` (`ReminderServiceImpl.java:45`) and has no injectable
`Clock`, while the fixture's dates are absolute because the same rows feed the Phase 3 XLSX
cells. The full-path run therefore requires the JVM's date to be pinned to the reference date —
via `libfaketime` on the WAR process, or by setting the capture container's date — otherwise it
selects nothing and cannot be reproduced on any later day. The pinning mechanism and its exact
invocation are recorded in `reference.md` as part of the capture procedure, and Phase 1 confirms
the pinned date does not disturb Liquibase or JWT issuance during the same boot.

#### 2. Reminder golden reference

**File**: `src/test/resources/golden/reminders/*.json`

**Intent**: Store the selected set from both capture paths, so S-04 can assert that HEAD selects
exactly the same reminders.

**Contract**: One entry per invocation, carrying event type, owner login, owner `langKey`,
vehicle handle, event handle, the due date, and the `diff` day count — no raw entity ids. Two
files: one for the typed-seam
capture and one for the full-path capture. Both are sorted by the total order above. The two are
expected to agree; any disagreement is recorded rather than reconciled, because it would mean the
advance-derivation step does something the typed seam does not.

#### 3. Capture record and worktree teardown

**File**: `context/changes/golden-baseline-capture/reference.md`

**Intent**: Record the reminder capture mechanism, the fixed `now`, the `dates` set, the
configured advances, and the resulting files; then remove the `6e19b96` worktree and the MariaDB
container.

**Contract**: `reference.md` is self-sufficient for re-running the entire capture from scratch —
worktree creation, build, database, fixture load, all six endpoint calls, and both reminder
paths. After teardown, `git worktree list` shows no stray worktree and the repository working
tree is clean apart from the intended new files.

### Success Criteria:

#### Automated Verification:

- Both reminder golden files exist and are sorted by the documented total order
- The rows at `+1` and `-1` day from an advance appear in **neither** file
- The null-`nextByDate` routine service appears in neither file
- Rows landing exactly on an advance appear in both files, for both owners
- The typed-seam capture and the full-path capture agree entry-for-entry
- Re-running the capture on a different calendar day, with the clock pinned as `reference.md`
  documents, produces byte-identical files
- `git worktree list` shows no leftover worktree; no capture container is left running

#### Manual Verification:

- The `diff` values in the captured set match the configured advances, confirming the fixture
  dates are placed as intended
- `reference.md` read end to end is sufficient to reproduce every golden file without consulting
  this plan or the research doc

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 5: Build the consumption harness at HEAD

### Overview

Give S-03 and S-04 what they need to use the reference: a workbook-to-values extractor, a
reference loader, a comparison function, and a way to re-create the golden dataset under H2.

### Changes Required:

#### 1. Workbook value extractor

**File**: `src/test/java/com/kasztelanic/carcare/golden/WorkbookValues.java`

**Intent**: Reduce an XLSX byte array to the same structure Phase 3 stored, so a live response
and a golden file can be compared directly.

**Contract**: Reads with POI, never through `DataFormatter`; takes the raw
`getNumericCellValue()` / `getStringCellValue()` and the style's data-format string. Produces
sheets in workbook order, applies Phase 3's per-sheet sort policy — sorting the cost report's
per-vehicle rows by the same composite key, leaving every other sheet in natural order — rounds
numbers to the same fixed precision, and emits the same `Infinity`/`NaN` sentinels. The sort
policy, its key, and the rounding rules are defined once here and referenced from
`reference.md`, not restated.

#### 2. Reference loader and comparison

**File**: `src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java`

**Intent**: Load a named golden file from the classpath and compare it against captured live
values, producing a failure message that names the first differing sheet, row, and cell — or JSON
path — rather than dumping two documents.

**Contract**: Comparison is exact on the fixed-precision string form, not epsilon-based on
doubles. Comparison is **handle-based, never id-based**: the caller passes the handle→id map that
golden seeding returned, and `GoldenReference` maps every entity id in the live response back to
its handle before comparing, so a live `vehicleId` of 412 and a captured handle
`vehicle:en-primary` match. Metadata is compared alongside the body, scoped to the status plus the headers
`ReportResource.prepareResponse` sets explicitly (`Content-Type`, `Content-Disposition`,
`Cache-Control`) and `X-Total-Count` where present. Container-supplied headers are outside the
comparison: the capture runs over real HTTP against a booted WAR while HEAD-side consumption runs
through MockMvc, which emits no `Content-Length`, `Transfer-Encoding`, or `Date`. `reference.md`
states that exclusion and this reason. The API is a small surface intended for direct use from
`*ResourceIT` classes extending `AbstractSessionIT`.

#### 3. `SessionFixtures` explicit-value overloads

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`

**Intent**: Add overloads that accept the values the golden dataset needs, so the same rows can
be created under H2, and add a single explicitly-invoked method that seeds the whole golden
dataset.

**Contract**: `repairFor`, `routineServiceFor`, `inspectionFor`, and `insuranceFor` gain
overloads taking the fields the fixture varies — mileage, date, cost, and the type-specific
fields including nullable `nextByDate` and `nextByMileage` — and `refuelFor` gains one taking
volume and cost. The existing single-argument convenience methods keep their current behaviour
and values unchanged, so no existing IT is affected. Golden seeding is a separate method that
`run(...)` does **not** call; it is invoked only by tests that want the golden dataset. It
**returns the handle→id map** for the rows it just created — the runtime counterpart of the
capture-side map in the branch inventory — which is what a test hands to `GoldenReference` and
what it uses to build requests keyed by vehicle id. It
carries **no idempotence guard**: `AbstractSessionIT` is `@Transactional`, so every row it writes
rolls back at the end of each test, and a boolean flag on the context-wide singleton would
outlive the rows it claims to track — leaving the second golden test with an empty database and
a guard reporting "already seeded". Each test re-seeds inside its own transaction.

#### 4. Mirroring verification

**File**: `src/test/java/com/kasztelanic/carcare/golden/GoldenDatasetMirrorIT.java`

**Intent**: Prove that seeding the golden dataset through `SessionFixtures` under H2 produces
the same logical rows the SQL fixture produces, so S-03 and S-04 can rule out the dataset as a
cause. A remaining value difference then means either a migration difference or one of the
intentional post-baseline divergences `reference.md` lists — never a dataset difference.

**Contract**: Extends `AbstractSessionIT`, seeds the golden dataset, and asserts per-table row
counts and the field values that the captured references depend on — dates, mileages, costs,
volumes, `validThru`, `nextByDate`, `nextByMileage`, owner, and `langKey` — addressing each row
by its handle. It also asserts that the returned handle→id map covers every handle the branch
inventory declares, since a missing entry would make a golden comparison silently unresolvable.
It compares against
the fixture definition, **not** against the captured output values; comparing output is S-03's
job and would be a scope breach here. No `@MockBean`, no `@DirtiesContext`.

#### 5. Reference document completion

**File**: `context/changes/golden-baseline-capture/reference.md`

**Intent**: Close the record with the harness entry points, so a reader arriving from S-03 or
S-04 knows what to call.

**Contract**: Names `WorkbookValues`, `GoldenReference`, the golden seeding method, and the
mirroring test, and states plainly that F-02 delivers the reference and the harness while parity
assertions belong to S-03 and S-04.

It also carries an **Expected divergences at HEAD** section listing all five `src/main` files
`4ad88bd` changed after the baseline, with the captured surface each one affects, so S-03 and
S-04 do not chase a deliberate fix as a migration regression. `AGENTS.md`'s "byte-identical
between `3e91ed4` and HEAD" statement is corrected in the same phase to name `4ad88bd` as the
first post-`3e91ed4` commit to touch `src/main`.

### Success Criteria:

#### Automated Verification:

- `./mvnw test` passes at HEAD
- `./mvnw verify` passes at HEAD with no pre-existing test regressed
- `GoldenDatasetMirrorIT` passes
- `WorkbookValues` applied to a golden XLSX capture reproduces the stored structure exactly
- The existing single-argument `SessionFixtures` builders still produce their previous values
- Golden seeding returns a handle→id map covering every handle the branch inventory declares, and
  `GoldenReference` resolves a live response's entity ids through it before comparing

#### Manual Verification:

- A comparison failure message is specific enough to locate the differing cell without opening
  the workbook by hand
- `reference.md` read cold gives a reader enough to start S-03 without re-reading this plan
- The golden seeding method is confirmed not to run for existing ITs
- `reference.md`'s expected-divergences section names all five `4ad88bd` files with the surface
  each affects, and `AGENTS.md`'s byte-identical claim is corrected

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- `WorkbookValues` rounding and sentinel handling, including the `Infinity` unit-price cell and a
  cell whose data-format string is the shared `"0.00"` style
- `GoldenReference` comparison on a deliberately-mismatched pair, asserting the failure message
  names the differing location

### Integration Tests:

- `GoldenDatasetMirrorIT` — the golden dataset seeds correctly under H2 and matches the fixture
  definition field for field

### Manual Testing Steps:

1. Re-run the Phase 3 capture from `reference.md` alone, without consulting this plan, and
   confirm the golden files are byte-identical to the committed ones.
2. Hand-verify three captured statistics figures against the fixture rows, including one
   consumption figure that exercises the `skip(1)` and the `HALF_UP` rounding.
3. Open one captured vehicle report in a spreadsheet application and confirm the sheet order,
   sheet names, and localisation match the captured reference.

## Performance Considerations

None. The capture runs once against a disposable environment, and the added test surface is one
integration test plus two unit test classes.

## Migration Notes

No schema change and no Liquibase changelog. `golden-dataset.sql` is a test resource loaded
manually during capture; it is never applied by Liquibase and never runs in `dev` or `prod`.

## References

- Related research: `context/changes/golden-baseline-capture/research.md`
- Artefact precedent: `context/archive/resolvable-build/error-contract.md`
- Boot-against-disposable-MariaDB precedent:
  `context/archive/2026-08-27-client-server-contract-trial/plan.md:167-213`
- Test layer this extends: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`,
  `src/test/java/com/kasztelanic/carcare/web/rest/AbstractSessionIT.java`
- Deferred decisions this baseline must inform: `context/changes/session-parity/plan.md:110-112,576-578`
  and `context/changes/session-parity/reviews/impl-review.md:135-143`
- Downstream consumers: `context/foundation/roadmap.md` S-03 and S-04

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Boot spike at `6e19b96`

#### Automated

- [x] 1.1 The `6e19b96` worktree builds offline
- [x] 1.2 MariaDB container reachable and `carcare` schema exists
- [x] 1.3 WAR boots under `dev` and `GET /management/health` returns `UP`
- [x] 1.4 `POST /api/authenticate` returns a token for both `admin` and `user`
- [x] 1.5 No change under `src/` or `pom.xml`; `git worktree list` shows only the capture worktree

#### Manual

- [x] 1.6 `reference.md` contains every command verbatim, in order
- [x] 1.7 The empty-lookup-table finding is recorded

### Phase 2: Author the golden fixture

#### Automated

- [ ] 2.1 `golden-dataset.sql` loads into the migrated `6e19b96` MariaDB without error
- [ ] 2.2 Row counts per table match the counts stated in `reference.md`
- [ ] 2.3 No fixture id collides with a Liquibase-seeded id

#### Manual

- [ ] 2.4 Every branch in the Phase 2 contract has a covering row in the branch inventory
- [ ] 2.5 Reference date is away from month/year boundaries and is not 29 February
- [ ] 2.6 Inventory plus SQL explains why each row exists
- [ ] 2.7 Every fixture row has a unique handle; the inventory's handle→id map covers all of them

### Phase 3: Capture reports and statistics

#### Automated

- [ ] 3.1 Every endpoint call returns the status recorded in `reference.md`, 5xx included
- [ ] 3.2 A golden file exists for every call listed in `reference.md`
- [ ] 3.3 Re-running the capture produces byte-identical golden files
- [ ] 3.4 Every number parses as fixed-precision decimal or is a documented sentinel
- [ ] 3.5 No golden file contains a raw entity id; identities are Phase 2 handles

#### Manual

- [ ] 3.6 Both report locales captured with differing sheet names
- [ ] 3.7 Zero-volume refuel unit-price cell shows the `Infinity` sentinel
- [ ] 3.8 Zero-mileage consumption and same-date mileage merge are visible in captured values
- [ ] 3.9 Three captured figures hand-verified against the fixture rows

### Phase 4: Capture reminder selection

#### Automated

- [ ] 4.1 Both reminder golden files exist and are sorted by the documented total order
- [ ] 4.2 Rows at `+1` and `-1` day from an advance appear in neither file
- [ ] 4.3 The null-`nextByDate` routine service appears in neither file
- [ ] 4.4 Rows exactly on an advance appear in both files, for both owners
- [ ] 4.5 Typed-seam and full-path captures agree entry-for-entry
- [ ] 4.6 Re-running the capture on a different day, clock pinned, produces byte-identical files
- [ ] 4.7 No leftover worktree and no capture container left running

#### Manual

- [ ] 4.8 Captured `diff` values match the configured advances
- [ ] 4.9 `reference.md` alone is sufficient to reproduce every golden file

### Phase 5: Build the consumption harness at HEAD

#### Automated

- [ ] 5.1 `./mvnw test` passes at HEAD
- [ ] 5.2 `./mvnw verify` passes at HEAD with no pre-existing test regressed
- [ ] 5.3 `GoldenDatasetMirrorIT` passes
- [ ] 5.4 `WorkbookValues` reproduces the stored structure exactly from a golden XLSX capture
- [ ] 5.5 Existing single-argument `SessionFixtures` builders still produce their previous values
- [ ] 5.6 Golden seeding returns a complete handle→id map; `GoldenReference` resolves ids through it

#### Manual

- [ ] 5.7 Comparison failure message locates the differing cell without opening the workbook
- [ ] 5.8 `reference.md` read cold is enough to start S-03
- [ ] 5.9 Golden seeding confirmed not to run for existing ITs
- [ ] 5.10 Expected-divergences section covers all five `4ad88bd` files; `AGENTS.md` claim corrected
