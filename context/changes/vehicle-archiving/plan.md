# Vehicle Archiving Implementation Plan

## Goal

Replace destructive vehicle deletion with a soft-archive lifecycle that keeps the existing client
delete contract, hides archived vehicles from ordinary operational workflows, preserves the
historical report/statistics behavior chosen by the owner, and provides administrators with a
discoverable restore workflow.

The implementation must preserve the current Spring Boot 3/Jakarta/JPA conventions, the existing
JWT security model, the injected application `Clock`, and the golden reference behavior for all
non-archiving scenarios.

## Current State Analysis

- `Vehicle` has no archive marker. `VehicleServiceImpl.deleteVehicle` calls `deleteById`, while the
  five event tables have non-cascading vehicle foreign keys, so physical deletion is both unsafe
  and incompatible with the requested lifecycle.
- `VehicleRepository.findByOwnerIsCurrentUser()` feeds the ordinary vehicle list and is the only
  production caller of that method. The owner-scoped single-id query is shared by vehicle detail,
  edit/delete, event creation, event access, single-vehicle reports, and mileage statistics; it
  cannot be globally changed to active-only without breaking historical consumers.
- `findAllByIdAndOwnerIsCurrentUser()` feeds forthcoming events, cost reports, and cost statistics.
  Those consumers need different archive policies: forthcoming events are active-only, while cost
  consumers include matching archived vehicles.
- Reminder repositories use derived queries that do not constrain the joined vehicle. Reminder
  selection must become explicitly active-only without changing selected reminders, mail content,
  date windows, scheduling, or the injected `Clock` behavior. Delivery ordering is currently
  unspecified and remains so.
- Event resources currently authorize by vehicle ownership but do not classify archived vehicles.
  Direct operations need an owned-archived versus unknown/foreign distinction; composite event
  collections should continue to return `200` with archived rows omitted.
- The client deletes through `DELETE api/vehicle/{id}`, then reloads `/api/vehicle/all`; it expects
  the current `200` response/body and `carcareApp.vehicle.deleted` alert header. No client change is
  required.
- `/api/admin/**` is already restricted to `ROLE_ADMIN`, but no admin vehicle controller exists.
  Existing admin resources and `PaginationUtil` provide the project conventions for the new API.
- There is no existing `410 Gone` exception or test. `ExceptionTranslator` already converts
  `@ResponseStatus` exceptions to the project’s RFC7807-style `ProblemDetail` response.
- Golden fixtures are index-exact and contain no archived vehicles. They must remain unchanged;
  dedicated archive fixtures belong outside the golden seed path.

## Desired End State

### Ordinary vehicle workflows

- `GET /api/vehicle/all` returns active owned vehicles only.
- `GET`, `PUT`, and the first `DELETE /api/vehicle/{id}` retain their current successful contract
  for active owned vehicles.
- `DELETE /api/vehicle/{id}` sets `archived_at` and returns the existing vehicle body and deleted
  alert. It does not remove the vehicle, its events, or its image.
- A direct request for an owned archived vehicle returns `410 Gone`; an unknown or foreign id
  retains the existing `404` behavior.
- Repeating `DELETE` for an already archived owned vehicle returns `410 Gone`.
- Normal event creation, direct event reads/updates/deletes, forthcoming-event queries, reminders,
  and normal vehicle lists exclude archived vehicles. Direct access to an event belonging to an
  owned archived vehicle returns `410`; unknown/foreign targets retain the existing not-found
  behavior.
- The composite `/api/events` collection returns `200` and omits archived rows, including when no
  active rows remain.

### Historical consumers

- Read-only mileage and consumption statistics, plus a single-vehicle report for an owned archived
  vehicle, continue to work against the retained data.
- Cost reports and cost statistics automatically append the current user’s archived vehicles that
  have at least one event in the requested inclusive period. The requested active segment keeps
  its existing order; archived rows are appended in deterministic vehicle-id order and are not
  duplicated.
- Reminder selection remains active-only, with the current date/clock and mail-content contracts
  intact; delivery ordering remains unspecified.

### Administrator recovery

- `GET /api/admin/vehicles/archived` returns a paginated list of archived vehicles, sorted by
  `archivedAt desc, id asc`, exposing `id`, `ownerLogin`, `make`, `model`, `licensePlate`, and
  `archivedAt`.
