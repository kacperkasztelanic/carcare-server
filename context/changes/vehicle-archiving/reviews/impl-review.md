<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Vehicle Archiving

- **Plan**: `context/changes/vehicle-archiving/plan.md`
- **Scope**: Phases 1–4 (all)
- **Date**: 2026-08-29
- **Verdict**: NEEDS ATTENTION → **TRIAGED 2026-08-29** (9 fixed, 1 deferred to follow-ups, 1 withdrawn as incorrect)
- **Findings**: 0 critical, 4 warnings, 6 observations
- **Commit range**: `718a011^..HEAD` (718a011, 8a917f7, 3da00f1, d16cdf3, a8c7793)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria verification

`./mvnw verify` with `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` → **BUILD SUCCESS**.
38 unit tests (1 skipped) + 237 integration tests (0 failures, 0 errors, 0 skipped).
The 217→237 integration jump is legitimate: the previously `@Disabled`
`deletingVehicleWithHistoryCurrentlyViolatesForeignKeys` was converted into a real archive test.

- `git diff --check` → clean.
- `src/test/resources/golden/**` → untouched (`git diff --name-only` over `src/test/resources` is empty).
- `ReportParityIT`, `GoldenDatasetMirrorIT`, `ReminderSelectionParityIT` all pass unmodified in substance.

## Verified clean (no findings)

- **410-vs-404 classification leaks no existence.** `VehicleScopeServiceImpl:27-30` throws only
  after an owner-scoped lookup, so a foreign archived vehicle stays 404.
  `OwnerIsolationIT.archivedVehicleAndEventsRemainNotFoundToAnotherOwner` pins this across all five
  event types plus the vehicle.
- **Admin surface double-gated**: `/api/admin/vehicles` under the existing `/api/admin/**` rule
  (`SecurityConfiguration:79`, unchanged) plus a class-level `@PreAuthorize` ADMIN, matching
  `TestDataResource`. `AdminVehicleResourceIT` proves 403 for a normal user on both routes.
- **No PII leak**: `AdminVehicleDto` exposes exactly the six documented fields; the mapper touches
  only `getOwner().getLogin()`.
- **`Clock` convention respected**: `Instant.now(clock)` at `VehicleServiceImpl:69`; no bare
  `now()` in any changed file.
- **L2 cache coherent**: archive and restore both mutate a managed entity and `save()`; no bulk
  `@Modifying` update. Query cache is off in all three profiles.
- **Physical delete gone**: no `vehicleRepository.delete*` in `src/main`; images preserved on
  archive (`imageStorageService.delete` remains only on the pre-existing edit path).
- **Liquibase**: additive, nullable, unique changeset id, hand-written rollback, no `context`
  attribute (so it runs under the H2 `test` context); all five `(vehicle_id, date)` index columns
  verified against the real schema and `VehicleEvent`'s `@Column(name = "date")`.
- **ArchUnit layering intact**: no `com.kasztelanic.carcare.web` import in `service/` or `repository/`.
- **Event-service coverage**: all 25 cells of the 5×5 matrix (create / vehicle-scoped list / get /
  update / delete × five event types) route through `VehicleScopeService`.
- **Scope boundaries**: no physical deletion, cascading deletion, owner unarchive route, client/i18n
  change, audit table, request flags, or golden regeneration.
- **Correctly unchanged**: `ReminderServiceImpl` (the archive filter lands via the three repository
  queries), `SecurityConfiguration`, `VehicleResource`, `EventResource`.

## Findings

