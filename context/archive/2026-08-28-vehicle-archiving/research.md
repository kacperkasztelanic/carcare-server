---
date: 2026-08-28T20:25:26+00:00
researcher: Kacper Kasztelanic
git_commit: 38fe3057cfd55b36623ac61b187a83ee990fde2b
branch: refactor
repository: carcare/server
topic: "Vehicle archiving (S-05 / US-02 / FR-009 / FR-012)"
tags: [research, codebase, vehicle, archiving, soft-delete, liquibase, reminders, reports]
status: complete
last_updated: 2026-08-28
last_updated_by: Kacper Kasztelanic
last_updated_note: "Recorded owner decisions on archive semantics, report reach, and column shape"
---

# Research: Vehicle archiving

**Date**: 2026-08-28T20:25:26+00:00
**Researcher**: Kacper Kasztelanic
**Git Commit**: `38fe3057cfd55b36623ac61b187a83ee990fde2b`
**Branch**: `refactor`
**Repository**: `carcare/server` (GitLab — no GitHub permalinks available)

## Research Question

What does the codebase require in order to deliver S-05 `vehicle-archiving`: an owner archives
a vehicle that has event history, the vehicle disappears from the vehicle list, from upcoming
events and from reminders, while its costs and statistics keep counting toward historical
reporting?

Spec sources already fixed before this research: `context/foundation/prd.md` US-02 (`:205-219`),
FR-009 (`:308-318`), FR-012 (`:326-332`), FR-015 (`:345-350`), the archiving domain rule
(`:446-459`), the backward-compatibility constraint (`:386-399`), and
`context/foundation/roadmap.md` S-05 (`:400-421`). All three prerequisites (S-01, S-03, S-04)
are `done`.

## Summary

The change is small in surface and sharp in risk. Six findings drive it:

1. **Exactly one existing query can be filtered in place.** Of the three `VehicleRepository`
   methods, only `findByOwnerIsCurrentUser()` has a single caller and is purely
   forward-looking (the vehicle list). The other two are shared between forward- and
   backward-looking consumers and must NOT be filtered — filtering them would erase archived
   vehicles from cost reports and statistics, which is precisely the failure FR-009 warns about.

2. **The three reminder queries have no join to `Vehicle` at all.** `findByValidThruIn` ×2 and
   `findByNextByDateIn` are derived methods with no vehicle or owner constraint. FR-012's
   "except that archived vehicles are excluded" requires converting each to an explicit `@Query`
   with `join x.vehicle v ... and v.archived = false` — not appending a predicate to an
   existing join.

3. **Archiving can be made genuinely invisible to client 1.2.5 by repurposing `DELETE`.**
   The client's only vehicle-removal path is `axios.delete('api/vehicle/{id}')` followed by a
   re-fetch of `api/vehicle/all` (`../client/.../vehicle.reducer.ts:189-197`). If `DELETE`
   sets the flag instead of deleting the row and keeps emitting
   `X-carcareApp-alert: carcareApp.vehicle.deleted`, the client behaves identically — and its
   i18n bundle has no `archived` key, so a new alert key would render as a missing translation.

4. **Nothing leaks into JSON by accident.** `VehicleMapper` and `VehicleDetailsMapper` are
   hand-written explicit field-by-field mappers, not MapStruct. A new entity field appears in
   the API only if deliberately added to `VehicleDto`. FR-009's "no new request parameter"
   therefore costs nothing to honour.

5. **`DELETE` on a vehicle with history is broken today and already has a `@Disabled` test
   waiting for this slice**, at `VehicleResourceIT.java:165-179`, deferred here by name in
   both `session-parity` and `client-server-contract-trial`.

6. **The golden parity suite is index-exact and will punish careless scope.** `GoldenReference`
   compares JSON arrays strictly by size and index, and `WorkbookValues` preserves XLSX row and
   column position. Adding an "Archived" row to the vehicle report, or changing the ordering or
   cardinality of any stats/events response, breaks `ReportParityIT` until fixtures are
   regenerated. The golden dataset seeds no archived vehicles, so a correctly scoped change
   should leave every existing fixture untouched — that is a useful correctness signal, not just
   a chore avoided.

