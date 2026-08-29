---
date: 2026-08-29T13:51:22+0200
researcher: Kacper Kasztelanic
git_commit: 094e32bbb193834fe001eda4535d92a6e28dcff0
branch: refactor
repository: kkasztel_carcare/server
topic: "Archived-vehicle purge and user-deletion disposition (FU-1)"
tags: [research, codebase, vehicle-archiving, user-deletion, foreign-keys, data-lifecycle, admin-api]
status: complete
last_updated: 2026-08-29
last_updated_by: Kacper Kasztelanic
last_updated_note: "Open Question 1 resolved by the owner: no legal or contractual retention obligation exists."
---

# Research: Archived-vehicle purge and user-deletion disposition (FU-1)

**Date**: 2026-08-29T13:51:22+0200
**Researcher**: Kacper Kasztelanic
**Git Commit**: `094e32bbb193834fe001eda4535d92a6e28dcff0`
**Branch**: `refactor`
**Repository**: `kkasztel_carcare/server`

## Research Question

FU-1 from the `vehicle-archiving` implementation review: archiving made vehicle rows permanent, so
`DELETE /api/users/{login}` now fails for any user who ever owned a vehicle. The archive has no
terminal state — an admin can restore a vehicle but never dispose of one.

Scoped by the owner as a **full data-lifecycle** question, evaluating two dispositions for
`deleteUser`: **refuse with 409** and **reassign to a tombstone owner**.

## Summary

Six findings drive the plan.

1. **The current behaviour is a 500, verified empirically.** Deleting a user who owns any vehicle —
   active or archived — returns `500` with `message: error.http.500`. `DataIntegrityViolationException`
   has no handler in `ExceptionTranslator`, so the FK violation falls through the catch-all.

2. **No erasure, GDPR, or retention requirement exists anywhere in this project.** This is a verified
   negative, not an absence of evidence. The question was asked exactly once, at programme start, and
   was never answered — `CODEBASE_ANALYSIS.md:243` asks *"Are reports/audit legally retained?"*, and
   shaping resolved only the first half of that question. **The disposition choice is unconstrained by
   any recorded requirement and is a fresh owner decision.** The plan must not claim a legal driver.

   **Resolved 2026-08-29 (owner): there is no legal or contractual retention obligation.** The
   repository silence was not an omission. See "Open Questions → 1 (resolved)" for what this unblocks
   and what it rules out.

3. **This is not an FR-007 regression as verified, but it is a real narrowing.** I raised the parity
   framing when scoping this; the evidence does not support it as stated. At baseline `6e19b96`,
   deleting a user who *currently owned* a vehicle already returned 500 — identical to today. What
   changed is narrower and still real: users owning only *event-free* vehicles went from
   deletable-via-a-two-step-workflow to permanently undeletable. Two aggravating facts: that workflow
   was never reachable by an admin acting alone (`deleteVehicle` is owner-scoped on
   `?#{principal.username}`), and FR-007's verification never reached this boundary at all — no test
   anywhere deletes a vehicle-owning user.

4. **The two dispositions are not symmetric, and P1 sharpened the asymmetry.** This is the sharpest
   structural finding, and it was refined after the owner answered Open Question 1.

   - **409 needs purge to be a complete answer.** It converts an opaque 500 into a meaningful status,
     but the user remains *undeletable* until an admin can dispose of the vehicles. Shipping the 409
     alone changes the error code and nothing else.
   - **Tombstone does not.** Reassigning vehicles to a synthetic owner removes the user row and solves
     the stated problem outright, with **no destructive operation anywhere**. Purge would then exist
     solely to give the archive a terminal state — real housekeeping, but a smaller and less urgent
     job than "an admin must be able to delete a user", and (per P1) one with no compliance driver
     behind it.

   So the change has two separable parts, and **how separable they are depends on the disposition**.
   The plan should state explicitly which of the two jobs each part is doing, and should not assume
   purge is load-bearing under tombstone.

5. **Nothing cascades, anywhere.** There is no `@OneToMany`, no `cascade`, and no `orphanRemoval` in
   the whole of `src/main`, and no `ON DELETE` clause on any of the nine FKs in the schema. No
   repository exposes any delete method beyond what `JpaRepository` inherits. A purge must delete the
   five event tables explicitly, then the vehicle, then the image file — and must read the image
   filename *before* deleting the row.

