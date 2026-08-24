# Resolvable Dependency Graph — Plan Brief

> Full plan: `context/changes/resolvable-build/plan.md`
> Roadmap item: **F-01** · PRD refs: FR-001, FR-002

## What & Why

`./mvnw` fails during Maven model construction because 11 dependencies receive no version
from the declared `jhipster-dependencies` 8.0.0 BOM. The compiler never runs, so the size of
the Jakarta EE migration ahead is an estimate rather than a measurement. This change makes
the dependency graph resolve so javac produces a real error list — and, by the owner's
decision, also removes Zalando Problem in favour of Spring 6 `ProblemDetail` and moves jjwt
to 0.12.3.

## Starting Point

Spring Boot 3.1.5 and Java 17 are declared in `pom.xml`, but `src/main` still carries 152
`javax.*` imports and 18 files touching `tech.jhipster.*`. Nothing compiles, and no
integration test has loaded a Spring context since 2022-08-01. `spring-boot-dependencies`
3.1.5 is already in the local repository.

## Desired End State

`./mvnw validate` passes on the default, `prod`, and `IDE` profiles. `./mvnw compile` fails
as a *compilation* error, not a *model* error — javac runs, resolves every classpath entry,
and reports the full Jakarta namespace surface. No `org.zalando` import and no jjwt 0.11 API
call remains anywhere in the tree. F-03 receives a measured worklist instead of an estimate.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| BOM strategy | Swap `jhipster-dependencies` → `spring-boot-dependencies` 3.1.5 | The roadmap's stated F-01 outcome, and measurement showed the cost is 3 extra pins, not the ~40 it feared | Roadmap + Plan |
| Zalando Problem | Remove **and** rewrite error handling to Spring `ProblemDetail` in this change | Owner's call: never pin a library with no Spring 6 release | Plan (owner override) |
| jjwt | Pin 0.12.3 **and** rewrite `TokenProvider` in this change | Owner's call: land the upgrade as one self-contained unit | Plan (owner override) |
| springdoc | Migrate to `springdoc-openapi-starter-webmvc-ui` 2.2.0 | Zero Java usages, `api-docs` profile keeps working, no follow-up needed | Plan |
| `SecurityConfiguration` | Strip `SecurityProblemSupport` only — no `SecurityFilterChain` rewrite | Converting it would pull in FR-004 and the unanswered frame-options decision that blocks F-03 | Plan |
| `spring-cloud-starter-bootstrap` | Drop | No `bootstrap.yml` exists; keeping it means importing the whole spring-cloud BOM | Plan |
| Hibernate coordinates | `org.hibernate` → `org.hibernate.orm` in 6 places | Hibernate 6 renamed the groupId; two of the six are outside `<dependencies>` and fail at compile, not at validate | Plan |
| `jhipster-framework` | Pin 8.0.0 as an explicit temporary bridge | Code still uses it; FR-002 completes at F-03 | Roadmap + Plan |
| Compiler | Add `-Xmaxerrs 10000` | Without it javac stops inside `domain/` and Phases 2 and 3 have no verification mechanism | Plan |

## Scope

**In scope:**
- `pom.xml` — BOM swap, 14 version resolutions, 2 removals, compiler args
- `web/rest/errors/` — `ExceptionTranslator` plus 5 exception classes onto `ProblemDetail`
- `config/` — `JacksonConfiguration` module removal, `SecurityConfiguration` zalando strip, two new security error handlers
- `security/jwt/TokenProvider.java` and its two tests — jjwt 0.12 API
- Two new documents: the error wire contract and the F-03 migration-surface handoff

**Out of scope:**
- Any `javax.*` → `jakarta.*` conversion (152 imports, F-03)
- `SecurityFilterChain` / `requestMatchers` rewrite (FR-004, F-03)
- Removing `tech.jhipster.*` usages or the artifact (F-03)
- `jackson-datatype-hibernate5` → `hibernate6` (F-03)
- Changing `ErrorConstants.PROBLEM_BASE_URL` — preserved verbatim
- Running any test — impossible until F-04

## Architecture / Approach

Three phases. Phase 1 is pom-only and the only one whose success a Maven exit code can
confirm today; it also raises `-Xmaxerrs` so javac reports every diagnostic instead of
stopping early, which is what makes Phases 2 and 3 checkable at all. Phases 2 and 3 are
independent of each other, each removing one library's API surface, each verified by
grepping the source for the old API and grepping javac's full output for errors attributable
to that library.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Resolvable dependency graph | `./mvnw validate` green on three profiles; javac runs; migration surface measured | Hand-pinning 4 versions with nothing watching them afterwards — an accepted PRD consequence |
| 2. Error handling on `ProblemDetail` | Zalando gone; error contract documented and reproduced | The error body is client-visible and cannot be verified until F-04 |
| 3. jjwt 0.12.3 in `TokenProvider` | jjwt 0.11 API gone; inventory refreshed for F-03 | Token signing and validation rewritten with no test feedback until F-04 |

**Prerequisites:** Java 17 exactly — `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`
(the SDKMAN default is newer and trips the enforcer). Network access to Maven Central and to
the GitLab registry serving `com.kasztelanic.carcare:client:1.2.5`.

**Estimated effort:** Phase 1 is short and mechanical — a working configuration has already
been validated in a sandbox. Phases 2 and 3 are the bulk: roughly 12 files of careful,
untestable rewriting.

## Open Risks & Assumptions

- **Two client-visible surfaces change with no test able to run.** Error response bodies
  (Phase 2) and token signing (Phase 3) are both rewritten two phases before `./mvnw verify`
  can execute anything. Mitigated by writing the error contract down before touching code and
  by keeping the jjwt change strictly mechanical — but the exposure is real and was accepted
  deliberately.
- **Assumption: jjwt 0.12.3 preserves the JWS format.** The key still derives from the same
  base64 secret via `Keys.hmacShaKeyFor` at HS512, so existing tokens should still verify.
  Unverifiable until F-04.
- **Assumption: springdoc 2.2.0 honours `springdoc.api-docs.path` identically.** The key name
  is unchanged between 1.x and 2.x, but the rendered output is unobservable until a context
  boots.
- **`SecurityConfiguration` still will not compile** after Phase 2 — it extends
  `WebSecurityConfigurerAdapter`, removed in Spring Security 6. That is intended, not an
  oversight.
- Build health depends on the private GitLab registry continuing to serve `client:1.2.5`, a
  dependency outside this repository.

## Success Criteria (Summary)

- A developer can run `./mvnw compile` and get a complete, trustworthy list of what the
  Jakarta migration actually requires — the first time that has been true on this branch.
- No JHipster BOM, no Zalando dependency, and no jjwt 0.11 API call remains.
- F-03 begins from `migration-surface.md`, a measured worklist with the `javax.sql.DataSource`
  trap already excluded.