## Detailed Findings

### 1. The vehicle query surface, classified

No Criteria API, no `Specification`, no `EntityManager`, no native SQL anywhere in
`src/main/java`. Every read is a Spring Data derived method or `@Query` JPQL, so the entire
filter decision lives in six repository interfaces.

`repository/VehicleRepository.java`:

| Method | Line | Callers | Classification | Filter? |
|---|---|---|---|---|
| `findByOwnerIsCurrentUser()` | `:16-17` | `VehicleServiceImpl:36` only | FORWARD — the vehicle list | **Yes, in place** |
| `findByIdAndOwnerIsCurrentUser(id)` | `:19-20` | 10 callers (below) | MIXED | **No** |
| `findAllByIdAndOwnerIsCurrentUser(ids)` | `:24-25` | 3 callers (below) | MIXED | **No — add a filtered sibling** |

`findByIdAndOwnerIsCurrentUser` callers — `VehicleServiceImpl:29` (get), `:52` (edit), `:61`
(delete), `ReportServiceImpl:46` (vehicle report — BACKWARD), `StatisticServiceImpl:60`
(mileage stats — BACKWARD), and the five `add<Event>` paths at `RepairServiceImpl:43`,
`InsuranceServiceImpl:42`, `RoutineServiceServiceImpl:43`, `InspectionServiceImpl:44`,
`RefuelServiceImpl:43`.

`findAllByIdAndOwnerIsCurrentUser` callers — `ReportServiceImpl:61` (cost report — BACKWARD),
`StatisticServiceImpl:68` (cost stats — BACKWARD), `EventServiceImpl:40` (forthcoming events —
FORWARD).

**This is the crux of the slice.** The forward-looking forthcoming-events path and the two
backward-looking cost paths call the *same* repository method. A blanket filter satisfies
"excluded from upcoming events" and simultaneously violates "costs continue to count". The
filter must be introduced as a *new* method (e.g.
`findAllActiveByIdAndOwnerIsCurrentUser`) consumed only by `EventServiceImpl:40`, leaving the
existing method — and the comment at `VehicleRepository.java:22-23` pinning its result order to
`cost-en.json`'s index-exact array — untouched.

Note also `VehicleRepository.java:22-23`: the existing ordering is insertion order "by luck, not
contract". Do not perturb it.

### 2. Reminders (FR-012)

`ReminderServiceImpl.sendReminders()` (`:44-55`) is `@Scheduled(cron = "0 0 8 * * *")`, reads
`LocalDate.now(clock)` from the injected `Clock`, and fans out to three selection queries:

- `insuranceRepository.findByValidThruIn(dates)` — `ReminderServiceImpl:59`
- `inspectionRepository.findByValidThruIn(dates)` — `ReminderServiceImpl:70`
- `routineServiceRepository.findByNextByDateIn(dates)` — `ReminderServiceImpl:81`

All three are declared as derived methods at line `:17` of `InsuranceRepository`,
`InspectionRepository`, `RoutineServiceRepository`. They are **system-wide**: no owner
constraint, no vehicle join. Each then walks `event.getVehicle().getOwner()` in the service
loop to address the mail.

Consequence for FR-012: each must become an explicit `@Query` that joins `x.vehicle` so the
archive predicate has something to attach to. This is the only place in the slice where a
query gains a join rather than a predicate.

`ReminderResource:23-27` exposes `GET /api/reminder/send` to `ROLE_ADMIN`, which is how
`ReminderSelectionParityIT` and any manual check trigger the job.

### 3. Forthcoming events

`EventResource` (`:27-31`) is `POST /api/events` taking a client-supplied
`List<PeriodVehicle>`. `EventServiceImpl.findForthcomingEvents` (`:33-46`) resolves those ids
through `findAllByIdAndOwnerIsCurrentUser` and windows the events by date in memory.

