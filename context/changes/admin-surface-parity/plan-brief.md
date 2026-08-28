# Admin Surface Parity — Plan Brief

> Full plan: `context/changes/admin-surface-parity/plan.md`
> Research: `context/changes/admin-surface-parity/research.md`

## What & Why

Complete parity coverage for the server's administrative and operational APIs after the Spring Boot 3 migration. The work primarily strengthens integration tests, while intentionally correcting three malformed creation Locations and the unusable reminder-advance DELETE binding.

## Starting Point

The scoped production behavior remains close to the `6e19b96` baseline, but direct coverage is uneven: users and audits have partial ITs, five operational resources have none, and two captured reminder references are unused.

## Desired End State

All 22 scoped handlers have deterministic coverage for stable payload, status, headers, security, and persistence effects. Canonical Locations and reminder DELETE work as intended, both reminder goldens prove the expected six mail selections, and the frozen user/audit UI still functions.

## Key Decisions Made

| Decision | Choice | Why | Source |
|---|---|---|---|
| Production scope | Test-focused, except four approved route corrections | Current business logic otherwise matches the runnable baseline | Research |
| Empty pagination | Pin current `page=0` | This accepted migration correction is safer and client-invisible | Plan |
| Creation Locations | Fix all three to canonical hyphenated routes | The frozen client has no dependency on malformed values | Plan |
| Reminder DELETE | Repair `days` binding and enable 200/404 tests | The user explicitly authorized restoring the operation | Plan |
| Header namespaces | Preserve `X-carcare-*` vs `X-carcareApp-*` | They are independent historical contracts | Research |
| Security | Preserve authenticated reads and ADMIN mutations | Ordinary workflows consume lookup/configuration reads | Research |
| Failure coverage | Stable edges only | Avoid pinning accidental database exceptions | Plan |
| Reminder depth | Consume both captured references | Selection parity is otherwise untested | Plan |
| Time seam | Inject a system-default `Clock` | Enables deterministic full-path testing without changing runtime dates | Plan |
| Browser scope | Users and audits only | They are the only frozen-client ADMIN pages | Plan |

## Scope

**In scope:**

- Complete user/authority and audit response, pagination, header, security, and browser coverage.
- Localized lookup/configuration CRUD, canonical Locations, and functional reminder-advance DELETE.
- Test-data invariants plus reminder endpoint and both golden selection references.

**Out of scope:**

- Service extraction, schema changes, new validation, or cleaner persistence errors.
- Lookup detail GETs/updates/order/pagination and new operational UI.
- Test-data idempotency/determinism or reminder reliability/timezone/observability redesign.

## Architecture / Approach

Use full-context MockMvc and rollback transactions. Extend existing user/audit ITs, add focused lookup and test-data ITs, and reuse `SessionFixtures.seedGoldenDataset()` for reminder tests. Controller-local URI fixes stay in their current resources; `ReminderServiceImpl` receives a system-default `Clock` that tests replace with the captured reference date.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. User and Audit Contracts | Exact client-facing administration contracts and browser smoke | Brittle pagination/header expectations |
| 2. Lookup and Configuration Routes | Canonical routes, localization, CRUD, and security matrix | Intentional API compatibility correction |
| 3. Operational Endpoints and Reminder Selection | Test-data invariants, deterministic reminders, both goldens, full suite | Fixture/clock/mock interaction correctness |

**Prerequisites:** JDK `17.0.20-tem`, Byte Buddy test agent, and a runnable frozen client/MariaDB environment for the two manual smokes.

**Estimated effort:** About three implementation sessions across three independently verifiable phases.

## Open Risks & Assumptions

- Unknown external scripts may assert the old malformed Locations or historical reminder DELETE failure; client 1.2.5 does not.
- Corrected item Locations identify DELETE-capable resources but still return 405 to GET because no detail handlers are being added.
- The fixture mirror and system-default clock preserve current behavior but intentionally do not solve reminder timezone policy.

## Success Criteria (Summary)

- Stable contracts and authorization for all 22 handlers are covered by enabled full-context tests.
- Canonical Locations, working reminder DELETE, and both six-call reminder references are verified.
- Targeted suites, full `./mvnw verify`, whitespace checks, and user/audit browser smokes pass.
