<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-01 `session-parity`

- **Plan**: `context/changes/session-parity/plan.md`
- **Scope**: Phases 1–7 of 7 (full plan)
- **Date**: 2026-08-27
- **Verdict**: NEEDS ATTENTION
- **Findings**: 1 critical, 4 warnings, 2 observations

`./mvnw verify` is green: 28 unit tests (1 skipped) and 173 integration tests (1 skipped — the
intended `@Disabled` placeholder), 0 failures. Baseline was 22 / 115, so +6 unit and +58 IT.
All 8 commits map cleanly to phases and every `src/main` change is small and reversible.

Recorded as NEEDS ATTENTION rather than REJECTED despite the critical finding: it is a one-line
test-matcher fix with no production impact, so it does not meet the REJECTED bar (security, data
safety, major drift, or failing tests).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

## Findings

### F1 — Client wire invariant (a) does not actually assert non-null

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/ClientWireContractIT.java:46-52,110`
- **Detail**: The plan's Desired End State names four wire invariants "whose violation crashes or
  dead-ends client 1.2.5". Invariant (a) — every string the client `.trim()`s unconditionally is
  non-null in a GET response — is asserted with `jsonPath(...).isEmpty()`. Verified empirically
  during this review (throwaway probe against `JsonPathExpectationsHelper`, since removed):
  `isEmpty()` **passes** for a JSON `null`, because `ObjectUtils.isEmpty(null)` is true. All 13
  assertions across the two tests therefore pass unchanged if the server starts returning null for
  these fields — the exact regression they exist to catch. `value("")` correctly failed for null in
  the same probe.
- **Fix**: Replace `isEmpty()` with `value("")` at both sites. The tests already force the columns
  to `''` via `JdbcTemplate`, so `""` is the precise expected value and it rejects null.
  - Strength: Restores the invariant the slice's central deliverable depends on; mechanical change.
  - Tradeoff: None — same tests, stricter matcher.
  - Confidence: HIGH — the null-vs-empty behavior was verified by direct probe, not from memory.
  - Blind spot: None significant.
- **Decision**: FIXED — `isEmpty()` → `value("")` at both sites; ClientWireContractIT green (4 tests).

### F2 — Undocumented production wire-contract widening in `InsuranceTypeDto`

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/kasztelanic/carcare/service/dto/InsuranceTypeDto.java:21-32`
- **Detail**: A `@JsonCreator(mode = DELEGATING)` taking a raw `JsonNode` was added so the client's
  bare-string PUT shape deserializes. This is a permanent widening of the public request contract and
  appears in no plan section: "Changes Required" never mentions it, and Migration Notes states
  `src/main` changes are "confined to `HeaderUtil`, the deleted `HeaderUtilInitializer`, and the four
  Phase 6 fixes." It is also absent from the `change.md` epilogue and from `AGENTS.md`, and it is
  asymmetric — `FuelTypeDto` keeps strict object-only parsing. The plan anticipated the other outcome
  ("if it is Jackson, that path already returns 400 and only the null/unknown cases need this fix")
  while Phase 6 §4 demanded a clean 200 for the bare string; the implementation resolved that tension
  silently.
- **Fix A ⭐ Recommended**: Record it in the `change.md` epilogue and in `AGENTS.md`'s contract
  subsection, stating the `FuelTypeDto` asymmetry and why it is safe.
  - Strength: Keeps working, client-verified behavior; closes the only real gap, which is the record.
  - Tradeoff: The widened contract becomes permanent by default.
  - Confidence: HIGH — behavior is covered by two green ITs (object-wrapped and bare-string PUT).
  - Blind spot: The client tree is absent from this checkout, so the bare-string PUT shape rests on
    research's `file:line` citation rather than direct observation here.
- **Fix B**: Revert the creator; let Jackson reject the bare string as a 400.
  - Strength: Restores strict parity; `src/main` matches the plan's Migration Notes.
  - Tradeoff: Client 1.2.5's insurance edit flow breaks — the exact breakage FR-008 treats as the
    pager event.
  - Confidence: MEDIUM — depends on the client shape claim above.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — documented in the `change.md` review epilogue and in `AGENTS.md`'s client-contract section, naming the `FuelTypeDto` asymmetry as intentional.

### F3 — Roadmap edit far exceeded "S-02's Risk paragraph only"

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: `context/foundation/roadmap.md:50,68,313-328`
- **Detail**: Phase 7 §4's contract was "Edit S-02's Risk paragraph only. Do not change its Status —
  `/10x-archive` owns that." The S-02 risk edit itself is correct and well-judged. But the same commit
  also created a new roadmap slice S-07 `client-server-contract-trial` with a full section and `next`
  status, flipped S-01's table status to `implemented`, appended to S-03's Risk, and re-sequenced
  Stream C from "S-01 / S-02 / S-03 / S-04, all four parallel" to "S-01 → S-07 (next); S-02 / S-03 /
  S-04 follow". That last change is a material re-planning decision — three slices the roadmap called
  parallel are now gated behind a slice invented inside S-01.
