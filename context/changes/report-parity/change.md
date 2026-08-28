---
change_id: report-parity
title: Report parity
status: impl_reviewed
created: 2026-08-28
updated: 2026-08-28
archived_at: null
---

## Notes

Roadmap entry **S-03** (`context/foundation/roadmap.md`). Prerequisites F-02
(`golden-baseline-capture`, implemented) and F-04 (`test-context-restored`, archived) are both
delivered. Parallel with S-01, S-02, S-04.

Research (2026-08-28, `research.md`) ran a live probe of all eleven committed golden references
against HEAD. Ten of eleven already match once two test-harness defects are removed; the eleventh
is the documented intentional divergence from `4ad88bd`. See `research.md` for the evidence.

All four research open questions were resolved on 2026-08-28 (`research.md` § Decisions, D1–D4):
keep the zero-consumption golden and assert the divergence; fix i18n shadowing inside S-03; make
the `VehicleRichMapper`/`VehicleReport` ordering genuinely deterministic with an `id` tiebreaker
while deliberately leaving the missing `ORDER BY` on `findAllByIdAndOwnerIsCurrentUser` unpinned;
no Polish cost-report golden.