- `PUT /api/admin/vehicles/{id}/restore` clears `archived_at` and returns `200` with the restored
  admin DTO. Restoring an active vehicle is an idempotent successful no-op; an unknown id is `404`.
- The existing `/api/admin/**` security rule is reused. No client UI, owner-facing unarchive route,
  new alert key, or separate audit table is introduced.
- Archived timestamps are set using the injected application `Clock`, making lifecycle tests
  deterministic.

## Key Discoveries and Constraints

- `VehicleRepository.java` must retain an owner-scoped inclusive single-id query for historical
  consumers while gaining policy-specific active and archived-period queries.
- A shared `VehicleScopeService` is the safest seam for active classification. It can use the
  existing inclusive owner lookup to distinguish owned archived (`410`) from absent/foreign (`404`)
  without spreading archive checks through every controller.
- Event repositories should stay inclusive where report/statistics mappers need retained events;
  active filtering belongs at the operational service boundary or in dedicated active queries.
- Archive/restore must mutate a managed `Vehicle` and save it. Avoid bulk updates so Hibernate’s
  second-level cache does not retain stale vehicle state.
- The migration is additive: `vehicles.archived_at TIMESTAMP NULL`, with `NULL` meaning active.
  Add indexes for the archive marker and the five embedded event date paths used by archive-aware
  queries. Do not drop the column while archived data exists.
- Full-context MockMvc integration tests are the project standard. Use the existing golden tests as
  regression guards and add non-golden archive fixtures so golden dataset cardinality and indexes
  do not move.

## Scope Boundaries

This change includes the server schema, archive lifecycle, event/reminder filtering, historical
report/statistics policy, admin list/restore API, RFC7807 `410 Gone` behavior, and integration
coverage.

This change does not include physical deletion, cascading event deletion, an owner unarchive route,
client UI or copy changes, new i18n keys, request flags for including archived data, a dedicated
audit-log table, strict database locking, or regeneration of golden reference fixtures.

## Implementation Approach

1. Add the nullable archive column and query indexes, then introduce repository methods whose names
   make active-only, historical-inclusive, and archived-period policies explicit.
2. Add `VehicleScopeService` as the owner-facing policy boundary. It will classify an inclusive
   owner lookup, provide active owned vehicles for operational paths, assert active ownership for
   direct event targets, and return the final ordered cost-report vehicle list without changing the
   existing active segment order.
3. Replace physical deletion with a clock-based managed-entity archive. Reuse the current response
   body and alert contract. Add an `ArchivedResourceException` mapped to `410 Gone` through the
   existing exception translator.
4. Route operational vehicle/event/reminder paths through active policy methods while leaving
   historical report/statistics paths inclusive where specified.
5. Add a dedicated admin service/resource and DTO for paginated archived-vehicle discovery and
   idempotent restore. Keep the normal `VehicleDto` unchanged so archive state is not accidentally
   exposed in the existing client contract.
6. Add isolated archive fixtures and full-context integration coverage, then run targeted and full
   verification with the required JDK 17 toolchain.

## Critical Implementation Details

### Ownership and status classification

`VehicleScopeService.findActiveOwnedVehicle(id)` should first use the existing owner-scoped
inclusive lookup. A present vehicle with non-null `archivedAt` raises `ArchivedResourceException`;
an empty lookup remains an ordinary not-found result for the caller. The same classification must
be applied after event repositories return an event, using its vehicle owner/archive state.

Do not replace the inclusive repository method with an active-only predicate: that would make
historical single-vehicle reports and mileage statistics silently disappear.

### Cost-period union

Keep the current requested vehicle segment and its ordering. Add a query for the current user’s
archived vehicles where at least one of the five event types has an embedded event date in the
inclusive `[from, to]` range. `VehicleScopeService.findCostVehicles(requestedIds, from, to)` returns
the final ordered `List<Vehicle>`: load the requested segment once through the existing inclusive
query, append archived query results ordered by `Vehicle.id`, and de-duplicate both segments through
an insertion-ordered map keyed by vehicle id. `ReportServiceImpl` and `StatisticServiceImpl` consume
that list directly; do not pass the composed ids through a second unordered `IN` query. The
composition executes inside the callers’ existing read-only transactions so entity mapping remains
within the persistence context. Empty event types and null dates must not create false matches.

