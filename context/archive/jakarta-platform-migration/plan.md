# Jakarta Platform Migration Implementation Plan

## Overview

Complete the F-03 platform cutover so the Spring Boot 3.1.5 application compiles on Java 17 without Jakarta EE 8 imports or `tech.jhipster.*` dependencies. The change preserves the existing HTTP, JWT, mail, caching, locale, logging, Liquibase, and profile contracts while moving their ownership to Spring Boot or project-local code.

This plan deliberately stops at a trustworthy main-source build. F-04 remains responsible for repairing the H2 test context, completing the broader test-source Jakarta migration, and proving runtime security and persistence behavior.

## Current State Analysis

The Maven dependency model is already resolvable and compilation reaches `javac`, but main compilation reports 398 unique diagnostics. Of these, 393 are unconverted Jakarta namespaces, two are caused by the removed `WebSecurityConfigurerAdapter`, and three are caused by Thymeleaf's Spring 5 integration.

The application also compiles against a temporary `jhipster-framework` bridge. Eighteen main classes still use JHipster configuration, bootstrap, logging, locale, Liquibase, async, or REST utility types. Six tests instantiate the same types. Runtime configuration additionally references Hibernate 5 Jackson integration, removed naming strategies, a legacy MariaDB dialect, and profile processing that Spring Boot 3.1 no longer supports.

The test context is independently broken by the removed `FixedH2Dialect` and five likely CLOB-versus-`TEXT` validation mismatches. Those failures prevent F-03 from honestly proving runtime security or application-context behavior.

## Desired End State

- Java 17 default and `IDE` profile main compilation succeed with Spring Boot 3.1.5.
- Main code contains no `javax.persistence`, `javax.validation`, `javax.servlet`, `javax.transaction`, or `javax.mail` references.
- Main and test *sources* contain no `tech.jhipster.*` references, and `jhipster-framework` is absent from the dependency graph. One documented exception remains: `src/test/resources/config/application.yml` still names `tech.jhipster.domain.util.FixedH2Dialect`, which F-04 owns.
- Security uses Spring Security 6 bean-based configuration while retaining the existing authorization, JWT, header, session, CORS, and ProblemDetail contracts.
- Spring Boot owns Liquibase startup synchronously, and the existing `-Pno-liquibase` behavior remains available.
- Canonical application configuration lives under `application.*`, with temporary compatibility aliases for all consumed legacy `JHIPSTER_*` environment variables.
- Hibernate 6, Thymeleaf 6, MariaDB, naming, and Liquibase-plugin references are internally consistent.
- The roadmap clearly assigns implementation removal to F-03 and runtime/test parity to F-04 and S-02.

### Key Discoveries

- `javax.cache`, `javax.sql.DataSource`, and `javax.crypto.SecretKey` are valid JSR-107/JDK APIs and must not be renamed.
- Boot-native synchronous Liquibase startup removes a development-only schema-readiness race created by the JHipster async helper.
- The REST utility usages in `AuditResource`, `UserResource`, and `ExceptionTranslator` cannot remain for S-02 without retaining the JHipster dependency.
- The current security configuration contains contradictory frame-options rules and duplicate stateless-session configuration. The accepted contract is one stateless rule and `X-Frame-Options: DENY`.
- Alert/error headers are emitted under two different application names today (`X-carcareApp-*` from the local static utility used by ten resources, `X-carcare-*` from the JHipster utility used by `UserResource` and `ExceptionTranslator`). The accepted contract unifies both on `spring.application.name`.
- Actual security parity cannot be proved until F-04 restores full-context MockMvc; F-03 can prove compilation, migration completeness, and a reviewed source contract only.

## What We're NOT Doing

- Repairing the H2 dialect or the five long-text schema-validation mismatches.
- Editing existing Liquibase changesets or creating a speculative forward database migration.
- Migrating all remaining test-source `javax.*` imports or claiming `./mvnw test`/`verify` is green.
- Rewriting standalone MockMvc integration tests; F-04 will move product behavior assertions to full-context MockMvc.
- Changing JWT format, signing algorithm, expiry semantics, login response shape, route authorization, mail contents, cache sizes, pagination headers, or locale-cookie behavior. The one deliberate exception is the alert/error header *name*: ten endpoints move from `X-carcareApp-*` to `X-carcare-*` (Phase 2 §6), an accepted contract change rather than a preserved one.
- Renaming operational variables such as `JHIPSTER_SLEEP`; they are deployment plumbing rather than Java framework dependencies.
- Removing temporary legacy configuration aliases; their later retirement requires a separate deployment-coordination change.

