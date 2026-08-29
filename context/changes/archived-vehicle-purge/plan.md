# Archived-Vehicle Purge & User-Deletion Disposition Implementation Plan

## Overview

Fix FU-1 from the vehicle-archiving implementation review: user deletion is permanently blocked by
the `vehicles.owner_id` FK (currently an opaque 500), and the archive has no terminal state. The
chosen disposition is **tombstone reassignment** — `deleteUser` reassigns owned vehicles to
`anonymoususer` (archiving active ones) and then deletes the user, preserving the 204 contract with
no destructive operation — plus an interlocked, audited **admin purge endpoint**
(`DELETE /api/admin/vehicles/{id}/purge`) that gives the archive a terminal state as pure
housekeeping (P1: no legal or compliance driver exists in either direction).

## Current State Analysis

- `UserService.deleteUser` (`src/main/java/com/kasztelanic/carcare/service/UserService.java:221-227`)
  does a bare `userRepository.delete(user)`. For any user owning a vehicle — active or archived —
  the `vehicles.owner_id` FK fires at commit, Spring translates it to
  `DataIntegrityViolationException`, and `ExceptionTranslator.handleUncaught` returns 500. The user
  row survives but its caches were already evicted.
- Nothing cascades: zero `@OneToMany`/`cascade`/`orphanRemoval` in `src/main`, no `ON DELETE` clause
  on any of the nine FKs, and no repository declares any delete method beyond `JpaRepository`
  inheritance.
- `AdminVehicleService` owns the admin lifecycle (`findArchived`, `restoreVehicle`) with
  deliberately owner-blind `findById`. `restoreVehicle`
  (`service/impl/AdminVehicleServiceImpl.java:47-54`) has **no archived-state guard** — a purge
  modelled on it would hard-delete a live vehicle, which is why the interlock is explicit new
  behaviour, not copied precedent.
- `Vehicle.owner` is a mutable `@ManyToOne(optional = false)` with `@Setter`
  (`domain/Vehicle.java:74-80`); reassignment is a plain setter, and entity-level `save()` keeps
  the ehcache L2 region coherent.
- All `VehicleRepository` owner queries are `?#{principal.username}`-scoped
  (`repository/VehicleRepository.java:19-61`) — they resolve to the *acting admin*, so they cannot
  load the deleted user's vehicles. An un-scoped query does not exist and must be added.
- All five event repositories already expose un-scoped `findByVehicleId(Long)` selects
  (e.g. `repository/RefuelRepository.java:23-24`) — the purge can load-then-`deleteAll`, which
  routes through Hibernate's `EntityDeleteAction` and evicts L2 correctly.
- Reminder queries are global but exclude archived vehicles at the query level
  (`vehicle.archivedAt is null` in `InsuranceRepository`/`InspectionRepository`/
  `RoutineServiceRepository` `findByValidThruIn`/`findByNextByDateIn`) — so archive-on-reassign
  guarantees the tombstone never receives reminder mail.
- `anonymoususer` is seeded (`config/liquibase/data/user.csv`, id 2), has no authorities, and is
  already filtered from the admin user list (`UserService.java:246` via `Constants.ANONYMOUS_USER`).
- No test anywhere deletes a vehicle-owning user (`grep vehicle` over `UserResourceIT`,
  `UserServiceIT`, `AccountResourceIT` returns zero hits), which is how the narrowing went unnoticed.
- `PersistentAuditEvent` + `PersistenceAuditEventRepository` allow writing an audit row directly
  (setters on principal/auditEventType/auditEventDate/data); the actuator path
  (`CustomAuditEventRepository.add`) is `REQUIRES_NEW` and would commit the audit even when the
  purge rolls back — write the entity directly instead. `AuditResourceIT` mixes containment
  assertions with positional `$[0]` checks on an unfiltered `GET /management/audits`
  (`AuditResourceIT.java:70-74`), and its `@BeforeEach deleteAll()` rolls back — so committed
  audit rows are not provably harmless. The purge IT must therefore clean up its own
  `VEHICLE_PURGED` row (Phase 2 §5).