### Persistence and cache safety

Set `archivedAt` with `Instant.now(clock)`, call the existing repository save path, and retain the
current transaction semantics. Restore performs the inverse on the managed entity. Do not use a
bulk JPQL/SQL update for either operation because `Vehicle` is second-level cached.

### HTTP contract

Implement `ArchivedResourceException` with `@ResponseStatus(HttpStatus.GONE, reason =
"Resource is archived")` and rely on `ExceptionTranslator` for the existing RFC7807 response shape:
`application/problem+json`, status `410`, request path, and `message: error.http.410`. No new client
alert or translation key is needed. Unknown and foreign resources must continue through the normal
`404` path.

### Migration safety

Create a new timestamped Liquibase changelog, reference it from `master.xml`, and include rollback
for the additive column/indexes. Operational rollback must first restore or otherwise account for
archived rows before dropping `archived_at`; the normal backup/restore scripts remain the recovery
mechanism for database-level rollback.

## Phase 1: Schema and Query Foundation

### Changes

- Update `src/main/java/com/kasztelanic/carcare/domain/Vehicle.java` with nullable `Instant
  archivedAt`, preserving the project’s Lombok/JPA entity style and leaving `VehicleDto` unchanged.
- Add `src/main/resources/config/liquibase/changelog/20260828120000_vehicle_archiving_changelog.xml`
  and reference it from `master.xml`. Add nullable `vehicles.archived_at`, an archive-marker index,
  and vehicle/date indexes for the five event tables used by archive-period queries. Include a
  rollback that removes the new indexes and column only for a controlled pre-data rollback.
- Update `VehicleRepository.java` to make policy boundaries explicit: filter the ordinary owner
  list to active vehicles, retain the existing inclusive owner-scoped single/multi-id methods,
  add an active multi-id method for forthcoming/operational paths, add the archived-period query
  for cost consumers, and add the paginated archived-owner-neutral query needed by the admin list.
- Update `InsuranceRepository`, `InspectionRepository`, and `RoutineServiceRepository` reminder
  queries from unjoined derived predicates to explicit JPQL joins with `vehicle.archivedAt is null`.
  Preserve the current date collection parameters and result types.
- Add focused repository/query tests or extend the relevant integration fixture to prove active,
  archived, period boundary, and owner isolation behavior before service changes land.

For the archived-period query, use explicit `exists` predicates over each event table’s embedded
date rather than joining all five event tables at once; this avoids row multiplication and makes the
“at least one matching event” rule unambiguous.

#### Automated Verification

- Compile and run the repository/schema-focused tests with Java 17.
- Run `HibernateTimeZoneIT` and the existing golden dataset mirror guard to confirm the migration
  validates under H2 and does not alter golden fixture cardinality.
- Run `git diff --check` and inspect generated Liquibase SQL/schema metadata where available.

#### Manual Verification

- Inspect the migration and confirm existing rows receive `NULL`, the column is nullable, and all
  five event date query paths have appropriate indexes.
- Start the application against a disposable database and verify the normal vehicle JSON has no
  new archive field.

#### Success Criteria

- The schema can represent active and archived vehicles without changing existing golden data.
- Repository and reminder query methods expose the required active/historical/period policies.
- The project compiles and the schema-focused tests pass on Java 17.

## Phase 2: Archive Lifecycle, 410 Responses, and Admin Restore

### Changes

- Add `VehicleScopeService` and its implementation under `service/` and `service/impl/`. Centralize
  active owned lookup, archived-versus-not-found classification, active multi-id selection, and
  ordered cost-period `List<Vehicle>` composition via
  `findCostVehicles(requestedIds, from, to)`.
- Add `ArchivedResourceException` under the project’s service exception package with HTTP status
  `410 Gone`; verify it is rendered through the existing `ExceptionTranslator` as the standard
  `ProblemDetail` response.
- Update `VehicleServiceImpl.java` so delete sets `archivedAt` from the injected `Clock`, saves
  the managed entity, and keeps the old body/status/deleted alert. Remove the physical delete and
  any image/event deletion side effects. Route normal get/edit/delete through active classification.
- Update all five event service implementations so vehicle-based creation/list paths require an
  active owned vehicle and direct event-id operations assert that the event’s vehicle is active.
  Preserve inclusive repository access needed by historical report/statistics consumers.
