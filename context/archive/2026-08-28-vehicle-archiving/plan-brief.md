# Vehicle Archiving — Plan Brief

> Full plan: `context/changes/vehicle-archiving/plan.md`
> Research: `context/changes/vehicle-archiving/research.md`

## What & Why

Turn vehicle deletion into a recoverable soft archive. Ordinary owners lose operational access to
archived vehicles, retained data remains available to historical analytics, and administrators gain
a discoverable restore workflow.

## Starting Point

Vehicles are physically deleted through `VehicleServiceImpl`, despite non-cascading event foreign
keys. Repository methods are shared by active workflows and historical consumers, reminders do not
filter archived parents, no `410 Gone` contract exists, and `/api/admin/**` has no vehicle resource.

## Desired End State

`DELETE /api/vehicle/{id}` preserves its existing successful client contract while setting
`archived_at`. Owned archived direct resources return RFC7807 `410 Gone`; unknown/foreign resources
remain `404`. Operational events/reminders are active-only, cost consumers append matching archived
vehicles, read-only historical consumers remain inclusive, and admins can list/restore archives.

## Key Decisions Made

| Decision | Choice | Why | Source |
|---|---|---|---|
| Archive representation | `vehicles.archived_at TIMESTAMP NULL` | Additive soft-delete marker; null means active | Research / D3 |
| Delete contract | Keep `200`, body, and deleted alert | Frozen client deletes then reloads the list | Research / D4–D5 |
| Archived direct access | `410 Gone` for owned archived resources | Distinguishes archived from unknown/foreign | Owner decision |
| Operational visibility | Active-only | Archived vehicles leave normal workflows | Research / D1 |
| Historical reads | Single-vehicle reports and read-only stats remain inclusive | Retained records must remain useful | Research / D2 / D6 |
| Cost consumers | Append period-matching archived vehicles by deterministic id order | Historical cost reporting follows the requested period | Research / D2 |
| Timestamp source | Injected application `Clock` | Deterministic lifecycle tests | Owner decision |
| Restore surface | Paginated admin list plus idempotent restore API | Discoverable recovery without client UI | Owner decision |
| Fixtures | Dedicated non-golden archive fixtures | Golden data is index-exact and must not move | Research |
| Concurrency | Existing transaction semantics; no explicit locking | Keep the slice focused and consistent with the app | Owner decision |

## Scope

**In scope:**

- Liquibase schema/index changes and explicit repository policies.
- Vehicle archive lifecycle, active event/reminder filtering, historical report/statistics policy,
  `410 Gone`, admin archive discovery, restore, and integration coverage.

**Out of scope:**

- Physical deletion, cascading cleanup, owner-facing unarchive, client UI/copy/i18n changes,
  request flags, audit-table redesign, strict locking, and golden fixture regeneration.

## Architecture / Approach

Add `VehicleScopeService` as the owner-facing policy boundary and retain inclusive repository
queries where historical consumers need them. Mutate managed entities with the injected `Clock` so
Hibernate’s second-level cache stays coherent. Add a dedicated admin service/resource and DTO under
`/api/admin/vehicles`, reusing the existing admin security rule and pagination conventions.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Schema and Query Foundation | Archive marker, indexes, policy-specific repositories, active reminders | Accidentally filtering historical consumers |
| 2. Archive Lifecycle and Admin Restore | Soft delete, `410`, active event guards, paginated restore API | Correct owned-archived vs unknown/foreign classification |
| 3. Consumer Behavior | Active operational paths, period cost union, historical analytics, reminder exclusion | Join/order/date-boundary regressions |
| 4. Fixtures and Regression Verification | Isolated fixtures, wire contracts, full suite, golden protection | Hidden client or fixture compatibility drift |

**Prerequisites:** JDK `17.0.20-tem`, existing test JVM arguments/Byte Buddy setup, and disposable
application/database access for manual lifecycle verification.

**Estimated effort:** About four independently verifiable implementation sessions across four
phases.

## Open Risks & Assumptions

- Existing event/report/statistics query reuse makes a blanket repository archive filter unsafe;
  each caller must be assigned an explicit policy.
- `410` applies to direct owned archived resources, while composite collections intentionally remain
  `200` with archived rows omitted.
- Liquibase rollback that drops `archived_at` is only safe before archive data is written; operational
  recovery remains backup/restore.
- No client artifact update is expected because the current delete status/body/header contract is
  preserved.

## Success Criteria (Summary)

- Archive and restore work through the server API with correct `200`/`410`/`404` semantics.
- Operational workflows exclude archived vehicles; historical and cost policies match the approved
  period behavior.
- Admin discovery/restore, ownership isolation, exception shape, client wire contract, and reminder
  behavior are covered by tests.
- Java 17 `./mvnw verify` passes, golden references remain unchanged, and no physical deletion path
  remains.