- `User` has no Lombok builder — plain class with setters (`domain/User.java:38,131`).
  `VehicleDetails` fields carry `@Getter @Setter` (`domain/VehicleDetails.java`), so the image
  filename is mutable. `ImageStorageService.save(byte[], fileType)` returns the stored filename;
  `delete(name)` is idempotent and never throws.
- Three DELETE response conventions coexist; this plan standardises on 204 + alert header for the
  new endpoint (matching `UserResource`, the other irreversible admin delete), and reuses
  `carcareApp.vehicle.deleted` via `HeaderUtil.createEntityDeletionAlert("vehicle", id)` — no client
  bundle change, per the i18n contract.

## Desired End State

- `DELETE /api/users/{login}` returns **204 + `userManagement.deleted`** for every user except
  `system` and `anonymoususer` (protected: 400). For a vehicle-owning user, all owned vehicles —
  active and archived — are reassigned to `anonymoususer` in the same transaction, with previously
  active vehicles archived (`archivedAt` from the injected `Clock`). The user row is gone. Reminder
  mail never reaches the tombstone.
- Any `DataIntegrityViolationException` reaching the advice translates to **409** (logged at warn,
  no error-level stack trace) instead of an opaque 500 — covering residual FK paths and in-use
  lookup deletion.
- `DELETE /api/admin/vehicles/{id}/purge` (ADMIN only): **404** for an unknown id, **409** for an
  active vehicle (interlock), **204 + `carcareApp.vehicle.deleted`** for an archived vehicle. A
  successful purge removes the vehicle row and all five event tables' rows in FK-safe order, deletes
  the image file **after commit**, and writes a `VEHICLE_PURGED` audit event **inside the
  transaction** (acting admin as principal; vehicle id, owner login, and event counts in the data
  map).
- Accepted blast radius (documented, deliberate): after a purge, the vehicle id returns **404** on
  owner-scoped reads where an archived vehicle returned 410, and its costs vanish from reports and
  statistics across all date windows. This is the irreversible outcome the purge exists to produce;
  the owner accepted it as housekeeping (P1) gated behind the archive interlock (P3).
- The full suite is green, including the golden parity tests and the absolute-count assertions
  (`AdminVehicleResourceIT` `X-Total-Count == "2"`, `ReminderSelectionParityIT` `hasSize(6)`),
  proving the new non-transactional tests leak no rows into the shared H2.

### Key Discoveries:

- The removed reference implementation (`git show 718a011^:...VehicleResourceIT.java`, lines
  181-187) purged via raw JDBC in exactly the required FK order — reuse its ordering and its
  `NOT_SUPPORTED` + try/finally test discipline.
- Hibernate L2 is enabled in dev/prod and disabled in tests
  (`application-dev.yml:44`, `application-prod.yml:49`, `application-test.yml:38`) — the single
  most dangerous implementation mistake (bulk `@Modifying` delete) is invisible to the entire suite.
- `jhi_persistent_audit_event` stores the actor as a plain `principal` string with no FK — deleting
  a user silently orphans their audit rows; P1 accepts this (erasure need not be complete).
- `reminder_advances` is a global, `@Immutable`, read-only-cached lookup with no owner or vehicle
  column — the purge must not touch it.
- The test suite runs against a JVM-wide shared H2 (`DB_CLOSE_DELAY=-1`, `forkCount=1`,
  `reuseForks=true`): a leaked committed row is not a local failure but breaks the three
  absolute-count/golden ITs named above. Every committing test needs try/finally cleanup.

## What We're NOT Doing

- **No client changes** — no new `carcareApp.*` or `error.http.*` keys; the purge reuses
  `carcareApp.vehicle.deleted` and user deletion keeps `userManagement.deleted`.
- **No bulk purge** — no purge-all-for-owner or purge-by-filter endpoint. Purge is per-vehicle by
  id (P1: rare, interlocked, non-bulk).
- **No `restoreVehicle` archived-state guard** — considered and declined for this change (existing
  IT contract `restoringAnActiveVehicleIsAnIdempotentSuccess`); file as a follow-up if wanted.
- **No last-admin guard** — only `system` and `anonymoususer` are protected (P7).
- **No permanent purge ledger** — the 30-day audit event is the record (P4); no new table.
- **No schema change** — no Liquibase changelog. The interlock uses existing `archived_at`, the
  tombstone uses existing `owner_id`, the audit reuses `jhi_persistent_audit_event`.
