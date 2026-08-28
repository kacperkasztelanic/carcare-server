# Merge-Request CI — Plan Brief

> Full plan: `context/changes/merge-request-ci/plan.md`
> Research: `context/changes/merge-request-ci/research.md`

## What & Why

`.gitlab/gitlab-ci.yml` gates all four of its jobs on `only: tags` / `except: branches`, and its one
test job runs `./mvnw test` — Surefire, which excludes `*IT*`. So no branch or merge request receives
any verification, and **no integration test has ever executed in this pipeline's history**. That is
the same mechanism by which a total suite outage went unnoticed from 2022-08-01 onward. This change
makes a merge request run `./mvnw verify` and report the results where the reviewer reads them.

Delivers roadmap **S-06** / PRD **FR-017**.

## Starting Point

One never-revised commit (`9db3421`) defines four tag-gated jobs: `test` (Surefire only), `build`
(`./mvnw deploy`, publishes to the package registry), and `app`/`proxy` (Docker image pushes). The
GitLab API confirms the project has had **zero merge requests in its lifetime**; all seven pipelines
were tag pushes, the last on 2023-03-21. Meanwhile `./mvnw verify` is green at HEAD in **42.6 s** —
38 unit + 217 integration tests, on H2, with no database and no registry token needed.

## Desired End State

Opening a merge request creates a one-job pipeline that compiles and runs both suites in about a
minute. Failures appear in the MR's Tests tab by name; coverage appears as line annotations in the
diff. Pushing another commit cancels the superseded run. Tagging a release still produces the
identical four-job pipeline it has produced since 2022.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| CI host | GitLab.com (`kkasztel_carcare/server`) | `myszu` and GitHub `origin` are mirrors; `ci_config_path` already points at `.gitlab/` | Research |
| Job shape | One `verify` job, not split test/verify | Surefire and Failsafe are two phases of one 43 s lifecycle; splitting means compiling twice | Research |
| Gating | Convert all four jobs to `rules` + a `workflow:` block | `rules` and `only` are a hard error in the same job; `workflow:` is the documented duplicate-pipeline fix | Plan |
| **Tag rule in `workflow:`** | **Mandatory `- if: $CI_COMMIT_TAG`** | Without it the release pipeline is **silently never created** — see Open Risks | Plan |
| Cache | Key on `pom.xml` | Currently unkeyed, so all jobs on all refs share one `default` key and clobber each other | Plan |
| MR widget | JUnit (both suites) + JaCoCo diff annotations | Both artifacts already fall out of `verify`; only the paths need declaring | Plan |
| Coverage enforcement | Report only, no floor | PRD: "no floor blocks a merge" — a threshold would block work before the suite is complete | Research |
| Coverage badge | Excluded | JaCoCo prints no percentage to stdout; a headline number needs a build step purely to emit one | Plan |
| Merge gate | Documented manual step, not in the diff | `only_allow_merge_if_pipeline_succeeds` is a project setting; the roadmap outcome is "receives feedback" | Plan |

## Scope

**In scope:** the `verify` job on merge requests; JUnit + JaCoCo reporting into the MR widget;
`only`/`except` → `rules` across all four jobs; a `workflow:` block; `default:` instead of deprecated
top-level globals; a keyed Maven cache; `services: []` to detach dind from the JVM job;
`$CI_JOB_TOKEN` + `--password-stdin` in the docker jobs; `.m2/` in `.gitignore`.

**Out of scope:** Checkstyle/lint execution; dependency and container scanning; any coverage floor;
a coverage badge; a WAR artifact from MRs; the mutable `:latest` image tag; GitHub Actions; any
application, test, or `pom.xml` change.

## Architecture / Approach

A `workflow:` block decides which pipelines exist at all; per-job `rules` decide which jobs appear in
them. Tag pushes match `$CI_COMMIT_TAG` and produce the unchanged four-job release pipeline; merge
requests match `merge_request_source` and produce exactly one `verify` job. The two paths share only
the `default:` block, and the MR job overrides `image` and `services` away from it. Everything the MR
job needs is public, so it runs with no credentials — which is what keeps it reproducible locally.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Gating and globals | `default:` block, `workflow:` rules, keyed cache, all four jobs on `rules` | Deliberately a no-op — but a wrong `workflow:` block silently kills releases |
| 2. Merge-request verify job | The deliverable: `verify` on MRs, with JUnit + JaCoCo reporting | Wrong JUnit glob reports zero tests while looking healthy |
| 3. Release hygiene + merge gate | `$CI_JOB_TOKEN`, `--password-stdin`, `.gitignore`, the settings decision | Unverifiable until the next release tag |

**Prerequisites:** S-01 (`session-parity`) — done, along with S-02 through S-04, so a real suite
exists for the pipeline to run. `glab` authenticated to gitlab.com (confirmed). Permission to open a
throwaway MR on `kkasztel_carcare/server`.

**Estimated effort:** ~1 session. One YAML file, one `.gitignore` line, plus a throwaway MR to
confirm the widget.

## Open Risks & Assumptions

- **The documented `workflow:` snippet is a trap.** GitLab's canonical "switch between branch and MR
  pipelines" block matches only `merge_request_event` and `$CI_COMMIT_BRANCH`. A tag push sets
  neither of those — so no rule matches, and because `workflow` is evaluated *before* jobs, **no
  pipeline is created and no error is raised**. The next `git tag` would just produce nothing. The
  plan adds `- if: $CI_COMMIT_TAG` and verifies it by lint simulation against a real tag ref.
- **Cache economics are unmeasured.** A cold repository pulls ~107 MB of jars across 184 artifacts
  plus plugins. Whether GitLab's cache beats re-downloading on shared runners is only knowable by
  running it; fallback is `policy: pull` on the MR job.
- **The `docker login` change cannot be verified during this change** — only the next release tag
  exercises it. It should be the first suspect if a future tag pipeline fails.
- **The pipeline is advisory until someone flips a setting.** `only_allow_merge_if_pipeline_succeeds`
  is `false`, and it cannot be changed from the repository.
- **`--dry-run` cannot simulate `merge_request_event`**, so the MR half of the change is confirmed
  only by opening a real throwaway merge request.

## Success Criteria (Summary)

- Opening a merge request produces a one-job pipeline; failures are named in the Tests tab and
  coverage annotates the diff.
- `glab ci lint --dry-run --ref <tag> --include-jobs` still lists exactly `test`, `build`, `app`,
  `proxy` — the release path is provably untouched.
- The Tests tab reports 255 tests, not zero.
