# Archived-Vehicle Purge & User-Deletion Disposition — Plan Brief

> Full plan: `context/changes/archived-vehicle-purge/plan.md`
> Research: `context/changes/archived-vehicle-purge/research.md`
> Owner decisions: `context/changes/archived-vehicle-purge/change.md` (P1–P7)

## What & Why

FU-1 from the vehicle-archiving review: archiving made vehicle rows permanent, so
`DELETE /api/users/{login}` now fails with an opaque 500 for any user who ever owned a vehicle, and
the archive has no terminal state — an admin can restore a vehicle but never dispose of one. This
change fixes user deletion via **tombstone reassignment** (no destructive operation) and gives the
archive a terminal state via an interlocked, audited admin **purge** endpoint. Per owner decision
P1, there is no legal or contractual retention obligation in either direction, so the purge is
housekeeping, not compliance — rare, interlocked, non-bulk by design.

## Starting Point

User deletion does a bare `userRepository.delete(user)` that FK-fails on `vehicles.owner_id` with
no `DataIntegrityViolationException` handler (500, verified empirically). Nothing cascades
anywhere (no `@OneToMany`, no `ON DELETE` on any of the nine FKs), the admin surface
(`/api/admin/vehicles`) already owns the archive lifecycle with an owner-blind `findById`, and the
seeded `anonymoususer` account is already filtered from the admin user list. No test anywhere
deletes a vehicle-owning user — that gap is how the narrowing went unnoticed.

## Desired End State

An admin can delete any user except `system`/`anonymoususer` (204, contract unchanged); the user's
vehicles move to `anonymoususer` with active ones archived, and reminder mail never reaches the
tombstone. An admin can hard-purge an **archived** vehicle via `DELETE /api/admin/vehicles/{id}/purge`
(404 unknown / 409 active / 204 purged): rows gone in FK order, image file deleted after commit,
`VEHICLE_PURGED` audit event written in-transaction. Any FK violation anywhere now returns 409
instead of 500. Purged vehicles vanish from reports (documented, accepted blast radius: their ids
return 404 where archived ones returned 410).

## Key Decisions Made

| Decision                     | Choice                                            | Why (1 sentence)                                                                                              | Source            |
| ---------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ----------------- |
| Retention obligation         | None either direction                             | Owner-verified: nothing compels retention or erasure, so purge is housekeeping, not compliance                 | Change (P1)       |
| deleteUser disposition       | Tombstone reassignment to `anonymoususer`         | Solves user deletion outright with zero destructive ops; preserves the S-02 204 contract                      | Plan (P2)         |
| Purge interlock              | `archivedAt != null` required (409 otherwise)     | Two-step deliberate act; avoids inheriting restoreVehicle's missing-guard flaw                                | Plan (P3)         |
| Purge audit                  | `VEHICLE_PURGED` persistent audit event, in-TX    | Zero schema change; 30-day attribution window proportionate to housekeeping                                   | Plan (P4)         |
| FK-violation error surface   | Class-level DIV → 409 handler                     | Fixes the whole bug class (incl. untested in-use lookup deletion), not one instance                            | Plan (P5)         |
| Image cleanup                | After-commit via TransactionSynchronization       | A file delete followed by rollback is unrecoverable and invisibly corrupts                                     | Plan (P6)         |
| Protected logins             | Guard `system` + `anonymoususer` (400)            | Cheap; under tombstone it is practically required — the tombstone target must never be deletable              | Plan (P7)         |
| L2-safe deletion style       | Entity-level `deleteAll`/`delete` only, no `@Modifying` | Bulk deletes bypass ehcache eviction in dev/prod and pass the entire test suite (L2 off in tests)         | Research (finding 6) |
| Purge route                  | `DELETE /api/admin/vehicles/{id}/purge`           | Follows the `/{id}/restore` sub-resource-verb precedent; disambiguates from owner-facing `DELETE /api/vehicle/{id}` | Plan |
| DELETE response              | 204 + existing `carcareApp.vehicle.deleted` alert | Matches UserResource (the other irreversible admin delete); no client bundle change                            | Research + Plan   |

