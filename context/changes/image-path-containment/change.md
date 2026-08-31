---
change_id: image-path-containment
title: Image path containment
status: implementing
created: 2026-08-31
updated: 2026-08-31
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

### Research scope decision — 2026-08-31

`/10x-research` was invoked with change-id `image-path-containment` (roadmap **S-05**), but the
owner widened the scope at the clarification step to cover the whole vehicle image write path as
one subject: **S-02 `image-write-ordering`**, **S-04 `image-format-allowlist`** and
**S-05 `image-path-containment`** together. Rationale: all three rewrite the same ~72-line class
(`service/impl/ImageStorageServiceImpl.java`) and its callers, and the roadmap already records that
parallelising S-02 and S-04 would conflict.

`research.md` in this folder therefore covers all three slices. It is expected to feed three
separate plans, or one merged one — that sequencing decision is deliberately left open for
`/10x-plan`.

The owner also directed that the PRD's accepted premise for FR-008 — "it guards a path no code can
reach, since filenames are server-generated UUIDs with no client influence" — be **verified against
the code rather than inherited** from the shaping record.

### Plan review triage — 2026-08-31

`/10x-plan-review` returned REVISE with 6 findings (0 critical, 3 warnings, 3 observations); all
six were fixed in `plan.md`. Two were substantive: the test harness now uses a **single JVM-wide
scratch root** rather than a per-class `static @TempDir`, because the latter registers a different
property value per subclass and would have forked a Spring context for each; and Phase 4's claim
that the Phase 2 callback cleans up after a rejected upload was wrong — nothing is written on that
path, and `addVehicle` registers no callback at all, so create-path rollback orphans are now a
stated out-of-scope gap. Report: `reviews/plan-review.md`.
