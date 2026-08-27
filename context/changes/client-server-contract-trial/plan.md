# Client-server contract trial Implementation Plan

## Overview

Restore the two browser-confirmed legacy-client contracts on the server, then complete a focused
Playwright trial of the remaining vehicle-event flows. The aim is a clean startup and a client form
that can persist every value it permits, without expanding this server-only change into unrelated
feature work.

## Current State Analysis

The frozen React 1.2.5 bundle is served from the WAR. It calls public `/management/info` during
startup and assumes an `activeProfiles` array, which Spring Boot 3 no longer supplies here. It also
allows licence-plate values up to 20 characters while the server model and schema cap them at 10;
the first 11-character browser submission produced a generic 500.

## Desired End State

An anonymous client load has no `applicationProfile` exception, and a logged-in user can create and
edit a licence plate of up to 20 characters. On a clean MariaDB-backed WAR, Playwright can complete
CRUD for vehicle, repair, routine service, inspection, insurance, and refuel; any further observed
server mismatch is either fixed with a regression test or explicitly recorded as a separate owning
slice.

### Key Discoveries:

- `application-profile.ts` dereferences `data.activeProfiles` without a guard
  (`../client/src/main/webapp/app/shared/reducers/application-profile.ts:19-25`), while
  `/management/info` is intentionally public (`SecurityConfiguration.java:81-84`).
- `Vehicle` and its original Liquibase table define a 10-character licence plate
  (`src/main/java/com/kasztelanic/carcare/domain/Vehicle.java:54-59`,
  `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:182-190`), in
  conflict with both client vehicle forms’ 20-character rules.
- The existing full-context security smoke and vehicle-resource integration test are the appropriate
  regression homes (`src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:20-53`,
  `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:29-152`).

## What We're NOT Doing

- Rebuilding or modifying the frozen sibling client.
- Changing authentication semantics: anonymous `/api/account` remains a Problem Details 401.
- Broad client-console cleanup unrelated to a reproduced client/server interface failure.
- Vehicle deletion with event history (owned by S-05), report comparisons (S-03/F-02), or reminder
  behavior (S-04/F-02).
- Guessing fixes for event forms before the browser trial reproduces a fault.

## Implementation Approach

Preserve the client’s legacy management payload with a narrowly scoped Spring Boot Actuator
`InfoContributor`; retain all current security policy. Make server validation and storage accept the
client’s documented licence-plate maximum through a new Liquibase migration. Prove both at the
server boundary, then use the real bundle and clean disposable MariaDB to drive every event flow.
The browser trial is evidence-led: only a captured mismatch earns a server change and corresponding
regression coverage.

## Critical Implementation Details

`/management/info` must expose the active profile names as a top-level `activeProfiles` detail and
the configured `info.display-ribbon-on-profiles` value as the exact top-level
`display-ribbon-on-profiles` detail. The custom contributor must emit both client fields explicitly;
do not enable broad environment-property exposure. Add the schema change as a new timestamped
Liquibase changelog and include it from `master.xml`; never edit the 2019 changeset.

## Phase 1: Restore the client startup profile contract

### Overview

Supply both legacy profile fields expected by the compiled client and pin the public management
endpoint behavior.

### Changes Required:

#### 1. Profile Info contributor

**File**: `src/main/java/com/kasztelanic/carcare/management/ProfileInfoContributor.java`

**Intent**: Add a small Actuator `InfoContributor` that obtains the active profile names and the
configured `info.display-ribbon-on-profiles` value from the Spring `Environment`, then contributes
them under the exact `activeProfiles` and `display-ribbon-on-profiles` keys.

**Contract**: `GET /management/info` returns a JSON array at `$.activeProfiles` and the configured
ribbon value at `$['display-ribbon-on-profiles']`. No credentials, unrelated `info.*` properties,
or arbitrary environment fields are exposed.

#### 2. Management security regression

**File**: `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java`

**Intent**: Extend the existing application-MockMvc security matrix with an anonymous
`/management/info` assertion for status 200, the active-profile array, and the configured ribbon
value.

**Contract**: The test must preserve the existing anonymous `/api/account` 401 assertion and verify
both restored client fields in the real Spring context.

### Success Criteria:

#### Automated Verification:

- `SecurityConfigurationIT` proves anonymous `/management/info` returns a non-empty
  `activeProfiles` array containing the active test profile and the configured
  `display-ribbon-on-profiles` value.
- `SecurityConfigurationIT` continues to prove anonymous `/api/account` returns the existing
  Problem Details 401 response.

#### Manual Verification:

- On a clean MariaDB-backed WAR, an anonymous browser load reaches the login route with no
  `applicationProfile` / `activeProfiles.includes` console exception.

---

## Phase 2: Align licence-plate persistence with the compiled forms

### Overview

Widen the server’s licence-plate model and database column to the 20-character limit the frozen
create and edit forms enforce.

### Changes Required:

#### 1. Entity length alignment

**File**: `src/main/java/com/kasztelanic/carcare/domain/Vehicle.java`

**Intent**: Change the Bean Validation and JPA column length for `licensePlate` from 10 to 20.

**Contract**: Vehicle create and edit accept any nonblank plate through 20 characters; longer values
remain invalid under the server model.

#### 2. Forward-only schema migration

**Files**: `src/main/resources/config/liquibase/changelog/<timestamp>_client_contract_changelog.xml`,
`src/main/resources/config/liquibase/master.xml`

**Intent**: Add and include a new timestamped `modifyDataType` changeset that widens
`vehicles.license_plate` to `VARCHAR(20)` for MariaDB and H2.

**Contract**: Existing plates remain unchanged, while the new database schema matches the entity and
the frozen client’s 20-character limit. The historical 2019 changelog stays untouched.