- **No erasure of orphaned audit rows or other users' data referencing the principal string** —
  P1 explicitly accepts incomplete erasure.
- **No change to the owner-scoped vehicle/event API surface** — D1–D8 contracts from
  vehicle-archiving stand; the only client-visible transition is the documented 410→404 for a purged
  id.

## Implementation Approach

Two separable halves, ordered by urgency. Phase 1 fixes the acute user-facing bug (the 500) with
the tombstone disposition plus the two guard rails decided with the owner (protected logins,
class-level FK 409). Phase 2 adds the archive's terminal state as pure housekeeping — explicitly
*not* load-bearing for user deletion, which is what makes each phase independently shippable.
Phase 3 records the roadmap slice and proves the whole suite from a clean build.

All new destructive code uses **entity-level repository operations only** (`deleteAll(entities)`,
`delete(entity)`, `save(entity)`) — never `@Modifying` bulk deletes — so Hibernate's
`EntityDeleteAction`/`EntityUpdateAction` keep the ehcache L2 regions coherent in dev/prod.
All new date-dependent code injects the `Clock` bean (never `Instant.now()`/`LocalDate.now()`
directly), per the repo convention pinned by `ReminderSelectionParityIT`.

## Critical Implementation Details

- **Timing & lifecycle (purge ordering)** — the image filename must be read from
  `vehicleDetails` *before* any row delete; the five event deletes must all precede the vehicle
  delete (FKs are non-deferrable, enforced per statement); the image file may only be deleted
  *after commit* (a `TransactionSynchronization` checking `STATUS_COMMITTED`) — a file delete
  followed by a rollback is unrecoverable and invisibly corrupts (`load()` silently substitutes
  `default.png`). The audit event is written *inside* the transaction so no audit survives a
  rolled-back purge.
- **Debug & observability (the L2 trap no test can catch)** — Hibernate L2 is on in dev/prod and
  off in tests. A bulk `@Modifying` delete would pass all 255+ tests and serve stale vehicles from
  ehcache for up to `time-to-live-seconds: 3600` in production. Entity-level deletes are mandatory;
  the only actual proof of eviction is the phase-2 manual check (immediate 404 on a dev instance
  with L2 on, well inside the cache TTL).
- **State sequencing (deleteUser)** — reassign-and-save the vehicles *before*
  `userRepository.delete(user)`: the FK fires per statement, so deleting the user first would abort
  the transaction mid-flight. The existing `clearUserCaches(user)` before commit becomes correct
  (rather than premature) precisely because the commit now succeeds.

## Phase 1: User-Deletion Disposition — Tombstone, Guards, FK 409

### Overview

Make `DELETE /api/users/{login}` work for vehicle owners via tombstone reassignment, protect the
seeded accounts, and translate the `DataIntegrityViolationException` class to 409. This phase alone
resolves the acute FU-1 symptom; it is valuable standalone and shippable without phase 2.

### Changes Required:

#### 1. Un-scoped owner query

**File**: `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java`

**Intent**: `UserService.deleteUser` runs under the *admin's* principal, so the existing
`?#{principal.username}` owner queries would resolve to the admin and find nothing. The service
needs every vehicle — active and archived — owned by the user being deleted.

**Contract**: new `@Query` method `List<Vehicle> findAllByOwnerLogin(@Param("login") String login)`
selecting `where vehicle.owner.login = :login` with **no** principal reference and **no** archived
filter. Carry a short comment explaining why it must stay un-scoped (mirror the comment style
already used in this repository for the admin queries).

#### 2. ProtectedLoginException + handler

