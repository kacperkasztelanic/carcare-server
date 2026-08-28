# Merge-Request CI Implementation Plan

## Overview

Convert `.gitlab/gitlab-ci.yml` from tag-only legacy gating to `rules`-based gating governed by a
`workflow:` block, add a single `verify` job that runs on merge requests and reports test results
and coverage into the MR widget, and fix the pipeline hygiene defects surrounding it.

This delivers roadmap **S-06** / PRD **FR-017**: *a developer opening a merge request receives
automated compile, test, and verify results, rather than verification running only on tags.*

## Current State Analysis

`.gitlab/gitlab-ci.yml` is a single-commit file (`9db3421 "Add GitLab pipeline"`), never revised.
Four jobs across three stages, every one gated by `only: tags` + `except: branches`:

| Job | Stage | Script | Purpose |
| --- | --- | --- | --- |
| `test` | `test` | `./mvnw test -s .gitlab/ci_settings.xml` | Surefire only |
| `build` | `build` | `./mvnw deploy -s … -Pprod -DskipTests` | publishes to the package registry |
| `app` | `docker` | build + push `…/server/app` | image release |
| `proxy` | `docker` | build + push `…/server/proxy` | image release |

Confirmed against the live GitLab API and a local build run (see `research.md` for full evidence):

- **No merge request has ever existed on this project** (`state=all` returns 0), and all seven
  pipelines in its history were tag pushes, the last on 2023-03-21 (`1.3.10`). There is no MR
  pipeline behaviour to preserve.
- **`./mvnw test` has never run an integration test.** `pom.xml:252-255` excludes `**/*IT*` from
  Surefire; `pom.xml:274-287` binds Failsafe's `verify` goal, which no job invokes.
- **`./mvnw verify` is green at HEAD in 42.6 s** — 38 unit tests (1 skipped) + 217 integration
  tests (1 skipped), on in-memory H2 with no database and no registry token.
- **`ci_config_path` is already `.gitlab/gitlab-ci.yml`** on project `20026062`. Editing the file in
  place is correct; a root `.gitlab-ci.yml` would be ignored.
- **`shared_runners_enabled: true`**, 10 instance-type runners visible, `build_timeout: 3600`,
  `auto_cancel_pending_pipelines: enabled`, `only_allow_merge_if_pipeline_succeeds: **false**`.

## Desired End State

Opening a merge request against `kkasztel_carcare/server` creates a pipeline with exactly one job,
`verify`, which compiles the project and runs both test suites in roughly a minute. Its results
appear in the merge request's **Tests** tab, and coverage appears as line annotations in the diff.
Pushing another commit to the same branch cancels the superseded run rather than queuing behind it.

Tagging a release continues to produce the identical four-job pipeline it has produced since 2022.

**How to verify:** `glab ci lint --dry-run --ref <existing-tag> --include-jobs` lists `test`,
`build`, `app`, `proxy` and no others; the same command with `--ref master` lists nothing under the
tag rules; and a throwaway merge request produces a one-job pipeline whose Tests tab reports 255
tests.

### Key Discoveries:

- **The documented `workflow:` snippet would silently disable the release pipeline.** GitLab's
  canonical "switch between branch and MR pipelines" block matches only `merge_request_event` and
  `$CI_COMMIT_BRANCH`. A tag push sets `CI_COMMIT_TAG` and leaves `CI_COMMIT_BRANCH` unset, so no
  rule matches — and because `workflow` is evaluated *before* jobs, **no pipeline is created at
  all**. No error is raised. `- if: $CI_COMMIT_TAG` must be in the block.
- **`rules` and `only`/`except` are a hard pipeline error in the same job**
  (`jobs:test config key may not be used with 'rules': only`), so conversion is all-or-nothing per
  job.
- **Report paths are non-default.** `pom.xml:19,86-87` redirect Surefire and Failsafe to
  `target/test-results/{test,integrationTest}`. GitLab's documented Maven example globs
  `target/surefire-reports/TEST-*.xml`, which **does not exist here** — copying it verbatim reports
  zero tests while appearing to work.
- **Job-level `services` completely overrides `default:services`** rather than merging, so
  `services: []` detaches dind from the JVM job.
- **Top-level `image`/`services`/`cache` are the deprecated form**; `default:` is current and also
  accepts `interruptible`.
