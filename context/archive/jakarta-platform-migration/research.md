---
date: 2026-08-25T01:26:02+02:00
researcher: Codex
git_commit: bfd3973c01f6f944940e4ffcb7a9ba43b3842c66
branch: refactor
repository: carcare-server
topic: "What needs to be planned for the Jakarta platform migration"
tags: [research, codebase, jakarta, spring-boot-3, hibernate-6, spring-security-6]
status: complete
last_updated: 2026-08-25
last_updated_by: Codex
---

# Research: Jakarta platform migration

**Date**: 2026-08-25T01:26:02+02:00  
**Researcher**: Codex  
**Git Commit**: bfd3973c01f6f944940e4ffcb7a9ba43b3842c66  
**Branch**: refactor  
**Repository**: carcare-server

## Research Question

What work must be planned for `jakarta-platform-migration`?

## Summary

This is a compile-unblocking foundation change, not a test-suite recovery change. The Maven platform work (F-01) is already complete: the project uses the Spring Boot 3.1.5 BOM and compilation reaches `javac`. The current blocker is a deliberately measured 398 unique compiler diagnostics: 393 from unconverted Jakarta EE imports, two from the removed `WebSecurityConfigurerAdapter`, and three from Thymeleaf's Spring 5 integration. See the [measured migration surface](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/archive/resolvable-build/migration-surface.md#L32).

Plan F-03 as one coordinated change: convert main-source Jakarta imports, replace Spring Security's adapter configuration while preserving the security contract, remove all main-source `tech.jhipster.*` dependencies/configuration, and correct Boot 3/Hibernate 6 runtime seams. It should prove `./mvnw compile` (including `-PIDE`) on Java 17; H2 schema repair and `./mvnw verify` belong to F-04.

## Implementation Scope to Plan

### 1. Preserve the established dependency baseline

