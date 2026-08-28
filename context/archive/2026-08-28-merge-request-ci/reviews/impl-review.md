<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Merge-Request CI

- **Plan**: `context/changes/merge-request-ci/plan.md`
- **Scope**: Phases 1–3 of 3 (full plan)
- **Date**: 2026-08-28
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS (1 observation) |
| Pattern Consistency | PASS (1 observation) |
| Success Criteria | PASS (1 observation) |

## Verification re-run during this review

- `glab ci lint .gitlab/gitlab-ci.yml` → valid
- `POST projects/20026062/ci/lint` (dry_run, include_jobs) `ref=1.3.10` → exactly `test`, `build`, `app`, `proxy`
- same, `ref=master` → `valid:false`, "The resulting pipeline would have been empty"
- `grep -nE '^\s+(only|except):'`, `grep -n CI_BUILD_TOKEN`, `grep -nE 'docker login.*-p '` → all empty
- `git check-ignore -q .m2/repository` → exit 0
- `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify -s .gitlab/ci_settings.xml` → BUILD SUCCESS, 44.6 s
- JUnit XML tallies: 38 unit (1 skipped) + 217 IT (1 skipped) = **255**, matching criterion 2.6
- `target/jacoco/test/jacoco.xml` and `target/jacoco/integrationTest/jacoco.xml` both present
- GitLab docs (`ci/yaml/artifacts_reports`) confirm wildcards in `coverage_report:path` merge multiple reports — the `target/jacoco/*/jacoco.xml` glob is correct

Diff is confined to `.gitlab/gitlab-ci.yml`, `.gitignore`, and the change docs — no application, test, or `pom.xml` change, as the plan required. The plan body was never edited during implementation; only Progress checkboxes moved.

## Findings

### F1 — JVM release jobs still attach a privileged docker:dind service

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `.gitlab/gitlab-ci.yml:28-34` (`test`), `:54-63` (`build`)
- **Detail**: Phase 2 established the correct pattern on the new job — `services: []` at `:38`, because job-level `services` fully overrides `default:services` and a JVM job has no use for dind. `test` and `build` run the same `eclipse-temurin:17` image with the same non-need, but still inherit `default: services: [docker:dind]` (`:3-4`), so every tag pipeline starts a privileged dind sidecar twice for nothing. Phase 3 was scoped as "release-path hygiene" and this is the one such defect it did not pick up.
- **Fix**: Add `services: []` to the `test` and `build` jobs, matching `verify`. Neither script invokes docker, so it is behaviour-neutral — but it lands on the release path, which no MR can exercise, so it shares the deferred-verification caveat with manual item 3.6.
  - Strength: Two lines; makes the file internally consistent about which jobs need dind.
  - Tradeoff: Unverifiable until the next tag build, like the `--password-stdin` change.
  - Confidence: HIGH — `verify` already proves the override semantics in this file.
  - Blind spot: None significant.
- **Decision**: FIXED — added `services: []` to the `test` and `build` jobs (`.gitlab/gitlab-ci.yml:31,57`). Re-verified: `glab ci lint` valid, and the ci/lint dry-run on `ref=1.3.10` still returns exactly `test`, `build`, `app`, `proxy`.

### F2 — Branch pipelines can only ever be empty; master gets no CI

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: `.gitlab/gitlab-ci.yml:26` (workflow rule 4)
- **Detail**: Implementation matches the plan verbatim, so this is a plan-level observation, not drift. Rule 4 (`- if: $CI_COMMIT_BRANCH`) admits branch pipelines, but no job's rules can match a branch push: all four release jobs require `$CI_COMMIT_TAG` and `verify` requires `merge_request_event`. The lint API confirms it — `ref=master` returns `valid:false`, "The resulting pipeline would have been empty." Consequences: (a) pushing to a branch with no open MR produces a pipeline-creation error rather than nothing; (b) once `refactor` merges, `master` itself is never verified — only MRs and tags are.
- **Fix A ⭐ Recommended**: Add a default-branch rule to `verify` (`- if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH`) so post-merge master is covered and rule 4 stops producing empty pipelines.
  - Strength: Closes the post-merge verification gap; the job and its reports already exist.
  - Tradeoff: Adds a ~1-minute pipeline to every master push; slightly widens the change's scope past FR-017.
  - Confidence: MEDIUM — mechanically simple, but it is a scope decision the owner should make.
  - Blind spot: Not lint-verified for the MR path interaction; would need a dry-run on `master` after the edit.