#### 3. Resource-level regression

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java`

**Intent**: Add an authenticated create (and, where practical, edit) case with an 11–20 character
plate using the existing `SessionFixtures` lookup setup.

**Contract**: The request returns its normal success status and alert header rather than a 500, and
the response preserves the full plate.

### Success Criteria:

#### Automated Verification:

- The focused vehicle resource test persists a 20-character licence plate under the H2/Liquibase
  test context.
- `./mvnw verify` passes with the new migration and no schema-validation drift.

#### Manual Verification:

- The exact browser create/edit path with an 11–20 character licence plate succeeds and shows the
  normal client toast.

---

## Phase 3: Complete the evidence-led browser contract trial

### Overview

Run the frozen bundle against a new clean MariaDB instance and close the remaining CRUD surface with
captured browser evidence instead of source-only assumptions.

### Changes Required:

#### 1. Disposable trial environment and test-data setup

**File**: none — runtime verification procedure recorded in
`context/changes/client-server-contract-trial/change.md`

**Intent**: Start the locally built WAR with an isolated MariaDB, authenticate as `admin/admin`,
invoke each admin-only fuel and insurance lookup-population endpoint exactly once and verify it
returns `true`, then authenticate as `user/user` for the browser trial. If seeding fails or must be
retried, recreate the disposable database first because the population operations are not
idempotent.

**Contract**: The browser uses the actual bundled client. Test-data setup is restricted to one-shot
lookup seeding under the admin session and is not mistaken for a user-flow assertion; all CRUD
assertions run under the seeded `user` account.

#### 2. Full vehicle-event flow matrix

**File**: none unless a reproduced mismatch requires a focused server regression/fix

**Intent**: With Playwright, perform vehicle create/list/detail-open/edit/delete (using an event-free
test vehicle). For repair, routine service, inspection, insurance, and refuel, create the event,
verify it in the list and details popover, open its edit form (exercising GET-by-ID), edit it, and
delete it. Capture response status, visible toast/state, and console exceptions for every mutation.

**Contract**: A server-side correction is made only after the browser run reproduces the failure. It
must include the smallest relevant full-context or resource integration regression and a rerun of
the exact browser step. A concern owned by another roadmap slice is recorded with evidence and left
unchanged.

#### 3. Trial record and repository hygiene

**File**: `context/changes/client-server-contract-trial/change.md`

**Intent**: Record the client version, date, environment, matrix results, all fixes, and any scoped
handoff. Ensure generated `.playwright-mcp/` artifacts are not staged.

**Contract**: The change record is independently sufficient to distinguish expected failures,
confirmed fixes, and deferred product work.

### Success Criteria:

#### Automated Verification:

- Each newly fixed browser/server defect has a targeted repeatable server regression test.
- The focused tests and final `./mvnw verify` are green.

#### Manual Verification:

- The clean-browser matrix completes each supported vehicle and event CRUD path without a server
  compatibility failure or unhandled client exception.
- The final change record includes reproducible evidence for every outcome.

---

## Testing Strategy

### Integration Tests:

- Extend `SecurityConfigurationIT` for both public management-info client fields and retain the
  existing 401 shape assertion.
- Extend `VehicleResourceIT` for 20-character create/edit persistence and alert behavior.
- Add tests adjacent to the resource involved only if Phase 3 reproduces an additional server
  contract defect.

### Manual Testing Steps:

1. Start the clean MariaDB-backed WAR and load the client anonymously; confirm the expected
   `/api/account` 401 is suppressed by the client and no application-profile exception appears.
2. Log in as `admin/admin`, call each lookup-population endpoint exactly once, and verify each
   returns `true`; if either call fails or must be retried, recreate the disposable database.
3. Log in as `user/user`, create and edit a 20-character-plate vehicle, then verify its list/detail
   representation and toast.
4. For each event type, create one valid entry, verify it in the list and details popover, open its
   edit form (exercising GET-by-ID), edit it, and delete it; verify its toast and returned state
   before continuing.
5. Use an event-free vehicle for vehicle deletion; record the existing event-history delete
   limitation as S-05-owned rather than treating it as a trial failure.

## Migration Notes

The licence-plate migration is widening-only and preserves existing values. Test it through the
normal H2 Liquibase path and a fresh MariaDB browser environment before considering production
deployment.

## References

- Research: `context/changes/client-server-contract-trial/research.md`
- Existing browser handoff: `context/changes/session-parity/change.md:94-99`
- Management security pattern: `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:20-53`
- Vehicle resource fixture pattern: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:29-152`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands.

### Phase 1: Restore the client startup profile contract

#### Automated

- [ ] 1.1 `GET /management/info` exposes both legacy profile fields anonymously
- [ ] 1.2 The existing anonymous `/api/account` Problem Details contract remains intact

#### Manual

- [ ] 1.3 An anonymous real-client load has no application-profile console exception

### Phase 2: Align licence-plate persistence with the compiled forms

#### Automated

- [ ] 2.1 The entity and a new Liquibase changeset widen licence plates to 20 characters
- [ ] 2.2 Authenticated vehicle create/edit persists a 20-character plate with its normal alert
- [ ] 2.3 `./mvnw verify` passes with the migration

#### Manual

- [ ] 2.4 The real-client create/edit form accepts and displays an 11–20 character plate

### Phase 3: Complete the evidence-led browser contract trial

#### Automated

- [ ] 3.1 Each extra confirmed server defect has focused regression coverage and final verification is green

#### Manual

- [ ] 3.2 The clean-browser CRUD matrix completes for vehicle and all five event types
- [ ] 3.3 The change record contains the trial evidence and any correctly scoped handoffs