Because the client populates every vehicle selector from `getVehicles()` → `GET /api/vehicle/all`
(`../client/.../events.tsx:51`, `reports.tsx:40`, `statistics.tsx:41`), filtering the vehicle
list alone already makes archived vehicles unselectable in the UI. The server-side filter on
this path is defence in depth against a hand-crafted request — worth having, since US-02's
"no longer appears in upcoming events" should be a server guarantee rather than a client habit.

### 4. Write path, DTO, and the client contract

`VehicleResource` (`/api/vehicle`) has no method-level security; `SecurityConfiguration:80`
authenticates `/api/**` and ownership is enforced per row in the repository queries.

`VehicleServiceImpl.deleteVehicle` (`:58-65`) loads the owner-scoped vehicle for the response
body, then calls a bare `vehicleRepository.deleteById(id)`. All five event FKs into `vehicles`
are non-cascading — `20190922082653_changelog.xml:238` (refuels), `:247` (insurances), `:250`
(inspections), `:253` (repairs), `:256` (routine_services), none carrying `onDelete` — and
`Vehicle` has no `@OneToMany`, no `cascade`, no `orphanRemoval`. Hence the
`DataIntegrityViolationException` → unhandled → 500 that `client-server-contract-trial`
reproduced against real MariaDB (`context/archive/2026-08-27-client-server-contract-trial/change.md:154-157`).

The client side:

- `../client/src/main/webapp/app/modules/carcare/vehicle/vehicle.reducer.ts:189-197` —
  `deleteVehicle` issues `axios.delete('api/vehicle/{id}')` then dispatches `getVehicles()`.
- `.../vehicle-delete-dialog.tsx:19` is the only caller; `.../vehicle.tsx:92-95` is the button.
- `../client/src/main/webapp/i18n/en/carcare.json` exposes exactly three vehicle alert keys:
  `created`, `updated`, `deleted`. **There is no `archived` key.** Emitting a new alert key
  would surface an untranslated string in the frozen client.
- Pinned client version `1.2.5` (`pom.xml:13`) matches the sibling working tree
  (`../client/package.json:3`), so the tree read here is the deployed contract.

Serialization: `VehicleMapper.vehicleToVehicleDto` (`:23-33`) and `vehicleDtoToVehicle`
(`:35-48`) whitelist fields explicitly; `VehicleDetailsMapper` (`:22-38`, `:40-54`) does the
same; entities are never serialized directly. A new `Vehicle.archived` field therefore stays
out of every response unless explicitly wired — which satisfies FR-009's "no new request
parameter" and the PRD's rejection of a per-report "include archived" option
(`prd.md:386-392`) for free.

`VehicleServiceImpl.updateVehicle` (`:67-75`) copies six named fields from the incoming DTO onto
the managed entity, so `PUT /api/vehicle/{id}` cannot clobber an archive flag that is not in the
DTO. It does call `imageStorageService.delete(...)` on every edit — unrelated to this slice, but
worth knowing that image lifecycle is tied to edit, not to delete.

### 5. Schema and Liquibase

`src/main/resources/config/liquibase/master.xml` includes exactly three changelogs:
`00000000000000_initial_schema.xml`, `20190922082653_changelog.xml`,
`20260827153000_client_contract_changelog.xml`. A fourth file,
`changelog/20190102222057_changelog.xml`, exists on disk but is **not** included and does not
execute — do not pattern-match on it.

The `vehicles` table is created at `20190922082653_changelog.xml:182-211` (changeSet
`1569140831585-9`). The most recent and closest precedent for the new changelog is
`20260827153000_client_contract_changelog.xml:8`, which widened `license_plate` to
`VARCHAR(20)`. Naming convention: `yyyyMMddHHmmss_<description>_changelog.xml`, appended as a
new `<include>` at the end of `master.xml`; existing changesets are never rewritten
(`prd.md:396-399`).

Only one changeset in the repository uses `context="test"`
(`00000000000000_initial_schema.xml:138`, the `jhi_date_time_wrapper` fixture table). The
archive column runs unconditionally in every environment, so it needs no context.

