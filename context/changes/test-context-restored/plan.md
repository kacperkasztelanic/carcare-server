# Test Context Restored Implementation Plan

## Overview

Restore the Spring Boot 3 test foundation so `./mvnw verify` compiles every test source, boots the
real application context against H2, exercises REST integration tests through the application
`MockMvc`, and finishes green. The change removes configuration shadowing and restores meaningful
security coverage without changing production persistence mappings or pulling CarCare business
coverage forward from S-01–S-04.

## Current State Analysis

Main sources compile and package on Java 17 after F-03, but test compilation stops before any test
can execute. The current baseline is 39 compiler diagnostics across six failing files: 20 Java EE
or Spring 5 imports need mechanical migration, while `javax.crypto.SecretKey` is a valid JDK/JCE
type and must remain unchanged.

The test resource `config/application.yml` has the same classpath location as the main resource.
Because `target/test-classes` precedes `target/classes`, it replaces the main file instead of
overlaying it. Tests therefore lose the Liquibase master path, CSP, management base path and
exposure, and disabled mail-health setting. A live probe proved that supplying the missing
configuration plus a Hibernate 6 H2 type adjustment loads 598 bean definitions.

Five REST ITs build controllers with `MockMvcBuilders.standaloneSetup(...)`. This bypasses the real
filter chain and, for `UserResourceIT`, the Spring proxy that enforces `@PreAuthorize`. The current
suite therefore reports positive admin behavior without authenticating an administrator and does
not protect F-03's Spring Security 6 matcher migration.

## Desired End State

- Java 17 test compilation succeeds with the sole remaining `javax.*` test import being the
  JDK-owned `javax.crypto.SecretKey`.
- Tests run with an explicitly active `test` profile layered over shared main configuration, an H2
  datasource, test-safe SMTP values, and strict `hibernate.hbm2ddl.auto: validate`.
- All five length-65535 CLOB mappings validate under H2 through a test-only dialect fixture; no
  production entity or Liquibase schema contract changes.
- `UserJwtControllerIT`, `AuditResourceIT`, `ExceptionTranslatorIT`, `AccountResourceIT`, and
  `UserResourceIT` use the application `MockMvc`; `WebConfigurerTest` remains standalone by design.
- Representative anonymous, USER, and ADMIN requests prove filter-level and method-level
  authorization, SPA access, security headers, and public management behavior.
- `./mvnw test` and `./mvnw verify` pass. Any suite repairs remain limited to migration-exposed
  fixtures or objectively stale expectations; business-feature coverage stays deferred.

### Key Discoveries

- The compile baseline is 39 errors, not 37; the rows in the upstream research table already sum
  to 39 (`context/changes/test-context-restored/research.md`, section D).
- Resource-based test-profile activation works in Maven and IDE runs without adding
  `@ActiveProfiles` to all 11 context tests or changing Surefire/Failsafe configuration.
- Layering inherits main's Gmail SMTP settings, so the test profile must explicitly restore
  localhost port 25 with authentication and STARTTLS disabled
  (`src/main/resources/config/application.yml:67`).
- Hibernate 6 maps `String(length = 65535)` to VARCHAR under stock H2 because
  `H2Dialect.getMaxVarcharLength()` returns 1,048,576. A test dialect returning 65,534 promotes all
  five mappings to CLOB while keeping validation strict.
- The fifth long-text mapping is `vehicles.notes`, embedded from `VehicleDetails`, not
  `vehicle_details.notes` (`src/main/java/com/kasztelanic/carcare/domain/Vehicle.java:67`).
- `AuditResource` explicitly owns `/management/audits`; Actuator base-path configuration does not
  create or remove that controller route (`src/main/java/com/kasztelanic/carcare/web/rest/AuditResource.java:26`).
- `AccountResource` deliberately returns success for an unknown password-reset email to prevent
  account enumeration, so the existing `400` expectation is stale
  (`src/main/java/com/kasztelanic/carcare/web/rest/AccountResource.java:157`).

## What We're NOT Doing

- Adding `@Lob` or Hibernate JDBC type annotations to production entities.
- Editing, wiring, or deleting Liquibase changelogs, including the orphaned
  `20190102222057_changelog.xml`.