### F1 — User deletion now permanently blocked by the vehicles FK

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/UserService.java:221`
- **Detail**: `deleteUser` does a plain `userRepository.delete(user)`. `vehicles.owner_id` carries a
  hard FK to `jhi_user` (`FKhm05kh6d8f082pgddom1q1yco`, no `ON DELETE CASCADE`). Because archiving
  never removes vehicle rows and no purge path exists, `DELETE /api/users/{login}` now fails with an
  FK violation for any user who has ever owned a vehicle. This narrows a partially pre-existing
  break: before this change a vehicle *with events* already 500'd on delete, so those users were
  already undeletable — the new regression is users owning event-free vehicles. No test covers it,
  and the admin API can restore but never purge, so there is no erasure path at all.
- **Fix A ⭐ Recommended**: Add an admin hard-purge endpoint for archived vehicles (cascading their
  five event tables), and have `deleteUser` purge or reassign the user's vehicles first.
  - Strength: Closes the erasure dead end and gives the archive a terminal state, which the
    lifecycle currently lacks.
  - Tradeoff: Real new scope — the plan explicitly excluded physical deletion, so this is a
    follow-up change, not a patch.
  - Confidence: HIGH — FK definition and `deleteUser` body both read directly.
  - Blind spot: Haven't checked whether a GDPR/retention requirement already exists for this system.
- **Fix B**: Record as a known limitation now, and add a test that pins the current 500 so it isn't
  discovered in production.
  - Strength: Zero-risk, honest, keeps this change's scope intact.
  - Tradeoff: Leaves users undeletable indefinitely.
  - Confidence: HIGH — trivially implementable.
  - Blind spot: None significant.
- **Decision**: DEFERRED via Fix A — queued as FU-1 in `context/changes/vehicle-archiving/follow-ups/review-fixes.md`. Out of scope for this change (the plan excluded physical deletion); needs its own slice covering an admin purge and a `deleteUser` disposition.

### F2 — Cost queries ignore requestedIds and are unbounded

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/impl/VehicleScopeServiceImpl.java:40`,
  `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:33`
- **Detail**: `findArchivedByOwnerIsCurrentUserWithEventsBetween` has no `vehicle.id in :requestedIds`
  predicate, so every cost report/statistics call unions in *every* owned archived vehicle with an
  in-period event — `VehicleArchivingAnalyticsIT:66` pins a 3-id request returning 7 rows. This
  matches decision D2 and the plan verbatim ("append the current user's archived vehicles"), so it is
  intent, not drift. The concerns are forward-looking: the client has no way to opt out, the result
  set grows with the archive for the system's lifetime, and the five correlated `exists` subqueries
  run even for users with zero archived vehicles. Related edge case: an empty `vehicleIds` request
  previously returned nothing and now returns every in-period archived vehicle.
- **Fix**: Short-circuit the archived query when the owner has no archived vehicles (cheap
  `archived_at is not null` existence pre-check), leaving the union semantics as D2 specified.
  - Strength: Removes the per-request cost for the common case without touching owner-approved behavior.
  - Tradeoff: Doesn't bound growth for heavy archive users.
  - Confidence: MED — the union semantics are deliberate; only the always-on cost is clearly worth changing.
  - Blind spot: No load data on realistic archive sizes.
- **Decision**: FIXED — added `existsArchivedByOwnerIsCurrentUser()` and short-circuited `findCostVehicles` so the five-`exists` period query is skipped entirely for owners with no archived vehicles. D2 union semantics unchanged.

### F3 — Archive test helper never landed in SessionFixtures

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java` (unchanged)
- **Detail**: Phase 4 bullet 1 said "Add dedicated archive fixtures/helpers in the session test
  support without calling them from `seedGoldenDataset` or changing `GOLDEN_HANDLES`." The constraint
  half holds (golden data uncontaminated), but the helper was never added. Four test classes each
  re-derive "setArchivedAt + save" under three different names — `archive()` in
  `VehicleArchivingRepositoryIT:115`, `archived()` in `VehicleArchivingAnalyticsIT:169`,
  `archivedVehicle()` in `AdminVehicleResourceIT:98`, and inline in `ReminderSelectionParityIT:135` —
  each pulling its own `@Autowired VehicleRepository`. The next archive test will add a fifth copy.
- **Fix**: Add `archivedVehicleFor(login, instant)` to `SessionFixtures` (outside `seedGoldenDataset`
  and `GOLDEN_HANDLES`) and collapse the four duplicates onto it.
- **Decision**: FIXED — added `archive(vehicle, instant)` and `archivedVehicleFor(login, instant)` to `SessionFixtures` (outside `seedGoldenDataset` / `GOLDEN_HANDLES`); collapsed all four duplicates onto them and removed three now-dead `@Autowired VehicleRepository` fields.

### F4 — No test guards the VehicleDto wire shape

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java`
- **Detail**: Phase 4 asked to "assert normal vehicle JSON remains backward-compatible". The delete
  half is asserted (alert header + body id at `VehicleResourceIT:165`), but nothing asserts
  `archivedAt` is absent from `/api/vehicle` responses — a grep for `archivedAt").doesNotExist`
  across `src/test/java` returns nothing. `VehicleDto` and `VehicleMapper` are both genuinely clean
  (0 occurrences of `archivedAt`), so compatibility holds today; it is simply unguarded against a
  future mapper change.
