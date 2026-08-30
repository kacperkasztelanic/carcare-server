<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Archived-Vehicle Purge & User-Deletion Disposition Implementation Plan

- **Plan**: `context/changes/archived-vehicle-purge/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-29
- **Verdict**: REVISE
- **Findings**: [1 critical] [3 warnings] [1 observation]

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | FAIL |

## Grounding

9/9 paths ✓, ~28 symbols/claims verified via deep sub-agent ✓, brief↔plan ✓

Deep verification confirmed every risky claim: reminder exclusion is real at the query level (all three reminder repositories filter `vehicle.archivedAt is null`, queries not principal-scoped); the L2 trap is real (dev/prod on at application-dev.yml:44 / application-prod.yml:49, test off at application-test.yml:38; `Vehicle` + all five event entities `@Cache(NONSTRICT_READ_WRITE)`; TTL 3600 via ApplicationProperties.java:53; zero `@Modifying` in src/main); the git reference purge test exists exactly as cited at `718a011^` (NOT_SUPPORTED + try/finally + raw JDBC purge in FK order); the audit write-directly path is sound (`CustomAuditEventRepository.add` is REQUIRES_NEW at line 53, `PersistenceAuditEventRepository` is a plain JpaRepository, `PersistentAuditEvent` has setters; the 255-char truncation lives only in `add` but plan values are short by construction); `findByVehicleId(Long)` exists un-scoped on all five event repositories; `UserService` is class-level `@Transactional` + `@RequiredArgsConstructor` with only `UserResource` calling `deleteUser`; `anonymoususer` is seeded (id 2) and filtered at UserService.java:246; the fuel-type FK has no ON DELETE clause; `HeaderUtil.createEntityDeletionAlert("vehicle", id)` emits `carcareApp.vehicle.deleted`; `NoSuchElementException` → 404 handler exists (ExceptionTranslator.java:90). No test expecting 500 sits on a DataIntegrityViolationException path, and unactivated users can never own vehicles.

## Findings

### F1 — Progress section is missing rows for two Phase-1 criteria

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: `## Progress` → `### Phase 1` (vs Phase 1 Success Criteria)
- **Detail**: Phase 1 defines 4 automated + 2 manual success criteria (plan.md:276-287), but the Progress block tracks only 3 + 1 (1.1–1.4). "ArchTest green (the new exception carries no web imports)" and "Reminder schedule unaffected" have no matching `- [ ]` rows — a mechanical Progress↔Phase contract violation that /10x-implement will trip on. Nothing has landed yet, so renumbering is safe.
- **Fix**: In the Phase-1 Progress block add `- [ ] 1.4 ArchTest green (new exceptions carry no web imports)` to Automated, renumber the manual smoke to 1.5, and add `- [ ] 1.6 Reminder schedule unaffected (tombstone-owned vehicles are archived)` to Manual.
- **Decision**: FIXED (single-fix applied: added 1.4 ArchTest green, renumbered manual smoke to 1.5, added 1.6 Reminder schedule unaffected)

### F2 — Purge IT's committed audit row never gets cleaned up

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 §5 AdminVehiclePurgeIT (cleanup contract)
- **Detail**: The 204 purge case commits a `VEHICLE_PURGED` audit row that no finally-cleanup touches — the JDBC helper purges event tables + vehicles only. The plan justifies this with "AuditResourceIT uses containment assertions (hasItem)" (plan.md:48-49), but that claim is incomplete: AuditResourceIT also asserts positionally — `$[0].timestamp/type/data` at AuditResourceIT.java:70-74 and 108-110 — on an unfiltered `GET /management/audits`, after a `@BeforeEach deleteAll()` that rolls back (AuditResourceIT.java:50-52), so committed foreign rows stay visible. Whether `$[0]` hits the sample row depends on unspecified H2 row ordering — an order-dependent flake, and a violation of the plan's own leak-discipline rule (plan.md:485-488).
- **Fix**: Extend AdminVehiclePurgeIT's finally to delete the committed `VEHICLE_PURGED` row (child rows in `jhi_persistent_audit_evt_data` first, then the parent — same JDBC style as the helper), and drop the "containment assertions" justification from Current State Analysis.
- **Decision**: FIXED (single-fix applied: purge IT finally also deletes the VEHICLE_PURGED audit row; Current State justification corrected)

### F3 — Lookup-409 test needs fixture support no phase adds, and omits fuel-type-row cleanup

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §6 LookupMaintenanceResourceIT case
- **Detail**: The test contract says "create a committed vehicle on a dedicated fuel type" (plan.md:268-269), but `SessionFixtures.vehicleFor` hardcodes the shared "fixture-fuel" row (SessionFixtures.java:100-113, 412-415) and the only FuelType-parameterized builder is private (`goldenVehicle`, lines 388-390) — Phase 2 §2 adds image/user/JDBC helpers but no fuel-type support, and it lands after Phase 1 anyway. Separately, the finally cleans "the vehicle rows" but not the committed dedicated fuel-type row. LookupMaintenanceResourceIT itself is count-relative (X-Total-Count = live `fuelTypeRepository.count()`, lines 48-49) so no assertion breaks today — but it contradicts the plan's stated "every committing test cleans up in finally" discipline. (Owner resolution is fine: `ownerFor` resolves any existing login, so the dedicated user works as-is.)
- **Fix**: Add a fuel-type-parameterized vehicle helper (e.g. `vehicleFor(String ownerLogin, FuelType fuelType)`) to Phase 1's fixture work, and extend the test's finally to delete the vehicle rows then the dedicated fuel-type row.
- **Decision**: FIXED (single-fix applied: Phase 1 §6 now specifies the vehicleFor(String, FuelType) overload and fuel-type-row cleanup in FK-safe order)

### F4 — DIV handler also flips create-path unique-race 500s — undocumented blast radius

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §3 DIV class handler
- **Detail**: The handler contract describes the covered class as "residual FK paths and in-use lookup deletion" (plan.md:67-68, 193-194), but a class-level handler also changes create paths: `registerUser`'s deliberate delete/flush duplicate race (UserService.java:86-97) and the lookup unique constraints `UC_FUEL_TYPESENGLISH/POLISH/TYPE_COL` (changelog lines 214-220) go from error-logged 500 to warn 409. Verified harmless to the suite (the only four 500-expecting tests are non-DIV paths: ExceptionTranslatorIT:110, AccountResourceIT:102, 413, 754) and arguably an improvement — but the implementer should know the surface is wider than the plan's wording.
- **Fix**: Add one sentence to the Phase 1 §3 contract: the handler also covers unique-constraint violations on create paths (registration duplicate race, lookup-type unique columns) — 500→409, an improvement, no test pins 500 today.
- **Decision**: FIXED (single-fix applied: blast-radius note added to the Phase 1 §3 contract)

### F5 — "deleteQuietly" names a method that doesn't exist

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §3 step 5
- **Detail**: "deleteQuietly never throws" (plan.md:355-356) — but the interface method is `delete` (ImageStorageService.java:5-9); `deleteQuietly` is the Commons-IO call inside the impl (ImageStorageServiceImpl.java:61-66).
- **Fix**: Rewrite as "ImageStorageService.delete (idempotent, never throws)".
- **Decision**: FIXED (single-fix applied: step 5 now names ImageStorageService.delete with FileUtils.deleteQuietly as the impl detail)

## Verdict After Fixes

All five fixes are one-liners; resolving F1 (and the warnings) moves the verdict REVISE → SOUND.