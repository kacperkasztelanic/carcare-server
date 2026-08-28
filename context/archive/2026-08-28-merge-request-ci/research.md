---
date: 2026-08-28T20:05:41+02:00
researcher: Kacper Kasztelanic
git_commit: 6c6702dc3560614854200b033510cb6c4eab682f
branch: refactor
repository: carcare-server (gitlab.com/kkasztel_carcare/server, project 20026062)
topic: "Run compile, test, and verify on merge requests (roadmap S-06 / PRD FR-017)"
tags: [research, ci, gitlab-ci, pipeline, merge-request, maven, jacoco, junit-reports]
status: complete
last_updated: 2026-08-28
last_updated_by: Kacper Kasztelanic
---

# Research: Merge-request CI

**Date**: 2026-08-28T20:05:41+02:00
**Researcher**: Kacper Kasztelanic
**Git Commit**: `6c6702d`
**Branch**: `refactor`
**Repository**: `carcare-server` — CI host is **gitlab.com/kkasztel_carcare/server** (project `20026062`)

## Research Question

Roadmap **S-06** / PRD **FR-017**: *a developer opening a merge request receives automated
compile, test, and verify results, rather than verification running only on tags.*

Scope confirmed with the owner before research:

| Decision | Answer |
| --- | --- |
| Which host hosts the merge requests | **GitLab.com** (`kkasztel_carcare/server`). The `myszu` and `origin`/GitHub remotes are mirrors; no GitHub Actions workflow is in scope. |
| How far the pipeline reaches | **Compile + test + verify**, **MR-widget test reporting**, and **pipeline hygiene**. |
| Explicitly out of scope | Lint (Checkstyle) execution and security/dependency scanning. Consistent with the roadmap, which lists dependency scanning as an accepted non-goal and coverage floors as deliberately deferred. |

## Summary

**The work is small, unblocked, and cheap to verify — and the single biggest risk it was
supposed to carry does not exist.**

Six findings drive the plan:

1. **Nothing runs on merge requests today, and nothing ever has.** All four jobs in
   `.gitlab/gitlab-ci.yml` carry `only: tags` + `except: branches`. The GitLab API confirms
   the project has had **zero merge requests in its lifetime** and every one of its seven
   pipelines was a tag push, the most recent on **2023-03-21** (tag `1.3.10`). The current
   `pom.xml` version `1.3.11` has never been tagged, so the pipeline has not run at all since.
2. **`./mvnw verify` is green and takes 42.6 s wall-clock** on a warm Maven cache — measured
   at HEAD today, `BUILD SUCCESS`, **38 unit tests** (1 skipped) + **217 integration tests**
   (1 skipped). The roadmap's premise that S-06 must wait for a real suite is satisfied: S-01
   through S-04 are all `done` and the suite has grown from the 22+115 recorded in `AGENTS.md`
   to 38+217. A verify job on every MR is a ~1-minute job, not a budget concern.
3. **The private-registry risk is not real.** Both the `client` project (`20026111`) and the
   `server` project are **public**, and `client:1.2.5` resolves over plain anonymous HTTPS
   (verified: `HTTP 200` for both the `.pom` and the `.jar` with no credentials). An MR
   pipeline needs no registry token to build.
4. **`only`/`except` and `rules` cannot coexist in one job**, so migrating is all-or-nothing
   per job. Because the MR job must *not* trigger `deploy`, `docker build`, or `docker push`,
   all four existing jobs need converting to `rules` alongside the new one.
5. **JUnit and JaCoCo artifacts already exist in the right shape — but not at the paths
   GitLab's documented example uses.** The pom redirects Surefire and Failsafe to
   `target/test-results/{test,integrationTest}` (10 + 29 XML files), not
   `target/surefire-reports`. JaCoCo XML is produced at `target/jacoco/{test,integrationTest}/jacoco.xml`
   and contains real data (IT run: 2 625 lines covered / 388 missed).
6. **Two hygiene defects will actively cost time on every MR pipeline**: a top-level
   `services: docker:dind` that attaches to the JVM jobs which have no use for it, and an
   unkeyed global Maven cache. Two more are correctness/deprecation issues in the tag jobs.

One thing the plan cannot fix from inside the repo: `only_allow_merge_if_pipeline_succeeds`
is **`false`** on the project. Until that is flipped in project settings, MR CI is advisory.

## Detailed Findings

### Current pipeline: `.gitlab/gitlab-ci.yml`

Four jobs across three stages, every one of them tag-gated.

| Job | Stage | Script | Gate |
| --- | --- | --- | --- |
| `test` | `test` | `./mvnw test -s .gitlab/ci_settings.xml` | `only: tags` / `except: branches` |
| `build` | `build` | `./mvnw deploy -s … -Pprod -DskipTests` | same |
| `app` | `docker` | build + push `…/server/app` | same |
| `proxy` | `docker` | build + push `…/server/proxy` | same |

