<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Client-server contract trial

- **Plan**: `context/changes/client-server-contract-trial/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-27
- **Verdict**: SOUND (after triage)
- **Findings**: 1 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS |

## Grounding

Grounding: 7/7 paths ✓, 6/6 symbols ✓, brief↔plan ✓. Progress↔Phase consistency: valid ✓.

## Findings

### F1 — The event “open” browser step does not exist

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — Full vehicle-event flow matrix
- **Detail**: The plan requires create/list/open/edit/delete for every event type, but the frozen client has no event-detail/open route. It exposes list, new, edit, delete, and a details popover, so the step cannot be completed as written.
- **Fix**: Change each event flow to “create → verify in list/details popover → open edit form (exercising GET-by-ID) → edit → delete.” Keep the separate vehicle detail/open step.
- **Decision**: FIXED — plan now uses the client-supported list/details-popover/edit-form flow while retaining vehicle detail-open coverage.

### F2 — The ribbon field will not be restored

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Phase 1 — Profile Info contributor
- **Detail**: The plan says `info.display-ribbon-on-profiles` will continue to appear through Spring Boot’s standard environment contributor. Under Boot 3.1.5, that contributor is disabled by default, and the repository does not enable it. The client reads both `activeProfiles` and `display-ribbon-on-profiles`, while the planned test asserts only the first.
- **Fix ⭐ Recommended**: Have `ProfileInfoContributor` contribute both exact top-level client fields and assert both in `SecurityConfigurationIT`.
  - Strength: Restores the complete payload without exposing other `info.*` properties on a public endpoint.
  - Tradeoff: The custom contributor owns two legacy fields instead of one.
  - Confidence: HIGH — verified against Boot 3.1.5 configuration and the frozen reducer.
  - Blind spot: None significant.
- **Decision**: FIXED — the plan now requires the custom contributor and integration test to cover both exact client profile fields.

### F3 — Trial seeding omits its required admin session

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 — Disposable trial environment and test-data setup
- **Detail**: Both lookup-population endpoints require ADMIN, but the procedure mentions only authenticating as the fixture user. Population uses `saveAll` against unique lookup columns, so retrying it is not idempotent.
- **Fix**: Specify “login as `admin/admin` → invoke each seed endpoint exactly once and verify `true` → login as `user/user`”; on a failed or retried seed, recreate the disposable database.
- **Decision**: FIXED — the plan now specifies the one-shot admin seeding session, user-session handoff, and disposable-database retry rule.

## Confirmed claims without findings

- Phase 2’s production change surface is sufficient: `Vehicle.java` and the Liquibase column are the only fixed-width enforcement points for `licensePlate`.
- A local Spring Boot-native `InfoContributor` fits the existing architecture; no competing project-owned contributor pattern exists.
