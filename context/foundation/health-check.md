---
project: CarCare Server
checked_at: 2026-08-24T18:45:00Z
health_status: critical-issues
context_type: brownfield
language_family: java
stack_assessment_available: true
checks_run:
  - lockfile
  - dependency_audit
  - outdated_deps
  - test_runner
  - ci_cd
  - configuration
  - secret_scan
audit_findings:
  critical: 1
  high: 3
  moderate: 6
  low: 2
test_runner_detected: true
ci_provider: GitLab CI
recommended_fixes: 12
---

# Health Check — CarCare Server

## Dependency Health

### Lockfile

```
Status: n/a — Maven has no lockfile; dependency versions are pinned in pom.xml
Package manager: Maven (wrapper 3.9.6), plus a private GitLab registry for the client artifact
```

Maven's equivalent of lockfile integrity is that every coordinate resolves to an
explicit version, whether declared directly or supplied by a BOM. **That property
is currently broken.** Eleven dependencies receive no version from the declared
`jhipster-dependencies` 8.0.0 BOM, so the project has the Maven equivalent of a
missing lockfile — the build is not merely unpinned, it does not resolve at all:

```
commons-io:commons-io                    org.hibernate:hibernate-envers
io.jsonwebtoken:jjwt-api                 org.hibernate:hibernate-jcache
io.jsonwebtoken:jjwt-impl                org.hibernate:hibernate-jpamodelgen
io.jsonwebtoken:jjwt-jackson             org.springdoc:springdoc-openapi-webmvc-core
jakarta.cache:cache-api                  org.zalando:problem-spring-web
org.hibernate:hibernate-core
```

Confirmed by running `./mvnw -o validate` at head with JDK 17 on 2026-08-24.

One further supply-chain note: the client artifact is fetched from a private
GitLab registry, and versions below 1.2.3 are no longer retrievable. Build
reproducibility for older commits therefore depends on an external service, not
on this repository.

### Security Audit

```
Tool: skipped — no built-in dependency-vulnerability tool for the Java/Maven ecosystem
Recommended external tool: org.owasp:dependency-check-maven, or GitLab Dependency Scanning
Direct vs transitive: not distinguished (no scan performed)
```

A vulnerability scan could not be performed even manually: **the POM does not
resolve, so the dependency tree cannot be enumerated.** No tool — OWASP
Dependency-Check, Snyk, or GitLab's own scanner — can produce a dependency graph
from a model that fails to construct. Restoring dependency resolution is a
prerequisite for any security scanning at all.

The findings below therefore come from version analysis rather than CVE matching.

#### CRITICAL findings

- **Committed JWT signing key, shared between dev and production, not overridden
  at deploy time.** `src/main/resources/config/application-prod.yml:106` and
  `src/main/resources/config/application-dev.yml:92` contain byte-identical
  `base64-secret` values (verified by hash comparison; the value is not
  reproduced here). The production file's own comment states: *"As this is the
  PRODUCTION configuration, you MUST change the default key, and store it
  securely."* It was never changed — the key has been committed since the initial
  commit in October 2018.

  Critically, `src/main/docker/app.yml` overrides the datasource password and the
  mail password via environment variables but **does not override the JWT
  secret**, and no `JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET` override appears
  anywhere in the Docker, script, or CI configuration. The running production
  system signs tokens with the committed key.

  Anyone with read access to this repository — now, or at any point in the last
  eight years — can forge a valid token for any user, including one carrying
  `ROLE_ADMIN`. Rotating the value in the working tree does not remove it from
  git history.

#### HIGH findings

- **The declared platform is past end of open-source support.** Spring Boot
  3.1.5 was released in November 2023; the 3.1.x line stopped receiving free
  security patches in November 2024. Current is 4.1.1. Any vulnerability
  disclosed in the Spring Boot 3.1 line after that date is unpatched in this
  configuration, and no scanner is watching, because none can run.

- **The build produces no compiler feedback.** `./mvnw compile` fails during
  Maven model construction, before javac. For a statically typed language this
  removes the primary signal an agent — or a developer — relies on to know
  whether a change is valid.