- **Fix**: Add `.andExpect(jsonPath("$.archivedAt").doesNotExist())` to the existing GET and list
  assertions in `VehicleResourceIT`.
- **Decision**: FIXED — added `jsonPath("$.archivedAt").doesNotExist()` to the GET assertion and `$[*].archivedAt` to the list assertion in `VehicleResourceIT`.

### F5 — Every 410 logs a full stack trace at WARN

- **Severity**: 📋 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:179`
- **Detail**: `ArchivedResourceException` carries `@ResponseStatus(GONE)` rather than an explicit
  handler, so routine archived access falls through `handleUncaught` and hits
  `log.warn("Unhandled exception mapped to {}", status, ex)` with a full stack trace. Archived access
  is expected client behavior, not an anomaly. It is also the only type in `service/exception/` using
  `@ResponseStatus` — the other five (`InvalidLookupTypeException`, `EmailAlreadyUsedException`,
  `InvalidPasswordException`, `ReportGenerationException`, `UsernameAlreadyUsedException`) are bare
  `RuntimeException`s with explicit handlers, so this is also a pattern divergence.
- **Fix**: Add a dedicated `@ExceptionHandler(ArchivedResourceException.class)` to
  `ExceptionTranslator` returning `ProblemDetail.forStatus(GONE)` without stack-trace logging; drop
  the `@ResponseStatus`. Fixes the log noise and the pattern divergence together.
- **Decision**: FIXED — dropped `@ResponseStatus` from `ArchivedResourceException` and added an explicit `@ExceptionHandler(ArchivedResourceException.class)` to `ExceptionTranslator`, mirroring `handleInvalidLookupTypeException`. Verified by absence: zero `"Unhandled exception mapped to 410 GONE"` lines in the post-fix build log. The 410 contract (status, problem+json, title, `error.http.410`, path) is unchanged and still pinned by `ExceptionTranslatorIT`.

### F6 — N+1 on the admin archived list

- **Severity**: 📋 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:50`,
  `src/main/java/com/kasztelanic/carcare/service/mapper/AdminVehicleMapper.java:13`
- **Detail**: `Vehicle.owner` and `Vehicle.fuelType` are `@ManyToOne(optional = false)` with no
  `fetch` attribute, i.e. EAGER. `findAllArchived` selects the entity with no `join fetch`, so
  Hibernate issues secondary selects per row and the mapper then dereferences
  `vehicle.getOwner().getLogin()`. Bounded by page size and softened by the `User` L2 cache.
- **Fix**: `select vehicle from Vehicle vehicle join fetch vehicle.owner where vehicle.archivedAt is
  not null ...`, paired with an explicit `countQuery` attribute.
- **Decision**: FIXED — added `join fetch vehicle.owner` plus an explicit `countQuery` to `findAllArchived`.

### F7 — Upper date boundary untested