**Files**: `src/main/java/com/kasztelanic/carcare/service/exception/ProtectedLoginException.java`
(new), `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: deleting `system` (a live admin login) or `anonymoususer` (the tombstone target) must
be a clean, attributable 400 rather than a silent success or the destruction of the tombstone
account.

**Contract**: bare `RuntimeException` in `service/exception/` following the
`ArchivedResourceException` shape — no Spring Web imports (`ArchTest` forbids `service` depending
on `web`), constructor carrying the login for the title. In `ExceptionTranslator`, an
`@ExceptionHandler(ProtectedLoginException.class)` → `ProblemDetail.forStatus(BAD_REQUEST)`,
`title = ex.getMessage()`, routed through `handleExceptionInternal` — the exact pattern of
`handleArchivedResourceException` (ExceptionTranslator.java:128-135).

#### 3. DataIntegrityViolationException class handler

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: FK violations are a bug class, not one bug — in-use lookup deletion very likely 500s
today the same way user deletion did. Translate the class, not the instance (P5).

**Contract**: `@ExceptionHandler(DataIntegrityViolationException.class)` → 409 `CONFLICT`,
`ProblemDetail` with a descriptive title (e.g. "Data integrity violation"), `log.warn` with the
exception (routine client behaviour — no error-level stack trace, matching the 094e32b rationale
recorded on `handleArchivedResourceException`), routed through `handleExceptionInternal`. The
message falls back to `error.http.409`; whether the frozen client renders that key is a known
cosmetic limitation consistent with existing handled 4xx (e.g. `InvalidLookupTypeException`) —
accept it, do not mint new keys. Blast-radius note: as a class-level handler it also flips
create-path unique-constraint races — `registerUser`'s duplicate-race flush
(UserService.java:86-97) and the lookup-type unique columns (`UC_FUEL_TYPESENGLISH`/`POLISH`/`TYPE`)
— from error-logged 500 to warn 409. No existing test pins a 500 on any DIV path (verified), so
this is an accepted improvement.

#### 4. UserService.deleteUser — guards + tombstone reassignment

**File**: `src/main/java/com/kasztelanic/carcare/service/UserService.java`

**Intent**: perform the owner-decided disposition: refuse protected logins, reassign all owned
vehicles to the tombstone (archiving actives), then delete the user — atomically, in the one
transaction the class-level `@Transactional` already provides.

**Contract**: add `VehicleRepository` and `Clock` to the `@RequiredArgsConstructor` fields.
`deleteUser(String login)` flow:

1. If `login` equals `Constants.SYSTEM_ACCOUNT` or `Constants.ANONYMOUS_USER` → throw
   `ProtectedLoginException`.
2. `userRepository.findOneByLogin(login)` — if absent, do nothing (existing behaviour; the
   nonexistent-login 204 contract from S-02 stands).
3. Resolve the tombstone: `userRepository.findOneByLogin(Constants.ANONYMOUS_USER).orElseThrow(...)`
   (an `IllegalStateException` — the seed guarantees it and the guard protects it).
4. `vehicleRepository.findAllByOwnerLogin(login)` → for each vehicle: `setOwner(tombstone)`;
   `if (getArchivedAt() == null) setArchivedAt(clock.instant())`; `vehicleRepository.save(vehicle)`.
   Entity-level saves only — they update the `Vehicle` L2 region correctly.
5. `userRepository.delete(user)` (JPA removes the `jhi_user_authority` join rows automatically),
   then the existing `clearUserCaches(user)` — now correct because the commit succeeds.

#### 5. UserDeletionDispositionIT (new, non-transactional)

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/UserDeletionDispositionIT.java` (new)

**Intent**: pin the real commit path — the FK, the reassignment, archive-on-reassign, and the
reminder exclusion — none of which a class-`@Transactional` test can observe (rollback hides
commit-time FK violations, and `DataIntegrityViolationException` marks the shared transaction
rollback-only).

**Contract**: extends `AbstractSessionIT`; committing test methods override the class annotation
with `@Transactional(propagation = Propagation.NOT_SUPPORTED)`; every committing method wraps its
body in try/finally with manual cleanup via a shared JDBC purge helper (Phase 2 adds it to
`SessionFixtures`; until then a private copy in this IT, deleted when the fixture helper lands).
Fixture pattern from the removed reference test: create a dedicated committed user (unique login,
activated, 60-char encoded password, `created_by` set — `User` has no builder, use setters),
vehicles and events via `SessionFixtures` (repository saves auto-commit under `NOT_SUPPORTED`).
Cases:

- Delete a user owning one active + one archived vehicle, both with events → **204**, header
  `X-carcareApp-alert: userManagement.deleted`; user row gone; both vehicles owned by
  `anonymoususer`; the previously-active vehicle has `archivedAt != null`; the three reminder
  queries (`findByValidThruIn` × 2, `findByNextByDateIn`) return no events for those vehicles.
- Delete a user with no vehicles → **204** (regression guard).
- Delete a nonexistent login → **204** (S-02 contract: "including nonexistent login").

Cleanup (finally): purge the created vehicles' event rows + vehicle rows by captured ids via JDBC;
delete the created user row if it survived a failed deletion.

#### 6. Guard and lookup-409 coverage in existing ITs

**Files**: `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`,
`src/test/java/com/kasztelanic/carcare/web/rest/LookupMaintenanceResourceIT.java`

**Intent**: cover the protected-login guard and exercise the new class handler on a second member
of the FK bug class — in-use lookup deletion, which is currently untested.

**Contract**:

- `UserResourceIT`: `DELETE /api/users/system` and `DELETE /api/users/anonymoususer` → **400**
  with the expected title. Transactional is fine — the guard rejects before any write.
- `LookupMaintenanceResourceIT`: one `NOT_SUPPORTED` method — create a committed vehicle on a
  dedicated fuel type, then `DELETE /api/fuel-type/{type}` via the API → **409**, and the fuel type
  row still exists (the delete rolled back). `SessionFixtures.vehicleFor(String)` hardcodes the
  shared `fixture-fuel` row, so Phase 1 first adds a `vehicleFor(String ownerLogin, FuelType
  fuelType)` overload to `SessionFixtures` (the dedicated type is created via the API or a
  repository save). try/finally cleans the vehicle rows **then the dedicated fuel-type row** —
  FK-safe order, per the leak discipline. The failed delete persists nothing, so no user cleanup
  is needed.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify -Dit.test=UserDeletionDispositionIT` passes
- `./mvnw verify -Dit.test='UserResourceIT,LookupMaintenanceResourceIT'` passes
- Full `./mvnw verify` green — in particular no regression in `AdminVehicleResourceIT`,
  `ReminderSelectionParityIT`, `ReportParityIT`, `UserResourceIT` (the leak-sensitive set)
- `ArchTest` green (the new exception carries no web imports)

#### Manual Verification:

- Against dev MariaDB: create a user with vehicles via the API, delete the user as admin → 204;
  the vehicles appear in `GET /api/admin/vehicles/archived` with `ownerLogin: "anonymoususer"`
- Reminder schedule unaffected (the tombstone-owned vehicles are archived, hence excluded)

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 2: Admin Purge Endpoint — Interlock, Audit, Image Cleanup

### Overview

Give the archive a terminal state: a per-vehicle, admin-only, archived-first hard purge with
FK-safe ordering, an in-transaction audit event, and after-commit image deletion. Pure
housekeeping by design — nothing in phase 1 depends on it.

### Changes Required:

#### 1. VehicleNotArchivedException + handler

**Files**: `src/main/java/com/kasztelanic/carcare/service/exception/VehicleNotArchivedException.java`
(new), `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: the interlock (P3) must reject an active vehicle with a meaningful 409 rather than
silently hard-deleting live data — the missing-guard flaw documented on `restoreVehicle` must not
be inherited.

**Contract**: bare `RuntimeException` in `service/exception/` (same shape as
`ProtectedLoginException`); `@ExceptionHandler` → 409 `CONFLICT`, `title = ex.getMessage()`,
`handleExceptionInternal` routing.