- **CI has never run the integration tests.** `.gitlab/gitlab-ci.yml:20` runs
  `./mvnw test`, which invokes Surefire only; Surefire is configured to exclude
  `*IT*` and `*IntTest*`. Failsafe is bound to `verify`, and `verify` is never
  invoked — the build stage runs `deploy -DskipTests`. This is the mechanism by
  which a total integration-suite breakage went unnoticed from 2022-08-01 to
  2026-08-24: the tests that broke were never executed by the pipeline.

#### MODERATE findings

- Eleven unmanaged dependency coordinates (detailed above) — moderate as a
  supply-chain concern, high as a build blocker; counted once, under Lockfile.
- No dependency or container vulnerability scanning in CI. Compounding: FR-002
  transfers roughly 40 dependency versions to manual management.
- Development profile sets `cors.allowed-origins: "*"` with credentials allowed
  (`application-dev.yml`). Safe locally; dangerous if the profile is ever active
  in a deployed environment.
- JaCoCo is configured but enforces no coverage floor — no `check` goal, no
  `<rule>` block in `pom.xml`.
- CI pushes mutable `latest` tags alongside the commit tag for both images, so a
  redeploy of `latest` is not reproducible.
- CI uses `$CI_BUILD_TOKEN`, deprecated in favour of `$CI_JOB_TOKEN`.

#### LOW findings

- No `.dockerignore`, so Docker build context includes more than needed.
- `checkstyle.version` (10.9.2) is declared as a property but no Checkstyle
  plugin execution is bound — a declared-but-unused quality tool.

### Outdated Dependencies

```
Packages with major version gaps: 6
```

Declared versions compared against Maven Central metadata on 2026-08-24:

| Dependency | Declared | Latest | Gap |
|---|---|---|---|
| `org.springframework.boot` | 3.1.5 | 4.1.1 | **1 major + past EOL** |
| `org.hibernate.orm:hibernate-core` | 6.2.13.Final | 7.4.6.Final | **1 major** |
| `org.liquibase:liquibase-core` | 4.20.0 | 5.0.4 | **1 major** |
| `io.vavr:vavr` | 0.10.3 | 1.0.1 | **1 major (0.x → 1.0)** |
| `com.google.guava:guava` | 31.1-jre | 33.x | **2 major** |
| `tech.jhipster:jhipster-dependencies` | 8.0.0 | 8.11.0 | 11 minors |
| `org.apache.poi:poi-ooxml` | 5.2.5 | 5.5.1 | 3 minors |
| `org.mapstruct:mapstruct` | 1.5.3.Final | 1.6.3 | 1 minor |
| `org.projectlombok:lombok` | 1.18.30 | 1.18.46 | 16 patches |
| `org.jacoco:jacoco-maven-plugin` | 0.8.8 | 0.8.15 | 7 patches |

Two of these interact with decisions already made:

- **Vavr 0.10.3 → 1.0.1 is a real API break.** The deferred functional-programming
  sweep will land on a different API surface than the one currently in use, so
  that work should pin its target version before starting.
- **JaCoCo 0.8.8 predates JDK 21 class-file support** (added in 0.8.11). Not
  blocking at JDK 17, but it constrains any future toolchain move.

The JHipster gap is noted for completeness only — FR-002 removes the dependency
entirely.

## Test Suite

```
Test runner: JUnit 5 — Surefire 3.0.0 (unit) + Failsafe 3.0.0 (integration)
Tests found: 22 unit, 102 integration (21 test files, 11 integration classes)
Test execution: failing — cannot execute at head
```

```
Configuration: pom.xml (surefire excludes *IT*/*IntTest*; failsafe bound to verify)
Framework: JUnit 5 + Spring Boot Test, ArchUnit (layer enforcement), JaCoCo 0.8.8
```

A test runner is properly configured, and the split between unit and integration
tests is correct. The problem is not configuration — it is that **no test can run
at head**, because the build fails before compilation.

At the last runnable commit (`6e19b96`, 2022-05-20) the picture is:

- **22/22 unit tests pass.**
- **94/102 integration tests pass.** The 8 failures all occur in classes using
  `MockMvcBuilders.standaloneSetup(...)`, which bypasses the application's
  configured object mapper; 3 were traced directly to that harness. They are test
  defects, not product defects.

From `63d72ef` (2022-08-01) onward, all 102 integration tests error before
executing, because the test configuration names
`tech.jhipster.domain.util.FixedH2Dialect`, removed from `jhipster-framework` in
7.9.0.