This is load-bearing: `application-test.yml` sets `hibernate.hbm2ddl.auto: validate`. The
entity field and the changelog must land in the same commit or **every** integration test fails
at context startup, not just the archiving ones.

`VehicleDetails` (the `@Embeddable` flattened into `vehicles`) is the wrong home for the flag —
it would route the field through `VehicleDetailsDto`/`VehicleDetailsMapper`, which exist to
carry image bytes and notes. The flag belongs on `Vehicle` alongside `owner`.

### 6. Test surface

Directly affected:

- `VehicleResourceIT.java:165-179` — `@Disabled("S-05 vehicle-archiving owns deleting vehicles
  with event history")`, asserting today's 5xx. This slice owns re-enabling or replacing it. It
  runs with `@Transactional(propagation = Propagation.NOT_SUPPORTED)` and a manual
  `purgeVehicle` helper (`:181-186`), because the H2 instance is JVM-wide
  (`DB_CLOSE_DELAY=-1`) and rows committed outside the class transaction survive. A harness trap
  recorded in `session-parity` research (`:232-234`): under a class-level `@Transactional` IT the
  delete flushes at *test rollback*, not during the request, so the FK violation never surfaces
  in the `MvcResult`. An archive path no longer trips FK constraints, so a replacement test may
  return to the ordinary transactional style — but only after confirming the archive is asserted
  through a re-read, not through the flushed-entity illusion.
- `EventResourceIT.java:18-29` — asserts `/api/events` returns exactly 3 events for a
  `vehicleWithEventsFor("user")`. Adding an archived-vehicle case belongs here.
- `OwnerIsolationIT` — the per-resource owner/foreign matrix (`:90-91`, `:107-108`, `:124-125`,
  `:143-144`, `:162-163`, `:180-197`). Archiving another owner's vehicle must 404, and that
  belongs in this matrix.
- `SessionFixtures` (`src/test/java/.../fixtures/SessionFixtures.java`) is the canonical builder:
  `vehicleFor(login)` (`:99-121`), `vehicleWithEventsFor(login)` (`:123-131`),
  `seedGoldenDataset()` (`:277-360`). Any archived fixture goes here.

At risk if scope creeps:

- `GoldenReference.firstDifference` (`:346-360`) compares JSON arrays by size then index. Any
  change to the cardinality or ordering of a stats/events response breaks parity.
- `WorkbookValues.extract` preserves XLSX row/column position exactly; only the `Costs` sheet's
  middle rows are sorted (`:105-108`, `:149-158`). Adding an "Archived" row to the vehicle
  report's General sheet shifts every subsequent index in `vehicle-en.json`, `vehicle-pl.json`,
  `vehicle-unowned.json`.
- `ReportParityIT.forthcomingEventsKeepTheCapturedOrdering` (`:151-175`) pins `/api/events`
  ordering with indexed `jsonPath` assertions.
- `GoldenDatasetMirrorIT` (`:82`, `:84-92`, `:118-164`) asserts exact repository row-count deltas
  and every field of every seeded row against `GOLDEN_HANDLES`.

Because `seedGoldenDataset()` creates no archived vehicles, a correctly scoped implementation
leaves all thirteen golden fixtures under `src/test/resources/golden/` byte-identical. **Treat a
golden-fixture diff as a signal that the archive filter reached a backward-looking query**, not
as a fixture that needs regenerating.

`ReminderSelectionParityIT` substitutes the clock via an `@Import`ed nested `@TestConfiguration`
exposing a `@Primary` `Clock` fixed at `SessionFixtures.GOLDEN_REFERENCE_DATE` (2026-04-15)
(`:46-73`), with `@MockBean MailService` and `ArgumentCaptor`s (`:173-213`) compared against
`golden/reminders/*.json`. That is the harness for proving FR-012: seed an archived vehicle whose
insurance falls inside the window and assert the captured mail calls are unchanged.

## Code References

