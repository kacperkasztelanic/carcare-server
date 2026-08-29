<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Vehicle Archiving Implementation Plan

- **Plan**: `context/changes/vehicle-archiving/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-28
- **Verdict**: SOUND
- **Findings**: 1 critical, 3 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS |

## Grounding

Grounding: 12/12 paths ✓, 8/8 symbols ✓, brief↔plan ✓. Deep verification: 3/4 risky claims confirmed; 1 partially confirmed.

## Findings

### F1 — Progress block violates the implementation parser contract

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Progress
- **Detail**: Progress uses `#### Phase N` instead of the required `### Phase N`. Its 13 summary checkboxes are unnumbered and do not map one-for-one to the phase verification criteria. `/10x-implement` will not parse this structure reliably.
- **Fix**: Rebuild Progress with matching `### Phase N: <name>` headings and numbered `- [ ] N.M <title>` entries for every automated and manual verification criterion.
- **Decision**: FIXED — normalized phase headings and rebuilt Progress with numbered rows matching every automated and manual verification item; the synchronized test-matrix refinement now totals 24 rows.

### F2 — Cost-union ordering can be lost during refetch

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Cost-period union; Phases 2–3
- **Detail**: The plan composes ordered IDs and then passes them to existing report/statistics logic. The existing `IN :id` repository query has no ordering guarantee—its own comment says current ordering is “by luck.” Refetching the union can lose both the requested segment’s order and the archived append position.
- **Fix A ⭐ Recommended**: Return the final ordered `List<Vehicle>` from `VehicleScopeService` and let both consumers map it directly.
  - Strength: One materialization point; ordering and de-duplication are explicit.
  - Tradeoff: Domain entities cross an internal service boundary.
  - Confidence: HIGH — both consumers already operate transactionally on repository-returned vehicles.
  - Blind spot: The method’s read-only transaction ownership should be stated explicitly.
- **Fix B**: Keep ordered IDs, bulk-load once, then reconstruct the vehicle list using the ID sequence before calculation.
  - Strength: Keeps the policy boundary ID-oriented.
  - Tradeoff: Adds reordering logic that could diverge between the two consumers.
  - Confidence: HIGH — mechanically sound if centralized.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — `VehicleScopeService.findCostVehicles(...)` returns the final ordered `List<Vehicle>` for direct consumption within the callers’ existing read-only transactions; no unordered refetch occurs.

### F3 — Referenced research contradicts the final HTTP contract

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: References → `research.md`
- **Detail**: The active plan and brief require `410 Gone` plus an admin restore API. Referenced `research.md` still prescribes `404`, repeated-delete `404`, no unarchive API, and SQL-only administrator restoration. An implementer following that call table could build the wrong contract.
- **Fix**: Refresh the stale D1 table and open-question text in `research.md` to match the plan, or explicitly mark those passages superseded.
- **Decision**: FIXED — refreshed `research.md` to record owned-archived `410`, absent/foreign `404`, repeated-delete `410`, and administrator API restoration.

### F4 — Archived event coverage is assigned to the wrong test harness

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 and Phase 4 testing
- **Detail**: `EventResourceIT` covers only composite `POST /api/events`, but the plan assigns archived direct-event `410` coverage there. Direct CRUD belongs to the five Refuel/Repair/RoutineService/Inspection/Insurance resource ITs. Phase 4 also omits those files from its coverage list.
- **Fix**: Keep composite omission tests in `EventResourceIT`; add an explicit archived-owner matrix for list/create/get/update/delete across the five event resource ITs or a shared matrix in `OwnerIsolationIT`.
- **Decision**: FIXED — assigned composite coverage to `EventResourceIT`, the archived CRUD matrix to all five direct event resource ITs, and foreign `404` protection to `OwnerIsolationIT`; synchronized Progress.

### F5 — Reminder ordering is not an existing contract

- **Severity**: 🔎 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Reminder constraints and Phase 3
- **Detail**: The plan promises to preserve mail ordering, but the three repository queries have no `ORDER BY`, and `ReminderSelectionParityIT` compares calls without regard to order.
- **Fix**: Describe reminder selection/content as preserved and ordering as unspecified, unless the change deliberately introduces and tests an explicit ordering contract.
- **Decision**: FIXED — clarified that reminder selection, content, date windows, and clock behavior are preserved while delivery ordering remains unspecified.