**Effective integration coverage of business behaviour is zero, and has been for
four years.** There is no coverage of vehicle CRUD, any of the five event types,
reminder selection, report generation, the statistics calculators, or
owner-isolation negative cases — the existing 102 tests cover account, user,
security, audit, and configuration concerns inherited from the generator.

One structural asset is worth naming: `ArchTest` mechanically enforces that
`service` and `repository` must not depend on `web`. That is a real, executable
guardrail — once the build runs again.

## CI/CD

```
Provider: GitLab CI
Configuration: .gitlab/gitlab-ci.yml
```

| Stage | Status | Notes |
|---|---|---|
| Lint | ✗ | not configured (Checkstyle version declared, no execution bound) |
| Test | ~ | `./mvnw test` — unit tests only; integration tests never run |
| Build | ✓ | `./mvnw deploy -Pprod -DskipTests` → WAR + two Docker images |
| Type check | ✓ | implicit — javac runs as part of `test` |
| Security | ✗ | not configured (no dependency or container scanning) |

**The pipeline only runs on tags.** Every job carries `only: tags` with
`except: branches`, so no branch or merge request receives any automated
verification. Combined with `test` invoking Surefire only, the pipeline has never
executed an integration test in its history.

Additional observations: the Docker stage runs privileged `docker:dind`; images
are pushed to both the commit tag and a mutable `latest`; and `$CI_BUILD_TOKEN`
is deprecated in favour of `$CI_JOB_TOKEN`. The `eclipse-temurin:17` image is
correctly matched to the enforcer's JDK constraint.

## Configuration

### High severity

- **Secrets committed to version control** — `application-prod.yml` and
  `application-dev.yml` both carry the same JWT signing key, tracked in git and
  not gitignored. Fix: source the key from the environment
  (`JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET`), generate a new one, and add it
  to `src/main/docker/app.yml` alongside the existing password overrides. Treat
  the historical value as permanently compromised.

### Medium severity

- **No security scanning configuration** — no OWASP Dependency-Check plugin, no
  GitLab dependency/container scanning template. Fix: add
  `org.owasp:dependency-check-maven` to the build, or include GitLab's
  `Dependency-Scanning.gitlab-ci.yml` and `Container-Scanning.gitlab-ci.yml`
  templates.
- **No lint execution** — `checkstyle.version` is declared but no plugin
  execution is bound. Fix: either bind a Checkstyle execution with a
  `checkstyle.xml`, or remove the unused property.
- **No coverage floor** — JaCoCo reports but does not enforce. Fix: add a
  `check` goal with a `<rule>` once a real suite exists (deliberately deferred —
  see below).

### Low severity

- **No `.dockerignore`** — build context is larger than necessary. Fix: add one
  excluding `target/`, `.git/`, `context/`, and IDE directories.
- **No root `.env.example`** — partially mitigated by
  `src/main/docker/env-template`, which documents the three variables the Compose
  file expects. Fix: none required; the template is adequate.

Present and in good order: `.gitignore`, `.editorconfig`, `CLAUDE.md`,
`AGENTS.md`, `src/main/docker/env-template`.

## Stack Assessment Cross-Reference

```
Stack assessment: context/foundation/stack-assessment.md
Agent readiness (from stack-assess): ready-with-compensation
```

| Quality gate gap | Health-check finding | Status |
|---|---|---|
| JHipster — training data: fail | Dependency 11 minors behind; the removed `FixedH2Dialect` is the direct cause of the four-year test outage | **Reinforced** |
| JHipster — documentation: fail | The breaking removal appears in no changelog; it was found only by comparing jar contents | **Reinforced** |
| JHipster — conventions: partial | Lookup resources still bypass the service layer; no lint rule guards against it | **Reinforced** |
| Typed: pass, with codegen caveat | Compensation applied — `AGENTS.md` now carries the "do not reason from source alone" rule | **Mitigated** |
| Typed: pass — operational caveat | Confirmed: the build produces no compiler feedback at all, so type safety yields no practical benefit today | **Reinforced** |
| Conventions: pass (ArchUnit-enforced) | Confirmed present, but ArchUnit cannot run while the build fails | **Reinforced** |
| No trustworthy feedback loop | Confirmed and root-caused: CI never invoked `verify`, so the suite's breakage was structurally invisible | **Reinforced** |

