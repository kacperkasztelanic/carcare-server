---
date: 2026-08-27T11:06:18+02:00
commit: 0c443c1
branch: refactor
repository: carcare-server
topic: client-server-contract-trial
---

# Research: Trial and fix client-server contract issues

## Goal and boundary

Exercise the frozen React client 1.2.5 against the migrated server, fix only demonstrated
compatibility failures in this repository, and leave broader feature-parity work in its owning
roadmap slices. The client is a prebuilt Maven dependency, so server-side compatibility is the
available fix surface.

## Confirmed browser findings

The Phase 7 clean-MariaDB Playwright smoke (2026-08-27) logged in, listed/opened a vehicle, created
a valid vehicle, showed the expected `Vehicle added` toast, and created a repair. It also established
two actual UI/server contract failures:

1. An anonymous page load requests `/api/account` (the expected 401) and `/management/info`. The
   latter response lacks `activeProfiles`, but the client unconditionally calls
   `data.activeProfiles.includes(...)` in
   `../client/src/main/webapp/app/shared/reducers/application-profile.ts:19-25`. This throws a
   startup console error. `GET /management/info` is deliberately public in
   `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:81-84`; the server has
   no `InfoContributor` that supplies the legacy `activeProfiles` detail. The authentication 401
   itself is expected: all other `/api/**` routes require authentication
   (`SecurityConfiguration.java:79-80`) and the dedicated entry point produces Problem Details
   (`security/ProblemDetailAuthenticationEntryPoint.java:32-42`).
2. The client permits a 20-character licence plate on create
   (`../client/src/main/webapp/app/modules/carcare/vehicle/vehicle-create.tsx:111-120`) and edit
   (`vehicle-details-update.tsx:143-151`). A 11-character value was accepted by the form but the
   server returned 500. Server validation and the column both stop at 10 characters
   (`src/main/java/com/kasztelanic/carcare/domain/Vehicle.java:54-59`; original schema
   `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:182-190`). This is
   a live contract mismatch, not a reason to weaken error reporting. The frozen client is the
   compatibility target, so the prospective server fix is to widen the model and schema through a
   new Liquibase change rather than modify the historical changelog.

## Existing contract coverage

`session-parity` deliberately stopped after one vehicle and repair. Its handoff records the
successful path and the startup error in
`context/changes/session-parity/change.md:94-99`; its Phase 7 expressly assigns full event CRUD,
console hygiene, and fixes here (`context/changes/session-parity/plan.md:631-652`). The existing
server integration suite checks REST parity but cannot observe the bundled client’s assumptions.

The five event resources expose the relevant create endpoints: repair, routine service,
inspection, insurance, and refuel respectively (`src/main/java/com/kasztelanic/carcare/web/rest/*Resource.java`).
Their DTO-to-entity mapping is separate by event type, while the client applies its own unit and
string conversion before dispatch (for example,
`../client/src/main/webapp/app/modules/carcare/refuel/refuel.reducer.ts:125-170` and
`../client/src/main/webapp/app/modules/carcare/service/service.reducer.ts:125-170`). Therefore the
remaining forms must be exercised through the browser before a server change is selected.

The alert-header contract restored by S-01 is compatible with the client: it looks for headers
ending in `app-alert` / `app-params` on successful mutations
(`../client/src/main/webapp/app/config/notification-middleware.ts:18-38`). Error handling instead
falls back to the Problem Details `message` field when no matching error header exists
(`notification-middleware.ts:41-103`), so that behavior should be characterized while testing,
not broadened speculatively.

## Recommended plan shape

1. Add a server integration regression for `/management/info` that asserts an `activeProfiles`
   array and retain the endpoint’s anonymous accessibility. Implement the smallest legacy-profile
   `InfoContributor` using the active Spring environment. Re-run a browser anonymous-load check and
   require no application-profile exception.
2. Widen licence plates from 10 to 20 consistently in the entity and a new Liquibase changeset.
   Add a REST/integration regression that creates an 11–20 character plate successfully, then
   repeat the exact browser form path that previously 500ed.
3. On a disposable clean MariaDB, use Playwright to perform create, list/open, edit, and delete for
   vehicle plus repair, routine service, inspection, insurance, and refuel. Record each browser
   request/result and console exception. Fix only a failure reproduced in that run; otherwise add a
   narrow server characterization test where it protects a confirmed legacy-client payload.
4. Verify the focused Maven tests and `./mvnw verify`; preserve the browser run evidence in the
   change record. Do not add generated `.playwright-mcp/` artifacts to version control.

## Roadmap relationship

This is S-07, marked next in `context/foundation/roadmap.md:50-53,313-328`. It does not replace
F-02 `golden-baseline-capture`, which remains ready and provides historical reference values only
for report/reminder work (`roadmap.md:143-149,443`).

## Risks and decisions for planning

- The client accepts licence plates through 20 characters, while the production schema is 10;
  widening requires an additive Liquibase migration and H2-compatible verification.
- The management-info payload is a public, legacy-client interface. The contributor should expose
  only the active-profile list, preserving the existing `info.display-ribbon-on-profiles` property
  rather than exposing an arbitrary environment.
- Browser testing needs a clean MariaDB and pre-seeded fuel/insurance lookup values. The existing
  admin-only test-data endpoints are suitable setup support, but are not part of the user-session
  assertions.
