# Jakarta Platform Migration — Plan Brief

> Full plan: `context/changes/jakarta-platform-migration/plan.md`
>
> Research: `context/changes/jakarta-platform-migration/research.md`

## What & Why

Complete the Spring Boot 3/Jakarta cutover so the main application compiles on Java 17 without Jakarta EE 8 or `tech.jhipster.*` dependencies. Preserve the existing security, configuration, startup, mail, caching, locale, logging, and REST contracts while moving their implementation to Spring Boot or project-owned code.

## Starting Point

Maven resolves and reaches `javac`, where 398 measured diagnostics expose the source migration surface. A temporary JHipster bridge still supports 18 main classes and six tests; the independently broken H2 context means runtime parity cannot yet be proved.

## Desired End State

Default and IDE-profile main compilation are green, JHipster is absent from source and dependencies, and Boot 3/Hibernate 6 configuration is internally consistent. F-04 receives an explicit, bounded handoff for H2, persistence validation, test imports, full-context MockMvc, and runtime security proof.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| JHipster ownership | Replace all main usages and six coupled test fixtures in F-03 | The bridge cannot be removed coherently while compiled sources retain its utilities | Research / Plan |
| Test boundary | Broader test migration and runtime parity stay in F-04 | Current H2 and test compilation failures make F-03 runtime claims unreliable | Research / Plan |
| Liquibase startup | Use synchronous Boot auto-configuration | Ensures schema readiness before JPA, jobs, or requests | Research / Plan |
| Configuration namespace | Canonical `application.*` with legacy `JHIPSTER_*` fallbacks | Removes framework ownership without breaking deployment inputs | Plan |
| Security verification | Compile/static contract in F-03; full-context assertions in F-04 | Preserves honesty about what the broken context can prove | Research / Plan |
| Locale compatibility | Keep a small quoted-cookie resolver | Preserves existing client language-cookie behavior | Plan |
| Async execution | Native task executor; no exact wrapper-log emulation | Retains functional behavior without copying obsolete internals | Research / Plan |
| Profile processing | Boot 3 dev profile group for API docs | Removes legacy processing while retaining current dev behavior | Research / Plan |
| Operational naming | Keep `JHIPSTER_SLEEP` | Deployment naming is outside the Java dependency cutover | Plan |

## Scope

**In scope:**

- Main Jakarta namespaces and Thymeleaf Spring 6 imports.
- Spring Security 6 filter-chain and method-security configuration.
- Application-owned properties, REST utilities, locale/logging adapters, and async executor.
- Boot-native Liquibase and Boot 3 profile handling.
- Hibernate 6 Jackson, dialect, naming, and Liquibase-plugin compatibility.
- Six JHipster-coupled test fixtures and removal of `jhipster-framework`.
- Roadmap correction and precise F-04 handoff.

**Out of scope:**

- H2 dialect/schema repair and any speculative Liquibase changeset.
- Remaining test-source Jakarta imports, standalone MockMvc replacement, or green `verify`.
- Runtime security certification before F-04.
- JWT/API behavior changes or operational variable renaming.

## Architecture / Approach

First reach a green main compile with the temporary bridge, then replace JHipster-owned configuration and utilities and remove the bridge. Close Hibernate/profile seams last and validate the default, IDE, and production main-build paths. This ordering keeps the compiler useful at each major boundary and isolates test-context recovery.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Jakarta and Security 6 | Green main compile on the new namespaces and security APIs | Silent authorization/filter drift |
| 2. Boot-native/JHipster removal | Project-owned configuration and zero JHipster dependency | Property, startup, or response-contract drift |
| 3. Hibernate 6 closure | Consistent runtime configuration and F-04 handoff | Configuration compiles but still needs runtime proof |

**Prerequisites:** Spring Boot 3.1.5 dependency baseline at current HEAD; Java 17 at `/Users/kacper/.sdkman/candidates/java/17.0.20-tem`.

**Estimated effort:** Approximately three implementation sessions plus a separate F-04 test-recovery change.

## Open Risks & Assumptions

- Legacy environment aliases are intentionally temporary and require later deployment coordination before removal.
- Synchronous Liquibase increases development startup time but removes the schema-readiness race.
- The Hibernate naming strategy is selected to preserve underscore names, but MariaDB/H2 schema parity still requires F-04 runtime evidence.
- Existing standalone MockMvc tests are not reliable evidence for application ObjectMapper or filter-chain behavior.

## Success Criteria (Summary)

- Java 17 default and `-PIDE` main compilation pass, and the production artifact packages with test compilation intentionally skipped.
- No forbidden main `javax.*`, Spring Security 5 API, `tech.jhipster.*`, or `jhipster-framework` dependency remains.
- Security and startup contracts are explicitly preserved, with all runtime-only verification assigned to an actionable F-04 handoff.