- `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:16-25` — the three vehicle queries; only the first is safely filterable
- `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java:40` — forward-looking caller sharing a query with reports
- `src/main/java/com/kasztelanic/carcare/service/impl/ReportServiceImpl.java:46,61` — backward-looking; must keep seeing archived vehicles
- `src/main/java/com/kasztelanic/carcare/service/impl/StatisticServiceImpl.java:60,68` — same
- `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java:44-55,59,70,81` — the scheduled job and its three unjoined selection queries
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:33-39,58-65` — the list query and the bare `deleteById`
- `src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java:72-80` — the DELETE handler and its alert header
- `src/main/java/com/kasztelanic/carcare/domain/Vehicle.java:27-95` — immutable-style entity; where the flag goes
- `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleMapper.java:23-48` — hand-written whitelist mapping
- `src/main/resources/config/liquibase/master.xml` — three includes; append the fourth
- `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:182-211,238-256` — `vehicles` table and the five non-cascading FKs
- `src/main/resources/config/liquibase/changelog/20260827153000_client_contract_changelog.xml:8` — closest precedent for a column migration
- `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:165-186` — the `@Disabled` placeholder this slice owns
- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:99-131,277-360` — fixture builders
- `src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java:346-360` — index-exact array comparison
- `src/test/java/com/kasztelanic/carcare/golden/ReminderSelectionParityIT.java:46-73` — fixed-clock substitution
- `src/test/resources/config/application-test.yml` — `hbm2ddl.auto: validate`, JVM-wide H2, `TestH2Dialect`
- `../client/src/main/webapp/app/modules/carcare/vehicle/vehicle.reducer.ts:189-197` — the client's only removal call
- `../client/src/main/webapp/i18n/en/carcare.json` — `carcareApp.vehicle` has only `created`/`updated`/`deleted`

## Architecture Insights

- **The forward/backward split is not a property of endpoints, it is a property of callers.**
  Two of the three vehicle lookups are shared across the split. The archive filter therefore
  cannot be expressed as "filter the repository"; it has to be expressed as "give the
  forward-looking callers their own query". That is the single design constraint that shapes
  the whole slice.
- **Ownership is enforced per row, in JPQL, via `?#{principal.username}`** — never in a service
  guard. An archive predicate is the same kind of thing and belongs in the same place, which
  keeps the existing auditability story intact (roadmap Open Question 8 defers consolidating
  this; do not pre-empt it here).
- **Hand-written mappers are a feature here.** They make "invisible to the client" the default
  rather than something to be enforced.
- **The event repositories' `findByVehicleId` variants carry no owner filter**
  (`InsuranceRepository:25-26` and the four siblings), safe only because `VehicleRichMapper:65-81`
  is always reached after an owner-scoped vehicle lookup. Adding an archive predicate there
  would be both unnecessary and dangerous: `VehicleRichMapper` serves both sides of the split.
- **`hbm2ddl.auto: validate` under a JVM-wide H2** means schema and entity move together or the
  whole suite goes red at startup. There is no partial-failure mode to debug from.

## Historical Context (from prior changes)

- `context/archive/2026-08-26-session-parity/research.md:214-234` — the original diagnosis of
  the broken delete: no `onDelete` on any of the six FKs, no `cascade`/`orphanRemoval`, bare
  `deleteById`. Recommends covering only the working case and deferring the fix here, and
  records the flush-at-rollback harness trap.
- `context/archive/2026-08-26-session-parity/plan.md:104-108` — "What We're NOT Doing": fixing
  the delete "pre-empts that design", covered by a `@Disabled` placeholder instead.
- `context/archive/2026-08-26-session-parity/plan-brief.md:39` — the same decision recorded as a
  research trade-off.
- `context/archive/2026-08-27-client-server-contract-trial/change.md:154-157` — reproduced
  against real MariaDB, not only H2: `DELETE /api/vehicle/1` with one repair attached returns
  500 with a Problem Details body; an event-free vehicle deletes with a clean 200. Explicitly
  "S-05's to resolve".