- **JaCoCo XML already exists** at `target/jacoco/{test,integrationTest}/jacoco.xml` with real data
  (IT run: 2 625 lines covered, 388 missed). GitLab accepts `coverage_format: jacoco` natively — no
  Cobertura conversion — and wildcards merge multiple reports.
- **Everything the MR job needs is public.** Projects `20026062` and `20026111` are both public and
  `client:1.2.5` returns HTTP 200 anonymously for `.pom` and `.jar`. No job token required.
- **`glab ci lint --dry-run --ref <ref> --include-jobs` simulates pipeline creation** for a branch or
  tag context, making the release-path regression check automatable. It cannot simulate
  `merge_request_event`, so the MR side is manual.

## What We're NOT Doing

- **No Checkstyle / lint execution.** `checkstyle.version` is declared with no bound execution
  (`health-check.md:253-255`); out of scope by owner decision.
- **No dependency or container scanning.** A named non-goal in the roadmap.
- **No coverage floor or `jacoco:check` rule.** PRD explicitly: *"no floor blocks a merge. Avoids a
  threshold that would block work before the suite is complete."* Coverage is reported, never enforced.
- **No coverage percentage badge.** JaCoCo prints no percentage to stdout; a headline number would
  need an extra build step purely to emit one. Diff annotations carry the useful signal.
- **No WAR artifact from the MR job.** The tag path already publishes it.
- **No change to the mutable `:latest` image tag.** `src/main/docker/app.yml` and `deploy.sh` may
  depend on it; changing it risks the deploy path for no MR-CI benefit.
- **No splitting `test` and `verify` into separate jobs.** They are two phases of one 43-second
  Maven lifecycle; splitting means compiling twice or passing `target/` as an artifact.
- **No GitHub Actions workflow.** MRs happen on gitlab.com; `origin` and `myszu` are mirrors.
- **No application, test, or `pom.xml` changes.** The build already emits everything needed.

## Implementation Approach

Three phases ordered by risk, not by convenience.

Phase 1 restructures gating across the whole file but is **designed to be a behavioural no-op** —
the same four jobs run on the same tag refs. Isolating it means the release-path regression can be
proven absent by lint simulation *before* any new job muddies the picture. Phase 2 adds the actual
deliverable against that verified baseline. Phase 3 is cleanup that touches only the release path
and one project setting.

## Critical Implementation Details

**The `workflow:` block must include a tag rule.** Rules are evaluated top-to-bottom and the first
match wins; if none match, the pipeline is never created. Because tag pushes set `CI_COMMIT_TAG` and
leave `CI_COMMIT_BRANCH` unset, omitting `- if: $CI_COMMIT_TAG` silently stops all releases with no
error message. This is the single highest-consequence line in the change and the reason Phase 1 is
verified independently.

**`interruptible` only affects running jobs.** The project already has
`auto_cancel_pending_pipelines: enabled`, which cancels *pending* pipelines. Cancelling a superseded
job that has already started additionally requires `interruptible: true`.

**`artifacts: when: always` is load-bearing.** The default (`on_success`) drops artifacts from failed
jobs — precisely the runs whose test reports matter.

---

## Phase 1: Gating and Globals

### Overview

Move the deprecated top-level globals into `default:`, introduce a `workflow:` block that switches
branch pipelines to MR pipelines while preserving tag pipelines, key the Maven cache on `pom.xml`,
and convert all four existing jobs from `only`/`except` to `rules`. No job's script changes; no job
is added or removed.

### Changes Required:

#### 1. Global configuration

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: Replace the deprecated top-level `image`, `services`, and `cache` keys with a `default:`
block, so per-job overrides follow the documented precedence rules. Keep `variables:` at the top
level, where it still belongs.

**Contract**: `default:` carries `image: docker:latest`, `services: [docker:dind]`, and the `cache`
block. `variables:` (`IMAGE_NAME_APP`, `IMAGE_NAME_PROXY`, `MAVEN_OPTS`) and `stages:` stay
top-level and unchanged. `MAVEN_OPTS` must keep pointing the local repository at
`$CI_PROJECT_DIR/.m2/repository` — GitLab can only cache paths inside the project directory.

