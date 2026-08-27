# Client-server contract trial — Plan Brief

> Full plan: `context/changes/client-server-contract-trial/plan.md`
> Research: `context/changes/client-server-contract-trial/research.md`

## What & Why

Make the frozen React 1.2.5 client work cleanly with the migrated server. A real Playwright smoke
already found a startup payload regression and a licence-plate length mismatch; this plan fixes those
two confirmed issues, then completes an evidence-led CRUD trial of the other event flows.

## Starting Point

The WAR serves the prebuilt client, so server compatibility is the available corrective surface.
The current `/management/info` payload lacks the `activeProfiles` array that the bundle
unconditionally reads, and Spring Boot no longer automatically exposes the configured ribbon field.
The client also permits licence plates twice as long as the server schema.

## Desired End State

Anonymous load reaches login without an application-profile exception. A user can save any
client-valid licence plate, and clean-browser CRUD exercises for vehicle plus all five event types
finish with normal server responses, client state, and toasts.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Startup payload | Server `InfoContributor` restores both exact client profile fields | Preserves the legacy interface without exposing the whole environment. | Research + plan review |
| Licence plate contract | Widen server model and schema to 20 | The frozen create and edit forms both accept 20 characters. | Research |
| Remaining work | Browser evidence before each extra fix | Avoids speculative changes to legacy payloads and event behavior. | Research |
| Historical baseline | Keep F-02 separate | Golden output governs reports/reminders, not browser compatibility. | Roadmap |

## Scope

**In scope:**

- Public management-info profile payload.
- Licence-plate model and Liquibase widening migration.
- Focused integration regressions and real-client CRUD trial.

**Out of scope:**

- Modifying/rebuilding the client, vehicle deletion with history, report/reminder parity, and any
  issue not reproduced through the trial.

## Architecture / Approach

An Actuator contributor supplies the two top-level management-info details the legacy client reads.
A forward-only Liquibase migration aligns storage with the client’s form validation. Full-context
MockMvc catches durable server contracts; Playwright validates the bundled UI against a disposable
MariaDB WAR and determines whether any further narrow fix is needed.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Startup profile contract | Both legacy profile fields and regression test | Exposing more environment than intended |
| 2. Licence-plate alignment | 20-character entity/schema/client path | Migration/schema drift across H2 and MariaDB |
| 3. Browser contract matrix | Evidence for all vehicle-event CRUD flows | Unseen issue must stay narrowly scoped |

**Prerequisites:** S-01 delivered; Java 17; a disposable local MariaDB and the built WAR.
**Estimated effort:** ~2–3 sessions across 3 phases.

## Open Risks & Assumptions

- Further event defects may exist; they will be fixed only when Playwright reproduces them.
- The management endpoint remains public by existing policy; contributor output must be minimal.
- Generated `.playwright-mcp/` evidence remains untracked.

## Success Criteria (Summary)

- No startup application-profile exception in the real client.
- A 20-character licence plate persists and displays through the normal UI flow.
- Browser CRUD evidence covers vehicle and every event type, with focused regression tests for each
  real server compatibility correction.
