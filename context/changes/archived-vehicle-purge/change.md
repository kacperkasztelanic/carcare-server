---
change_id: archived-vehicle-purge
title: Admin purge for archived vehicles and user-deletion disposition
status: preparing
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