#### 2. Cache key

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: The cache currently has no `key`, so every job on every ref shares GitLab's `default`
key and concurrent jobs overwrite each other's cache. Key it on `pom.xml` so a dependency change
invalidates cleanly.

**Contract**: Under `default: cache:`, add `key: { files: [pom.xml] }` alongside the existing
`paths: [.m2/repository]`.

#### 3. Workflow rules

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: Add a pipeline-level `workflow:` block so that a branch with an open merge request
produces one MR pipeline instead of a duplicate branch pipeline, while tag pipelines continue to be
created.

**Contract**: A new top-level `workflow:` key. Rule order matters — first match wins, no match means
no pipeline. The tag rule is mandatory; without it, releases stop silently.

```yaml
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_TAG
    - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
      when: never
    - if: $CI_COMMIT_BRANCH
```

#### 4. Job gating conversion

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: Convert `test`, `build`, `app`, and `proxy` from legacy `only`/`except` to `rules`, so
the file uses one gating mechanism and the new MR job can coexist with them.

**Contract**: In each of the four jobs, the `only: [tags]` / `except: [branches]` pair is replaced by
`rules: [{ if: $CI_COMMIT_TAG }]`. Behaviour is intended to be identical. Note that merge-request
pipelines do not define `CI_COMMIT_BRANCH` or `CI_COMMIT_TAG`, so these four jobs correctly never
appear on an MR.

### Success Criteria:

#### Automated Verification:

- CI configuration is syntactically valid: `glab ci lint .gitlab/gitlab-ci.yml`
- A tag ref still produces all four release jobs: `glab ci lint --dry-run --ref <latest existing tag> --include-jobs` lists `test`, `build`, `app`, and `proxy`
- A plain branch ref produces none of the four release jobs: `glab ci lint --dry-run --ref master --include-jobs`
- No `only:` or `except:` key remains in the file: `grep -nE '^\s+(only|except):' .gitlab/gitlab-ci.yml` returns nothing
- The tag rule is present in the workflow block: `grep -n 'CI_COMMIT_TAG' .gitlab/gitlab-ci.yml` matches inside `workflow:`

#### Manual Verification:

- Reading the diff confirms no job's `script:` block changed — this phase is a gating-only refactor

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human before proceeding to the next phase.

---

## Phase 2: The Merge-Request Verify Job

### Overview

Add the single job this change exists to deliver: `./mvnw verify` on every merge request, reporting
into the MR widget.

### Changes Required:

#### 1. New `verify` job

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: Run compile plus both test suites on merge requests and surface the results where the
reviewer reads them. Detach the inherited dind service, which this job has no use for.

**Contract**: A new job in the existing `test` stage:

- `image: eclipse-temurin:17` — matches the enforcer's exact-JDK-17 constraint; the wrapper supplies Maven 3.9.6
- `services: []` — job-level `services` fully overrides `default:services`, removing dind
- `interruptible: true` — required for the project's auto-cancel setting to stop a superseded *running* job
- `script: ./mvnw verify -s .gitlab/ci_settings.xml` — the settings file is harmless here (it only adds a `Job-Token` header for the `gitlab-maven` server id) and keeps the command identical to the release job's form
- `rules: [{ if: $CI_PIPELINE_SOURCE == "merge_request_event" }]`

#### 2. MR widget reporting

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: Publish JUnit results from both suites and JaCoCo coverage so failures appear in the
Tests tab and coverage appears as diff annotations.

**Contract**: An `artifacts:` block on the `verify` job. `when: always` is required — the default
`on_success` would discard reports from exactly the failing runs that need them. The JUnit paths are
the pom's relocated directories, **not** Maven's defaults.

```yaml
  artifacts:
    when: always
    reports:
      junit:
        - target/test-results/test/TEST-*.xml
        - target/test-results/integrationTest/TEST-*.xml
      coverage_report:
        coverage_format: jacoco
        path: target/jacoco/*/jacoco.xml
```

### Success Criteria:

#### Automated Verification:

- CI configuration remains valid: `glab ci lint .gitlab/gitlab-ci.yml`
- The release path is still intact after adding the job: `glab ci lint --dry-run --ref <latest existing tag> --include-jobs` still lists exactly `test`, `build`, `app`, `proxy`
- The referenced report paths are the ones the build actually produces: after `./mvnw verify`, `ls target/test-results/test/TEST-*.xml target/test-results/integrationTest/TEST-*.xml target/jacoco/*/jacoco.xml` all resolve
- The command the job runs is green locally: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify -s .gitlab/ci_settings.xml` exits 0 with 38 unit and 217 integration tests

#### Manual Verification:

- A throwaway merge request produces a pipeline containing exactly one job, `verify`
- That pipeline's Tests tab reports 255 tests, not zero — this is the check that catches a wrong JUnit glob
- Coverage annotations appear on changed lines in the MR diff
- Pushing a second commit to the open MR cancels the first run rather than queuing behind it
- Deliberately breaking one test surfaces it by name in the Tests tab, confirming `when: always`

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human before proceeding to the next phase.

---

## Phase 3: Release-Path Hygiene and the Merge Gate

### Overview

Fix the two deprecation/exposure defects in the docker jobs, stop the CI-local Maven repository from
appearing as untracked, and record the one change that cannot live in this diff.

### Changes Required:

#### 1. Registry login in the docker jobs

**File**: `.gitlab/gitlab-ci.yml`

**Intent**: `$CI_BUILD_TOKEN` is deprecated in favour of `$CI_JOB_TOKEN`, and passing a token via
`-p` on the command line exposes it in the container's process list.

**Contract**: In both the `app` and `proxy` jobs, the `docker login` line becomes a
`--password-stdin` form fed from `$CI_JOB_TOKEN`, keeping `-u gitlab-ci-token` and `$CI_REGISTRY`.
This path is exercised only by a tag pipeline, so it cannot be verified by any merge request.

#### 2. Ignore the CI-local Maven repository

**File**: `.gitignore`

**Intent**: `MAVEN_OPTS` places the local repository at `$CI_PROJECT_DIR/.m2/repository`. Anyone
reproducing the CI command locally gets an untracked ~300 MB directory.

**Contract**: Add a `.m2/` entry. The file already ignores `/target/` at line 78.

#### 3. Record the merge gate as a manual step

**File**: `context/changes/merge-request-ci/change.md`

**Intent**: `only_allow_merge_if_pipeline_succeeds` is `false` on project `20026062`. Until it is
flipped, the new pipeline is advisory — it reports but does not block. This is a project setting,
not a repository change, so it cannot land in this diff.

**Contract**: A note under `## Notes` recording the setting, its current value, where it lives
(**Settings → Merge requests → Merge checks → "Pipelines must succeed"**), and that the roadmap
outcome ("receives automated feedback") is satisfied by the file change alone. The flip is the
owner's call, to be made after the first real MR proves the pipeline green.

### Success Criteria:

#### Automated Verification:

- CI configuration remains valid: `glab ci lint .gitlab/gitlab-ci.yml`
- The deprecated token variable is gone: `grep -n 'CI_BUILD_TOKEN' .gitlab/gitlab-ci.yml` returns nothing
- No token is passed on a command line: `grep -nE 'docker login.*-p ' .gitlab/gitlab-ci.yml` returns nothing
- `.m2/` is ignored: `git check-ignore -q .m2/repository` exits 0
- The release path is unchanged one final time: `glab ci lint --dry-run --ref <latest existing tag> --include-jobs` lists exactly `test`, `build`, `app`, `proxy`

#### Manual Verification:

- The next release tag pushes both images successfully — the only real proof the login change works, and it will not occur during this change
- The owner has decided whether to flip "Pipelines must succeed", and `change.md` records the decision

---

## Testing Strategy

This change adds no application code and therefore no unit or integration tests. Verification is
structural and behavioural.

### Static verification:

- `glab ci lint` for syntactic validity after every edit
- `glab ci lint --dry-run --ref <tag> --include-jobs` as the release-path regression test, run at the
  end of each of the three phases — this is the check that would have caught a missing tag rule in
  the `workflow:` block
- `grep` assertions that legacy keys (`only:`, `except:`, `CI_BUILD_TOKEN`) are fully gone

### Build verification:

- `./mvnw verify -s .gitlab/ci_settings.xml` locally, confirming the exact command the job runs is
  green and produces the exact report paths the `artifacts:` block references

### Manual testing steps:

1. Push the branch and open a throwaway merge request against `master` on gitlab.com.
2. Confirm the pipeline contains exactly one job, `verify`, and that no `deploy` or `docker push`
   job appears.