## Scope

**In scope:** tombstone reassignment in `UserService.deleteUser`; protected-login guard; class-level
`DataIntegrityViolationException` → 409; `DELETE /api/admin/vehicles/{id}/purge` with interlock,
FK-ordered entity-level deletes, in-TX audit event, after-commit image deletion; two new
non-transactional ITs plus targeted additions to `UserResourceIT`/`LookupMaintenanceResourceIT`;
roadmap S-08 entry.

**Out of scope:** client changes of any kind; bulk purge; `restoreVehicle` archived-state guard;
last-admin rule; permanent purge ledger table; any Liquibase schema change; erasure of orphaned
audit rows (P1 accepts incomplete erasure).

## Architecture / Approach

`UserService.deleteUser` gains `VehicleRepository` + `Clock`: guard protected logins → load the
user's vehicles via a new **un-scoped** `findAllByOwnerLogin` query (existing owner queries resolve
`?#{principal.username}` to the acting admin) → reassign to `anonymoususer` with
archive-on-reassign → delete the user, all in the existing transaction. `AdminVehicleService`
gains `purgeVehicle(id)`: interlock check → capture image filename → load the five event lists via
existing `findByVehicleId` selects → register the after-commit image deletion → entity-level
`deleteAll` + `delete` (L2-safe) → save the `PersistentAuditEvent` in the same transaction. One new
exception pair (`ProtectedLoginException` 400, `VehicleNotArchivedException` 409) plus the DIV
handler, all in the established `ArchivedResourceException` pattern.

## Phases at a Glance

| Phase   | What it delivers                                                              | Key risk                                                                  |
| ------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 1. User-deletion disposition | Tombstone reassignment, protected-login guards, DIV→409 class handler + non-transactional ITs | Shared-H2 row leaks from committing tests breaking absolute-count assertions |
| 2. Admin purge endpoint     | Interlocked purge, in-TX audit, after-commit image delete + purge IT           | L2 eviction is unverifiable by the suite (entity-level deletes mandatory)    |
| 3. Documentation & full verification | Roadmap S-08 entry, clean full `./mvnw verify`, combined end-to-end smoke | None new — proves phases 1–2 together                                      |

**Prerequisites:** vehicle-archiving (S-05) landed — yes (archived 2026-08-29). Java 17 via
`JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`. MariaDB on `localhost:3306/carcare` for the
manual dev smokes only; tests run on in-memory H2.
**Estimated effort:** ~2–3 sessions across 3 phases.

## Open Risks & Assumptions

- A purge is irreversible; the only recovery is the unscheduled, unverified manual backups
  (`src/main/scripts/backup.sh`). Accepted under the housekeeping framing; interlock + audit are
  the mitigations.
- Tombstone recovery (reassigning vehicles back to a recreated user) has no admin endpoint — it is
  a direct DB update. Known operational caveat, accepted.
- The frozen client 1.2.5 may render `error.http.409` untranslated for the DIV handler (it may
  lack that key in its bundles). Cosmetic, consistent with existing handled 4xx; the client
  repository was not inspected.
- `error.http.409`/message rendering and the 410→404 transition for purged ids are client-visible;
  only the latter is documented behaviour, the former a display limitation.
- Whether the two scheduled purges (audit retention, unactivated users) run in the deployed system
  is unverified (research's explicitly-not-verified list) — does not affect this plan.

## Success Criteria (Summary)

- Any user except `system`/`anonymoususer` is deletable (204); their vehicles land with
  `anonymoususer`, actives archived, no reminder mail to the tombstone.
- Purge: 404 unknown / 409 active / 204 purged, rows and image file gone, audit event recorded,
  immediate 404 on dev with L2 on.
- Full `./mvnw verify` green from clean, including golden and absolute-count ITs — no shared-H2
  leaks from the new committing tests.