#### 2. SessionFixtures — image, committed-user, and JDBC-purge support

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`

**Intent**: the non-transactional ITs need three things the fixtures do not yet provide: a real
image file on disk, a committed test user, and a shared try/finally cleanup helper.

**Contract**:

- Image helper: inject `ImageStorageService`; `imageFor(Vehicle)` (or similarly named) saves real
  bytes via `imageStorageService.save(bytes, "png")`, sets the returned filename on
  `vehicle.getVehicleDetails()`, and saves the vehicle.
- Committed-user helper: creates a user with a unique login (plain setters — no builder),
  activated, valid password hash and `created_by`, and saves it.
- JDBC purge helper: `purgeRowsFor(Collection<Long> vehicleIds)` deleting the five event tables
  then `vehicles` by id — the ordering and style of the removed reference implementation. Raw JDBC
  cleanup is safe in tests (L2 is off in the test profile). If a private copy was created in
  `UserDeletionDispositionIT` during phase 1, remove it in favour of this shared helper.

#### 3. AdminVehicleService.purgeVehicle

**Files**: `src/main/java/com/kasztelanic/carcare/service/AdminVehicleService.java`,
`src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java`

**Intent**: implement the purge with the interlock, FK ordering, L2-safe deletes, in-TX audit, and
after-commit image deletion — all inside the transaction the class-level `@Transactional` already
provides.

**Contract**: `void purgeVehicle(Long id)` on the interface; new constructor injections: the five
event repositories, `ImageStorageService`, `PersistenceAuditEventRepository`, `Clock`. Flow:

1. `vehicleRepository.findById(id)` → empty → `NoSuchElementException` (the existing handler
   already maps it to 404).
2. `archivedAt == null` → throw `VehicleNotArchivedException` (message naming the id).
3. Capture `vehicle.getVehicleDetails().getImage()` **before** any delete.
4. Load the five event lists via the existing `findByVehicleId(id)` selects.
5. Register a `TransactionSynchronization` (`TransactionSynchronizationManager`) whose
   `afterCompletion` deletes the image file **only on `STATUS_COMMITTED`** —
   `ImageStorageService.delete` is idempotent and never throws (the impl delegates to Commons-IO
   `FileUtils.deleteQuietly`), so the hook needs no error handling.
6. `deleteAll` each event list, then `vehicleRepository.delete(vehicle)` — entity-level only; no
   `@Modifying` anywhere (see Critical Implementation Details).
7. Save a `PersistentAuditEvent` **inside this transaction**: type `VEHICLE_PURGED`, principal =
   `SecurityUtils.getCurrentUserLogin().orElse("unknown")` (the acting admin), `auditEventDate` =
   `clock.instant()`, data map with vehicle id, owner login, per-event-type counts, and the image
   filename — every value under the 255-char `EVENT_DATA_COLUMN_MAX_LENGTH`.

#### 4. AdminVehicleResource — DELETE /{id}/purge

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/AdminVehicleResource.java`

**Intent**: expose the purge as an admin-only sub-resource verb, following the `/{id}/restore`
precedent and disambiguating from the owner-facing archive verb `DELETE /api/vehicle/{id}`.

**Contract**: `@DeleteMapping("/{id}/purge")` calling `adminVehicleService.purgeVehicle(id)`,
returning **204** with `HeaderUtil.createEntityDeletionAlert("vehicle", id.toString())` — emits
`carcareApp.vehicle.deleted`, no client change. Unknown id → 404 via the `NoSuchElementException`
path. The class-level `@PreAuthorize` ADMIN guard already covers the new method.

