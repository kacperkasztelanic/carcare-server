---
project: carcare (com.kasztelanic.carcare:carcare 1.3.11)
checked_at: 2026-08-30T09:16:00Z
health_status: needs-attention
context_type: brownfield
change_id: security-baseline
language_family: java
stack_assessment_available: true
checks_run:
  - lockfile
  - dependency_audit
  - outdated_deps
  - test_runner
  - ci_cd
  - configuration
audit_findings:
  critical: 0
  high: 0
  moderate: 0
  low: 0
  note: "no advisory scanner ran — counts are 'not measured', not 'clean'"
test_runner_detected: true
ci_provider: GitLab CI
recommended_fixes: 8
---

# Health Check — CarCare Server

Run against branch `refactor` at commit `28d0bc1`, in the context of the
`security-baseline` change. The previous health check, written for the closed
platform-foundation change, was archived to
`context/foundation/archive/health-check-2026-08-28-foundation.md`.

Same detection caveat as the stack assessment: this skill's project-marker probe
does not list `pom.xml` or `build.gradle`, so it finds nothing in a Java project
and would stop before checking anything. Detection was done from `pom.xml` and
`mvnw` directly.

## Dependency Health

### Lockfile

```
Status: present (pom.xml — exact-version pinning, plus .mvn/wrapper/maven-wrapper.properties)
Package manager: Maven 3.9.6 (./mvnw wrapper)
```

Maven has no separate lockfile; reproducibility comes from version pinning in the POM.
This project pins well: **40 `*.version` properties** are declared explicitly, the
`jhipster-dependencies` BOM was replaced by `spring-boot-dependencies` with explicit
overrides, and both the Java version (`[1.8,18)`) and the Maven version (`[3.9.6,)`) are
enforced at build time by the enforcer plugin. The `DependencyConvergence` enforcer rule
is also active, which catches transitive version conflicts that would otherwise resolve
silently.

One caveat that Maven shares with every non-lockfile ecosystem: transitive dependency
versions come from the BOM and are not individually pinned. `mvn dependency:tree` is the
only way to see what actually resolves.

### Security Audit

```
Tool: skipped — no advisory scanner available
Summary: not measured
Direct vs transitive: not measured
```

The skill's dispatch table lists Java as "skip — no built-in audit tool", and none of the
usual external scanners are installed on this machine: `dependency-check`, `ossindex`,
`grype`, `trivy`, `osv-scanner`, and `snyk` are all absent. `pom.xml` declares no
`dependency-check-maven` or `ossindex-maven-plugin` either.

**This is a gap, not a clean bill of health.** No vulnerability scanning happens anywhere
in this project — not locally, not in CI. The zeroes in this report's frontmatter mean
"not measured", and should not be read as "no vulnerabilities".

Recommended external tool for this ecosystem: the OWASP `dependency-check-maven` plugin
bound to a non-default profile so it does not slow the normal build, or `osv-scanner`
run against the resolved dependency tree. Either is a bounded, one-time setup.

### Outdated Dependencies

```
Packages with major version gaps: 9 of 40 pinned properties
Tool: versions-maven-plugin 2.16.2 (display-property-updates), run live against Maven Central
```

The finding that matters most:

- **`spring-boot`: 3.1.5 → latest stable 3.5.16 (3.x line) or 4.1.1 (4.x line).**
  The 3.1 line's own last release was **3.1.12** — this tree is 7 patch releases behind
  even within its EOL line, and the 3.1 line stopped receiving OSS fixes some time ago.
  There are four intervening minor lines (3.2, 3.3, 3.4, 3.5) and a major (4.x, now at
  4.1.1). This is the single largest health finding in the project and the reason the
  overall status is `needs-attention` rather than `healthy`.

Direct dependencies two or more major/minor generations behind:

| Property | Current | Latest | Gap |
|---|---|---|---|
| `spring-boot.version` | 3.1.5 | 3.5.16 (or 4.1.1) | 4 minor lines, or 1 major |
| `tika-core.version` | 2.7.0 | 4.0.0 | 2 majors |
| `vavr.version` | 0.10.3 | 1.0.0 | pre-1.0 → 1.0 |
| `liquibase.version` | 4.20.0 | 5.0.4 | 1 major |
| `guava.version` | 31.1-jre | 33.7.1-jre | 2 majors |
| `springdocs.version` | 2.2.0 | 3.1.0 | 1 major |
| `logstash-logback-encoder.version` | 7.4 | 9.0 | 2 majors |
| `archunit-junit5-engine.version` | 1.0.1 | 1.5.0 | 5 minors |
| `commons-io.version` | 2.15.0 | 2.22.0 | 7 minors |