The compensation recommended by the stack assessment **has been applied** —
`AGENTS.md` grew from 114 to 212 lines on 2026-08-24 with seven sections covering
the toolchain requirement, the known-good baseline, both failure causes, the
JHipster replacement guidance, and the two reading traps.

## Recommended Fixes

Ranked by impact on day-to-day work with this codebase. This project is a live
system with real users, not a training exercise, so findings are presented as a
single ranked list.

### 1. Rotate the JWT signing key and remove it from configuration

**Impact**: The production system signs tokens with a key committed to git since
2018 and shared with the development profile. Anyone with repository access can
forge a token for any user, including an administrator. This is the only finding
here that is exploitable today, by someone outside your control, without touching
the codebase.
**Severity**: critical
**Effort**: moderate (15–30 min)
**Fix**:

```bash
# 1. Generate a new key
openssl rand -base64 128 | tr -d '\n'

# 2. Add it to the deployment environment, NOT to a tracked file.
#    In src/main/docker/app.yml, alongside the existing password overrides:
#      - JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET=${JWT_SECRET_ENV}
#    and add JWT_SECRET_ENV to src/main/docker/env-template (as a placeholder).

# 3. Replace the tracked values with empty defaults in both
#    application-prod.yml and application-dev.yml.
```

Every existing token is invalidated, so users re-login once — already accepted by
the PRD's non-functional requirement ("at most one forced re-login"). The old key
remains in git history permanently; treat it as compromised rather than attempting
history rewriting on a repository with a live deployment.

> **This conflicts with a decision already recorded.** The PRD defers JWT rotation
> to a separate security pass and marks it "not blocking." That decision was made
> on the information available during shaping, which described the secret as
> committed in production configuration only. Two facts found here change the risk
> calculus: the key is **identical to the development key**, and it is **not
> overridden at deploy time**, so it is genuinely live. Worth reconsidering the
> deferral — but it remains your call, and nothing else in the plan depends on it.

### 2. Restore dependency resolution

**Impact**: Nothing else can proceed. No compilation, no tests, no dependency
scan, no vulnerability enumeration — a Maven model that fails to construct blocks
every downstream tool.
**Severity**: high
**Effort**: significant (> 1 hour)
**Fix**: Supply explicit versions for the eleven unmanaged coordinates, or replace
the `jhipster-dependencies` BOM with `spring-boot-dependencies`. This is FR-001
and FR-002 in the PRD; do it before touching any `javax.*` import, since the
compiler cannot give trustworthy feedback until the graph resolves.

### 3. Make CI run integration tests

**Impact**: This is the root cause of the four-year silent outage. `./mvnw test`
runs Surefire only, which excludes `*IT*`. Whatever regression suite FR-015
produces will be equally invisible unless the pipeline invokes `verify`.
**Severity**: high
**Effort**: quick (< 5 min)
**Fix**: change `.gitlab/gitlab-ci.yml:20` from `./mvnw test` to `./mvnw verify`.
Pair it with fix 4 so it actually runs on the changes that matter.

### 4. Run the pipeline on branches and merge requests

**Impact**: Every job is gated on `only: tags` / `except: branches`, so code
receives no verification until it is already tagged for release. Feedback arrives
after the decision it should have informed.
**Severity**: high
**Effort**: quick (< 5 min)
**Fix**: replace the `only`/`except` blocks on the `test` job with
`rules: [{ if: '$CI_PIPELINE_SOURCE == "merge_request_event"' }, { if: '$CI_COMMIT_TAG' }]`.
Leave `build` and the Docker stages tag-only. This is FR-017 in the PRD.

### 5. Plan a Spring Boot upgrade beyond 3.1.x

**Impact**: 3.1.x left free security support in November 2024, so the declared
platform receives no patches. Combined with the absence of scanning, an
unpatched advisory would go entirely unnoticed.
**Severity**: high
**Effort**: significant (> 1 hour)
**Fix**: not part of the current change, and it should not be — landing a green
build on 3.1.5 first is the right sequence. But choose the eventual target
deliberately rather than by default, and record it. Note that Jakarta EE and
Spring Security 6 migration work done now carries forward to any Boot 3.x or 4.x
target.

### 6. Add dependency vulnerability scanning

