---
project: carcare (com.kasztelanic.carcare:carcare 1.3.11)
assessed_at: 2026-08-30T00:00:00Z
agent_readiness: ready-with-compensation
context_type: brownfield
change_id: security-baseline
stack_components:
  language: Java 17
  framework: Spring Boot 3.1.5
  build_tool: Maven 3.9.6 (./mvnw wrapper)
  test_runner: JUnit 5 (Surefire 3.0.0 / Failsafe 3.0.0) + AssertJ + ArchUnit 1.0.1
  package_manager: Maven
  ci_provider: GitLab CI (.gitlab/gitlab-ci.yml)
  deployment_target: Docker Compose behind NGINX; Jib 3.3.1 → eclipse-temurin:17-jre
gates_passed: 7
gates_partial: 2
gates_failed: 0
---

# Stack Assessment — CarCare Server

Assessed in the context of the `security-baseline` change
(`context/foundation/prd.md`). The previous assessment, written for the closed
platform-foundation change, was archived to
`context/foundation/archive/stack-assessment-2026-08-28-foundation.md`.

**A note on detection.** This skill's project-marker probe searches for
`package.json`, `Cargo.toml`, `pyproject.toml`, `go.mod`, `Gemfile`,
`composer.json`, `*.csproj`, and `pubspec.yaml` — it does not list `pom.xml` or
`build.gradle`, so it finds nothing in a Java project and would stop. The stack
here was detected from `pom.xml` and `mvnw` directly. Worth fixing upstream in
the skill rather than working around each time.

## Stack Components

**Language — Java 17, pinned exactly.** `pom.xml` sets `<java.version>17</java.version>`,
the compiler plugin uses it for both `source` and `target`, and the enforcer plugin
declares `requireJavaVersion [1.8,18)` with the message "JHipster supports JDK 8 to 17".
The pin is a hard build failure on anything newer, not a preference. `-parameters` is
passed to the compiler, which is load-bearing for constructor-parameter name resolution.

**Framework — Spring Boot 3.1.5, hand-maintained.** The `jhipster-dependencies` BOM was
removed in favour of `spring-boot-dependencies` plus explicitly pinned versions. Zero
`tech.jhipster.*` usages remain. Hibernate 6.2.13.Final, Liquibase 4.20.0, Ehcache,
MapStruct 1.5.3.Final, Lombok 1.18.30, Vavr 0.10.3, Guava 31.1-jre, POI 5.2.5,
jjwt 0.12.3, springdoc 2.2.0, Tika 2.7.0.

**Build tool — Maven 3.9.6 via the `./mvnw` wrapper.** Enforcer pins the minimum Maven
version too. Six profiles: `dev` (default), `prod`, `tls`, `no-liquibase`, `api-docs`,
`IDE`. The `IDE` profile is the one that wires the MapStruct processor and the Hibernate
JPA metamodel generator — without it, generated mappers and `*_` classes do not exist on
disk.

**Test runner — JUnit 5 across two phases.** Surefire runs unit tests and excludes
`*IT*`/`*IntTest*`; Failsafe runs integration tests against in-memory H2. AssertJ for
assertions, ArchUnit 1.0.1 enforcing that `service` and `repository` must not depend on
`web`, JaCoCo 0.8.8 for coverage. Test results are relocated out of Maven's default
`target/surefire-reports/` into `target/test-results/{test,integrationTest}`.
202 main and 55 test source files; the suite is 38 unit and 217 integration tests, green.

**CI — GitLab, at a non-default path.** `.gitlab/gitlab-ci.yml`, because the project's
`ci_config_path` points there; a root `.gitlab-ci.yml` would be ignored entirely. A
top-level `workflow:` block admits exactly three pipeline contexts, and a rule that
matches nothing creates no pipeline at all, silently. Merges are not gated on a green
pipeline — `only_allow_merge_if_pipeline_succeeds` is `false` by owner decision.

**Deployment — Docker Compose behind NGINX.** `src/main/docker/` holds `app.yml`,
`mariadb.yml`, `sonar.yml`, a `reverseproxy/` directory, `deploy.sh`, `entrypoint.sh`,
and `env-template`. Jib 3.3.1 builds onto `eclipse-temurin:17-jre`, amd64.