- Weakening or removing `hibernate.hbm2ddl.auto: validate`.
- Validating the production MariaDB schema against entity mappings; F-04 remains a test-only gate.
- Adding vehicle, event, report, statistics, reminder, or other CarCare business-behavior tests.
- Converting `WebConfigurerTest` away from its intentional standalone filter-unit harness.
- Repairing JaCoCo wiring, adding coverage thresholds, changing CI, or changing the client.
- Treating a green scaffolding suite as proof that the CarCare product behavior is correct.

## Implementation Approach

First restore a compilable and structurally correct test environment: migrate imports, layer an
explicit test profile, and add the narrow H2 compatibility fixture. Once a focused context test
proves Liquibase-before-validation startup, convert the lower-risk REST ITs and establish a compact
runtime security matrix. Then convert the stateful account and user-management tests, where real
services and method security change fixture requirements. Finish by running the entire suite and
repairing only migration-exposed test defects under the agreed scope boundary.

## Critical Implementation Details

### Timing & lifecycle

Rename the test YAML and activate `test` atomically. A renamed profile file without activation lets
Maven's filtered default `dev` profile load MariaDB settings; activation without the rename keeps
the shadowing defect. The effective test environment must be proven before any REST harness
conversion is used as evidence.

### State sequencing

Create and compile `TestH2Dialect` before changing the profile's dialect FQN, then boot one focused
context before converting REST tests. Keep one active-profile strategy across every context test so
Spring's context cache is not fragmented; do not use `@DirtiesContext` for routine isolation.

## Phase 1: Restore the Test Foundation

### Overview

Make all test sources compile and replace the shadowed resource with a layered, explicit test
profile. Add the H2 compatibility fixture and a focused configuration contract test so the first
runtime checkpoint proves the context, Liquibase ordering, and strict schema validation together.

### Changes Required

#### 1. Test-source Jakarta and Spring 6 imports

**Files**: `src/test/java/com/kasztelanic/carcare/repository/timezone/DateTimeWrapper.java`,
`src/test/java/com/kasztelanic/carcare/web/rest/TestUtil.java`,
`src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`,
`src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorTestController.java`,
`src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`,
`src/test/java/com/kasztelanic/carcare/repository/CustomAuditEventRepositoryIT.java`

**Intent**: Remove the measured test-compilation blockers without changing test behavior or adding
dependencies.

**Contract**: Convert persistence, validation, servlet, and mail imports to their `jakarta.*`
equivalents and Thymeleaf's template engine to `org.thymeleaf.spring6.SpringTemplateEngine`.
Preserve `javax.crypto.SecretKey` in
`src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java:18`.

#### 2. Layered test-profile resources

**Files**: `src/test/resources/config/application.yml` (rename),
`src/test/resources/config/application-test.yml`,
`src/test/resources/config/application.properties`

**Intent**: Eliminate the configuration-shadowing defect and make profile activation consistent in
Maven, IDE, and focused integration-test runs.

**Contract**: Move test overrides into `application-test.yml` and activate `test` from the test
resource `application.properties`. Retain H2, Liquibase `contexts: test`, strict schema validation,
test JWT, task-pool, and application mail overrides. Inherit the shared Liquibase master path, CSP,
management settings, and server defaults. Explicitly set SMTP to localhost:25 with blank
credentials and disabled auth/STARTTLS, and neutralize the Gmail-specific values that survive
layering by also overriding `spring.mail.tls` to `false` and
`spring.mail.properties.mail.smtp.ssl.trust` to empty (main sets them to `true` and
`smtp.gmail.com`; they are inert with STARTTLS off but contradict the neutralization contract). Remove the dead
`hibernate.id.new_generator_mappings` and vestigial bean-overriding setting.

#### 3. Hibernate 6 H2 compatibility fixture

**Files**: `src/test/java/com/kasztelanic/carcare/config/TestH2Dialect.java`,
`src/test/resources/config/application-test.yml`

**Intent**: Make Hibernate 6 classify the five existing length-65535 strings as CLOBs under H2
without changing production mappings or the Liquibase schema.

**Contract**: Add a test-only subclass of `org.hibernate.dialect.H2Dialect` whose
`getMaxVarcharLength()` boundary is below 65,535, and point `spring.jpa.database-platform` at its
FQN. Do not reproduce the removed JHipster dialect's FLOAT registration; it is unrelated to this
failure.

