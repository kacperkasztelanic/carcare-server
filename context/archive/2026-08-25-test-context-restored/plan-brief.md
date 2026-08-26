# Test Context Restored — Plan Brief

> Full plan: `context/changes/test-context-restored/plan.md`
> Research: `context/changes/test-context-restored/research.md`

## What & Why

Restore the Spring Boot 3 test foundation so `./mvnw verify` compiles, boots the real application context under H2, executes REST tests through the application stack, and finishes green. This is the last platform foundation blocking S-01–S-04; it restores trustworthy scaffolding, not missing CarCare business coverage.

## Starting Point

Main sources compile, but test compilation stops with 39 diagnostics. A live probe showed that the context loads 598 beans after fixing the shadowed configuration and H2 type boundary; five REST ITs still bypass the real security/filter/proxy stack with standalone MockMvc.

## Desired End State

Tests run under an explicit layered `test` profile with H2, strict schema validation, and safe SMTP overrides. All five selected REST ITs use application MockMvc, representative security boundaries are asserted, and both `./mvnw test` and `./mvnw verify` pass without production schema or API changes.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Test configuration | Resource-activated layered `test` profile | Removes the shadowing defect in Maven and IDE runs without an annotation sweep | Research + Plan |
| H2 CLOB handling | Test-only `TestH2Dialect` | Proven to validate all five mappings with zero production blast radius | Research + Plan |
| REST harness | Convert all five identified ITs | Exercises real converters, advice, filters, and method-security proxies | Research + Plan |
| Authorization | Representative anonymous/USER/ADMIN matrix | Guards both filter and method security without exhaustive duplication | Plan |
| Runtime security | Targeted smoke coverage | Protects SPA headers, private API, admin, and management matcher categories | Plan |
| Completion gate | Migration-scoped green `verify` | Later slices need a reliable green prerequisite, not a merely executing red suite | Plan |
| Product coverage | Defer to S-01–S-04 | Existing tests cover JHipster scaffolding, not CarCare behavior | Research / Roadmap |

## Scope

**In scope:**

- Migrate 20 test imports while preserving `javax.crypto.SecretKey`.
- Layer and activate the test profile; neutralize inherited SMTP settings.
- Add a test-only H2 dialect and configuration contract IT.
- Convert five REST ITs to application MockMvc.
- Add representative authorization and runtime security assertions.
- Fix migration-exposed fixtures and objectively stale expectations until `verify` is green.

**Out of scope:**

- Production entity annotations or Liquibase changes.
- Product tests for vehicles, events, reports, statistics, or reminders.
- `WebConfigurerTest` conversion, JaCoCo/CI work, client changes, or MariaDB schema validation.

## Architecture / Approach

Test resources activate `test` and layer `application-test.yml` over shared main configuration. The H2 datasource uses `TestH2Dialect` while retaining Hibernate validation. Full-context MockMvc then drives the real MVC and security stack; focused tests establish the boundary before the two stateful account/user classes are converted and the complete suite is stabilized.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Test foundation | Green test compile, layered profile, strict H2 context | Accidental `dev`/MariaDB or Gmail settings leaking into tests |
| 2. REST and security | Foundational full-context ITs and targeted security matrix | Confusing advice-level and filter-level 401/403 behavior |
| 3. Account and user | Real-service stateful ITs with enforced method security | Fixtures written for unproxied controllers no longer satisfy authorization |
| 4. Full-suite gate | Migration-scoped repairs and green `verify` | Unrelated product failures tempting scope expansion |

**Prerequisites:** F-03 is delivered; Java 17.0.20 and current Maven dependencies are available.
**Estimated effort:** Approximately 3–4 implementation sessions across four reviewable phases.

## Open Risks & Assumptions

- Additional failures cannot be enumerated until the restored context runs the whole suite; only migration-exposed fixture and stale-expectation repairs are authorized.
- `@MockBean MailService` creates one extra Spring context-cache variant; additional context mocks would increase suite time.
- A green F-04 suite still does not validate production MariaDB mappings or CarCare business flows.

## Success Criteria (Summary)

- Test compilation succeeds with only the JDK-owned `javax.crypto` import remaining.
- Focused configuration, Hibernate, REST, and security tests pass through the real application context.
- `./mvnw test` and `./mvnw verify` finish green with no production schema or API changes.