- `.gitlab/gitlab-ci.yml:21-24` — the `only`/`except` pair, repeated verbatim at `:33-36`,
  `:50-53`, `:65-68`.
- `.gitlab/gitlab-ci.yml:20` — `./mvnw test` is **Surefire only**. `pom.xml:252-255` excludes
  `**/*IT*` and `**/*IntTest*` from Surefire, and `pom.xml:274-287` binds Failsafe's
  `integration-test` and `verify` goals — which no job ever invokes. **No integration test has
  executed in this pipeline's history.** This is the exact mechanism the roadmap cites for the
  suite outage going unnoticed from 2022-08-01 onward.
- `.gitlab/gitlab-ci.yml:1-3` — `image: docker:latest` and `services: [docker:dind]` are set at
  the **top level**. The `test` and `build` jobs override `image` to `eclipse-temurin:17`
  (`:18`, `:27`) but **cannot override away the service** — every JVM job still starts a
  privileged Docker-in-Docker sidecar it never talks to. A new MR job inherits the same waste
  unless `services: []` is set on it.
- `.gitlab/gitlab-ci.yml:4-6` — `cache: paths: [.m2/repository]` with **no `key:`**, so all
  jobs on all refs share GitLab's `default` cache key. Paired with
  `MAVEN_OPTS: -Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository` (`:10`), which correctly
  keeps the repo inside the project dir where GitLab can cache it.
- `.gitlab/gitlab-ci.yml:43`, `:58` — `docker login -p $CI_BUILD_TOKEN`. `$CI_BUILD_TOKEN` is
  deprecated in favour of `$CI_JOB_TOKEN`, and `-p` on the command line leaks the token into
  the process list; `--password-stdin` is the current form. Tag-job-only, but in scope under
  "pipeline hygiene".

### Live project state (GitLab API, 2026-08-28)

Queried via `glab api` against `projects/kkasztel_carcare%2Fserver`:

```
id                                    = 20026062
path_with_namespace                   = kkasztel_carcare/server
default_branch                        = master
visibility                            = public
ci_config_path                        = .gitlab/gitlab-ci.yml
shared_runners_enabled                = true
only_allow_merge_if_pipeline_succeeds = false
merge_method                          = merge
build_timeout                         = 3600
ci_default_git_depth                  = 50
auto_cancel_pending_pipelines         = enabled
```

- **`ci_config_path` is already the custom `.gitlab/` path** — no project setting needs
  changing for the file to be picked up. Editing `.gitlab/gitlab-ci.yml` in place is correct;
  do **not** add a root `.gitlab-ci.yml`, it would be ignored.
- **Merge requests: `state=all` returns 0.** Not "none open" — none have ever existed. There is
  no historical MR pipeline behaviour to preserve or regress.
- **Pipelines: 7 total, all `source: push` on tag refs**, `1.3.4` (2022-05-16) through `1.3.10`
  (2023-03-21). All `success` — including `1.3.6` on 2022-08-01, which is precisely why the
  outage was invisible: a green `mvn test` says nothing about the ITs.
- **Runners: 10 `instance_type` shared runners** are visible to the project, several `online`.
  `shared_runners_setting = enabled` at the group (`8590571`), with
  `shared_runners_minutes_limit = null`. Capacity is not a blocker.
- **`auto_cancel_pending_pipelines` is already `enabled`**, but that only cancels *pending*
  pipelines. Cancelling a *running* superseded job additionally requires `interruptible: true`
  on the job.

### Build and suite measurement (run at HEAD today)

```
$ export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem
$ ./mvnw verify -s .gitlab/ci_settings.xml
[WARNING] Tests run: 38,  Failures: 0, Errors: 0, Skipped: 1     ← Surefire (unit)
[WARNING] Tests run: 217, Failures: 0, Errors: 0, Skipped: 1     ← Failsafe (integration)
[INFO] BUILD SUCCESS
[INFO] Total time:  41.641 s
real 42.64  user 76.81  sys 2.95
```

- Warm `~/.m2`. **Cold-cache cost** is the download of **184 classpath artifacts totalling
  ~107 MB of jars**, plus Maven plugins — call it 250–400 MB for a populated
  `$CI_PROJECT_DIR/.m2/repository`. That is the number the cache decision turns on: a large
  cache upload/download can cost more than it saves on a 43-second build.
- No database is needed. Tests run on in-memory H2 via the layered
  `src/test/resources/config/application.properties` + `application-test.yml` pair, so the MR
  job needs no `services:` at all.
