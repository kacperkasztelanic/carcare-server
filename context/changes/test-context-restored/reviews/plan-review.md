<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Test Context Restored Implementation Plan

- **Plan**: `context/changes/test-context-restored/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-25
- **Verdict**: REVISE
- **Findings**: 0 critical, 2 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

Grounding: 12/12 sampled existing paths ✓, 3/3 creation parents ✓, 3/3 symbols ✓, brief↔plan ✓. A live Java 17 `./mvnw test-compile` reproduced the documented 39 errors across six files.

## Findings

### F1 — UserResourceIT still performs real asynchronous SMTP

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 — Context isolation and mocks
- **Detail**: The plan promises explicit outbound-mail isolation but limits `@MockBean MailService` to `AccountResourceIT`. `UserResource.createUser()` calls asynchronous `MailService.sendCreationEmail()`, while `UserResourceIT` contains successful create tests and currently injects the real `MailService`. Tests can therefore make SMTP attempts and leave asynchronous work running after their transaction completes.
- **Fix**: Use the same `@MockBean MailService` declaration in both stateful test classes and update the Phase 3 contract accordingly. Identical mock definitions should reuse the same context-cache variant.
- **Decision**: PENDING

### F2 — Repository instructions become stale after the config rename

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 / Phase 4 documentation
- **Detail**: `AGENTS.md:50` names the test configuration file being removed, while `AGENTS.md:163-170` still attributes every context failure to `FixedH2Dialect`. Completing the plan would leave future agents with a false path and obsolete test status.
- **Fix**: Add `AGENTS.md` to the plan and refresh its test-configuration path, dialect explanation, and restored-suite status after final `verify`.
- **Decision**: PENDING

### F3 — Gmail-specific properties survive profile layering

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 — Layered test-profile resources
- **Detail**: The plan overrides host, port, credentials, authentication, and STARTTLS, but the layered profile still inherits `spring.mail.tls: true` and `spring.mail.properties.mail.smtp.ssl.trust: smtp.gmail.com`. They are effectively inert with STARTTLS disabled, but conflict with the manual promise that environment-specific SMTP settings are fully neutralized.
- **Fix**: Override those properties in `application-test.yml` and include them in `TestConfigurationIT`'s effective-configuration assertions.
- **Decision**: PENDING