**Impact**: FR-002 makes roughly 40 dependency versions hand-managed, with
nothing watching them. Currently an explicit non-goal, which is defensible only
while the build is broken and scanning is impossible anyway.
**Severity**: moderate
**Effort**: moderate (15–30 min)
**Fix**: add `org.owasp:dependency-check-maven` to the build, or include GitLab's
`Dependency-Scanning.gitlab-ci.yml` template. Revisit once fix 2 lands, since a
scan is impossible before then.

### 7. Confine the permissive CORS policy to local development

**Impact**: `allowed-origins: "*"` with credentials allowed is safe for local work
and dangerous anywhere else. The risk is profile leakage, not the setting itself.
**Severity**: moderate
**Effort**: quick (< 5 min)
**Fix**: verify no deployed environment activates the `dev` profile
(`src/main/docker/app.yml` sets `prod,api-docs` — currently correct), and add a
comment recording the constraint so it survives future edits.

### 8. Stop publishing mutable `latest` tags

**Impact**: A deployment referencing `latest` is not reproducible, which
undermines the rollback plan the PRD depends on.
**Severity**: moderate
**Effort**: quick (< 5 min)
**Fix**: remove the `docker tag`/`docker push` lines for `:latest` in both the
`app` and `proxy` jobs, and pin `src/main/docker/app.yml` to explicit versions —
it already does, at 1.3.11.

### 9. Replace the deprecated CI token variable

**Impact**: `$CI_BUILD_TOKEN` is deprecated; the pipeline will break on a future
GitLab release.
**Severity**: moderate
**Effort**: quick (< 5 min)
**Fix**: replace both occurrences with `$CI_JOB_TOKEN`.

### 10. Pin the Vavr target before the functional-programming work

**Impact**: Vavr 1.0.1 is a breaking change from the 0.10.3 currently in use. The
deferred style sweep would otherwise land on a moving target.
**Severity**: moderate
**Effort**: quick (< 5 min)
**Fix**: record the intended target version in the deferred change's notes.
Staying on 0.10.3 is a legitimate choice; making it deliberate is the point.

### 11. Resolve the declared-but-unused Checkstyle configuration

**Impact**: A declared quality tool that never runs is misleading — it suggests
linting exists when none does.
**Severity**: low
**Effort**: quick (< 5 min)
**Fix**: either bind a Checkstyle execution with a `checkstyle.xml`, or delete the
`checkstyle.version` property.

### 12. Add a `.dockerignore`

**Impact**: Build context includes `target/`, `.git/`, and IDE directories,
slowing image builds.
**Severity**: low
**Effort**: quick (< 5 min)
**Fix**: add a `.dockerignore` excluding `target/`, `.git/`, `context/`, `.idea/`,
`.mvn/`.

### Deliberately deferred — no action recommended

These are real gaps, already recorded as explicit non-goals in the PRD. They are
listed so the absence reads as a decision rather than an oversight:

- **Coverage thresholds in CI** — deliberately deferred so a floor cannot block
  work before FR-015's suite exists.
- **Observability: tracing, alerting, job monitoring** — accepted consequence
  being that a silently stopped reminder job would go unreported.
- **Container image scanning** — same rationale as fix 6.
- **Performance targets** — a settled position at the current scale.

## Summary

```
Health status: critical-issues
```

The verdict is driven by three compounding facts rather than by general decay.
The production system signs authentication tokens with a key that has been in
version control since 2018, is identical to the development key, and is not
overridden at deployment — the only finding here exploitable today by someone
outside your control. The build does not resolve, so there is no compiler
feedback, no test execution, and no possibility of a vulnerability scan. And the
pipeline has never invoked `verify`, which is precisely why a total
integration-suite failure stayed invisible for four years.

The underlying project is in better shape than that summary suggests. Test
infrastructure is correctly configured — unit and integration tests are properly
separated, ArchUnit mechanically enforces the layering, and JaCoCo is wired up;
none of it can execute, but none of it needs redesigning. A known-good commit
exists whose sources are byte-identical to head, giving genuine behavioural
reference. Instruction files are present and now carry substantial compensation
for the codebase's specific traps. Nothing here calls for a rewrite; it calls for
restoring a feedback loop that has been absent long enough for its absence to
become invisible.

Next step: rotate the signing key — it is 20 minutes of work and the only finding
with an active external exposure — then proceed with FR-001 and FR-002 as
planned. Fixes 3 and 4 are five-minute changes that should land with FR-017;
together they ensure the regression suite you are about to build cannot break
silently the way the last one did.