- `context/archive/2026-08-27-client-server-contract-trial/change.md:92-93` — the alert-header
  contract measured live: `X-carcareApp-alert: carcareApp.vehicle.deleted` with
  `X-carcareApp-params: 1`. This is the exact header an archive must keep emitting.
- `context/foundation/prd.md:308-318` — FR-009's own verification note that no `archived` or
  `deleted` flag exists anywhere in the domain or in any changelog, which is why it is `[new]`.
  Re-verified at this commit: still true.

## Related Research

- `context/archive/2026-08-26-session-parity/research.md` — vehicle and event CRUD contract
- `context/archive/2026-08-28-report-parity/research.md` — the backward-looking surface this
  change must not disturb
- `context/archive/2026-08-28-english-reminder-fix/` — the reminder path this change filters
- `context/archive/2026-08-27-golden-baseline-capture/reference.md` — the golden fixtures and
  their expected divergences

## Decisions (owner, 2026-08-28)

Recorded during research; these close the six open questions this document originally raised.

**D1 — Archive is soft-delete, and the user never sees the vehicle again.**
An archived vehicle disappears from the owner's world entirely: the vehicle list, the detail
fetch, edit, and adding new events. Direct access to an owned archived vehicle or one of its
events returns `410 Gone`; an unknown or foreign resource remains `404`. There is no owner-facing
unarchive path. Administrators restore vehicles through the paginated archive list and idempotent
restore API under `/api/admin/vehicles`. This resolves original questions 2, 3 and 4 together,
and it is stronger than US-02 required — US-02 only asked that the vehicle leave the list.

Implication for the query map in finding 1: `findByIdAndOwnerIsCurrentUser`
(`VehicleRepository.java:19-20`) was classified MIXED and "do not filter". That still holds for
the *method*: owner-facing callers must use the inclusive result to distinguish owned archived
(`410`) from absent/foreign (`404`), while historical callers remain inclusive:

| Caller | Line | Behaviour after D1 |
|---|---|---|
| `VehicleServiceImpl.getVehicle` | `:29` | classify → 410 for owned archived; 404 for absent/foreign |
| `VehicleServiceImpl.editVehicle` | `:52` | classify → 410 for owned archived; 404 for absent/foreign |
| `VehicleServiceImpl.deleteVehicle` | `:61` | archive active; repeated archive → 410; absent/foreign → 404 |
| `Repair/Insurance/RoutineService/Inspection/RefuelServiceImpl.add*` | `:43`,`:42`,`:43`,`:44`,`:43` | classify parent → 410 for owned archived; 404 for absent/foreign |
| `ReportServiceImpl.generateVehicleReport` | `:46` | **unchanged**, still resolves archived |
| `StatisticServiceImpl.calculateMileageStats` | `:60` | **unchanged**, still resolves archived |

Direct event get/edit/delete operations apply the same classification after their owner-scoped
event lookup returns the event and its vehicle. The last two rows are a deliberate asymmetry worth
stating in the plan: after D1 a bookmarked id returns `410` from `GET /api/vehicle/{id}` but still
produces output from
`GET /api/reports/vehicle/{id}`. It is consistent with the forward/backward rule
(`prd.md:446-459`) and harmless — the id is undiscoverable through the client — but it will look
like a bug to anyone reading the two endpoints side by side.

**D2 — Backward-looking reports include archived vehicles, period-scoped.**
The cost report and cost statistics union the caller's archived vehicles into the requested id
set, restricted to those with at least one event inside the requested `[dateFrom, dateTo]`. A
vehicle sold in March 2025 appears in a 2025 report and is absent from a 2026 one. This makes
US-02's second acceptance criterion reachable through frozen client 1.2.5 rather than true only
at the API — the point original question 5 raised.

**D3 — `archived_at TIMESTAMP NULL` on `vehicles`.** `NULL` means active. Filter is
`archivedAt is null`; admin restore is setting it back to `NULL`. Chosen over a boolean because
it records *when*, which D1's administrator recovery workflow and D2's period reasoning both benefit
from, at no extra migration cost.