#### 4. Effective configuration contract

**File**: `src/test/java/com/kasztelanic/carcare/config/TestConfigurationIT.java`

**Intent**: Guard the resource-layering fix against future regression instead of relying on context
startup as an indirect signal.

**Contract**: Assert that `test` is active and `dev` is not; the datasource is H2; Liquibase uses
`classpath:config/liquibase/master.xml` with the `test` context; management remains under
`/management` with mail health disabled; SMTP is localhost:25 without auth/STARTTLS and retains no
inherited Gmail TLS or `ssl.trust` value; and CSP is nonblank. The focused test must boot the same `CarcareApp` context as the suite.

### Success Criteria

#### Automated Verification

- Test compilation passes on Java 17: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw test-compile`
- The sole remaining `javax.*` test import is `javax.crypto.SecretKey`: `test "$(rg -n '^import javax\.' src/test/java)" = 'src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java:18:import javax.crypto.SecretKey;'`
- Generated test resources contain only the layered profile contract: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw clean process-test-resources && test ! -e target/test-classes/config/application.yml && test -e target/test-classes/config/application.properties && test -e target/test-classes/config/application-test.yml`
- Focused configuration and Hibernate context tests pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw verify -Dit.test=TestConfigurationIT,HibernateTimeZoneIT`

#### Manual Verification

- Test-profile review confirms shared defaults are inherited, environment-sensitive SMTP values are neutralized, and strict schema validation remains enabled.

**Implementation Note**: Pause after the focused context tests pass. REST harness conversion must
not begin until the effective profile and schema-validation contract are proven.

---

## Phase 2: Convert Foundational REST and Security Tests

### Overview

Move the lower-state REST tests onto the real application `MockMvc` and add the targeted runtime
security matrix selected during planning. This phase proves the real filter chain, application
ObjectMapper/advice, management authorization, SPA access, and migrated security headers.

### Changes Required

#### 1. Authentication integration test

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/UserJwtControllerIT.java`

**Intent**: Exercise authentication through the real application stack instead of a manually
constructed controller.

**Contract**: Add `@AutoConfigureMockMvc`, inject `MockMvc`, and remove controller construction and
`standaloneSetup`. Keep authentication requests anonymous because `/api/authenticate` is public.
Preserve successful normal/remember-me token responses and the failed-login `401` contract.