- **Fix A ⭐ Recommended**: Keep S-07 but restore Stream C's parallelism.
  - Strength: Preserves the genuinely useful follow-up scope the manual smoke uncovered, without S-01
    unilaterally serializing three independent slices.
  - Tradeoff: S-07's priority relative to S-02 / S-03 / S-04 stays unstated.
  - Confidence: MEDIUM — nothing in the smoke findings shows S-02 / S-03 / S-04 depend on S-07.
  - Blind spot: Haven't confirmed whether the resequencing was intended.
- **Fix B**: Leave as-is and note the roadmap expansion in the epilogue.
  - Strength: Zero churn; the record explains itself.
  - Tradeoff: Normalizes slices editing the roadmap beyond their plan's stated contract.
  - Confidence: HIGH — purely a documentation change.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix B — roadmap left as-is; the expansion and the provisional Stream C resequencing are now recorded in the `change.md` review epilogue.

### F4 — Cost-report isolation check cannot detect a leak

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java:201-203`
- **Detail**: For `POST /api/reports/costs` the non-owner branch (`EMPTY_REPORT`) asserts only
  `status().isOk()`. The response is an XLSX byte array and nothing inspects it. If
  `ReportServiceImpl.java:63` ever lost its `findAllByIdAndOwnerIsCurrentUser` predicate, owner B would
  receive a spreadsheet of owner A's costs and this test would still pass. Every other path in this
  file asserts 404 or an empty body. This is the one hole in the file the plan describes as "a complete
  statement of the guarantee", and it sits on the PRD's highest-severity guardrail.
- **Fix**: Parse the returned workbook with POI (already a main dependency) and assert the foreign
  response contains no cell matching the owner's `licensePlate`.
  - Strength: A leak assertion, not a computed value, so it stays inside S-03's boundary.
  - Tradeoff: Adds POI usage to the test layer; a few lines of workbook traversal.
  - Confidence: HIGH — POI is already on the compile classpath via the report generators.
  - Blind spot: Haven't checked whether the empty cost report emits header rows that would need
    excluding from the scan.
- **Decision**: FIXED — `assertCostReportIsolation(...)` parses both workbooks with POI: the owner report must contain the licensePlate, the foreign report must not. `EMPTY_REPORT` removed from `ForeignResult`.

### F5 — Criterion 4.6 ("no computed value asserted") is not held

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java:207`
- **Detail**: Phase 5's contract says "No computed value is asserted — shape and isolation only", and
  Progress items 4.6 and 6.5 are checked `[x]`. But the `EMPTY_RESULT` branch asserts
  `$.averageConsumption` equals `0.0` — a computed value, and specifically the one the epilogue tells
  S-03 to re-judge against F-02's golden baseline. S-03 will have to edit an owner-isolation test in
  order to change a statistics decision.
- **Fix**: Assert the response shape only (e.g. `jsonPath("$.averageConsumption").exists()`), leaving
  the value to S-03.
- **Decision**: FIXED — `$.averageConsumption` now asserted with `.exists()`; OwnerIsolationIT green (7 tests).

### F6 — Disabled delete test lacks the cleanup its plan required

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:124-133`
- **Detail**: Phase 4 §2 required the placeholder to carry
  `@Transactional(propagation = NOT_SUPPORTED)` "or equivalent and must clean up after itself". The
  propagation is correct; the cleanup is absent. Harmless while `@Disabled`, but H2 is shared JVM-wide
  (`DB_CLOSE_DELAY=-1`), so whoever enables this in S-05 inherits a test that commits a vehicle plus
  five events into the shared database and never removes them.
- **Fix**: Add an `@AfterEach` (or in-test `finally`) deleting the events and vehicle, so the test is
  safe to enable.
- **Decision**: FIXED — `try/finally` + `purgeVehicle(...)` via `JdbcTemplate` deletes the five event tables then the vehicle.

## Housekeeping (below finding threshold)

- `.playwright-mcp/` is untracked and not in `.gitignore` (leftover from the Phase 7 manual smoke).
- `src/test/java/com/kasztelanic/carcare/probe/` is an empty leftover directory.

## What went well

- The `ArchTest` boundary was respected exactly as planned: `InvalidLookupTypeException` lives in
  `service/exception`, with the handler in `ExceptionTranslator` routed through
  `handleExceptionInternal` so `path` and `message` are added like every other error body.
- The `AverageConsumptionResult` guard is pinned to `== 0` with a bit-identical negative-mileage
  regression case, exactly as Phase 6 §3 specified.
- `HeaderUtil`'s two `carcareApp` contracts are now separately named and unit-pinned, and
  `UserResource` / `ExceptionTranslator` still emit `X-carcare-*` as at baseline.
- The Phase 6 fixes changed exactly one Phase 4 assertion — the one the plan predicted.