- Retain Spring Boot's 3.1.5 BOM as the dependency authority; it is imported in [pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L108). `validate` passes and `compile` reaches sources, so dependency-resolution repair is out of scope.
- Preserve JCache's `javax.cache:cache-api`: this is JSR-107, not a Jakarta EE namespace to rename ([pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L223)).
- Replace the Hibernate 5 Jackson module dependency and bean together with their Hibernate 6 counterparts ([pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L185), [JacksonConfiguration](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/main/java/com/kasztelanic/carcare/config/JacksonConfiguration.java#L29)). This otherwise fails at first context load.
- Correct the Liquibase plugin's old Jakarta Validation 2.0.1 declaration and its legacy Hibernate/Spring naming references ([pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L645)). Use the Boot 3-managed Jakarta Validation 3 family.

### 2. Migrate the main Java namespace surface mechanically and safely

- Convert 148 main-source imports across 28 files: `javax.persistence` (101), `javax.validation` (35), `javax.servlet` (7), `javax.transaction` (4), and `javax.mail` (1). The concentration is the 15 JPA domain classes, then web validation, servlet filters/configuration, transactions, and mail.
- Convert the remaining test imports only in F-04; they cannot compile until main does, and F-04 owns context recovery.
- Maintain an explicit exclusion guard: `javax.sql.DataSource` in [LiquibaseConfiguration](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/main/java/com/kasztelanic/carcare/config/LiquibaseConfiguration.java#L18) and `javax.crypto.SecretKey` in `TokenProvider` are JDK APIs; neither has a Jakarta substitute.
- Run compilation with the configured annotation processors after entity changes. Lombok, MapStruct, and Hibernate's static metamodel generator are configured in [pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L401); generated output is validation evidence, never an edit target.

### 3. Replace Spring Security 5 configuration with Security 6 equivalence

- Rewrite `SecurityConfiguration` from `WebSecurityConfigurerAdapter`, `antMatchers`, and old chained DSL to a bean-based `SecurityFilterChain`, `requestMatchers`, and `@EnableMethodSecurity` ([current configuration](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java#L25)).
- Preserve the public authentication/reset routes, admin/management authorization, JWT application, CORS-before-JWT ordering, stateless sessions, custom 401/403 handlers, CSP, referrer, and permissions policies.
- Adopt the recorded decision to retain `frameOptions().deny()`, remove the contradictory later frame-options disable, remove duplicate stateless-session configuration, and remove the unused H2-console ignore rule ([roadmap](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/foundation/roadmap.md#L179)).
- Treat this as a behavioural-risk phase even though `antMatchers` is currently hidden by javac error recovery; it is confirmed absent in Security 6.

### 4. Finish JHipster removal inside this change

- Replace all remaining `tech.jhipster.*` usage in main sources and then remove the temporary `jhipster-framework` bridge ([pom.xml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/pom.xml#L166)). The remaining uses cover constants/properties, async execution, logging, locale resolution, Liquibase helpers, and web utilities.
- Reimplement `LiquibaseConfiguration` with Spring Boot/Liquibase-native code while retaining asynchronous execution, its exact changelog and `LiquibaseProperties` forwarding, and the `no-liquibase` profile behaviour ([configuration](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/main/java/com/kasztelanic/carcare/config/LiquibaseConfiguration.java#L28)).
- Resolve the roadmap inconsistency before implementation: F-03 and FR-002 require no JHipster class in main sources ([roadmap](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/foundation/roadmap.md#L162), [PRD](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/foundation/prd.md#L252)), but S-02 says its web utilities will be removed later. The F-03 acceptance criteria should govern: move these replacements into this plan, then amend S-02 to a parity-only concern.

### 5. Correct production configuration references removed by Boot 3/Hibernate 6

- Replace `SpringPhysicalNamingStrategy` in main YAML with an intentional Hibernate 6 physical naming strategy that preserves the existing underscore schema; Spring Boot 3.1 has no such class ([application.yml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/main/resources/config/application.yml#L60)).
- Replace legacy `MySQL5InnoDBDialect` and old naming strategy names in dev/prod and Liquibase diff configuration. Do not change Liquibase historical changesets in this compile change.
- Fix `MailService`'s `org.thymeleaf.spring5.SpringTemplateEngine` import to the Spring 6 package; the Boot starter already supplies the correct module.

## Suggested Phases and Completion Gates

1. **Namespace and platform compatibility** — migrate only convertible main imports; update Thymeleaf, Jackson/Hibernate 6, and the production naming/dialect references. Gate: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile` is green.
2. **Security 6 parity** — introduce the filter-chain configuration and targeted full-context security assertions for anonymous, forbidden, public, admin, and JWT-protected paths. Gate: the route/security contract is explicitly preserved and compilation remains green.
3. **De-JHipster configuration and utilities** — replace every main-source JHipster use, preserve Liquibase semantics, and remove the bridge dependency. Gate: no `tech.jhipster` main imports or POM dependency; both default and `-PIDE` compilation are green.
4. **F-04 handoff, not implementation** — record the exact next blockers: H2 dialect replacement, complete CLOB/TEXT schema-validation investigation, test-source imports, and replacement of unreliable standalone MockMvc coverage. Gate: a concise handoff identifies these without weakening `hibernate.hbm2ddl.auto: validate`.

## Deferred to F-04

- The test profile references the removed `tech.jhipster.domain.util.FixedH2Dialect` ([application.yml](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/src/test/resources/config/application.yml#L29)). Replacing it exposes CLOB-vs-`TEXT` schema validation for five columns, not only `inspections.details`; investigate and add only a forward timestamped Liquibase changelog if warranted.
- Do not disable schema validation to make tests pass.
- Migrate the remaining test imports and move product REST assertions away from standalone MockMvc where the application `ObjectMapper`/security filter chain is required. Failsafe `verify`, not `test`, is the meaningful end-to-end gate.

## Architecture Insights

- The migration crosses every conventional layer, but the risk clusters are narrow: entity metadata/annotation processing, the global security boundary, and boot-time infrastructure configuration.
- Security and JHipster removal must be planned together: twelve JHipster files sit in `config/`, overlapping the exact files affected by Spring 6 configuration changes. Splitting them would cause duplicate churn without a compilable intermediate state.
- The Spring Boot 3.1 reference confirms the supported physical naming strategy property and example configuration; applying it deliberately is necessary to retain the MariaDB/Liquibase schema contract rather than accepting a changed default.

## Historical Context

- [`resolvable-build` migration surface](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/archive/resolvable-build/migration-surface.md#L18) established the reliable 398-diagnostic starting point and separated source, security, and Thymeleaf causes.
- [`resolvable-build` plan](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/archive/resolvable-build/plan.md) deliberately left the Hibernate 5-to-6 runtime correction and H2 suite recovery for the follow-on foundations.
- The roadmap defines F-03 as main-source compilation and F-04 as test-context recovery ([F-04](https://github.com/kacperkasztelanic/carcare-server/blob/bfd3973c01f6f944940e4ffcb7a9ba43b3842c66/context/foundation/roadmap.md#L221)).

## Related Research

- No prior `research.md` artifact exists for this change; this is the active migration-scope record.

## Open Questions

- Reconcile the F-03/S-02 JHipster-removal ownership conflict before approving the implementation plan.
- Confirm the exact replacement for JHipster's asynchronous Liquibase helper using the declared Spring Boot 3.1.5 APIs, with a focused integration test once F-04 can boot a context.
- F-04 must establish whether a forward Liquibase migration is required for all five long-text schema pairs on both H2 and MariaDB.