**Instruction files — CLAUDE.md and AGENTS.md.** `CLAUDE.md` is a one-line `@AGENTS.md`
include; `AGENTS.md` carries the substance. It is unusually thorough for a project this
size, and several of the compensations this assessment would otherwise recommend are
already written there.

## Quality Gate Assessment

| Component   | Typed | Convention | Training Data | Documented | Verdict           |
|-------------|-------|------------|---------------|------------|-------------------|
| Language    | ~     | —          | —             | —          | pass-with-note    |
| Framework   | —     | ✓          | ~             | ✓          | pass-with-note    |
| Build tool  | —     | ✓          | ✓             | ✓          | pass              |
| Test runner | —     | —          | ✓             | ✓          | pass              |

Legend: ✓ = pass, ✗ = fail, ~ = partial, — = not applicable

7 pass, 2 partial, 0 fail.

### Gate Details

**Type safety — partial, and the partial is the interesting part.** Java is typed by the
language, so this passes on the surface. But the criterion's actual definition is "an
agent reading the code can reason about input/output shapes *from the source itself,
without running the program*" — and three annotation processors break exactly that.
Lombok generates getters, setters, builders and a private all-args constructor for
`@Value(staticConstructor = "of")` value objects; MapStruct generates the Entity↔DTO
mappers; the Hibernate JPA metamodel generator produces the `*_` classes. The last two
exist on disk only under the `IDE` profile. The type surface the compiler sees is not the
type surface the source shows.

Evidence: `pom.xml` declares `lombok 1.18.30` and `mapstruct 1.5.3.Final` in
`annotationProcessorPaths`; the `IDE` profile adds the metamodel generator. `AGENTS.md`
carries a section titled "Generated code — do not reason from source alone" recording a
concrete past failure: a source-only reading of `LoginVm` produced the false conclusion
that `/api/authenticate` could not deserialize its request body.

**Convention — pass.** Spring Boot is named in the criteria reference as a passing
example, and the layout here is the standard layered shape:
`web/rest/ → service/ → service/impl/ → repository/ → domain/`, with `service/dto/`,
`service/mapper/`, `config/`, `security/jwt/`, `aop/logging/`. A stranger can predict
where things live. ArchUnit mechanically enforces the layering rather than trusting it.

Evidence: package tree under `src/main/java/com/kasztelanic/carcare`; `ArchTest`;
`AGENTS.md` "Architecture" section.

**Training data — partial, at the project level rather than the framework level.** Spring
Boot passes this criterion comfortably within the Java family. The project does not
inherit that pass cleanly. It began as JHipster 5.5.0-generated Spring Boot 2 and now runs
on a hand-maintained Spring Boot 3 platform with the JHipster BOM and all `tech.jhipster.*`
usages removed — while keeping JHipster's layout, naming, and file structure. The result
looks like a JHipster project to a pattern-matcher and is not one. That intermediate state
is essentially absent from training data, and it is an active trap: JHipster documentation
appears applicable and is not, because it documents *generating* a fresh application rather
than upgrading a hand-maintained one.

Evidence: `pom.xml` uses `spring-boot-dependencies` with no `jhipster-dependencies`;
enforcer and Liquibase messages still say "JHipster"; `AGENTS.md` carries a section headed
"No tech.jhipster.* remains — do not reintroduce it" that names this exact failure mode.

**Documentation — pass, with a currency caveat that belongs to health-check.** Spring Boot
ships a versioned reference manual, and the correct reference for this tree is the one for
`spring-boot.version` in `pom.xml` — not JHipster guides. The caveat: 3.1.x is past OSS
support, so the docs remain published and accurate for the pinned version, but the version
itself is unpatched. That is a dependency-currency finding, not a documentation finding, and
`/10x-health-check` owns it.

## Gaps & Compensation

None of the four criteria fail outright. Three gaps are worth naming, and the first two are
already compensated in `AGENTS.md` — recorded here so the compensation is legible rather
than accidental.

**Gap 1 — generated code is invisible to source reading.** Already compensated.
`AGENTS.md` instructs verification against compiled output (`javap -p`) or runtime
behaviour rather than source, and cites the `LoginVm` incident as precedent. No addition
needed. For this change specifically it matters at one point: `TokenProvider`'s
`afterPropertiesSet()` and the `ApplicationProperties` binding that feeds it are
Lombok-generated at the accessor level, so FR-002's fail-fast check must be verified by
running the context, not by reading the class.