Minor/patch gaps, listed for completeness: `lombok` 1.18.30→1.18.46, `jjwt` 0.12.3→0.13.0,
`mapstruct` 1.5.3.Final→1.7.0.Beta2 (beta — not a real gap), `jacoco` 0.8.8→0.8.15,
`jib` 3.3.1→3.5.2, `poi-ooxml` 5.2.5→5.5.1, `sonar-maven-plugin` 3.9.1→5.7.0, plus several
Maven plugins whose "latest" is a beta and should be ignored.

Two properties are pinned to versions with **no newer release at all** and need no action:
`carcare-client.version` 1.2.5 (the frozen client) and `git-commit-id-plugin` 4.9.10.

Note that `tika-core` and `commons-io` are both on the image-handling path this change
touches — worth knowing, though neither upgrade belongs in `security-baseline`.

## Test Suite

```
Test runner: JUnit 5 — Surefire 3.0.0 (unit) + Failsafe 3.0.0 (integration)
Tests found: 287 across 44 test classes
Test execution: passing — ./mvnw verify → BUILD SUCCESS in 42.8s
```

```
Configuration: pom.xml (Surefire excludes *IT*/*IntTest*; Failsafe runs them against in-memory H2)
Framework:     JUnit 5 + AssertJ + ArchUnit 1.0.1 + JaCoCo 0.8.8
Results path:  target/test-results/{test,integrationTest}  (relocated from Maven's default)
```

Measured directly from the JUnit XML this run produced:

| Phase | Classes | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Unit (Surefire) | 10 | 38 | 0 | 0 | 1 |
| Integration (Failsafe) | 34 | 249 | 0 | 0 | 0 |
| **Total** | **44** | **287** | **0** | **0** | **1** |

**Documentation drift — the suite is larger than every artifact claims.** `AGENTS.md`
(lines 21 and 229–231), `context/foundation/prd.md`, `shape-notes.md`, and
`stack-assessment.md` all state "38 unit and 217 integration tests, 255 in total". The
real figures are **38 and 249, 287 in total**. `AGENTS.md` also attributes the single
skipped test to the integration phase ("217 integration tests (1 skipped)"); it is
actually in the unit phase — the `@Disabled` test in `WebConfigurerTest`, which the same
paragraph names correctly two lines earlier.

The 32 extra integration tests are the S-05 through S-08 work landing after the F-04
paragraph was written: `ClientWireContractIT`, `JwtSessionIT`, `UserDeletionDispositionIT`,
and `VehicleArchivingAnalyticsIT` are all present and passing but unmentioned. The drift is
benign in effect but corrosive in kind — the number is quoted as a guardrail in the PRD,
and a guardrail nobody has re-measured is not a guardrail.

**Coverage** (JaCoCo, this run):

| Phase | Instruction | Branch | Line |
|---|---|---|---|
| Unit | 3.2% | 1.8% | 5.0% |
| Integration | 75.7% | 28.1% | 88.1% |

Line coverage of 88% from integration tests is strong. **Branch coverage of 28% is not** —
roughly seven in ten conditional paths are never exercised. For this change specifically
that matters at exactly the place the work lands: FR-002's fail-fast check is a new branch,
and `ImageStorageServiceImpl`'s error handling is existing uncovered branching. There is no
coverage floor configured, so nothing prevents this ratio from drifting further.

## CI/CD

```
Provider: GitLab CI
Configuration: .gitlab/gitlab-ci.yml  (non-default path — ci_config_path points here;
               a root .gitlab-ci.yml would be silently ignored)
```

| Stage | Status | Notes |
|---|---|---|
| Lint | ✗ | not configured — no Checkstyle, no Modernizer, no formatter check |
| Test | ✓ | `verify` job runs `./mvnw verify` on merge requests and default-branch pushes; `test` job runs `./mvnw test` on tags |
| Build | ✓ | `build` job runs `./mvnw deploy -Pprod`, tags only |
| Type check | — | not applicable — the Java compiler is the type check, and it runs in every job |
| Security | ✗ | not configured — no dependency scan, no container scan, no SAST |