6. **The most dangerous implementation choice is invisible to the test suite.** Hibernate L2 caching
   is **enabled in dev/prod and disabled in tests**. A purge written as a bulk `@Modifying` JPQL
   delete bypasses L2 eviction entirely and would serve stale vehicles and events from ehcache for up
   to an hour in production, while passing all 275 tests. Entity-level `deleteAll(entities)` avoids
   this. No existing code faces this, because there are zero `@Modifying` annotations in the codebase.

## Detailed Findings

### Current behaviour — verified, not inferred

A temporary non-transactional probe was run and removed. Deleting a user owning an active vehicle and
a user owning an archived vehicle both produced:

```json
{"type":"https://www.jhipster.tech/problem/problem-with-message",
 "title":"Internal Server Error","status":500,
 "instance":"/api/users/probeactive","path":"/api/users/probeactive",
 "message":"error.http.500"}
```

Chain: H2/MariaDB raises a referential-integrity violation on
`FKHM05KH6D8F082PGDDOM1Q1YCO` → Spring translates to `DataIntegrityViolationException` →
`ExceptionTranslator` has no handler for it (`ConcurrencyFailureException` is the only
`org.springframework.dao.*` type handled, and `DataIntegrityViolationException` is not a subtype) →
catch-all `handleUncaught` logs `log.error` and returns 500. The user row survives.

Two consequences worth carrying into the plan:

- `open-in-view: false` (`src/main/resources/config/application.yml:59`) means the FK fires at the
  `UserService` transaction commit, *inside* the controller call — which is why the advice sees it.
- `UserService.clearUserCaches(user)` runs **before** the commit that fails
  (`service/UserService.java:224`), so on the failure path the user caches are evicted even though
  nothing was deleted.

### The referential graph — complete

Only nine FKs exist in the entire schema.

**Referencing `jhi_user`** (exactly two):

| Constraint | Base | → | ON DELETE | Evidence |
|---|---|---|---|---|
| `fk_user_id` | `jhi_user_authority.user_id` | `jhi_user.id` | none | `00000000000000_initial_schema.xml:70-74` |
| `FKhm05kh6d8f082pgddom1q1yco` | `vehicles.owner_id` | `jhi_user.id` | none | `20190922082653_changelog.xml:244` |

`jhi_user_authority` is the owning side of `User.authorities`, which JPA removes automatically on
entity delete — that is why `deleteUser` works today for users without vehicles.

**Referencing `vehicles`** (exactly five, all event tables): `refuels` (`:238`), `insurances`
(`:247`), `inspections` (`:250`), `repairs` (`:253`), `routine_services` (`:256`) — none with
`ON DELETE`.

**Explicitly checked and NOT related** — each of these looks relevant and is not:

- **`reminder_advances`** has only `id` and `type` columns (`20190922082653_changelog.xml:120-127`).
  It is a **global, `@Immutable`, READ_ONLY-cached lookup table with no owner or vehicle column**
  (`domain/ReminderAdvance.java:22-40`). It reads as per-user; it is not. **A purge must not touch it.**
- **`jhi_persistent_audit_event`** stores the actor as `principal varchar(50)` with **no FK**
  (`00000000000000_initial_schema.xml:100-102`). Deleting a user does not fail on audit rows — and
  silently orphans them. Whichever behaviour is wanted must be chosen deliberately.
- **No password-reset or token table exists.** `activation_key`, `reset_key`, `reset_date` are plain
  columns on `jhi_user`.

### Entity mapping — no cascade exists to lean on

Verified by grep across all of `src/main`: **zero occurrences of `@OneToMany`, `cascade`, or
`orphanRemoval`.**

- `Vehicle` holds **no** collection of events (`domain/Vehicle.java:32-104`). The vehicle↔event
  relationship is navigable only from the event side. `vehicleRepository.delete(vehicle)` emits one
  `delete from vehicles` and Hibernate never learns about children → guaranteed FK violation.
- `User` holds **no** collection of vehicles (`domain/User.java:38-133`).
- All five event entities hold `@ManyToOne(optional=false)` back to `Vehicle`, EAGER, no cascade.