- `eclipse-temurin:17` remains the correct image. `pom.xml:717` enforces Maven `>= 3.9.6` and
  the enforcer rejects any JDK newer than 17 — the wrapper supplies Maven 3.9.6
  (`.mvn/wrapper/maven-wrapper.properties`), so the image only has to be right about the JDK.

### Artifact paths for the MR widget

The pom deliberately relocates test output away from Maven defaults:

- `pom.xml:19` — `project.testresult.directory = ${project.build.directory}/test-results`
- `pom.xml:86-87` — `junit.utReportFolder = …/test-results/test`,
  `junit.itReportFolder = …/test-results/integrationTest`
- `pom.xml:251` / `pom.xml:268` — Surefire and Failsafe `<reportsDirectory>` point at those.

Verified on disk after the run above:

```
target/test-results/test/*.xml               10 files
target/test-results/integrationTest/*.xml    29 files
target/failsafe-reports/failsafe-summary.xml  (summary only — not JUnit XML)
target/surefire-reports/                      does not exist
```

**GitLab's documented Maven example uses `target/surefire-reports/TEST-*.xml` and
`target/failsafe-reports/TEST-*.xml`. Copying it verbatim would silently report zero tests.**
The correct globs are `target/test-results/test/TEST-*.xml` and
`target/test-results/integrationTest/TEST-*.xml`, and the job needs `artifacts: when: always`
so reports upload on a failing build — which is the only time they matter.

JaCoCo (`pom.xml:557-606`, activated via `pom.xml:467-469`) writes XML at both:

```
target/jacoco/test/jacoco.xml            LINE covered=160  missed=2853
target/jacoco/integrationTest/jacoco.xml LINE covered=2625 missed=388
```

Current GitLab supports `coverage_format: jacoco` natively — no Cobertura conversion step is
needed — and wildcards merge multiple reports, so `target/jacoco/*/jacoco.xml` covers both.

A JaCoCo/argLine gotcha worth recording because it *looks* broken and is not: `pom.xml:26`
declares `<argLine>` as a property (`-Duser.timezone=UTC`, needed by `HibernateTimeZoneIT`),
and JaCoCo's `prepare-agent` also writes the `argLine` property. JaCoCo **appends** the
existing value rather than replacing it. Confirmed empirically — coverage is non-zero *and*
`HibernateTimeZoneIT` passes in the same run. Do not "fix" this.

### GitLab syntax needed (fetched from current docs, 2026-08-28)

Avoiding duplicate branch + MR pipelines — the canonical `workflow` block:

```yaml
workflow:
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
      when: never
    - if: $CI_COMMIT_BRANCH
```

The `only` → `rules` translation for the four existing jobs is
`only: tags` + `except: branches` → `rules: [{ if: $CI_COMMIT_TAG }]`.

The mixing constraint is a hard pipeline error, not a warning:

```
jobs:test config key may not be used with `rules`: only
```

So `rules` and `only`/`except` cannot appear in the same job. Adding a `workflow:` block does
not by itself force the conversion, but leaving four jobs on legacy gating while the pipeline
is governed by `workflow: rules` is the configuration most likely to produce surprising
double-pipeline behaviour — convert all four.

`interruptible: true` on the MR job is what makes the project's already-enabled
auto-cancellation actually stop a superseded *running* job.

## Code References

- `.gitlab/gitlab-ci.yml:1-3` — top-level `docker:latest` + `docker:dind` service leaking into JVM jobs
- `.gitlab/gitlab-ci.yml:4-6` — unkeyed global `.m2/repository` cache
- `.gitlab/gitlab-ci.yml:16-24` — the `test` job: `./mvnw test`, tag-gated
- `.gitlab/gitlab-ci.yml:43`, `:58` — deprecated `$CI_BUILD_TOKEN`, password on the command line
- `.gitlab/ci_settings.xml:3-15` — configures a `Job-Token` header for the `gitlab-maven` server id only; the `gitlab-maven-client` repo resolves anonymously
- `pom.xml:19`, `pom.xml:86-87` — relocated JUnit report directories
- `pom.xml:26` — `argLine` with `-Duser.timezone=UTC`
- `pom.xml:90-101` — `gitlab-maven-client` (project 20026111, hardcoded) and `gitlab-maven` (`${CI_API_V4_URL}`/`${CI_PROJECT_ID}`) repositories
- `pom.xml:102-108` — `distributionManagement`, the reason `build` runs `deploy` and must stay tag-only
- `pom.xml:246-288` — Surefire excludes `*IT*`; Failsafe includes it and binds `verify`
- `pom.xml:557-606` — JaCoCo executions writing to `target/jacoco/{test,integrationTest}`
- `pom.xml:717` — enforcer's Maven `>= 3.9.6` rule

