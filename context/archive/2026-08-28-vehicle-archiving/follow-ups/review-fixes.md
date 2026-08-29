# Follow-ups from the vehicle-archiving implementation review

Source: `context/changes/vehicle-archiving/reviews/impl-review.md` (2026-08-29).
Everything else from that review was resolved during triage; this file holds what was
deliberately deferred to its own change.

## FU-1 — Archived-vehicle purge and user-deletion disposition (from F1)

**Severity at review**: WARNING · **Impact**: HIGH · Dimension: Safety & Quality

### Problem

`UserService.deleteUser` (`src/main/java/com/kasztelanic/carcare/service/UserService.java:221`) does a
plain `userRepository.delete(user)`. `vehicles.owner_id` carries a hard FK to `jhi_user`
(`FKhm05kh6d8f082pgddom1q1yco` in `20190922082653_changelog.xml:244`) with no `ON DELETE CASCADE`.

Before vehicle-archiving landed, a vehicle *with events* already failed to delete on its own FKs, so
users owning such vehicles were already undeletable. Archiving widens this: vehicle rows are now
**never** removed, so `DELETE /api/users/{login}` fails with an FK violation for any user who has
ever owned a vehicle at all. The admin API added by S-05 can restore an archived vehicle but never
dispose of one, so the archive has no terminal state and there is no erasure path.

### Why it was deferred

The vehicle-archiving plan explicitly excluded physical deletion from scope ("This change does not
include physical deletion, cascading event deletion..."). Adding a purge path is a genuine new slice,
not a patch to this change. Deferred by owner decision during triage on 2026-08-29.

### Suggested scope

- An admin hard-purge operation for archived vehicles that cascades the five event tables
  (`refuels`, `repairs`, `routine_services`, `inspections`, `insurances`) and the stored image.
- A decision on what `deleteUser` does with the user's vehicles — purge, reassign, or refuse with a
  clear 409 rather than an FK-driven 500.
- Integration coverage for deleting a user who owns (a) no vehicles, (b) an active vehicle,
  (c) an archived vehicle with events.
- Check whether any GDPR/retention requirement constrains the choice before picking one.

### Not yet verified

Whether a retention or erasure requirement already exists for this system — that should be
established before choosing between purge and refuse.