**No repository has any delete method.** All thirteen files in `repository/` contain only `@Query`
select methods; the only deletes available are those inherited from `JpaRepository`.

### Required purge sequence

FKs are `deferrable="false" initiallyDeferred="false"`, so ordering is enforced per statement, not at
commit:

1. delete `refuels` where `vehicle_id = ?`
2. delete `repairs`
3. delete `routine_services`
4. delete `inspections`
5. delete `insurances` *(1–5 mutually independent; all must precede 6)*
6. delete `vehicles` where `id = ?`
7. `imageStorageService.delete(filename)` — **filename must be read before step 6**

This exact order is corroborated by a prior reference implementation that existed in the tree and was
removed by the archiving change:

```java
// git show 718a011^:src/test/java/.../VehicleResourceIT.java  (lines 181-187)
private void purgeVehicle(Long vehicleId) {
    for (String table : new String[] { "refuels", "repairs", "routine_services", "inspections", "insurances" }) {
        jdbcTemplate.update("delete from " + table + " where vehicle_id = ?", vehicleId);
    }
    jdbcTemplate.update("delete from vehicles where id = ?", vehicleId);
}
```

Its call site was `@Disabled("S-05 vehicle-archiving owns deleting vehicles with event history")` and
asserted `status().is5xxServerError()` — direct historical evidence that this FK path has always
surfaced as a 500.

### Image storage

- `vehicles.image` is `VARCHAR(45)` holding a **filename** (UUID + extension), never bytes
  (`20190922082653_changelog.xml:197`, `domain/VehicleDetails.java:72-75`).
- Bytes live at `<application.data-directory.location>/<filename>` — `data` in dev/test,
  `/home/jhipster/data` in prod (`ImageStorageServiceImpl.java:68-71`).
- `delete` is **fully idempotent and never throws**: null/empty → `false` with no I/O, otherwise
  Commons-IO `deleteQuietly` (`ImageStorageServiceImpl.java:60-66`). Safe to call unconditionally.
- **Images are already orphaned today.** Neither the current archive path nor the pre-archiving hard
  delete ever called `delete`; its only production caller is `VehicleServiceImpl.updateVehicle:76`.
  Orphaned image files are a pre-existing condition, not something this change introduces.
- **Image deletion is not transactional.** If it runs before a rollback, the file is gone but the row
  survives, and `load()` silently substitutes `default.png` — the corruption is invisible.

### Second-level cache — the trap that no test can catch

`config/CacheConfiguration.java:38-56` creates 15 regions. `Vehicle` and all five event entities carry
`@Cache(NONSTRICT_READ_WRITE)`. Query cache is off in every profile.

| Profile | L2 enabled | Evidence |
|---|---|---|
| dev | **yes** | `application-dev.yml:44` |
| prod | **yes** | `application-prod.yml:49` |
| test | **no** | `application-test.yml:38` |

Entity-level `repository.delete(entity)` / `deleteAll(entities)` routes through Hibernate's
`EntityDeleteAction` and evicts L2 correctly. A bulk `@Modifying` JPQL/native delete **bypasses the
persistence context and L2 entirely** — stale entries survive up to `time-to-live-seconds: 3600`.
Because L2 is off in tests, **the entire suite would pass while production served deleted vehicles for
an hour.**

### HTTP and admin conventions

**The `ArchivedResourceException` pattern is the template for any new domain exception** — a bare
`RuntimeException` in `service/exception/` with no HTTP annotation, plus an explicit
`@ExceptionHandler` in `ExceptionTranslator` building `ProblemDetail.forStatus(...)` and routing
through `handleExceptionInternal`. This was established one commit ago (094e32b) and its stated
rationale — avoiding a stack trace for routine client behaviour — applies identically to a 409.

**Existing 409 convention: exactly one**, `ConcurrencyFailureException` → `HttpStatus.CONFLICT` with a
named constant message key (`ExceptionTranslator.java:144-149`). `HttpStatus.CONFLICT` appears nowhere
else in `src/main`. There is no application-level conflict exception and no
`ConflictAlertException` counterpart to `BadRequestAlertException`.

**RFC7807 shape** is applied centrally by `handleExceptionInternal` (`ExceptionTranslator.java:56-70`):
`type`, `title`, `status`, `instance`, `path`, and `message` defaulting to `"error.http." + status`.

