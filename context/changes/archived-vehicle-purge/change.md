---
change_id: archived-vehicle-purge
title: Admin purge for archived vehicles and user-deletion disposition
status: impl_reviewed
created: 2026-08-29
updated: 2026-08-29
archived_at: null
---

## Notes

FU-1 from the vehicle-archiving implementation review: user deletion is now permanently blocked by
the `vehicles.owner_id` FK because archiving never removes vehicle rows and no purge path exists.
Needs an admin hard-purge for archived vehicles plus a `deleteUser` disposition decision.

Source: `context/changes/vehicle-archiving/follow-ups/review-fixes.md` (FU-1), raised by the
implementation review at `context/changes/vehicle-archiving/reviews/impl-review.md` (finding F1),
triaged 2026-08-29 and deferred by owner decision because the vehicle-archiving plan explicitly
excluded physical deletion from its scope.

## Owner decisions

- **P1 (2026-08-29)** — There is **no legal or contractual retention obligation** for this system, in
  either direction: nothing compels retention, and nothing compels erasure. This answers the question
  left open at `CODEBASE_ANALYSIS.md:243` since programme start, and resolves Open Question 1 in
  `research.md`.

  Consequences: erasure is not a forcing function, so the retention value in FR-009 / US-02 / D2 stands
  unopposed; a purge is **housekeeping, not compliance**, so it may be rare, interlocked, non-bulk, and
  need not guarantee complete erasure; and the user-deletion problem is solvable without any
  destructive operation if tombstone reassignment is chosen.

- **P2 (2026-08-29, planning session)** — Disposition: **tombstone reassignment.** `deleteUser`
  reassigns all owned vehicles (active and archived) to `anonymoususer`, archiving previously
  active vehicles as it reassigns (`archivedAt` from the injected `Clock`), then deletes the user.
  The S-02 response contract (`204 + userManagement.deleted`, including nonexistent login) is
  preserved. Purge is thereby pure archive housekeeping — not load-bearing for user deletion.
  Resolves research Open Question 2.

- **P3 (2026-08-29, planning session)** — Interlock: **purge requires `archivedAt != null`.** An
  active vehicle is rejected with 409. Disposing of a live vehicle is a deliberate two-step act
  (archive, then purge). Guarding `restoreVehicle` against non-archived targets was considered and
  declined for this change (existing IT contract; follow-up if wanted). Resolves Open Question 3.

- **P4 (2026-08-29, planning session)** — Audit: a **`VEHICLE_PURGED` persistent audit event**
  written inside the purge transaction (acting admin as principal; vehicle id, owner login, event
  counts in the data map), reusing `jhi_persistent_audit_event`. The existing 30-day audit
  retention window is proportionate to housekeeping; no permanent ledger table. Resolves Open
  Question 4.

- **P5 (2026-08-29, planning session)** — Error scope: **class-level**
  `DataIntegrityViolationException` → **409** handler in `ExceptionTranslator` (logged at warn),
  fixing the whole bug class including the untested in-use lookup deletion. No per-instance
  pre-check — under P2 the known user-deletion instance cannot fire. Resolves Open Question 5.

- **P6 (2026-08-29, planning session)** — Image cleanup: the vehicle's image file is deleted
  **after commit** via `TransactionSynchronization` (`STATUS_COMMITTED` only), with the filename
  captured before the row deletion. Resolves Open Question 6.

- **P7 (2026-08-29, planning session)** — Guards: `deleteUser` refuses `system` and
  `anonymoususer` (400 with a clear title). No last-admin rule — over-engineered for a
  single-admin deployment. Resolves Open Question 7.