**Gap 2 — the JHipster-shaped, non-JHipster tree.** Already compensated. `AGENTS.md`
states the trap explicitly and directs verification at the Spring Boot reference for the
declared version. No addition needed.

**Gap 3 — two declared-but-unwired static-analysis tools.** Not compensated, and worth
fixing or removing. `pom.xml` declares `<checkstyle.version>10.9.2</checkstyle.version>`
and `<modernizer-maven-plugin.version>2.3.0</modernizer-maven-plugin.version>` as
properties, but `maven-checkstyle-plugin` and `modernizer-maven-plugin` appear **zero
times** as plugin declarations anywhere in the file. There is no `checkstyle.xml` in the
tree either. Both properties are dead. The risk is a reader — human or agent — concluding
from the property block that style and API-modernity are mechanically enforced when
nothing runs. JaCoCo, Surefire, Failsafe, Enforcer and Sonar are all genuinely wired
(2 occurrences each: property plus plugin declaration); these two are not.

### Recommended Instruction File Additions

One addition. The other two gaps are already covered.

```markdown
## Static analysis — what actually runs

`./mvnw verify` runs Surefire, Failsafe, JaCoCo and Enforcer. It does **not** run
Checkstyle or Modernizer: `pom.xml` declares `checkstyle.version` and
`modernizer-maven-plugin.version` as properties, but neither plugin is declared in
`<build>` or `<pluginManagement>`, and no `checkstyle.xml` exists in the tree. Both
properties are dead. Do not infer from them that style or API-modernity is enforced,
and do not "fix" a style violation on the assumption that a gate will catch it — nothing
will. Sonar (`sonar-maven-plugin`, `src/main/docker/sonar.yml`) is wired but runs only
when invoked explicitly.
```

## Change-specific findings

Two observations that fall out of the stack reading and bear directly on the
`security-baseline` PRD. Neither changes the readiness verdict; both are inputs the roadmap
will want.

**`src/main/docker/env-template` exists and is incomplete for this change.** It carries
exactly three lines — `MARIADB_PASSWORD_ENV=pass`, `MAIL_PASSWORD_ENV=pass`,
`MAIL_BASE_URL_ENV=IP | hostname` — and no signing-key entry. This is the file that
documents the host-environment delivery mechanism FR-001 adopts, and extending it is part
of that requirement's surface. Shaping did not find it; the PRD's Constraints section says
the change "reads the new key through the same host-environment mechanism it already uses
for the database and mail passwords" without naming where that mechanism is written down.

**CI cannot prove the deployment half of this change.** The release path — the jobs that
build the image and the proxy — is only ever exercised by a tag, so no merge request can
demonstrate that a change to `app.yml` or `env-template` works. Combined with
`only_allow_merge_if_pipeline_succeeds: false`, this means FR-001 and FR-002's deployment
behaviour has no automated verification available at all: it must be checked by hand on the
host. That is the mechanical reason behind Open Question 3's rollout-ordering constraint.

## Summary

**Verdict: ready-with-compensation.** 7 of 9 applicable criteria pass outright, 2 pass with
notes, none fail.

**Strengths.** A typed language on a convention-heavy framework that is mainstream within
its ecosystem, with layering enforced mechanically by ArchUnit rather than by convention
alone; a green two-phase test suite of 255 tests; exact version pinning on both Java and
Maven enforced at build time; and an `AGENTS.md` that already documents the two subtlest
traps in the tree — generated code and the JHipster-shaped-but-not-JHipster platform. This
project is better instrumented for agent work than most brownfield trees of its age.

**Gaps.** One unaddressed: two static-analysis tools declared as versions but never wired,
which misrepresents what `verify` enforces. One addition to `AGENTS.md` is proposed above.

**For the change in flight.** The stack imposes no friction on `security-baseline`. The
binding constraint is not the stack but the verification path: the deployment half of the
change is unreachable by CI and must be verified by hand on the host.

**Next step:** `/10x-health-check` — which owns the question this assessment deliberately
left alone, namely that Spring Boot 3.1.x is past OSS support and the tree is unpatched.