- Add `AdminVehicleDto`, an explicit admin mapper, `AdminVehicleService`/implementation, and
  `AdminVehicleResource` under `/api/admin/vehicles`. Implement:
  - `GET /api/admin/vehicles/archived` with `Pageable`, deterministic `archivedAt desc, id asc`
    ordering, `PaginationUtil` headers, and the six documented DTO fields.
  - `PUT /api/admin/vehicles/{id}/restore` with unscoped id lookup, managed timestamp clearing,
    idempotent active restore, `404` for unknown ids, and a `200` restored admin DTO with
    `archivedAt: null`.
- Apply the existing admin authorization convention at the resource boundary; do not modify the
  already-correct `/api/admin/**` rule in `SecurityConfiguration`.
- Reuse the existing entity-update alert family only if needed by the established mutation header
  convention; introduce no new client-facing key.

#### Automated Verification

- Extend `VehicleResourceIT` for archive success, retained body/alert, archived `410`, repeated
  archive `410`, and unknown/foreign `404` behavior.
- Add `AdminVehicleResourceIT` covering admin authorization, pagination, DTO shape, restore, active
  idempotency, and unknown-id `404`; verify a normal user cannot use either admin route.
- Extend `ExceptionTranslatorIT` (and its test controller fixture if needed) with a `410` response
  contract assertion.
- Extend `RefuelResourceIT`, `RepairResourceIT`, `RoutineServiceResourceIT`,
  `InspectionResourceIT`, and `InsuranceResourceIT` with an owned-archived matrix covering
  create, vehicle-scoped list, direct get, update, and delete behavior as `410 Gone`.
- Run `OwnerIsolationIT` to ensure the same archived vehicle and event targets remain `404` to
  another owner and do not leak archive status.

#### Manual Verification

- Archive an active vehicle, confirm it disappears from the owner list, then request its detail and
  an event route and verify the RFC7807 `410` response.
- Restore it through the admin list/restore API, confirm `archivedAt` becomes null, and verify the
  owner can see and operate on it again.

#### Success Criteria

- DELETE archives without deleting retained data and preserves the old client-facing success
  contract.
- Owned archived direct resources return `410`; unknown and foreign resources remain `404`.
- Administrators can discover and restore archived vehicles through the documented API.

## Phase 3: Forward and Historical Consumer Behavior

### Changes

- Update `EventServiceImpl` and the composite event path to use active multi-id selection and omit
  archived rows while retaining `200` for an empty collection.
- Update `ReportServiceImpl` and `StatisticServiceImpl` to consume the ordered `List<Vehicle>` from
  the scope service directly inside their existing read-only transactions. Preserve the existing
  requested segment, append matching archived vehicles by id without a second repository fetch, and
  keep read-only mileage/consumption and single-vehicle report paths historical-inclusive.
- Ensure direct event-id handlers classify archived parent vehicles before mapping a response, but
  do not add archive predicates to repositories used by historical analytics.
- Verify reminder services consume only active vehicles through the updated repository queries;
  retain `Clock`, due-date windows, selected reminders, and mail content without introducing an
  ordering contract.
- Add comments or method naming where an inclusive historical query is intentionally retained so a
  future blanket archive filter does not regress reporting.

#### Automated Verification

- Extend `EventResourceIT` for active-only request filtering and composite `200` omission behavior,
  including the case where no active rows remain.
- Add a focused `VehicleArchivingAnalyticsIT` for inclusive period boundaries, archived vehicles
  with each event type, deterministic append order, no duplicates, owner isolation, and the
  historical single-vehicle/statistics paths.
- Extend `ReminderSelectionParityIT` with an archived vehicle whose event would otherwise be due;
  assert it is not selected while existing active selection remains unchanged.
- Run `ReportParityIT`, `GoldenDatasetMirrorIT`, and the existing statistics/report integration
  coverage without modifying golden fixtures.

#### Manual Verification

- Compare a cost report/statistics request before and after archiving a vehicle with an in-period
  event: the archived row appears at the end; an out-of-period archived vehicle does not.
- Verify mileage/consumption and single-vehicle report requests still read retained archived data,
  while forthcoming events and reminders omit it.

#### Success Criteria

- Operational event and reminder behavior is active-only, including correct `410` direct access.
- Cost consumers include only period-matching archived vehicles in deterministic appended order.
- Historical read-only consumers remain available for owned archived vehicles.
- Existing golden parity and reminder timing behavior remain green.

