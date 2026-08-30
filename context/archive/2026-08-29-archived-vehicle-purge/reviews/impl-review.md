<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Archived-Vehicle Purge & User-Deletion Disposition

- **Plan**: context/changes/archived-vehicle-purge/plan.md
- **Scope**: Phases 1–3 of 3 (full plan)
- **Date**: 2026-08-29
- **Verdict**: REJECTED (solely due to F1, a one-line fix; all other dimensions clean or minor)
- **Findings**: [1 critical] [2 warnings] [6 observations]
- **Post-triage (2026-08-30)**: F1 fixed, F2 accepted (documented at the interlock), F3 accepted as decided, F4–F9 fixed. Full suite green after triage: 38 unit + 249 IT, 0 failures.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING (3 minor drifts — F6, F7, F8) |
| Scope Discipline | PASS (all 8 "NOT Doing" guardrails verified) |
| Safety & Quality | FAIL (1 critical — F1) |
| Architecture | PASS (layering clean, ArchTest green) |
| Pattern Consistency | WARNING (1 finding — F4) |
| Success Criteria | PASS (full verify green: 38 unit + 247 IT, 0 failures; greps pass; manual items owner-attested with commit stamps) |

## Verification evidence

- Full `./mvnw verify` green: 38 unit + 247 integration tests, 0 failures, 0 skipped (ArchTest included). New ITs confirmed passing within the suite: AdminVehiclePurgeIT (4), UserDeletionDispositionIT (3), LookupMaintenanceResourceIT (7).
- `grep -rn "@Modifying" src/main/java` → no matches (PASS).
- `grep -n "S-08" context/foundation/roadmap.md` → found (row at :57, section at :442).
- Drift agent verdict totals: 12 MATCH, 2 minor DRIFT (F6, F7 below), zero production-code drift, zero scope creep.
- Manual items 1.5, 1.6, 2.4, 3.3 all `[x]` with commit stamps; the plan's pause-for-confirmation mechanism was followed (epilogue bd511fd).

## Findings

### F1 — Protected-login guard bypassable via case-variant login

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/kasztelanic/carcare/service/UserService.java:228
- **Detail**: The P7 guard uses case-sensitive `String.equals`, but the login lookup is not. Verified live on dev MariaDB (`utf8mb4_unicode_ci`): `findOneByLogin("System")` MATCHES the `system` row. So `DELETE /api/users/System` (any case variant) skips the guard, finds the protected account, and deletes it — the exact outcome P7 exists to prevent: destroying the only admin, or the `anonymoususer` tombstone (breaking all future user deletions with 500s). H2 compares case-sensitively, so the whole suite is structurally blind to this — the same prod-only trap shape as the L2 issue the plan itself called out. Gated behind ADMIN role (authorized-user footgun, not privilege escalation). Before this change even exact-case deletion worked, so the guard is a strict improvement with a residual hole.
- **Fix**: Use `Constants.SYSTEM_ACCOUNT.equalsIgnoreCase(login) || Constants.ANONYMOUS_USER.equalsIgnoreCase(login)` in the guard (fires before the lookup, so H2 CAN test it — add case-variant cases to the UserResourceIT 400 tests).
- **Decision**: FIXED (Fix applied 2026-08-29: equalsIgnoreCase guard in UserService.deleteUser + 2 case-variant 400 tests in UserResourceIT; UserResourceIT 31/31 green)

### F2 — TOCTOU on the purge interlock

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java:93
- **Detail**: The `archivedAt != null` interlock is check-then-act with no `@Version` on Vehicle. A concurrent `restoreVehicle` committing between the purge's read and commit hard-deletes a now-ACTIVE vehicle; a double purge returns 204 and writes a duplicate `VEHICLE_PURGED` audit event. Admin-only, single-admin deployment (P7's own anti-over-engineering rationale), so likelihood is very low — but wrong-state destruction is the purge's worst-case failure.
- **Fix A ⭐ Recommended**: Accept and document as residual risk
  - Strength: Matches the owner's P7 single-admin reasoning; zero code, zero schema (a `@Version` fix would need Liquibase, violating the "No schema change" guardrail).
  - Tradeoff: If a second admin session is ever added, the race becomes reachable.
  - Confidence: HIGH — restore+purge race requires two admins on one vehicle.
  - Blind spot: Haven't checked whether any UI flow could self-race (none plausible — both are manual admin verbs).
- **Fix B**: Pessimistic lock on the interlock read
  - Strength: Closes the race without schema change — but only if `restoreVehicle` also locks, so it means touching two methods.
  - Tradeoff: More code on two paths for a race that needs two concurrent admins; lock-ordering bugs become possible.
  - Confidence: MEDIAN — mechanically simple, but the full fix is wider than it looks.
  - Blind spot: Restore would need its own IT to prove the lock.
- **Decision**: ACCEPTED (Fix A applied 2026-08-29: residual-risk comment documented at the interlock in AdminVehicleServiceImpl.purgeVehicle)