## Implementation Approach

First establish a green main compile with the temporary JHipster bridge still present. This isolates the mechanical Jakarta and Spring Security 6 cutover from configuration ownership changes. Next replace every JHipster consumer with project-owned or Spring Boot-native behavior, update the six directly coupled test fixtures, and remove the bridge. Finally close the Hibernate 6 and profile configuration seams, run the complete main-build matrix, and record the exact F-04 handoff.

## Critical Implementation Details

### Timing & lifecycle

Delete the custom asynchronous Liquibase bean and let Spring Boot auto-configuration run migrations synchronously. The explicit `spring.liquibase.change-log` property must point to `classpath:config/liquibase/master.xml`; otherwise Boot uses its unrelated default path. Preserve `-Pno-liquibase` through a profile resource setting `spring.liquibase.enabled: false`.

### State sequencing

Keep `JwtFilter` as a `GenericFilterBean` and preserve its current token-resolution semantics. Both the CORS filter and JWT filter remain anchored before `UsernamePasswordAuthenticationFilter`; do not invent an ordering between them that the current code does not define.

## Phase 1: Jakarta and Security 6 Compile Cutover

### Overview

Remove all main-source Jakarta EE 8 imports and migrate the global security boundary to Spring Security 6. The temporary JHipster bridge stays in place during this phase so the first checkpoint is a focused, green main compile.

### Changes Required

#### 1. Main-source Jakarta namespaces

**Files**: `src/main/java/com/kasztelanic/carcare/domain/*.java`, `src/main/java/com/kasztelanic/carcare/service/**/*.java`, `src/main/java/com/kasztelanic/carcare/web/**/*.java`, `src/main/java/com/kasztelanic/carcare/config/*.java`, `src/main/java/com/kasztelanic/carcare/security/jwt/*.java`

**Intent**: Convert the measured 148 main imports from Java EE namespaces to their Jakarta EE 9 equivalents without changing domain mappings, validation constraints, transactions, filter behavior, or mail behavior.

**Contract**: Replace only `javax.persistence`, `javax.validation`, `javax.servlet`, `javax.transaction`, and `javax.mail`. Preserve `javax.cache`, `javax.sql`, and `javax.crypto`. Do not edit Lombok-, MapStruct-, or Hibernate-generated output.

#### 2. Thymeleaf and mail compatibility

**File**: `src/main/java/com/kasztelanic/carcare/service/MailService.java`

**Intent**: Compile mail rendering and message creation against the Spring 6/Boot 3 stack.

**Contract**: Use the Thymeleaf Spring 6 template engine and Jakarta Mail types while preserving templates, locale selection, sender, multipart/HTML behavior, async annotations, and exception handling.

#### 3. Spring Security 6 configuration