## Phase 4: Fixtures, Integration Contracts, and Regression Verification

### Changes

- Add dedicated archive fixtures/helpers in the session test support without calling them from
  `seedGoldenDataset` or changing `GOLDEN_HANDLES`.
- Complete lifecycle and wire-contract coverage across `VehicleResourceIT`, the five direct event
  resource ITs, `OwnerIsolationIT`, `EventResourceIT`, the new admin IT, and focused archive
  analytics tests. Assert normal vehicle JSON remains backward-compatible and the delete alert/body
  remain unchanged.
- Add the `410` problem response fixture assertion through `ExceptionTranslatorIT` so status,
  content type, path, and `error.http.410` message are locked down.
- Run the complete verification suite with the repository’s Java 17 setup and the existing test
  JVM agent/timezone arguments where required. Review the final diff for accidental client/golden
  changes and document any generated-schema output that is intentionally absent.

#### Automated Verification

- Run targeted unit/integration tests for vehicle, all five direct event resources, composite events,
  admin, exception, reminder, report, and statistics behavior.
- Run `./mvnw verify` with `JAVA_HOME` set to the installed Java 17 distribution and the project’s
  existing Byte Buddy/JVM argument configuration.
- Run `git diff --check`, inspect test reports, and verify no files under the golden fixture data
  changed; any golden diff is a release-blocking regression requiring investigation.

#### Manual Verification

- Exercise the full archive → list omission → direct `410` → admin discovery → restore → normal
  operation flow against a disposable app/database.
- Confirm the frozen client still deletes successfully and reloads the active vehicle list without
  needing a client artifact or i18n change.
- Review migration rollback notes and confirm no physical vehicle/event deletion is present.

#### Success Criteria

- The complete test suite passes with the mandated Java 17 toolchain.
- Archive, restore, `410`, historical analytics, reminder, ownership, and client-wire contracts are
  covered by automated tests.
- Golden references and unrelated client/build artifacts remain unchanged.

## Testing Strategy

Use unit-level coverage for the archive status exception and scope-service ordering/classification
helpers, then full-context MockMvc integration tests for security, serialization, controller advice,
repositories, and transactions. Keep archive fixtures isolated from golden reference data. The
highest-risk assertions are:

- active versus archived versus foreign status classification;
- inclusive cost-period matching across all five event types and both date boundaries;
- historical single-vehicle reads after archive;
- reminder exclusion;
- idempotent admin restore and cache-visible state after restore;
- unchanged delete response/body/header and normal `VehicleDto` JSON.

## Performance and Operational Notes

- Use the archive-marker and event vehicle/date indexes for list, reminder, and period-existence
  queries; keep admin discovery paginated.
- The cost union retrieves each vehicle segment once, returns the final ordered entity list, and
  avoids both a second unordered bulk fetch and five-way join multiplication.
- Managed save operations preserve second-level cache coherence. Existing transaction boundaries
  remain the concurrency model; no explicit locking is required for this slice.
- The additive migration is backward-compatible for existing rows. Dropping it is not a safe normal
  rollback after archive timestamps have been written; restore data first or use the operational
  backup/restore procedure.

## References

- `context/changes/vehicle-archiving/research.md` — repository/service call graph, owner decisions,
  client contract, migration and golden-fixture constraints.