**i18n**: `src/main/resources/i18n/` contains **no** `carcareApp.*` and **no** `error.http.*` keys —
those live in the client bundles. Reusing `carcareApp.vehicle.deleted` via
`HeaderUtil.createEntityDeletionAlert("vehicle", id)` requires **no client change**; minting a new key
(e.g. `carcareApp.vehicle.purged`) would require a `../client` bundle change and would otherwise emit
an untranslated key silently.

**Route shape**: `/{id}/restore` (`AdminVehicleResource.java:46`) is the only sub-resource-verb
precedent. A plain `DELETE /api/admin/vehicles/{id}` is ambiguous against the soft-delete
`DELETE /api/vehicle/{id}`.

**No settled DELETE response convention** — three coexist: 204-empty (`UserResource:192`),
200-with-entity (`VehicleResource:75`), 200-empty (`FuelTypeResource:72`). Nothing arbitrates.

### Tombstone feasibility

**A suitable principal already exists.** `config/liquibase/data/user.csv` seeds four users, two of
which are named constants (`config/Constants.java:15-16`):

| login | activated | authorities | filtered from user list? |
|---|---|---|---|
| `system` | true | ROLE_ADMIN, ROLE_USER | **no** — visible in `GET /api/users` |
| `anonymoususer` | true | *(none)* | **yes** — `UserService.java:246` |

`anonymoususer` is the safer candidate: no authorities, already excluded from the admin user list, and
semantically apt. `system` is a live admin login whose bcrypt hash is committed to the repo.

Constraints: `login` unique/indexed and lowercased by an overridden setter (`domain/User.java:51,131`);
`password_hash` is `NOT NULL` and exactly 60 chars; `created_by` `NOT NULL`.

**Would tombstone-owned vehicles leak?** No, on read paths — every owner-facing vehicle and event query
filters on `?#{principal.username}`. Four real consequences remain:

1. **Reminder queries are global, not owner-scoped** (`ReminderServiceImpl:57-88`). They exclude
   archived vehicles, so a tombstone owning only *archived* vehicles sends no mail — but a tombstone
   owning an **active** vehicle would email `system@localhost`. Reassignment should therefore archive
   as it reassigns, or the tombstone must be excluded explicitly.
2. **The admin archived list would display `ownerLogin: "anonymoususer"`** — `findAllArchived` is not
   owner-scoped and the mapper reads `owner.login` straight through. Arguably a feature: purge
   candidates become discoverable.
3. **Retained-but-unreachable.** Cost history survives in the tables but no principal can query it,
   since cost paths are owner-scoped. The data is recoverable by an admin re-assigning it back — which
   is the substantive difference from a purge.
4. **`system` is itself deletable today** — no guard protects any login, until it owns a vehicle.

### Test landscape and blast radius

**The gap that hid this**: `grep -n "vehicle\|Vehicle"` over `UserResourceIT`, `UserServiceIT`, and
`AccountResourceIT` returns **zero hits**. Every user these tests delete is a locally-built fixture
with no vehicles.

**Four traps for any purge test:**

1. **Class-level `@Transactional` hides FK violations.** `AbstractSessionIT:20-23` rolls back, so
   deletes never commit and a commit-time FK failure never appears in the `MvcResult`. Any test
   asserting the deletion disposition must be non-transactional.
2. **A `DataIntegrityViolationException` marks the transaction rollback-only**, so anything after it in
   the same test fails. This is why the removed reference test used
   `@Transactional(propagation = NOT_SUPPORTED)` plus `try/finally` manual cleanup.
3. **Committed rows leak into a JVM-wide shared H2** (`DB_CLOSE_DELAY=-1`, `forkCount=1`,
   `reuseForks=true`), and a leak is not a local failure. It breaks:
   - `AdminVehicleResourceIT` — asserts `X-Total-Count == "2"` **absolutely** against the un-scoped
     global `findAllArchived`. The single most leak-sensitive assertion in the suite.
   - `ReminderSelectionParityIT` — `hasSize(6)` + `verifyNoMoreInteractions`, over **global,
     non-owner-scoped** reminder queries.
   - `ReportParityIT` — `GoldenReference` throws `IllegalArgumentException("Undocumented vehicle id")`
     on any unknown id; a hard error, not a diff.
   - `UserResourceIT:366` calls `userRepository.deleteAll()` + `flush()`. It passes today only because
     no *committed* vehicle rows exist. Any change that commits vehicle rows breaks it via the same FK.