**Files**: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java`, `src/main/java/com/kasztelanic/carcare/security/jwt/JwtConfigurer.java`, `src/main/java/com/kasztelanic/carcare/security/jwt/JwtFilter.java`

**Intent**: Replace removed adapter and matcher APIs with Spring Security 6 configuration while preserving the application's security boundary.

**Contract**: Define one `SecurityFilterChain`, a `WebSecurityCustomizer` for the existing true bypass paths, and `@EnableMethodSecurity` with pre/post and secured support. Delete `JwtConfigurer` after inserting `JwtFilter` directly before `UsernamePasswordAuthenticationFilter`. Preserve:

- Filter-chain bypasses for OPTIONS, static app/i18n/content, Swagger UI, and `/test/**`; remove only the stale H2-console bypass.
- Public registration, activation, authentication, and password-reset endpoints.
- ADMIN authority for `/api/admin/**` and non-public `/management/**`; authentication for remaining `/api/**`; public health, info, and Prometheus endpoints.
- `hasAuthority(ROLE_ADMIN)` semantics and existing method-security expressions.
- CSRF disabled, HTTP Basic enabled, custom 401/403 handlers, CSP, strict-origin-when-cross-origin referrer policy, the existing permissions policy, one stateless-session rule, and `X-Frame-Options: DENY`.
- Exact case-sensitive `Authorization: Bearer ` handling, downstream filter invocation, and no response writes for invalid/missing tokens inside `JwtFilter`.

#### 4. Temporary diagnostic compiler configuration

**File**: `pom.xml`

**Intent**: Retain the existing dependency baseline and annotation processors while the source cutover lands.

**Contract**: Keep the temporary JHipster bridge and full-error compiler setting only through this phase. Do not alter `javax.cache:cache-api` or the Java 17 enforcer range.

### Success Criteria

#### Automated Verification

- Forbidden Jakarta EE 8 namespaces are absent from `src/main/java`: `! rg -n 'javax\.(persistence|validation|servlet|transaction|mail)' src/main/java`
- Removed Spring Security 5 APIs are absent: `! rg -n 'WebSecurityConfigurerAdapter|EnableGlobalMethodSecurity|authorizeRequests\(|antMatchers\(' src/main/java`
- Exactly one `SecurityFilterChain` bean is declared and the obsolete `JwtConfigurer` source is removed: `[ "$(rg -c --no-filename 'SecurityFilterChain\s+\w+\s*\(' src/main/java | awk '{s+=$1} END {print s+0}')" = 1 ] && ! test -e src/main/java/com/kasztelanic/carcare/security/jwt/JwtConfigurer.java`
- Java 17 main compilation passes: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile`

#### Manual Verification

- Security source review confirms the matcher order, bypass paths, filter anchors, headers, session policy, method security, and ProblemDetail handlers match the contract above.

**Implementation Note**: Pause after the green compile and security-contract review before removing the JHipster bridge.

---

## Phase 2: Boot-Native Configuration and JHipster Removal

### Overview

Move every remaining JHipster-owned behavior to application configuration, Spring Boot auto-configuration, or small project-local adapters. Update the directly coupled test fixtures and remove the framework dependency.

### Changes Required

#### 1. Application-owned configuration model

**Files**: `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java`, `src/main/resources/config/application.yml`, `src/main/resources/config/application-dev.yml`, `src/main/resources/config/application-prod.yml`, `src/test/resources/config/application.yml`

**Intent**: Replace `JHipsterProperties` with a minimal type-safe model containing only values the application consumes, extending the `ApplicationProperties` class that already exists.

**Contract**: Canonical keys live under `application.cors`, `application.cache.ehcache`, `application.security`, `application.mail`, `application.audit-events`, and `application.logging`, alongside the **existing `application.data-directory.location`** binding, which must keep working unchanged (it is live in `application-dev.yml:112`, `application-prod.yml`, and `src/test/resources/config/application.yml:117`). `ApplicationProperties` is already `@ConfigurationProperties(prefix = "application", ...)`; flip its `ignoreUnknownFields` from `false` to `true` for the duration of the alias window, so a legacy key that has not yet been modelled degrades to "ignored" rather than hard-failing context startup. Neither Phase 2 gate would catch such a failure — both are compile-only.

Use `spring.application.name` for alert-header application naming — see Phase 2 §6, which makes this a deliberate, client-visible rename rather than a like-for-like move.

**Alias mechanism** — do not introduce a second `@ConfigurationProperties(prefix = "jhipster")` shim; that reinstates the namespace this change removes. Instead give each canonical YAML key a placeholder default naming the legacy environment variable:

```yaml
application:
  security:
    authentication:
      jwt:
        base64-secret: ${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:<current literal value>}
```

`APPLICATION_*` then wins automatically, because environment variables outrank `application.yml` in Boot's property-source order, while `JHIPSTER_*` still resolves through the placeholder when no canonical variable is set.

**Consumed legacy keys requiring an alias**: `jhipster.cors.*` (the whole `CorsConfiguration` block), `jhipster.cache.ehcache.time-to-live-seconds` and `.max-entries`, `jhipster.security.authentication.jwt.base64-secret`, `.token-validity-in-seconds`, `.token-validity-in-seconds-for-remember-me`, `jhipster.mail.from`, `jhipster.mail.base-url`, `jhipster.audit-events.retention-period`, `jhipster.logging.use-json-format`, and `jhipster.logging.logstash.{enabled,host,port,queue-size}`. `jhipster.security.content-security-policy` and `jhipster.clientApp.name` move without an environment alias — neither is set from the environment today. Remove unconsumed `jhipster.http` and `jhipster.api-docs` settings; Springdoc remains under `springdoc.*`. Also drop the now-dead `logging.level.tech.jhipster` entries (`application-dev.yml:14`, `application-prod.yml:15`).

#### 2. Configuration consumers and bootstrap profiles

**Files**: `src/main/java/com/kasztelanic/carcare/CarcareApp.java`, `src/main/java/com/kasztelanic/carcare/aop/logging/LoggingAspect.java`, `src/main/java/com/kasztelanic/carcare/config/CacheConfiguration.java`, `src/main/java/com/kasztelanic/carcare/config/DefaultProfileUtil.java`, `src/main/java/com/kasztelanic/carcare/config/LoggingAspectConfiguration.java`, `src/main/java/com/kasztelanic/carcare/config/WebConfigurer.java`, `src/main/java/com/kasztelanic/carcare/security/jwt/TokenProvider.java`, `src/main/java/com/kasztelanic/carcare/service/AuditEventService.java`, `src/main/java/com/kasztelanic/carcare/service/MailService.java`

**Intent**: Inject the project-owned properties and replace JHipster profile constants with local Spring profile names.

**Contract**: Preserve cache TTL/capacity, CORS mappings, JWT secret fallback and validity periods, mail sender/base URL, audit retention, default `dev` profile, and dev/prod/cloud conflict logging. Remove explicit `LiquibaseProperties` enablement from `CarcareApp`; Boot's Liquibase auto-configuration owns it.

#### 3. Native Liquibase and profile processing

**Files**: `src/main/java/com/kasztelanic/carcare/config/LiquibaseConfiguration.java`, `src/main/resources/config/application.yml`, `src/main/resources/config/application-dev.yml`, `src/main/resources/config/application-no-liquibase.yml`, `pom.xml`

**Intent**: Replace JHipster's asynchronous Liquibase helper with deterministic Spring Boot startup and normalize profile activation for Boot 3.1.

**Contract**: Delete the custom `LiquibaseConfiguration`. Set `spring.liquibase.change-log` to the existing XML master, retain dev/prod/test contexts, and disable migrations in `application-no-liquibase.yml`. Remove `spring.config.use-legacy-processing` plus profile self-activation/include from `application-dev.yml`. Do **not** replace them with a `dev` profile group: nothing in `src/main/java` reads the `api-docs` Spring profile, and `springdoc-openapi-starter-webmvc-ui` is an unconditional dependency (`pom.xml:199`), so springdoc is already active in every profile and the group would be dead config. The Maven `api-docs` profile stays as inert deployment plumbing. Maven and CLI remain responsible for selecting dev/prod/no-liquibase profiles.

#### 4. Native async execution

**File**: `src/main/java/com/kasztelanic/carcare/config/AsyncConfiguration.java`

**Intent**: Remove `ExceptionHandlingAsyncTaskExecutor` while preserving async mail and scheduled task infrastructure.

**Contract**: Expose the configured `ThreadPoolTaskExecutor` directly under the `taskExecutor` bean name, retain `@EnableAsync`, `@EnableScheduling`, pool properties, thread prefix, and `SimpleAsyncUncaughtExceptionHandler`. Exact JHipster wrapper log wording is not part of the product contract.

#### 5. Locale and logging adapters

**Files**: `src/main/java/com/kasztelanic/carcare/config/LocaleConfiguration.java`, `src/main/java/com/kasztelanic/carcare/config/QuotedCookieLocaleResolver.java`, `src/main/java/com/kasztelanic/carcare/config/LoggingConfiguration.java`, `src/main/java/com/kasztelanic/carcare/config/logging/LoggingUtils.java`

**Intent**: Preserve client locale-cookie parsing and optional JSON/Logstash logging without framework utilities.

**Contract**: The local resolver both **reads and writes** `NG_TRANSLATE_LANG_KEY` exactly as `AngularCookieLocaleResolver` does — it accepts normal and `%22`-quoted values on the read path, and re-emits the quoted form on the write path that `LocaleChangeInterceptor` (`LocaleConfiguration.java:23-25`, `?language=`) triggers. Construct it as `new CookieLocaleResolver("NG_TRANSLATE_LANG_KEY")`; the no-arg constructor and `setCookieName` are deprecated in Spring 6. The local logging helper preserves conditional JSON console, Logstash TCP, and context-listener behavior using application-owned settings.

#### 6. REST response utilities

**Files**: `src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java`, `src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java`, `src/main/java/com/kasztelanic/carcare/web/rest/util/ResponseUtil.java`, `src/main/java/com/kasztelanic/carcare/web/rest/AuditResource.java`, `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java`, `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: Extend the existing local utilities to cover the JHipster overloads currently used by user, audit, and error endpoints, and unify the two application-name namespaces that currently coexist.

**Current state**: two different names feed alert headers today. `web/rest/util/HeaderUtil.java:15` hardcodes `APPLICATION_NAME = "carcareApp"` and emits `X-carcareApp-alert` / `-params` / `-error`; it is imported by **ten** resource classes (`Vehicle`, `Repair`, `Refuel`, `Inspection`, `Insurance`, `InsuranceType`, `FuelType`, `RoutineService`, `ReminderAdvance`, `Event`). Separately, `UserResource.java:75` and `ExceptionTranslator.java:44` inject `@Value("${jhipster.clientApp.name}")` = `carcare` and emit `X-carcare-alert`. The class is `static` with a private constructor, so it has no Spring injection point.

**Contract** — **accepted contract change**: unify both paths on `spring.application.name` (`carcare`). All alert, param, and error headers become `X-carcare-*`; the ten resources above therefore change from `X-carcareApp-*` to `X-carcare-*`. This is a deliberate, client-visible rename, not a preservation. Either seed the static `APPLICATION_NAME` from `spring.application.name` via a small `@Configuration` initializer, or keep the field static and take `applicationName` as a parameter on the JHipster-shaped overloads — do **not** convert `HeaderUtil` into an injected bean, which would churn all thirteen call sites for no behavioral gain. `spring.application.name` is already `carcare` in both `src/main/resources/config/application.yml:52` and `src/test/resources/config/application.yml:19`, so dev, prod, and test agree.

Otherwise preserve translation flags, URI-aware pagination links plus `X-Total-Count`, and both optional response overloads including supplied headers and 404 behavior. S-02 verifies endpoint parity against the **renamed** headers but no longer owns these replacements.

#### 7. Coupled test fixtures and bridge removal

**Files**: `src/test/java/com/kasztelanic/carcare/config/WebConfigurerTest.java`, `src/test/java/com/kasztelanic/carcare/security/jwt/JwtFilterTest.java`, `src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java`, `src/test/java/com/kasztelanic/carcare/service/AuditEventServiceIT.java`, `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`, `src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java`, `src/test/resources/logback.xml`, `pom.xml`

**Intent**: Remove the last source-level dependency on JHipster without expanding into F-04's broader test recovery.

**Contract**: Replace only JHipster fixtures/types and immediately related constructor setup. Do not migrate unrelated test `javax.*` imports or convert test harnesses in this phase. Drop the dead `<logger name="tech.jhipster">` entry from `src/test/resources/logback.xml`. Remove the `jhipster-framework` dependency and version property after all source references are gone. Leave `JHIPSTER_SLEEP` unchanged, and leave `src/test/resources/config/application.yml`'s `FixedH2Dialect` line to F-04.

### Success Criteria

#### Automated Verification

- No JHipster Java/package references remain: `! rg -n 'tech\.jhipster' src/main/java src/test/java src/main/resources src/test/resources/logback.xml pom.xml` (scoped to exclude `src/test/resources/config/application.yml`, whose `FixedH2Dialect` line F-04 owns)
- The JHipster bridge is absent from the dependency graph: `! JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -q dependency:tree -Dincludes=tech.jhipster:jhipster-framework | grep -q jhipster-framework`
- Java 17 main compilation remains green: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile`
- Filtered resources retain the dev and no-Liquibase profile contracts: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -Pno-liquibase process-resources`
- The old alert-header namespace is gone and only one remains: `! rg -n 'carcareApp' src/main/java src/test/java`

#### Manual Verification

- Configuration review confirms every consumed legacy value moved to `application.*` with canonical-first legacy environment aliases and unchanged effective dev/prod/test values.
- Utility review confirms locale-cookie, alert/error header (single `X-carcare-*` namespace), pagination-link, optional-response, logging, async, and no-Liquibase behavior is represented without JHipster code.

**Implementation Note**: Do not use the broken test context as a phase gate. Record any newly exposed test compiler diagnostics for F-04 instead of widening this phase.

---

## Phase 3: Hibernate 6 Configuration Closure and F-04 Handoff

### Overview

Remove the remaining Boot 2/Hibernate 5 configuration seams, prove both supported main compilation modes, and leave a precise handoff for runtime and test recovery.

### Changes Required

#### 1. Hibernate 6 Jackson integration

**Files**: `pom.xml`, `src/main/java/com/kasztelanic/carcare/config/JacksonConfiguration.java`

**Intent**: Register the Jackson module matching the declared Hibernate 6 runtime.

**Contract**: Replace the Hibernate 5 datatype artifact and bean with their Hibernate 6 equivalents as one atomic change; preserve transient-annotation handling and existing ObjectMapper module registration behavior.

#### 2. Naming and dialect configuration

**Files**: `src/main/resources/config/application.yml`, `src/main/resources/config/application-dev.yml`, `src/main/resources/config/application-prod.yml`, `src/test/resources/config/application.yml`, `pom.xml`

**Intent**: Replace classes removed by Boot 3/Hibernate 6 while preserving the MariaDB/Liquibase underscore schema contract.

**Contract**: Use Hibernate 6's camel-case-to-underscores physical naming strategy, retain the supported Spring implicit naming strategy, and use `org.hibernate.dialect.MariaDBDialect` for dev/prod and Liquibase diff reference configuration. `org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy` is absent from `spring-boot-3.1.5.jar`, so the identical line in `src/test/resources/config/application.yml` must be replaced here too — that one line only; the `database-platform: tech.jhipster.domain.util.FixedH2Dialect` line in the same file stays F-04's. Do not change historical changesets.

#### 3. Liquibase plugin and compiler cleanup

**File**: `pom.xml`

**Intent**: Align build-time schema diff tooling with Jakarta Validation 3 and remove temporary diagnostic settings that are no longer needed.

**Contract**: Replace the old validation API coordinate/version with `jakarta.validation:jakarta.validation-api` aligned to Boot 3.1.5, update plugin naming/dialect references, and remove the temporary `-Xmaxerrs 10000` compiler arguments. Preserve all Lombok, MapStruct, and Hibernate metamodel annotation processors.

#### 4. Boot 2 to Boot 3 property sweep

**Files**: `src/main/resources/config/application.yml`, `src/main/resources/config/application-dev.yml`, `src/main/resources/config/application-prod.yml`, `src/test/resources/config/application.yml`

**Intent**: Retire actuator and MVC properties that Boot 3 renamed or removed, so configuration the compiler cannot see stops silently doing nothing.

**Contract**: Rename `management.metrics.export.prometheus.enabled` and `.step` (`application.yml:27-32`) **and the same block in `application-prod.yml:16-21`** to `management.prometheus.metrics.export.*` — Boot's own configuration metadata marks the old names deprecated at level `error` with that replacement since 3.0.0, so they bind to nothing today. Note the direction of the bug in prod: `application-prod.yml:20-21` explicitly sets `enabled: false`, and because that key is dead the Boot 3 default of `true` applies instead — the rename turns prometheus export *off* in prod, restoring the configured intent, rather than merely tidying a name. Review and correct `management.metrics.enable.*` and `management.metrics.web.server.auto-time-requests` (`:33-48`) against Boot 3.1 metadata, drop `spring.mvc.favicon.enabled` (`:83-85`, removed in Boot 2.2), and drop `management.endpoint.jhimetrics` (`:19-20`), whose endpoint disappears with the framework. Do not add `prometheus` to `management.endpoints.web.exposure.include` — that list is unchanged by this plan and the endpoint's current absence from it is pre-existing.

#### 5. Ownership and F-04 handoff documentation

**Files**: `context/foundation/roadmap.md`, `context/changes/jakarta-platform-migration/change.md`

**Intent**: Remove the F-03/S-02 ownership contradiction and document what the green main compile does not prove.

**Contract**: State that F-03 implements all JHipster utility replacements and that it renames alert/error headers to a single `X-carcare-*` namespace; S-02 verifies admin header/pagination/response parity against the renamed headers, and owns confirming the client does not key on the old `carcareApp` prefix. Keep F-04 responsible for the H2 dialect, five CLOB/`TEXT` pairs, remaining test imports, full-context MockMvc conversion, Liquibase/JPA startup order, and runtime security assertions. Do not weaken `hibernate.hbm2ddl.auto: validate`.

### Success Criteria

#### Automated Verification

- No legacy Hibernate 5, removed naming, legacy dialect, or Jakarta Validation 2 references remain in main configuration and `pom.xml`.
- Java 17 default main compilation passes: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile`
- Java 17 IDE-profile compilation and generated metamodel processing pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -PIDE compile`
- Production main artifact builds without compiling the known-broken tests: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -Pprod -Dmaven.test.skip=true package`
- No Boot 2 actuator/MVC property names survive in configuration: `! rg -n 'favicon|jhimetrics|^ {8}export:' src/main/resources/config src/test/resources/config` and the renamed block is present: `rg -q '^ {4}prometheus:' src/main/resources/config/application.yml`
- Roadmap and change documentation explicitly record the F-03, S-02, and F-04 ownership boundaries.

#### Manual Verification

- Final review confirms no database changeset, security behavior, or test-suite success is claimed without runtime evidence, and the F-04 handoff is sufficient to continue without rediscovery.

**Implementation Note**: Completion means a green, dependency-clean main build. Runtime context, H2 schema, and security parity remain explicit F-04 gates.

---

## Testing Strategy

### Unit Tests

- F-03 does not claim the existing test tree compiles; the six JHipster-coupled fixtures are updated only to remove the deleted dependency.
- Preserve the intent of `JwtFilterTest` for valid, invalid, missing, empty, and wrong-scheme Authorization headers for execution after F-04 test compilation recovery.
- Preserve mail, CORS, audit-retention, locale-cookie, pagination, response-wrapper, and header-generation assertions for restoration or focused replacement in F-04.

### Integration Tests

F-04 must add or migrate full-context MockMvc coverage for:

- Anonymous private API access returning UTF-8 `application/problem+json` 401 bodies with message and path.
- Authenticated USER access to admin endpoints returning the matching 403 contract; ADMIN access succeeding.
- All public authentication and reset endpoints remaining accessible.
- Public management health/info/Prometheus endpoints and ADMIN-only remaining management endpoints.
- JWT principal/authority installation, malformed-token handling, both filter anchors, stateless sessions, CORS, CSP, referrer, permissions, and exactly `X-Frame-Options: DENY`.
- Liquibase test-context execution before Hibernate validation, with both master changelogs present in `DATABASECHANGELOG`.

### Manual Testing Steps

1. After F-04 restores context startup, launch dev against a disposable MariaDB and confirm Liquibase finishes before the application reports readiness.
2. Launch with `-Pno-liquibase` against an already migrated disposable schema and confirm no migration runs.
3. Exercise login plus USER/ADMIN/public route samples and compare response status, headers, and ProblemDetail bodies with the recorded baseline.

## Performance Considerations

Synchronous Liquibase may lengthen development startup by the duration of migrations, but removes the more serious risk of requests, JPA, or scheduled jobs reaching an unready schema. Runtime request performance is otherwise unchanged. The native task executor retains the existing pool sizing and queue capacity.

## Migration Notes

There is no database data migration in F-03. One client-visible contract does change: alert/error response headers on ten resource endpoints move from `X-carcareApp-alert` / `-params` / `-error` to `X-carcare-*`, unifying them with the names `UserResource` and `ExceptionTranslator` already use. Any client code keyed on the `carcareApp` prefix must be updated in step; S-02 verifies the renamed contract. Canonical environment variables change from `JHIPSTER_*` to `APPLICATION_*`, but every consumed legacy variable remains a fallback during rollout. Existing Liquibase contexts and master changelog remain unchanged. Reverting the application commits restores the prior startup/configuration behavior without rolling back schema state.

## References

- Related research: `context/changes/jakarta-platform-migration/research.md`
- Platform dependency baseline: `pom.xml`
- Security boundary: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java`
- Liquibase bootstrap: `src/main/java/com/kasztelanic/carcare/config/LiquibaseConfiguration.java`
- Application configuration: `src/main/resources/config/application.yml`
- Roadmap ownership: `context/foundation/roadmap.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Jakarta and Security 6 Compile Cutover

#### Automated

- [x] 1.1 Forbidden Jakarta EE 8 namespaces are absent from `src/main/java`: `! rg -n 'javax\.(persistence|validation|servlet|transaction|mail)' src/main/java` — 8848e36
- [x] 1.2 Removed Spring Security 5 APIs are absent: `! rg -n 'WebSecurityConfigurerAdapter|EnableGlobalMethodSecurity|authorizeRequests\(|antMatchers\(' src/main/java` — 8848e36
- [x] 1.3 Exactly one `SecurityFilterChain` bean is declared and the obsolete `JwtConfigurer` source is removed: `[ "$(rg -c --no-filename 'SecurityFilterChain\s+\w+\s*\(' src/main/java | awk '{s+=$1} END {print s+0}')" = 1 ] && ! test -e src/main/java/com/kasztelanic/carcare/security/jwt/JwtConfigurer.java` — 8848e36
- [x] 1.4 Java 17 main compilation passes: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile` — 8848e36

#### Manual

- [x] 1.5 Security source review confirms the matcher order, bypass paths, filter anchors, headers, session policy, method security, and ProblemDetail handlers match the contract above. — 8848e36

### Phase 2: Boot-Native Configuration and JHipster Removal

#### Automated

- [x] 2.1 No JHipster Java/package references remain: `! rg -n 'tech\.jhipster' src/main/java src/test/java src/main/resources src/test/resources/logback.xml pom.xml` (scoped to exclude `src/test/resources/config/application.yml`, whose `FixedH2Dialect` line F-04 owns) — 4542b32
- [x] 2.2 The JHipster bridge is absent from the dependency graph: `! JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -q dependency:tree -Dincludes=tech.jhipster:jhipster-framework | grep -q jhipster-framework` — 4542b32
- [x] 2.3 Java 17 main compilation remains green: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile` — 4542b32
- [x] 2.4 Filtered resources retain the dev and no-Liquibase profile contracts: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -Pno-liquibase process-resources` — 4542b32
- [x] 2.5 The old alert-header namespace is gone and only one remains: `! rg -n 'carcareApp' src/main/java src/test/java` — 4542b32

#### Manual

- [x] 2.6 Configuration review confirms every consumed legacy value moved to `application.*` with canonical-first legacy environment aliases and unchanged effective dev/prod/test values. — 4542b32
- [x] 2.7 Utility review confirms locale-cookie, alert/error header (single `X-carcare-*` namespace), pagination-link, optional-response, logging, async, and no-Liquibase behavior is represented without JHipster code. — 4542b32

### Phase 3: Hibernate 6 Configuration Closure and F-04 Handoff

#### Automated

- [x] 3.1 No legacy Hibernate 5, removed naming, legacy dialect, or Jakarta Validation 2 references remain in main configuration and `pom.xml`. — c379620
- [x] 3.2 Java 17 default main compilation passes: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw compile` — c379620
- [x] 3.3 Java 17 IDE-profile compilation and generated metamodel processing pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -PIDE compile` — c379620
- [x] 3.4 Production main artifact builds without compiling the known-broken tests: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw -Pprod -Dmaven.test.skip=true package` — c379620
- [x] 3.5 Roadmap and change documentation explicitly record the F-03, S-02, and F-04 ownership boundaries. — c379620
- [x] 3.6 No Boot 2 actuator/MVC property names survive in configuration: `! rg -n 'favicon|jhimetrics|^ {8}export:' src/main/resources/config src/test/resources/config` and the renamed block is present: `rg -q '^ {4}prometheus:' src/main/resources/config/application.yml` — c379620

#### Manual

- [x] 3.7 Final review confirms no database changeset, security behavior, or test-suite success is claimed without runtime evidence, and the F-04 handoff is sufficient to continue without rediscovery. — c379620
