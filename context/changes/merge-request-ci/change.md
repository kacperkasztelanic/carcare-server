---
change_id: merge-request-ci
title: Merge request CI
status: impl_reviewed
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

### Phase 2 manual verification — evidence (recorded 2026-08-28 during impl review)

Manual items 2.5–2.9 were verified against throwaway merge request **!2**
(`tmp/ci-mr-smoke` → `master`, opened and closed 2026-08-28). Recovered from the GitLab API:

| Pipeline | Status | Jobs | Test report | Proves |
| --- | --- | --- | --- | --- |
| `2800731377` | success | `verify` only, 315 s | 255 total, 0 failed, 2 skipped | 2.5, 2.6, 2.7 |
| `2800747650` | **canceled** | `verify` | — | 2.8 — superseded by the next push rather than queued |
| `2800748097` | success | `verify` only, 135 s | 255 total, 0 failed, 2 skipped | 2.8 (the superseding run) |
| `2800752812` | **failed** | `verify`, 62 s | 39 total, **1 failed** | 2.9 — the deliberately broken test was reported despite the job failing, confirming `artifacts: when: always` |

`2800723742` (MR !1, failed at 71 s with 0 tests) was an earlier smoke attempt that did not reach
the test phase; MR !2 supersedes it.

The 255-test count matches a local `./mvnw verify` exactly (38 unit + 217 integration).

**Measured job duration corrects the plan.** `plan.md` § Performance Considerations estimated
"roughly one minute" from a 42.6 s local run. Actual shared-runner times are **135–137 s warm** and
**315 s cold** (first run, empty cache). Still far inside `build_timeout: 3600`, but the estimate
was optimistic by roughly a factor of two to five. MR !3 (`refactor` → `master`) reproduces the warm
figure: pipeline `2800826997`, 137 s, 255 tests green.

### Post-review change: two pipeline kinds, plus tags for release (2026-08-28)

The implementation review (`reviews/impl-review.md`, F2) found that the original workflow rule
`- if: $CI_COMMIT_BRANCH` admitted branch pipelines that no job could populate — the lint API
returned *"The resulting pipeline would have been empty"* for `ref=master`. The larger consequence
was that once `refactor` merges, `master` would have had no CI at all: two independently-green
merge requests can break `master` together with nothing to report it.

Owner then settled the intended shape explicitly: **merge requests and `master` run
test/verify; deploy/release runs on tags.** The workflow block was reduced to exactly those
three cases, and `verify` gained a default-branch rule:

```yaml
workflow:
    rules:
        - if: $CI_PIPELINE_SOURCE == "merge_request_event"
        - if: $CI_COMMIT_TAG
        - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
```

Two rules from the original block were dropped as dead weight under this shape:

- `- if: $CI_COMMIT_BRANCH` (catch-all) — the source of the empty-pipeline errors.
- `- if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS / when: never` — its job was to prevent a
  duplicate branch pipeline alongside an MR pipeline. With the catch-all gone, a feature branch
  never matches any rule, so there is nothing to suppress. Removing it also closes a foot-gun: had
  anyone opened a merge request *from* `master`, that rule would have matched a `master` push first
  and silently suppressed the default-branch verify.

Resulting behaviour, confirmed against the project's ci/lint API with `dry_run`:

| Context | ref tested | Pipeline |
| --- | --- | --- |
| Merge request (and each push to it) | — (lint cannot simulate `merge_request_event`) | `verify` |
| Push to `master` | `master` | `verify` |
| Push a tag | `1.3.10` | `test`, `build`, `app`, `proxy` |
| Push a feature branch with an open MR | `refactor` (MR !3 open) | none — the MR pipeline covers it |
| Push a feature branch with no MR | `websockets` | none — no error |

The MR row is proven empirically rather than by lint: pipelines `2800818669` and `2800826997` on
MR !3 both ran `verify` alone, green, 255 tests.

This widens the plan's Phase 1 and Phase 2 contracts, which specified the four-rule workflow block
and an MR-only rule on `verify`. `plan.md` is left as the historical record of what was planned;
this note records what changed after review. Future reviews should not read it as drift.

### Manual item 3.6 — accepted-deferred (2026-08-28)

3.6 ("the next release tag pushes both images successfully") verifies the Phase 3
`docker login --password-stdin` change, which only runs on a **tag** pipeline. No release
tag will be cut until the remaining roadmap work is ready — possibly later. The item is
accepted as deferred so the change can be archived; it must be re-checked on the first
tag build after `refactor` merges. If that pipeline's `app`/`proxy` jobs fail, the login
line is the first suspect.