4. **No test touches image storage at all.** Fixtures set `image` to `""`/`null`. Asserting "purge
   deletes the image" needs new support — a real file written to the data directory, or a
   `@MockBean ImageStorageService` (which forks an extra Spring context).

**Blast radius of physical deletion** — the behaviour change most worth pinning:

| Surface | Archived today | Purged |
|---|---|---|
| Cost report / cost statistics | auto-**appended** if in-period (D2) | **silently absent**; previously-issued reports become unreproducible |
| Single-vehicle report | 200 with full history | **404** (was 410) — a client-visible status transition |
| Mileage / consumption | 200 historical | 404 / empty |
| Forthcoming events, reminders | already excluded | no delta |
| Owner isolation | 404 to other owners | 404 either way; no delta |

Nothing outside `vehicles` holds a vehicle id — audit events store only a principal string,
`ReminderAdvance` has no references, and all DTO `vehicleId` fields are request/response-scoped. The
only dangling references after a purge are the image file on disk and any client-held id.

### Precedent for irreversible admin operations

The project **already ships four unguarded hard-delete admin paths** — `DELETE /api/fuel-type/{type}`,
`/api/insurance-type/{type}`, `/api/reminder-advance/{days}`, `/api/users/{login}` — plus two scheduled
purges (`AuditEventService:39-47`, 30-day audit retention; `UserService:274-284`, 3-day unactivated-user
purge). All are JHipster-era carryovers preserved by S-02's parity mandate; none was ever framed as a
policy decision.

Against that: **every recorded *decision* about irreversibility in this programme has been to defer
it.** PostgreSQL+Flyway were cut during shaping because *"Migrating live production data is the only
irreversible operation in the programme; doing it before behavioural test coverage exists inverts the
correct sequencing"* (`prd.md:497-500`). A hard purge would be the second irreversible operation, and
the first exposed as a live API.

Backups are manual host-side shell scripts (`src/main/scripts/backup.sh`) with no schedule, retention,
rotation, or verification anywhere in this repo; `restore.sh` is itself destructive. They are named as
the rollback vehicle for *cutover* (`prd.md:405-408`), never as protection against a mistaken admin
action.

### The case FOR retention — what a purge spends

The strongest recorded argument is a direct hit on this change (`shape-notes.md:592-595`):

> *"The user's decision was explicit: archived vehicles still count in cost reports and statistics. A
> car sold last year still appears in last year's total cost of ownership. **This is what makes
> archiving worth building rather than simply refusing deletion with a 409.**"*

Note carefully what that does and does not settle: it rejects 409 as the answer to **vehicle**
deletion. It says nothing about **user** deletion, which is the question here. But it does mean that
any purge which is routinely used delivers, for that vehicle, exactly the outcome the owner rejected —
at the cost of an entire slice.

Supporting: the domain rule is stated as a truth claim, not a feature — *"an archived vehicle is no
longer active, but **it did still happen**"* (`prd.md:446-448`). US-02's acceptance criteria (*"Its
events still contribute to cost reports and statistics"*, `prd.md:216-221`) are directly falsifiable by
a purge, and the programme guardrail at `prd.md:179-181` is unconditional: *"No vehicle, event, user
account, or audit record is lost or mangled."*

Four conceptual breakages if purged costs vanish from historical reports:

- **Historical totals become non-reproducible**, with no record that anything was removed — there is no
  vehicle audit table, and one was explicitly excluded from the archiving scope.
- **The golden suite's invariant inverts.** Its diff-as-signal rule works only while historical output
  is a pure function of history; a purge makes a legitimate historical change indistinguishable from a
  filter leak.
- **410 vs 404 becomes three-valued.** Under D1, 410 means *"existed, retained, unreachable"*. After a
  purge the same id must return 404, and the states become indistinguishable to a caller.
- **D2's period scoping degrades silently.** Archived vehicles appear/disappear *by date window*
  (deliberate, reversible); purge removes them *by administrative act*, permanently, across all windows
  — producing identical-looking empty results from completely different causes.