#### 5. AdminVehiclePurgeIT (new, non-transactional)

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AdminVehiclePurgeIT.java` (new)

**Intent**: pin the destructive path end-to-end — committed row removal, the after-commit image
hook, and the audit event — none of which a rolled-back test can observe (a rolled-back
transaction never runs `afterCompletion` with `STATUS_COMMITTED`, so the image assertion would be
vacuous).

**Contract**: extends `AbstractSessionIT`; committing methods `@Transactional(propagation =
NOT_SUPPORTED)` + try/finally cleanup via the `SessionFixtures` JDBC helper **and deletion of the
committed `VEHICLE_PURGED` audit row** (child rows in `jhi_persistent_audit_evt_data` first, then
the parent — the only committed audit rows the purge tests create). Use the seeded `user`
as owner (no user cleanup needed; the seeded account must survive). Cases:

- Purge an archived vehicle carrying all five event types and a real image file → **204**, header
  `X-carcareApp-alert: carcareApp.vehicle.deleted`; vehicle row gone; each of the five event tables
  has zero rows for the id; the image file no longer exists in the data directory; a
  `VEHICLE_PURGED` `PersistentAuditEvent` exists with principal `admin` and the vehicle id in its
  data.
- Purge an active vehicle → **409**; vehicle and its events still present.
- Purge an unknown id → **404**.
- Purge as a non-admin → **403** (no commit needed; any propagation is fine).

### Success Criteria:

#### Automated Verification:

- `./mvnw verify -Dit.test=AdminVehiclePurgeIT` passes
- Full `./mvnw verify` green — `AdminVehicleResourceIT`'s absolute `X-Total-Count == "2"` and
  `ReminderSelectionParityIT`'s `hasSize(6)` prove no committed rows leak into the shared H2
- `grep -rn "@Modifying" src/main/java` returns nothing

#### Manual Verification:

- Against dev MariaDB (L2 **on**): purge an archived vehicle → an immediate subsequent
  `GET /api/vehicle/{id}` returns 404 well inside the 3600s cache TTL (proves L2 eviction, which
  no test can prove); the image file is gone from the data directory; the `VEHICLE_PURGED` audit
  event is visible via `/management/audits` or the DB

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 3: Documentation & Full Verification

### Overview

Record the new roadmap slice and prove the whole change from a clean build plus a combined
end-to-end smoke of the two flows working together (delete user → purge the tombstone-owned
vehicles).

### Changes Required:

#### 1. Roadmap S-08 entry

**File**: `context/foundation/roadmap.md`

**Intent**: the research places this change as slice S-08 (prerequisite S-05, Stream D); like S-06
it traces to an implementation-review follow-up rather than a PRD FR.

**Contract**: add the S-08 row to the slices table and a short section following the existing
per-slice format (id, change id `archived-vehicle-purge`, prerequisite S-05, Stream D, status
`proposed` — `/10x-archive` flips it, per the mechanism noted in the research). Reference FU-1 /
`vehicle-archiving/reviews/impl-review.md` as the origin instead of a PRD FR.

#### 2. Clean full verification

**File**: none (verification only)

**Intent**: prove the entire suite passes from a cold Maven state with no ordering luck.

**Contract**: `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` then
`./mvnw verify` (optionally after `./mvnw clean`), confirming the full unit + integration count is
green — the pre-change baseline is 38 unit + 217 integration tests; the new ITs grow the
integration count.

### Success Criteria:

#### Automated Verification:

- Clean full `./mvnw verify` green from scratch
- `grep -n "S-08" context/foundation/roadmap.md` finds the new entry

#### Manual Verification:

- Combined end-to-end on dev MariaDB: create a user with vehicles and events → delete the user
  (204, vehicles tombstoned+archived) → purge each tombstone-owned archived vehicle (204) →
  `GET /api/admin/vehicles/archived` is empty again; a cost report for the period no longer
  contains the purged vehicle's costs (the documented, accepted blast radius)

---

## Testing Strategy

### Unit Tests:

- No new unit-test surface is required by this change: the logic is service/REST behaviour already
  covered by integration tests, and the repo's convention (JHipster) keeps service behaviour in
  `*IT` classes running the full context.

### Integration Tests:

- `UserDeletionDispositionIT` (new) — the commit-path truth for tombstone reassignment: FK
  succeeds, reassignment + archive-on-reassign land, reminder queries exclude the tombstone's
  events, 204 contract preserved for empty and nonexistent users.
- `AdminVehiclePurgeIT` (new) — the destructive path: FK-ordered row removal, after-commit image
  deletion, in-TX audit event, interlock 409, unknown 404, non-admin 403.
- `UserResourceIT` / `LookupMaintenanceResourceIT` (extended) — protected-login 400s; in-use
  fuel-type deletion → 409 (second member of the FK bug class).
- Leak discipline is part of the strategy: every committing (`NOT_SUPPORTED`) test cleans up in
  `finally` so the shared-H2 absolute-count assertions (`AdminVehicleResourceIT`,
  `ReminderSelectionParityIT`) and the golden mirror (`ReportParityIT`,
  `GoldenDatasetMirrorIT`) stay deterministic regardless of test order.

### Manual Testing Steps:

1. Dev MariaDB smoke of user deletion with vehicles (phase 1) — including the admin archived list
   showing `ownerLogin: "anonymoususer"`.
2. Dev MariaDB smoke of purge with L2 on (phase 2) — immediate 404 after purge, image gone, audit
   event present.
3. Combined workflow (phase 3) — delete user, purge tombstone-owned vehicles, archived list empty,
   cost report reflects the documented removal.

## Performance Considerations

- Purge is O(events of one vehicle): five fixed selects + one delete batch + one vehicle delete —
  bounded and rare (P1 explicitly chose non-bulk). No N+1 (fixed query count).
- Entity-level deletes evict L2 per entity — negligible cost at this scale, and the correctness
  requirement anyway.
- Tombstone reassignment is one select + one save per owned vehicle inside one transaction — an
  admin operation on a single-digit vehicle fleet per user; no pagination concern.
- No new scheduled work, no new indexes, no schema change.

## Migration Notes

- **No schema migration**: no Liquibase changelog. Deploy is a pure WAR swap.
- **Rollback of the code deploy**: revert the WAR. A tombstone reassignment that already happened
   is *recoverable* — the vehicle rows survive with `owner_id` pointing at `anonymoususer`; an
   operator can reassign them back (direct DB update; no admin reassign endpoint exists, noted as a
   known operational caveat).
- **A purge is irreversible by design**: the only recovery is the manual host-side backups
  (`src/main/scripts/backup.sh`) — which the research notes are unscheduled and unverified. This
  is the accepted tradeoff of the housekeeping framing (P1); the interlock (P3) and audit (P4) are
  the mitigations.

## References

- Research: `context/changes/archived-vehicle-purge/research.md` — FK graph, purge sequence, L2
  trap, test traps, tombstone feasibility, retention-cost analysis
- Owner decisions P1–P7: `context/changes/archived-vehicle-purge/change.md`
- Origin: `context/archive/2026-08-28-vehicle-archiving/follow-ups/review-fixes.md` (FU-1) and
  `reviews/impl-review.md` (finding F1)
- Predecessor decisions D1–D8: `context/archive/2026-08-28-vehicle-archiving/change.md`
- Exception-handler pattern: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:128-135`
- Removed reference purge test: `git show 718a011^:src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java`
  (lines 181-187)
