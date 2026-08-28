---
change_id: merge-request-ci
title: Merge request CI
status: implemented
created: 2026-08-28
updated: 2026-08-28
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

### Merge gate: "Pipelines must succeed" (project setting, not in this diff)

- **Setting**: `only_allow_merge_if_pipeline_succeeds`
- **Current value**: `false` on project `20026062` (`kkasztel_carcare/server`)
- **Where it lives**: GitLab → **Settings → Merge requests → Merge checks → "Pipelines must succeed"**
- **Status**: not flipped. The new `verify` pipeline is therefore *advisory* — it reports test
  results and coverage into the MR widget but does not block a merge.
- **Roadmap impact**: S-06 / FR-017 ("a developer opening a merge request receives automated
  compile, test, and verify results") is satisfied by the `.gitlab/gitlab-ci.yml` change alone.
  Blocking merges on green is a separate, stricter outcome.
- **Decision owner**: repo owner, to be made after the first real MR proves the pipeline green on
  a `master` that builds (i.e. once the migration branch merges). Not a repository change, so it
  cannot land in this diff.

**Decision (2026-08-28):** Owner chose to **defer** — leave `only_allow_merge_if_pipeline_succeeds` at `false`. The verify pipeline stays advisory. Revisit after the first real merge request runs green against a `master` that builds (i.e. once the migration branch merges). No project-setting change made as part of this change.