- **Fix B**: Drop workflow rule 4 entirely, so branch pushes create no pipeline and the empty-pipeline error disappears.
  - Strength: Smallest change; removes dead configuration and the error noise.
  - Tradeoff: Master stays permanently unverified between MR and tag.
  - Confidence: HIGH — behaviour is fully determined by the rules already verified.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A, then superseded by an owner directive that settled the intended shape: merge requests and `master` run test/verify, deploy/release runs on tags. The workflow block was reduced to three rules (`merge_request_event`, `$CI_COMMIT_TAG`, `$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH`) and `verify` gained the default-branch rule. Both the catch-all branch rule and the `$CI_OPEN_MERGE_REQUESTS / when: never` rule were dropped as dead weight — the latter also being a foot-gun, since an MR opened *from* `master` would have suppressed the default-branch verify. Verified against the ci/lint API: `ref=1.3.10` → `test`, `build`, `app`, `proxy`; `ref=master` → `verify`; `ref=refactor` and `ref=websockets` → no pipeline and no error (previously `websockets` returned *"the resulting pipeline would have been empty"*). See `change.md` § *Post-review change: two pipeline kinds, plus tags for release*.

### F3 — `.m2/` gitignore pattern is unanchored, unlike its neighbours

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `.gitignore:79`
- **Detail**: The two lines directly above are `/log/` and `/target/`, anchored to the repo root. `.m2/` is unanchored and so ignores any directory named `.m2` at any depth, while the Phase 3 contract's intent is specifically `$CI_PROJECT_DIR/.m2`. Harmless in practice; inconsistent with the file's own local convention.
- **Fix**: Change the entry to `/.m2/`.
- **Decision**: FIXED — `.gitignore:79` changed from `.m2/` to `/.m2/`, matching the anchored `/log/` and `/target/` above it. `git check-ignore -q .m2/repository` still exits 0.

### F4 — Phase 2 manual items carry no recorded evidence

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `context/changes/merge-request-ci/plan.md` (items 2.5–2.9)
- **Detail**: 2.5–2.9 are the only proof the deliverable works — one job on the MR, 255 tests in the Tests tab, coverage annotations, auto-cancel, `when: always`. All are checked `[x]` against commit `98952d0`, but that commit is the YAML edit; nothing in the repo records the throwaway MR number or pipeline IDs. Phase 3's manual items set the better example — 3.6 and 3.7 both carry written decisions in `change.md`. Not a rubber-stamp accusation: the results are internally consistent and the JUnit tallies reproduced here match 2.6's number exactly. But the evidence trail for the change's entire user-visible outcome lives only in GitLab.
- **Fix**: Add a short note under `## Notes` in `change.md` with the throwaway MR's IID and the verifying pipeline ID, so the first tag-build investigation has a known-good pipeline to compare against.
- **Decision**: FIXED — evidence table added under `## Notes` in `change.md`, recovered from the GitLab API: throwaway MR **!2** (`tmp/ci-mr-smoke`), pipelines `2800731377` (255 tests), `2800747650` (canceled, proving 2.8), `2800752812` (failed with 1 named test failure, proving 2.9 / `when: always`). All Phase 2 manual items are corroborated. The note also records that measured job duration is 135–137 s warm / 315 s cold, correcting the plan's "roughly one minute" estimate.