**D4 — `DELETE /api/vehicle/{id}` becomes the archive verb.** It keeps returning 200 with the
vehicle body and `X-carcareApp-alert: carcareApp.vehicle.deleted` / `X-carcareApp-params: <id>`.
No new endpoint, no new request parameter, no new i18n key.

**D5 — The client's delete-confirmation copy stays as it is.** "Are you sure you want to delete
this vehicle and all the associated information?" remains accurate from the user's point of
view, because under D1 the vehicle and its information genuinely are gone for them. Original
question 6 is withdrawn — there is no divergence to record and nothing for a future client
release to fix.

## Detailed Findings (cont.)

### 7. What D2 requires — the period-overlap query

`CostCalculatorImpl.sumCostsBetweenDates` (`:31-38`) is the authority on what "an event in the
period" means, and it is uniform across all five event types:

```java
.filter(i -> !i.getVehicleEvent().getDate().isBefore(dateFrom)
          && !i.getVehicleEvent().getDate().isAfter(dateTo))
```

So the window keys off the **embedded `VehicleEvent.date`**, inclusive at both ends — *not*
`Insurance.validThru` or `Inspection.validThru`, and not `RoutineService.nextByDate`. Those
forward-looking date fields drive reminders and upcoming events; they play no part in cost
windowing. A D2 selection query that used `validThru` would silently disagree with the calculator
it feeds.

The new `VehicleRepository` method therefore needs an owner constraint, an archived constraint,
and a five-way existence test over the same embedded field — shape:

```
select distinct v from Vehicle v
where v.owner.login = ?#{principal.username}
  and v.archivedAt is not null
  and ( exists (select 1 from Refuel e         where e.vehicle = v and e.vehicleEvent.date between :from and :to)
     or exists (select 1 from Repair e         where e.vehicle = v and e.vehicleEvent.date between :from and :to)
     or exists (select 1 from RoutineService e where e.vehicle = v and e.vehicleEvent.date between :from and :to)
     or exists (select 1 from Inspection e     where e.vehicle = v and e.vehicleEvent.date between :from and :to)
     or exists (select 1 from Insurance e      where e.vehicle = v and e.vehicleEvent.date between :from and :to) )
order by v.id
```

Consumed by exactly two call sites — `ReportServiceImpl:61` (cost report) and
`StatisticServiceImpl:68` (cost stats) — each unioning the result **after** the requested
vehicles, de-duplicated, with the requested order preserved.

Two ordering constraints bear on that union. `VehicleRepository.java:22-23` records that the
existing result order is insertion order "by luck, not contract" and feeds `cost-en.json`'s
index-exact array; and `GoldenReference.firstDifference` (`:346-360`) compares arrays by size then
index. Appending archived vehicles after the requested ones, ordered by id, keeps existing
responses byte-identical whenever the caller owns no qualifying archived vehicle — which is every
current golden fixture. The explicit `order by v.id` on the new query is what makes the appended
segment deterministic rather than luck-dependent in its own right.

`ReportResource` / `StatisticResource` request bodies are unchanged: the union happens
server-side, so FR-009's "no new request parameter" holds.

Note the single-vehicle backward-looking paths (`ReportServiceImpl:46`,
`StatisticServiceImpl:60`) need no union — they already resolve any id the caller passes,
archived or not, per D1's table.

## Open Questions

None blocking. Two things the plan should state rather than discover:

1. **The 410-vs-report asymmetry from D1** (`GET /api/vehicle/{id}` returns `410` for an owned archived vehicle
   while `GET /api/reports/vehicle/{id}` still serves one). Correct per the forward/backward
   rule, but it reads as inconsistent and deserves a comment at both call sites.
2. **D2's union changes the cardinality of two responses** that the golden suite compares
   index-exactly. The golden dataset seeds no archived vehicles, so every existing fixture should
   stay byte-identical — that invariant is worth asserting deliberately in the plan's
   verification step, because a fixture diff would mean the union leaked into a path it does not
   belong in.