#### 2. Exception translation integration test

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorIT.java`

**Intent**: Verify application MVC configuration and controller advice together while preserving
the distinction between advice-level 401/403 responses and filter-chain rejections.

**Contract**: Inject application `MockMvc` and remove standalone converter/advice assembly.
Requests under `/test/**` remain anonymous and reach the test controller through
`.anyRequest().permitAll()`. Preserve the existing 409, 400, 403, 401, 405, and 500 Problem Detail
contracts.

#### 3. Audit authorization integration test

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java`

**Intent**: Run audit behavior through the real `/management/**` filter-chain boundary.

**Contract**: Inject application `MockMvc`, remove standalone resolver/converter construction, and
authenticate existing behavior tests as ADMIN. Add representative anonymous `401` and USER `403`
assertions; retain ADMIN `200` and `404` outcomes. Use exact authority strings from
`AuthoritiesConstants`.

#### 4. Runtime security smoke matrix

**File**: `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java`

**Intent**: Protect the Spring Security 6 migration at the matcher and response-contract level.

**Contract**: Through application `MockMvc`, assert anonymous `GET /` returns `200` with CSP,
referrer-policy, permissions-policy, and `X-Frame-Options: DENY`; an anonymous private API request
returns UTF-8 `application/problem+json` `401` with message and path; and anonymous
`/management/health` remains public and healthy. Reuse `AuditResourceIT` for USER `403` and ADMIN
success rather than duplicating that matrix.

### Success Criteria

#### Automated Verification

- Foundational REST and security ITs pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw verify -Dit.test=UserJwtControllerIT,AuditResourceIT,ExceptionTranslatorIT,SecurityConfigurationIT`
- Converted foundational ITs no longer build standalone MockMvc: `! rg -n 'standaloneSetup|MockMvcBuilders' src/test/java/com/kasztelanic/carcare/web/rest/UserJwtControllerIT.java src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorIT.java`
- Security smoke assertions cover SPA headers, private-api `401`, audit USER `403`, audit ADMIN success, and public management health.

#### Manual Verification

- Security-contract review confirms filter-level Problem Details are not confused with exceptions handled inside `DispatcherServlet`, and matcher outcomes match `SecurityConfiguration`.

**Implementation Note**: Pause after the targeted matrix passes and confirm the role assignments
before converting the larger stateful REST tests.

---

## Phase 3: Convert Stateful Account and User Tests

### Overview

Replace the two complex standalone harnesses with the real application context. Preserve existing
business assertions while making persistence, authentication, method security, and outbound-mail
isolation explicit.

### Changes Required

#### 1. Account integration test

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AccountResourceIT.java`

**Intent**: Exercise account endpoints through one application `MockMvc` and real `UserService`
instead of two manually assembled controllers with inconsistent collaborators.

**Contract**: Inject application `MockMvc`; replace the local mail mock with context-scoped
`@MockBean MailService`; do not mock `UserService`. Convert account lookup fixtures to persisted
users and matching `@WithMockUser` principals, including a managed ADMIN authority where asserted.
Keep public register, activate, authentication-check, and password-reset routes anonymous. Preserve
authenticated account-update and password-change behavior. Change the nonexistent-email reset
expectation from `400` to `200`, matching the controller's anti-enumeration contract.

#### 2. User-management integration test

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`

**Intent**: Make the existing user-management tests execute through the Spring proxy so
`@PreAuthorize` is enforced for the first time.

**Contract**: Inject application `MockMvc` and remove standalone controller/advice/resolver setup.
Run existing create, update, delete, and authority-list behavior as ADMIN. Run list/get behavior as
an authenticated USER. Add one representative anonymous `401` and one USER-on-admin-method `403`
assertion. Prefer a class-level ADMIN principal with method-level USER overrides for read tests to
keep the role contract visible and compact.

#### 3. Context isolation and mocks

**Files**: `src/test/java/com/kasztelanic/carcare/web/rest/AccountResourceIT.java`,
`src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`

**Intent**: Prevent context mocks, caches, or security state from leaking between test methods or
classes.

**Contract**: Limit context mocking to `MailService`, declared identically in both
`AccountResourceIT` and `UserResourceIT` so the two classes share one context-cache variant. The
mock is required in both: `UserResource.createUser()` dispatches asynchronous
`MailService.sendCreationEmail()`, so an unmocked successful-create test in `UserResourceIT`
attempts real SMTP and leaves asynchronous work running past its transaction. Rely on Spring's
automatic Mockito reset between methods and transactional database rollback. Keep existing user
cache cleanup in `UserResourceIT`. Do not use `@DirtiesContext` or create a mocked `UserService`
context variant.

### Success Criteria

#### Automated Verification

- Stateful account and user ITs pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw verify -Dit.test=AccountResourceIT,UserResourceIT`
- All five migrated REST ITs are free of standalone MockMvc while `WebConfigurerTest` remains unchanged: `! rg -n 'standaloneSetup|MockMvcBuilders' src/test/java/com/kasztelanic/carcare/web/rest/UserJwtControllerIT.java src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorIT.java src/test/java/com/kasztelanic/carcare/web/rest/AccountResourceIT.java src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`
- Representative role assertions prove anonymous `401`, USER read success, USER-on-admin `403`, and ADMIN mutation success.

#### Manual Verification

- Fixture review confirms tests use persisted users and the real `UserService`, mock only outbound mail, and preserve the password-reset anti-enumeration behavior.

**Implementation Note**: Pause after both stateful classes pass. Do not widen failures into product
behavior changes without classifying them under the Phase 4 scope rule.

---

## Phase 4: Stabilize and Gate the Complete Suite

### Overview

Run every unit and integration test, repair migration-exposed fixture or expectation defects, and
record the final foundation boundary. Completion requires a green `verify`, but does not authorize
new CarCare business coverage or production behavior changes.

### Changes Required

#### 1. Migration-scoped failure triage

**Files**: Only test sources or test resources implicated by an observed failing test

**Intent**: Close failures revealed only after compilation and context startup without silently
changing production behavior to satisfy stale scaffolding.

**Contract**: A repair is in scope when it updates a removed API/namespace, fixes a broken fixture,
uses the real application configuration, or corrects an assertion contradicted by an intentional
existing contract. Confirm the suspected uninitialized `DateTimeProvider` in `UserServiceIT` at
runtime before changing it. If a failure indicates a CarCare business regression or requires
production domain/API changes, stop and route it to the owning S-01–S-04 slice instead of masking
it.

#### 2. Final change record

**File**: `context/changes/test-context-restored/change.md`

**Intent**: Leave a precise handoff describing what the green suite proves and what it does not.

**Contract**: Record the final test counts, any migration-scoped test repairs beyond Phases 1–3,
and the explicit absence of vehicle/event/report/statistics/reminder coverage. Do not claim
production schema validation or product parity.

#### 3. Repository instruction refresh

**File**: `AGENTS.md`

**Intent**: Prevent this change from leaving future agents with a dead file path and a test-status
narrative that the delivered suite contradicts.

**Contract**: After the final green `verify`, update three places. `AGENTS.md:50` names
`src/test/resources/config/application.yml`, the file Phase 1 renames — point it at the layered
`application.properties` + `application-test.yml` pair. Replace the "Why the tests currently fail"
section (`AGENTS.md:161–175`), which attributes every context failure to the removed
`FixedH2Dialect`, with the actual resolution: profile layering, the shared Liquibase changelog path
and CSP default, and the test-only `TestH2Dialect` CLOB boundary. Correct the "Integration tests
use a standalone harness" section (`AGENTS.md:203`) to record that the five listed REST ITs now use
application `MockMvc` and that only `WebConfigurerTest` remains standalone. Report real counts; do
not restate coverage the suite does not have.

### Success Criteria

#### Automated Verification

- Surefire unit tests pass: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw test`
- The complete unit and integration suite passes: `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw verify`
- The Jakarta migration guard still leaves only the JDK-owned test import: `test "$(rg -n '^import javax\.' src/test/java)" = 'src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java:18:import javax.crypto.SecretKey;'`
- Changed files pass whitespace validation: `git diff --check`

#### Manual Verification

- Final failure-triage review confirms every additional repair is migration-scoped and evidence-backed.
- Final scope review confirms no production entity, Liquibase schema, API behavior, client, or deferred business-test slice was changed.
- `AGENTS.md` review confirms the test-configuration path, the failure explanation, the standalone-harness note, and the reported suite status all match the delivered suite.

**Implementation Note**: Completion requires all automated checks plus human confirmation of the
scope review. A red `verify` is not an acceptable F-04 end state under the planning decision.

## Testing Strategy

### Unit Tests

- Preserve existing focused tests, including `TokenProviderTest`'s JDK-owned crypto type and
  `WebConfigurerTest`'s intentional standalone filter harness.
- Use `./mvnw test` to confirm Surefire's non-IT population remains green independently of the
  integration phase.
- Repair unit fixtures only after reproducing their runtime failure; do not act on static suspicions
  alone.

### Integration Tests

- `TestConfigurationIT` proves effective profiles and critical inherited/overridden properties.
- `HibernateTimeZoneIT` provides the first schema-validating context checkpoint.
- The five converted REST ITs exercise application MVC, converters, advice, persistence, security
  filters, and method proxies.
- `SecurityConfigurationIT` plus `AuditResourceIT` and `UserResourceIT` cover the representative
  anonymous/USER/ADMIN matrix without repeating it for every route.
- Failsafe-focused commands use `-Dit.test`; ordinary `./mvnw test` excludes `*IT*` by design.

### Manual Testing Steps

1. Review the effective test-profile assertions to confirm no `dev` profile or MariaDB datasource
   can leak into IDE or Maven test runs.
2. Review the role annotations and expected statuses in audit, account, and user-management tests
   against `SecurityConfiguration` and `@PreAuthorize`.
3. Review the final diff and failure log to confirm every change remains test-only or documentation-only.
4. Confirm the completion note distinguishes restored scaffolding from deferred product coverage.

## Performance Considerations

The test suite will boot the real MVC/security context instead of cheap standalone controllers.
Keep one resource-activated profile across all classes so Spring can reuse context caches. The
`@MockBean MailService` variant in `AccountResourceIT` creates one additional cache key; avoid more
context mocks and `@DirtiesContext` so the full suite remains practical.

## Migration Notes

This change has no database or production-data migration. The H2 dialect exists only under
`src/test/java`, and the profile/resource changes exist only under `src/test/resources`. Reverting
the test commits restores the previous harness without schema rollback. The orphaned changelog stays
untouched because wiring or deleting it is independent of restoring the test context.

## References

- Change research: `context/changes/test-context-restored/research.md`
- Roadmap F-04: `context/foundation/roadmap.md:245`
- F-03 handoff: `context/archive/jakarta-platform-migration/change.md`
- F-03 implementation review: `context/archive/jakarta-platform-migration/reviews/impl-review.md`
- Test configuration: `src/test/resources/config/application.yml`
- Shared configuration: `src/main/resources/config/application.yml`
- Security boundary: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java`
- Failsafe/Surefire contract: `pom.xml:767`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Restore the Test Foundation

#### Automated

- [x] 1.1 Test compilation passes on Java 17 — 5d2bf0d
- [x] 1.2 The sole remaining `javax.*` test import is `javax.crypto.SecretKey` — 5d2bf0d
- [x] 1.3 Generated test resources contain only the layered profile contract — 5d2bf0d
- [x] 1.4 Focused configuration and Hibernate context tests pass — 5d2bf0d

#### Manual

- [x] 1.5 Test-profile review confirms shared defaults are inherited, environment-sensitive SMTP values are neutralized, and strict schema validation remains enabled — 5d2bf0d

### Phase 2: Convert Foundational REST and Security Tests

#### Automated

- [x] 2.1 Foundational REST and security ITs pass — 23802d8
- [x] 2.2 Converted foundational ITs no longer build standalone MockMvc — 23802d8
- [x] 2.3 Security smoke assertions cover SPA headers, private-api `401`, audit USER `403`, audit ADMIN success, and public management health — 23802d8

#### Manual

- [x] 2.4 Security-contract review confirms filter-level Problem Details are not confused with exceptions handled inside `DispatcherServlet`, and matcher outcomes match `SecurityConfiguration` — 23802d8

### Phase 3: Convert Stateful Account and User Tests

#### Automated

- [x] 3.1 Stateful account and user ITs pass — 2dc4191
- [x] 3.2 All five migrated REST ITs are free of standalone MockMvc while `WebConfigurerTest` remains unchanged — 2dc4191
- [x] 3.3 Representative role assertions prove anonymous `401`, USER read success, USER-on-admin `403`, and ADMIN mutation success — 2dc4191

#### Manual

- [x] 3.4 Fixture review confirms tests use persisted users and the real `UserService`, mock only outbound mail, and preserve the password-reset anti-enumeration behavior — 2dc4191

### Phase 4: Stabilize and Gate the Complete Suite

#### Automated

- [x] 4.1 Surefire unit tests pass — 1a54b2f
- [x] 4.2 The complete unit and integration suite passes — 1a54b2f
- [x] 4.3 The Jakarta migration guard still leaves only the JDK-owned test import — 1a54b2f
- [x] 4.4 Changed files pass whitespace validation — 1a54b2f

#### Manual

- [x] 4.5 Final failure-triage review confirms every additional repair is migration-scoped and evidence-backed — 1a54b2f
- [x] 4.6 Final scope review confirms no production entity, Liquibase schema, API behavior, client, or deferred business-test slice was changed — 1a54b2f
- [x] 4.7 `AGENTS.md` review confirms the test-configuration path, the failure explanation, the standalone-harness note, and the reported suite status all match the delivered suite — 1a54b2f

> **Re-affirmed 2026-08-26** after implementation review (`reviews/impl-review.md`). 4.5 and 4.7 hold
> against the corrected records. **4.6 does not hold as written**: three production/build files changed —
> `CacheConfiguration.java` (disclosed at close-out), `PersistentAuditEvent.java` (`@EqualsAndHashCode(of = "id")`,
> a production entity change), and `pom.xml` (`-Duser.timezone=UTC` on `argLine`). No Liquibase schema,
> API behavior, client code, or deferred business-test slice was changed. Review finding F1 accepted the
> two undisclosed edits deliberately rather than reverting them; `change.md` and `AGENTS.md` now enumerate
> all three.