### F3 — DIV class-handler breadth extends past FK/unique

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:163
- **Detail**: Class-level `DataIntegrityViolationException` → 409 was an explicit owner decision (P5), and the plan documents the unique-race blast radius. What the plan did not spell out: the class also covers the SQLState 22 "data exception" family (truncation, bad values), so a future genuine bug could surface as warn-409 instead of error-500. Mitigations verified: full stack trace logged at warn; ProblemDetail leaks nothing (generic title, no SQL/constraint names).
- **Fix**: Accept as decided; alert on the warn message in prod; narrow the handler only if a genuine bug ever shows up as this 409.
- **Decision**: ACCEPTED (2026-08-30: owner-decided breadth stands; monitor warn-409 in prod, narrow only if a genuine bug surfaces as this 409)

### F4 — Handler comment contradicts its own code

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:165
- **Detail**: The comment says "log at warn without a stack trace, matching handleArchivedResourceException" — but the code logs the exception (with stack trace), and `handleArchivedResourceException` does not log at all. The code is the better choice (the trace is what makes F3 tolerable); the comment is wrong twice.
- **Fix**: Rewrite the comment to match the code.
- **Decision**: FIXED (2026-08-30: comment rewritten — warn with stack trace, contrasted with the non-logging 4xx handlers)

### F5 — Image-delete callback is fire-and-forget

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java:118
- **Detail**: The after-commit callback relies entirely on `FileUtils.deleteQuietly`'s never-throws behavior, and ignores `delete()`'s boolean return — a permission/lock failure silently orphans the image file. If delete ever started throwing, the exception would escape `afterCompletion` AFTER durable commit: a misleading 500 for a committed purge. Today's behavior is correct; nothing structural prevents future drift.
- **Fix**: Wrap the callback body in try/catch (log.warn) and log.warn when delete() returns false.
- **Decision**: FIXED (2026-08-30: @Slf4j added; callback wrapped in try/catch + log.warn on false delete)

### F6 — Committed-user helper lives in the IT, not SessionFixtures

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/kasztelanic/carcare/web/rest/UserDeletionDispositionIT.java:119
- **Detail**: Plan Phase 2 §2 contracts the committed-user helper into SessionFixtures ("image, committed-user, and JDBC-purge support"). It is implemented as a private method in the IT instead. All contract content is present (unique login, setters, activated, bcrypt, created_by), and it has exactly one consumer — AdminVehiclePurgeIT uses the seeded user per plan. Benign placement drift, zero production impact.
- **Fix**: Move `committedUser()` into SessionFixtures (mechanical, ~12 lines) so the plan stays authoritative for future ITs.
- **Decision**: FIXED (2026-08-30: helper moved to SessionFixtures with PasswordEncoder field; IT calls sessionFixtures.committedUser())

### F7 — S-08 missing from the Streams table

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/roadmap.md:73
- **Detail**: The S-08 slice row and per-slice section both landed as planned (roadmap.md:57, :442), and the plan attributes S-08 to Stream D — but the Streams table's row D still reads "S-05 / S-06, parallel". S-08's stream membership is recorded nowhere.
- **Fix**: Add S-08 to the Stream D row in the Streams table.
- **Decision**: FIXED (2026-08-30: Stream D chain now reads `S-05` / `S-06` / `S-08`)

### F8 — Plan spells the user-delete alert header wrong

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/archived-vehicle-purge/plan.md:64
- **Detail**: The plan says "204 + userManagement.deleted" is asserted via `X-carcareApp-alert`, but user deletion emits `X-carcare-alert` (UserResource uses the 3-arg createAlert, which passes `spring.application.name`). The test asserts the REAL contract, matching existing UserResourceIT — the implementation is correct; the plan text is misleading for future readers.
- **Fix**: Correct the plan's Desired End State to say `X-carcare-alert` for user deletion.
- **Decision**: FIXED (2026-08-30: plan Phase 1 §5 case list corrected to `X-carcare-alert`)

### F9 — Fixture setup sits outside try/finally in committing ITs

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/test/java/com/kasztelanic/carcare/web/rest/AdminVehiclePurgeIT.java:68
- **Detail**: All six committing (NOT_SUPPORTED) tests clean up correctly in finally — but fixture SETUP runs before the try. A fixture call throwing mid-setup (e.g., imageFor after vehicleWithEventsFor committed) leaks rows and an image file into the JVM-wide shared H2/data dir, breaking the absolute-count golden ITs. Low likelihood; exactly the leak class the plan's discipline exists for.
- **Fix**: Move setup inside try with null-safe finally cleanup (or an @AfterEach keyed on captured ids).
- **Decision**: FIXED (2026-08-30: all 5 setup-before-try instances restructured — AdminVehiclePurgeIT ×2, UserDeletionDispositionIT ×2, LookupMaintenanceResourceIT ×1; image file also cleaned on failure paths)