3. Confirm the Tests tab reports 255 tests. Zero tests means the JUnit glob is wrong.
4. Confirm coverage annotations render on changed lines in the diff.
5. Push a second commit; confirm the first pipeline is cancelled rather than queued.
6. Break one test, push, and confirm it is named in the Tests tab — verifying `when: always`.
7. Restore the test, confirm green, and close the throwaway MR.

## Performance Considerations

The job is expected to run in roughly one minute: 42.6 s measured locally on a warm Maven cache, plus
image pull and cache restore. `build_timeout` is 3600 s, so there is no risk of hitting it.

The cache decision is the one genuine unknown. A cold repository pulls ~107 MB of jars across 184
artifacts plus Maven plugins — call it 250–400 MB populated. Whether GitLab's cache upload and
download beat re-downloading from Maven Central on shared runners is only measurable by running it.
Keying on `pom.xml` at minimum stops concurrent jobs from clobbering a single shared cache entry.
If the first real MR shows cache restore dominating the job, the fallback is
`policy: pull` on the MR job so only default-branch runs refresh it.

## Migration Notes

The release path is the thing at risk, and it is unobservable until someone tags. Two mitigations:

- Phase 1 is deliberately a no-op, verified by lint simulation against a real existing tag ref before
  any new job is introduced.
- The `docker login` change in Phase 3 genuinely cannot be verified before the next release. It is
  small and mechanical, but it should be the first thing checked if a future tag pipeline fails.

Rollback is `git revert` of a single-file change; no state, schema, or artifact is affected.

## References

- Research: `context/changes/merge-request-ci/research.md`
- Roadmap slice S-06: `context/foundation/roadmap.md:56`, `:423-437`
- PRD FR-017: `context/foundation/prd.md:360-364`; coverage-floor non-goal at `:531-533`
- CI/CD audit: `context/foundation/health-check.md:210-233`
- Current pipeline: `.gitlab/gitlab-ci.yml:1-68`
- Relocated report paths: `pom.xml:19`, `pom.xml:86-87`, `pom.xml:251`, `pom.xml:268`
- JaCoCo executions: `pom.xml:557-606`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Gating and Globals

#### Automated

- [x] 1.1 CI configuration is syntactically valid — 8532231
- [x] 1.2 A tag ref still produces all four release jobs — 8532231
- [x] 1.3 A plain branch ref produces none of the four release jobs — 8532231
- [x] 1.4 No `only:` or `except:` key remains in the file — 8532231
- [x] 1.5 The tag rule is present in the workflow block — 8532231

#### Manual

- [x] 1.6 Diff confirms no job's `script:` block changed — 8532231

### Phase 2: The Merge-Request Verify Job

#### Automated

- [x] 2.1 CI configuration remains valid — 98952d0
- [x] 2.2 The release path is still intact after adding the job — 98952d0
- [x] 2.3 The referenced report paths are the ones the build actually produces — 98952d0
- [x] 2.4 The command the job runs is green locally — 98952d0

#### Manual

- [x] 2.5 A throwaway merge request produces exactly one job, `verify` — 98952d0
- [x] 2.6 The pipeline's Tests tab reports 255 tests, not zero — 98952d0
- [x] 2.7 Coverage annotations appear on changed lines in the MR diff — 98952d0
- [x] 2.8 A second commit cancels the first run rather than queuing — 98952d0
- [x] 2.9 A deliberately broken test is named in the Tests tab — 98952d0

### Phase 3: Release-Path Hygiene and the Merge Gate

#### Automated

- [x] 3.1 CI configuration remains valid — 6bf87b5
- [x] 3.2 The deprecated token variable is gone — 6bf87b5
- [x] 3.3 No token is passed on a command line — 6bf87b5
- [x] 3.4 `.m2/` is ignored — 6bf87b5
- [x] 3.5 The release path is unchanged one final time — 6bf87b5

#### Manual

- [x] 3.6 The next release tag pushes both images successfully (accepted-deferred — the `--password-stdin` login change is only exercised by a tag pipeline; no release tag will be pushed until the roadmap work is complete, possibly later. Verify this on the first tag build after merge.)
- [x] 3.7 The owner has decided whether to flip "Pipelines must succeed", recorded in `change.md` — 6bf87b5