## Code References

- `src/main/java/com/kasztelanic/carcare/service/UserService.java:221-227` — `deleteUser`, the plain
  `userRepository.delete(user)` that FK-fails
- `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java:187-195` — 204 + `X-carcare-alert`
- `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:128-135` — the
  `ArchivedResourceException` handler pattern to copy
- `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:144-149` — the only
  existing 409
- `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:181-197` —
  `handleUncaught`, where the FK violation currently lands
- `src/main/java/com/kasztelanic/carcare/web/rest/AdminVehicleResource.java:25-49` — admin conventions
- `src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java:49` — `restoreVehicle`
  uses bare `findById` with **no archived-state guard**
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:63-73` — soft delete
- `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:60-66` — idempotent
  `delete`
- `src/main/java/com/kasztelanic/carcare/config/CacheConfiguration.java:38-56` — the 15 cache regions
- `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:238-256` — all six
  vehicle-related FKs
- `src/main/resources/config/liquibase/data/user.csv` — the seeded `system` / `anonymoususer` accounts
- `src/test/java/com/kasztelanic/carcare/web/rest/AbstractSessionIT.java:20-23` — class `@Transactional`
- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:125-136` — `archive` /
  `archivedVehicleFor`, and the rule that they stay out of the golden seed

## Architecture Insights

- **The seam already exists.** `VehicleScopeService` centralises owner/active classification, and
  `AdminVehicleService` already owns the admin lifecycle. A purge belongs in `AdminVehicleService`
  alongside `restoreVehicle`, not in `VehicleServiceImpl` — which is correctly owner-scoped and must
  stay that way.
- **Admin operations are deliberately owner-blind.** `findAllArchived` and `restoreVehicle` use
  unscoped `findById`. This is what makes an admin-driven "purge then delete user" workflow possible at
  all — the equivalent owner-scoped workflow at baseline never was.
- **`restoreVehicle` has no archived-state guard**, so it will happily "restore" a never-archived
  vehicle. A purge modelled on it would inherit the same missing guard and could hard-delete a live
  vehicle. Whether purge requires prior archiving is a genuine interlock decision, and the existing
  restore precedent points the wrong way.
- **The 500-on-FK-violation is a bug class, not one bug.** Deleting a lookup row that is in use (a
  `fuel_types` row referenced by a vehicle) is very likely the same unhandled
  `DataIntegrityViolationException` → 500. No test covers it. A `DataIntegrityViolationException`
  handler would fix the class; this change could reasonably scope to one instance or to the class.
- **Layering constraint**: `ArchTest` forbids `service`/`repository` depending on `web`, so any new
  exception stays in `service/exception/` with no Spring Web imports — which is exactly why 094e32b
  moved `ArchivedResourceException` off `@ResponseStatus`.

## Historical Context (from prior changes)

- `context/changes/vehicle-archiving/research.md` — owner decisions D1–D5 and their rationale; D3 chose
  a timestamp over a boolean specifically *because it records **when***, which the admin recovery
  workflow benefits from.
- `context/changes/vehicle-archiving/change.md:17-42` — D6–D8, including D8's admin surface scope.
- `context/changes/vehicle-archiving/plan.md` — the scope boundary that excluded physical deletion,
  which is why FU-1 is a separate change.
- `context/changes/vehicle-archiving/reviews/impl-review.md` — finding F1, the origin of this work.
- `context/archive/2026-08-28-admin-surface-parity/research.md:122` — the recorded S-02 contract for
  `DELETE /api/users/{login}`: *"204 + userManagement.deleted, including nonexistent login"*. Vehicles
  are never mentioned in connection with user deletion anywhere in that change.
- `context/archive/2026-08-27-golden-baseline-capture/reference.md:30-68` — the F-02 capture inventory,
  which **does not cover the admin surface at all**; FR-007 was delivered from a source reading of
  `6e19b96`, not a runtime capture.
- `context/archive/2026-08-27-client-server-contract-trial/change.md:154-157` — the live MariaDB
  reproduction of vehicle deletion failing on event FKs at baseline.
- `context/foundation/shape-notes.md:28-29` — the vehicle-deletion policy decision that answered half of
  `CODEBASE_ANALYSIS.md:243` and dropped the retention half.