Three structural observations, all of which the stack assessment already flagged and this
check confirms against the file:

- **The release path is tag-only.** `build`, `app`, and `proxy` all carry
  `rules: - if: $CI_COMMIT_TAG`. No merge request can exercise them. A change to
  `src/main/docker/app.yml`, `Dockerfile`, `entrypoint.sh`, or `env-template` therefore has
  **no** automated verification available — which is precisely the surface FR-001 modifies.
- **Merges are not gated.** `only_allow_merge_if_pipeline_succeeds` is `false` by owner
  decision, so even the `verify` job is advisory.
- **The reporting paths are correct and worth not breaking.** The JUnit globs point at
  `target/test-results/{test,integrationTest}`, not Maven's default `surefire-reports/`,
  and `artifacts: when: always` is set so reports survive failing runs. Both are easy to
  regress and both are load-bearing.

The security row is the actionable one: there is no dependency scanning, container
scanning, or static application security testing anywhere in the pipeline — in a project
whose current change is explicitly a security change.

## Configuration

### High severity

- **No `.env` rule in `.gitignore`.** The file has no `.env`, `*.env`, or `env-template`
  entry at all. FR-001 delivers the JWT signing key through an uncommitted `.env` on the
  deployment host — and nothing currently prevents that file from being committed. This is
  a two-line fix that must land *with* FR-001, not after it. Fix: add `.env` and `.env.*`
  (with a `!env-template` negation if the template should stay tracked) to `.gitignore`.

- **`src/main/docker/env-template` does not document the signing key.** It carries three
  lines — `MARIADB_PASSWORD_ENV`, `MAIL_PASSWORD_ENV`, `MAIL_BASE_URL_ENV` — and no
  key entry. It is the project's only documentation of the host-environment mechanism
  FR-001 adopts. Fix: add the signing-key variable when FR-001 lands.

### Medium severity

- **No dependency or container scanning anywhere.** Covered under Security Audit and CI/CD
  above; repeated here because it is a configuration gap, not just an unrun tool. Fix: add
  `dependency-check-maven` under a dedicated profile, and a scan job to the pipeline.

- **No coverage floor.** JaCoCo produces reports but enforces no threshold, so the 28%
  branch coverage can drift downward without any signal. Fix: add a `jacoco:check` rule
  with a floor at or slightly below the current numbers, so the ratchet only turns one way.

### Low severity

- **No `.dockerignore`.** The `app` job copies the WAR and Dockerfile into the build root
  before `docker build`, so the whole repository is in the build context. Cosmetic here,
  but it slows the tag pipeline and risks including files nobody intended.

Present and correct: `.gitignore`, `.editorconfig`, `CLAUDE.md` (a one-line `@AGENTS.md`
include), `AGENTS.md`, and `src/main/docker/env-template`. No `.env.example` at the
repository root, but `env-template` fills that role for the deployment and is the
better-placed file — not counted as a gap.

No secret-bearing files are tracked by name. The committed secret is not a file but a
default value inside `application-prod.yml:105` and `application-dev.yml:88` — the defect
`security-baseline` exists to fix, confirmed still present at this commit.

## Stack Assessment Cross-Reference

```
Stack assessment: context/foundation/stack-assessment.md
Agent readiness (from stack-assess): ready-with-compensation
```

| Quality gate gap | Health-check finding | Status |
|---|---|---|
| Typed — partial (generated code invisible to source reading) | The compiler runs in every CI job, so generated types are always validated at build time; `AGENTS.md` documents the trap and the `javap -p` workaround | **Mitigated** |
| Training data — partial (JHipster-shaped, not JHipster) | `AGENTS.md` names the trap explicitly; no CI or tooling dependency on JHipster remains | **Mitigated** |
| Gap 3 — Checkstyle and Modernizer declared but unwired | Confirmed from the CI side too: the pipeline has **no lint stage at all**, so nothing enforces style anywhere, and the dead properties are the only thing suggesting otherwise | **Reinforced** |
| (new) Documentation is trusted but unverified | `AGENTS.md` states a test baseline that is 32 tests out of date; the same file is the primary compensation artifact for the two partial gates | **New — reinforces the compensation risk** |