- `src/main/java/com/kasztelanic/carcare/domain/Vehicle.java`
- `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java`
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java`
- `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java`
- `src/main/java/com/kasztelanic/carcare/service/impl/ReportServiceImpl.java`
- `src/main/java/com/kasztelanic/carcare/service/impl/StatisticServiceImpl.java`
- `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java`
- `src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java`
- `src/main/java/com/kasztelanic/carcare/web/rest/EventResource.java`
- `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`
- `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java`
- `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java`
- `src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java`
- `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java`
- `src/test/java/com/kasztelanic/carcare/web/rest/EventResourceIT.java`
- `src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java`
- `src/test/java/com/kasztelanic/carcare/golden/ReportParityIT.java`
- `src/test/java/com/kasztelanic/carcare/golden/ReminderSelectionParityIT.java`
- `src/test/java/com/kasztelanic/carcare/golden/GoldenDatasetMirrorIT.java`
- `src/main/scripts/backup.sh` and `src/main/scripts/restore.sh`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Schema and Query Foundation

#### Automated

- [x] 1.1 Compile and run the repository/schema-focused tests with Java 17. — 718a011
- [x] 1.2 Run `HibernateTimeZoneIT` and the existing golden dataset mirror guard to confirm the migration validates under H2 and does not alter golden fixture cardinality. — 718a011
- [x] 1.3 Run `git diff --check` and inspect generated Liquibase SQL/schema metadata where available. — 718a011

#### Manual

- [x] 1.4 Inspect the migration and confirm existing rows receive `NULL`, the column is nullable, and all five event date query paths have appropriate indexes. — 718a011
- [x] 1.5 Start the application against a disposable database and verify the normal vehicle JSON has no new archive field. — 718a011

### Phase 2: Archive Lifecycle, 410 Responses, and Admin Restore

#### Automated

- [x] 2.1 Extend `VehicleResourceIT` for archive success, retained body/alert, archived `410`, repeated archive `410`, and unknown/foreign `404` behavior. — 8a917f7
- [x] 2.2 Add `AdminVehicleResourceIT` covering admin authorization, pagination, DTO shape, restore, active idempotency, and unknown-id `404`; verify a normal user cannot use either admin route. — 8a917f7
- [x] 2.3 Extend `ExceptionTranslatorIT` and its test controller fixture if needed with a `410` response contract assertion. — 8a917f7
- [x] 2.4 Extend `RefuelResourceIT`, `RepairResourceIT`, `RoutineServiceResourceIT`, `InspectionResourceIT`, and `InsuranceResourceIT` with an owned-archived matrix covering create, vehicle-scoped list, direct get, update, and delete behavior as `410 Gone`. — 8a917f7
- [x] 2.5 Run `OwnerIsolationIT` to ensure the same archived vehicle and event targets remain `404` to another owner and do not leak archive status. — 8a917f7

#### Manual

- [x] 2.6 Archive an active vehicle, confirm it disappears from the owner list, then request its detail and an event route and verify the RFC7807 `410` response. — 8a917f7
- [x] 2.7 Restore it through the admin list/restore API, confirm `archivedAt` becomes null, and verify the owner can see and operate on it again. — 8a917f7

### Phase 3: Forward and Historical Consumer Behavior

#### Automated

- [x] 3.1 Extend `EventResourceIT` for active-only request filtering and composite `200` omission behavior, including the case where no active rows remain.
- [x] 3.2 Add a focused `VehicleArchivingAnalyticsIT` for inclusive period boundaries, archived vehicles with each event type, deterministic append order, no duplicates, owner isolation, and the historical single-vehicle/statistics paths.
- [x] 3.3 Extend `ReminderSelectionParityIT` with an archived vehicle whose event would otherwise be due; assert it is not selected while existing active selection remains unchanged.
- [x] 3.4 Run `ReportParityIT`, `GoldenDatasetMirrorIT`, and the existing statistics/report integration coverage without modifying golden fixtures.

#### Manual

- [x] 3.5 Compare a cost report/statistics request before and after archiving a vehicle with an in-period event: the archived row appears at the end; an out-of-period archived vehicle does not.
- [x] 3.6 Verify mileage/consumption and single-vehicle report requests still read retained archived data, while forthcoming events and reminders omit it.

### Phase 4: Fixtures, Integration Contracts, and Regression Verification

#### Automated

- [ ] 4.1 Run targeted unit/integration tests for vehicle, all five direct event resources, composite events, admin, exception, reminder, report, and statistics behavior.
- [ ] 4.2 Run `./mvnw verify` with `JAVA_HOME` set to the installed Java 17 distribution and the project’s existing Byte Buddy/JVM argument configuration.
- [ ] 4.3 Run `git diff --check`, inspect test reports, and verify no files under the golden fixture data changed; any golden diff is a release-blocking regression requiring investigation.

#### Manual

- [ ] 4.4 Exercise the full archive → list omission → direct `410` → admin discovery → restore → normal operation flow against a disposable app/database.
- [ ] 4.5 Confirm the frozen client still deletes successfully and reloads the active vehicle list without needing a client artifact or i18n change.
- [ ] 4.6 Review migration rollback notes and confirm no physical vehicle/event deletion is present.
