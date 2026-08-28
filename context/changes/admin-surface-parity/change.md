---
change_id: admin-surface-parity
title: Admin surface parity
status: implementing
created: 2026-08-28
updated: 2026-08-28
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Planning decisions: keep empty-page pagination at `page=0`; correct the three malformed lookup/config
  creation `Location` headers; repair reminder-advance DELETE path binding; consume both reminder
  golden fixtures; keep failure coverage to stable edges; manually smoke only users and audits.