The last row is the one worth dwelling on. The stack assessment's verdict of
`ready-with-compensation` rests on `AGENTS.md` being accurate, because that file *is* the
compensation for both partial gates. Finding a stale, quotable number in it does not
invalidate the verdict — the two trap sections it depends on are still correct — but it
does mean the compensation artifact needs the same periodic re-verification as the code.

## Recommended Fixes

### Category A — Fix before or during agent work

1. **Add `.env` to `.gitignore`** — *quick (< 5 min).*
   The change in flight delivers a signing key through an uncommitted `.env`, and nothing
   stops that file being committed. Fixing the key's provenance while leaving the door open
   for the replacement to be committed would defeat the change's own purpose. Must land with
   FR-001.

2. **Correct the test baseline in `AGENTS.md`** — *quick (< 5 min).*
   38 unit and **249** integration, 287 total; the single skipped test is in the unit phase.
   The number is quoted as a guardrail in the PRD, and an agent that trusts `AGENTS.md`
   (as it is instructed to) will report a false regression the first time it counts.
   `context/foundation/prd.md`, `shape-notes.md`, and `stack-assessment.md` carry the same
   stale figure and should be corrected in the same pass.

3. **Document that Checkstyle and Modernizer do not run** — *quick (< 5 min).*
   The stack assessment drafted the `AGENTS.md` block; the CI reading confirms it from the
   other side, since there is no lint stage either. Nothing in this project enforces style,
   and two version properties imply otherwise.

4. **Add dependency vulnerability scanning** — *moderate (15–30 min).*
   Nothing scans this project's dependencies, and the platform is on an unpatched line. Add
   `dependency-check-maven` under a dedicated profile so it does not slow the normal build,
   and wire a job into the pipeline. In a project whose current change is a security change,
   this is the conspicuous absence.

5. **Plan the Spring Boot upgrade as its own change** — *significant (> 1 hour); do not fold
   into `security-baseline`.*
   3.1.5 is EOL, is 7 patches behind even its own line's final release (3.1.12), and sits
   four minor lines behind the current 3.x (3.5.16) with a major line beyond it (4.1.1).
   This is the largest health finding in the project. It is also exactly the
   `dependency-alignment` change already identified on the modernization roadmap, and the
   PRD's Non-Goals section explicitly holds the tree at its current versions for the
   duration of `security-baseline`. Keep it that way — but sequence it next.

6. **Add a coverage floor** — *moderate (15–30 min).*
   Branch coverage is 28.1%. A `jacoco:check` rule pinned at or just below current numbers
   turns the ratchet one way without demanding new tests today.

7. **Add a `.dockerignore`** — *quick (< 5 min).*
   The tag pipeline builds with the whole repository in context.

### Category B — Not blocking, already in hand

8. **CI lint stage** — the pipeline has no lint job. Worth adding alongside fix 3, once
   there is a decision on whether Checkstyle should be wired or the dead properties simply
   removed. Deciding that is the prerequisite; adding the job is trivial afterwards.

Agent instruction files and CI/CD already exist here and are in good shape, so the usual
Category B items do not apply to this project.

## Summary

**Status: needs-attention.** Not `healthy`, for two reasons that compound: the platform is
on an unsupported release line with no scanning of any kind to tell you what that costs,
and the delivery mechanism the change in flight depends on has no `.gitignore` guard. Not
`critical-issues` either — the test suite is green and fast, dependency pinning is
disciplined, and CI reports correctly.

**Strengths.** 287 tests passing in 42.8 seconds with 88% line coverage; exact version
pinning on 40 properties with convergence enforcement and hard build-time pins on both Java
and Maven; a CI pipeline whose reporting paths are correctly configured against two
well-known traps; and instruction files that already compensate for the subtlest hazards in
the tree.

**Gaps, in order of consequence.** The unpatched EOL platform. The complete absence of
vulnerability scanning. The missing `.gitignore` rule, which is small but lands directly on
the current change. Branch coverage at 28% with no floor. And a documentation baseline that
has quietly drifted 32 tests out of date in the one file the project relies on to
compensate for its own subtleties.

**For the change in flight.** Nothing here blocks `security-baseline`. Two findings attach
to it directly and should be folded into its scope rather than deferred: the `.gitignore`
rule (fix 1) and the `env-template` signing-key entry. One finding — the EOL platform —
is deliberately out of scope per the PRD's Non-Goals and should stay there, but it is the
strongest argument yet for `dependency-alignment` being the next change after this one.