- **Severity**: 📋 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `src/test/java/com/kasztelanic/carcare/repository/VehicleArchivingRepositoryIT.java:82`
- **Detail**: `dateFrom` inclusive, `dateFrom.minusDays(1)` excluded, and `dateTo` inclusive are all
  covered; `dateTo.plusDays(1)` exclusion is not. The JPQL `between` is correct — the boundary is
  simply unpinned on one side.
- **Fix**: Add a `dateTo.plusDays(1)` exclusion case to the existing boundary test.
- **Decision**: FIXED — added an `afterPeriod` vehicle with a `dateTo.plusDays(1)` refuel; the existing `containsExactly` now pins upper-boundary exclusion.

### F8 — findAllArchived hardcodes ORDER BY while accepting a Pageable

- **Severity**: 📋 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:50`
- **Detail**: This is the project's only `Pageable` method backed by a hand-written `@Query`. Spring
  Data appends a client `?sort=` *after* the query's own `order by vehicle.archivedAt desc,
  vehicle.id asc`, making client sorting silently inert, and an unknown sort property surfaces as a
  500 rather than a 400. Determinism — the plan's actual requirement — is preserved, and the route is
  admin-only, so there is no information-disclosure risk.
- **Fix**: Either drop the hard-coded `order by` and pass the default through `PageRequest`, or
  document that the sort is fixed.
- **Decision**: FIXED (via the non-recommended option, by owner choice) — dropped the hardcoded `order by` from `findAllArchived`; `AdminVehicleServiceImpl.withDeterministicSort` now applies `archivedAt desc, id asc` when the caller supplies no sort, and appends `id asc` as a tiebreaker when it does. Client sort is now honoured instead of being silently inert. `VehicleArchivingRepositoryIT` was updated to match the relocated contract: the repository imposes no default order, and the default is pinned end-to-end by `AdminVehicleResourceIT`.

### F9 — archived_at declared TIMESTAMP rather than DATETIME

- **Severity**: 📋 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/config/liquibase/changelog/20260828120000_vehicle_archiving_changelog.xml:14`
- **Detail**: MariaDB `TIMESTAMP` is 1970–2038 bounded and session-timezone converted; `DATETIME` is
  the safer physical type for a Java `Instant`. H2 validates either, so no test will ever catch this.
  Changing it later means a migration over live archived data.
- **Fix**: Change the column type to `DATETIME` in the changelog now, before archive timestamps exist
  in production.
- **CORRECTION (2026-08-29, during triage)**: This finding was based on incomplete evidence and its
  recommendation is withdrawn. Every existing `Instant`-backed column in this schema —
  `jhi_user.created_date`, `jhi_user.last_modified_date`, `jhi_user.reset_date`, and
  `jhi_persistent_audit_event.event_date` — is already declared `timestamp`. So `archived_at
  TIMESTAMP` *follows* the established project convention rather than diverging from it, and
  changing only this column to `DATETIME` would make it the odd one out while leaving four sibling
  columns carrying the identical 2038/timezone exposure. The underlying MariaDB concern is real but
  systemic and pre-existing — it is a schema-wide question, not a defect in this change.
- **Decision**: PENDING — re-scoped, see correction above

### F10 — Restore emits no alert header

- **Severity**: 📋 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/AdminVehicleResource.java:45`
- **Detail**: Every sibling mutation emits an `X-carcareApp-alert` header (`VehicleResource:58/66/76`,
  `UserResource:192`); `restoreVehicle` emits none, so a client would show no confirmation. The class
  also has no `@Slf4j` logging, unlike `UserResource`. The plan made this conditional ("reuse the
  existing entity-update alert family only if needed by the established mutation header convention")
  and D8 excludes client UI work, so leaving it as-is is defensible.
- **Fix**: Add `.headers(HeaderUtil.createEntityUpdateAlert("vehicle", id.toString()))` to the restore
  response if the admin surface should follow the mutation-header convention.
- **Decision**: FIXED — `restoreVehicle` now returns `HeaderUtil.createEntityUpdateAlert("vehicle", id)`, i.e. the existing `carcareApp.vehicle.updated` key. No new i18n key introduced, so the plan constraint holds.