## Architecture Insights

- **The pipeline's release path and its feedback path have opposite requirements.** `build`
  runs `./mvnw deploy`, which publishes to the package registry via
  `distributionManagement` — that must remain strictly tag-gated. The MR job is the mirror
  image: it must run `verify` and publish nothing. This is why the new job is genuinely new
  rather than a relaxed gate on the existing `test` job.
- **`test` and `verify` are not worth splitting into two jobs here.** Surefire and Failsafe are
  two phases of a single 43-second Maven lifecycle; splitting them would mean either running
  `compile` twice or passing `target/` between jobs as an artifact, both of which cost more
  than they save. One `verify` job is the right shape.
- **The infrastructure the MR widget needs is already produced by the build** — JUnit XML and
  JaCoCo XML both fall out of `./mvnw verify` with no extra goals. The change is almost
  entirely a matter of declaring the right paths, and the paths are non-default.
- **Everything the MR job needs is public.** No job token, no `services:`, no database. That
  makes the job trivially reproducible locally, which is the property that keeps CI honest.

## Historical Context (from prior changes)

- `context/foundation/roadmap.md:56`, `:423-437` — **S-06** is `proposed`, prerequisite
  **S-01**, parallel with S-05, no blockers, no unknowns. S-01 is `done`
  (`context/archive/2026-08-26-session-parity/`), so S-06 is unblocked. The roadmap's stated
  reason for keeping a nice-to-have this late: `.gitlab/gitlab-ci.yml:20` runs Surefire only,
  so "the regression suite built across S-01 to S-05 would never execute in CI."
- `context/foundation/prd.md:360-364` — **FR-017**, priority *nice-to-have*, with the explicit
  caveat that it "has value only once FR-015 lands." FR-015 (the regression suite) is
  satisfied by S-01–S-04, all archived.
- `context/foundation/prd.md:531-533` — coverage thresholds in CI are a **named non-goal**:
  "no floor blocks a merge. Avoids a threshold that would block work before the suite is
  complete." Reporting coverage in the MR widget is in scope; *enforcing* it is not.
- `context/foundation/health-check.md:210-233` — the CI/CD audit that catalogued the tag-only
  gating, the missing integration tests, the privileged dind, the mutable `latest` tag, and
  `$CI_BUILD_TOKEN`. Note this file is **stale in its dependency section** (it still describes
  the pre-F-01 11-unresolved-dependency state); its CI/CD section, re-verified today, is
  accurate.
- `context/foundation/health-check.md:253-258` — Checkstyle-never-bound and no-coverage-floor
  are recorded as medium-severity findings. Both are deliberately **out of this change's
  scope** per the owner's answer.
- `git log -- .gitlab/` — a single commit, `9db3421 "Add GitLab pipeline"`. The file has never
  been revised since it was written.

## Related Research

No prior research artifact covers CI. The nearest neighbours are
`context/archive/2026-08-25-test-context-restored/` (which established that `./mvnw verify`
boots and passes — the precondition this change consumes) and
`context/foundation/health-check.md` (the audit that first recorded the gating defect).

## Open Questions

1. **Cache the Maven repository, or not?** A cold `verify` must fetch ~107 MB of jars plus
   plugins; a warm build is 43 s. Whether a keyed `.m2` cache is net-positive on GitLab shared
   runners depends on cache pull/push throughput, which is only measurable by running it both
   ways. Recommend keeping the cache, adding a `key` tied to `pom.xml`, and measuring on the
   first real MR.
2. **`only_allow_merge_if_pipeline_succeeds` is `false`.** Flipping it is a project setting,
   not a repository change, so it cannot land in this change's diff. Decide whether S-06 is
   "the pipeline runs" or "the pipeline gates the merge" — the roadmap outcome says
   *"receives automated feedback"*, which the file change alone satisfies.
3. **Compute-minute exposure.** `shared_runners_minutes_limit` is `null` at the group and the
   projects are public, but the API does not expose the namespace's actual quota or usage
   without owner scope. Low risk at ~1 minute per MR pipeline; worth a glance at the group's
   usage page before relying on it.
4. **Coverage percentage in the pipeline badge.** `coverage_report` drives MR *diff*
   annotations, but the pipeline's headline coverage number still comes from a `coverage:`
   regex over the job log — and JaCoCo prints no percentage to stdout. If a headline number is
   wanted, it needs an extra step to emit one. Diff annotations alone may be enough.
5. **`.m2/` is not in `.gitignore`.** CI writes `$CI_PROJECT_DIR/.m2/repository`; anyone
   reproducing the CI command locally gets an untracked 300 MB directory. One-line fix, worth
   folding in.