- Reminder exclusion queries: `repository/InsuranceRepository.java:17-19` (and Inspection /
  RoutineService equivalents)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: User-Deletion Disposition — Tombstone, Guards, FK 409

#### Automated

- [x] 1.1 `./mvnw verify -Dit.test=UserDeletionDispositionIT` passes — 63b4030
- [x] 1.2 `./mvnw verify -Dit.test='UserResourceIT,LookupMaintenanceResourceIT'` passes with new guard and lookup-409 cases — 63b4030
- [x] 1.3 Full `./mvnw verify` green with no regression in the leak-sensitive set (AdminVehicleResourceIT, ReminderSelectionParityIT, ReportParityIT, UserResourceIT) — 63b4030
- [x] 1.4 ArchTest green (new exceptions carry no web imports) — 63b4030

#### Manual

- [x] 1.5 Dev MariaDB smoke: delete a vehicle-owning user → 204, vehicles appear in admin archived list as anonymoususer-owned — 63b4030
- [x] 1.6 Reminder schedule unaffected (tombstone-owned vehicles are archived, hence excluded) — 63b4030

### Phase 2: Admin Purge Endpoint — Interlock, Audit, Image Cleanup

#### Automated

- [x] 2.1 `./mvnw verify -Dit.test=AdminVehiclePurgeIT` passes including image-file and audit assertions — 8beabb4
- [x] 2.2 Full `./mvnw verify` green; absolute-count assertions unaffected (no shared-H2 leak) — 8beabb4
- [x] 2.3 `grep -rn "@Modifying" src/main/java` returns nothing — 8beabb4

#### Manual

- [x] 2.4 Dev MariaDB smoke with L2 on: purge archived vehicle → immediate 404, image file gone, VEHICLE_PURGED audit event present — 8beabb4

### Phase 3: Documentation & Full Verification

#### Automated

- [x] 3.1 Clean full `./mvnw verify` from scratch green — 7bc82e3
- [x] 3.2 `grep -n "S-08" context/foundation/roadmap.md` finds the new entry — 7bc82e3

#### Manual

- [x] 3.3 Combined end-to-end: create user+vehicles+events → delete user → purge tombstone-owned vehicles → archived list empty, cost report reflects documented removal — 7bc82e3