**Roadmap placement**: a new slice would be **S-08**, prerequisite **S-05**, extending Stream D ("new
behaviour and feedback"). It would be the second roadmap item not traceable to a PRD FR — S-06 was the
first. Note that S-05's roadmap status still reads `proposed` in four places; that is **mechanism, not
oversight** — `/10x-archive` flips it, and `vehicle-archiving` has not been archived yet. Do not read
"proposed" as evidence the work is undone.

## Related Research

- `context/changes/vehicle-archiving/research.md` — the direct predecessor; its call-graph and
  golden-fixture sections apply unchanged here.
- `context/archive/2026-08-28-admin-surface-parity/research.md` — admin surface contracts.

## Open Questions

**Owner decisions for this change are recorded in `context/changes/archived-vehicle-purge/change.md`
under "## Owner decisions" — read that alongside this document.** Decision P1 there resolves Open
Question 1 below.

Ordered by how much they block planning. **With P1 answered, none of the remaining questions blocks
the start of planning** — each is a decision the plan itself can frame and put to the owner.

1. ~~**Is there any external legal or contractual retention obligation?**~~ **RESOLVED 2026-08-29
   (owner): no. There is no legal or contractual obligation, in either direction** — nothing compels
   retention, and nothing compels erasure.

   Three consequences the plan must carry:

   - **Erasure is not a forcing function.** No external deadline, no data-subject right, no compliance
     driver. Nothing pushes back against the retention value established by FR-009, US-02, and D2, so
     that value stands unopposed and remains the system's default posture.
   - **Purge is housekeeping, not compliance.** This changes its design envelope: it may require an
     archived-first interlock, it need not be bulk, it need not be fast, it need not guarantee
     *complete* erasure (orphaned audit rows carrying a principal login string may remain), and it may
     legitimately be rare and deliberately awkward. A compliance-driven purge would have had the
     opposite requirements on every one of those axes.
   - **It weakens the case for purging at all, but does not remove it.** The user-deletion problem is
     solvable by tombstone reassignment alone, with no destructive operation anywhere. Purge would then
     exist solely to give the archive a terminal state for genuine housekeeping — a real need, but a
     smaller and less urgent one than "an admin must be able to delete a user". The plan should be
     explicit about which of those two jobs each part of it is doing.
2. **Disposition: 409 or tombstone?** Not a symmetric choice — see Summary finding 4. A 409 keeps
   deletion an explicit two-step admin act and preserves history until someone decides otherwise, but
   is incomplete without the purge endpoint. A tombstone makes the user disappear immediately while
   retaining history in a state no principal can query, and needs no destructive operation at all —
   which sits with this programme's recorded posture of deferring irreversible operations
   (`prd.md:497-500`), now that P1 has removed any compliance pressure in the other direction.

   If the tombstone route is taken: `anonymoususer` is the safer of the two seeded accounts (no
   authorities, already filtered from the admin user list at `UserService.java:246`), and reassignment
   should archive as it reassigns, so reminder mail never reaches `system@localhost`.
3. **Does purge require the vehicle to be archived first?** An interlock makes purge a deliberate
   two-step act. The existing `restoreVehicle` sets the opposite precedent by having no such guard.
4. **Should a purge be audited?** No vehicle audit table exists and one was explicitly excluded from
   the archiving scope. Without one, a purge is unattributable and historical totals change with no
   record — the sharpest cost identified in the retention analysis.
5. **Fix the `DataIntegrityViolationException` → 500 generally, or only this instance?** The general
   fix also covers in-use lookup deletion, which is untested and very likely 500s today.
6. **Where does image cleanup run?** Purge is the natural point, but `ImageStorageService` has no
   transaction-aware hook, and a file delete followed by a rollback is unrecoverable.
7. **Should protected logins be guarded?** Nothing currently prevents deleting `system`,
   `anonymoususer`, or the last remaining admin. Out of scope for FU-1 as written, but it is adjacent
   and cheap.

**Explicitly not verified**: whether the two scheduled purges actually run in the deployed system;
whether `backup.sh` is scheduled outside this repo; whether in-use lookup deletion 500s (suspected by
symmetry, untested); and the client's behaviour on a 410→404 transition (the client lives in the
sibling `../client` repository and was not inspected).
