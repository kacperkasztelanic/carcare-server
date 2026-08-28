# Admin Surface Parity Implementation Plan

## Overview

Complete the S-02 administrative-surface slice by pinning the current user, authority, audit,
lookup, test-data, and reminder contracts with full-context integration tests. The implementation
also intentionally corrects four confirmed API defects: three malformed creation `Location`
headers and the unusable reminder-advance DELETE path binding. These corrections are an explicitly
authorized exception to S-02's “unchanged” wording and supersede the preserve-only guidance for
these four defects; all other parity behavior remains unchanged.

## Current State Analysis

The seven scoped resources already retain the known-good `6e19b96` business behavior after the
Spring Boot 3 migration. Existing coverage is concentrated in `UserResourceIT` and
`AuditResourceIT`, while lookup maintenance, reminder advances, test-data routes, and reminder
selection have no direct resource-level parity suite.

The server exposes 22 handlers across two kinds of administrative surface. User management and
audits are consumed by the frozen client; lookup/configuration reads are also used by ordinary
authenticated workflows. Lookup mutations, test-data generation, and manual reminder dispatch are
operational APIs with no current client UI.

Two captured reminder references are present but unused. `SessionFixtures.seedGoldenDataset()`
already provides an H2-safe mirror of the captured SQL dataset, so reminder parity can reuse that
fixture rather than loading high-range SQL ids or transcribing expected calls.

## Desired End State

Every scoped handler has deterministic full-context coverage for its stable response body, status,
headers, authorization boundary, and persistence effect. Empty pagination is pinned to the
accepted `page=0` behavior, alert-header namespaces remain intentionally split, and lookup reads
remain available to authenticated non-admin users.

Fuel type, insurance type, and reminder-advance creation responses return canonical item
Locations. Reminder-advance DELETE binds `days` consistently and returns its intended 200/deletion
header or 404 behavior. Both reminder golden fixtures are consumed against the fixed
`2026-04-15` reference date, and the complete Maven verification suite remains green.

### Key Discoveries:

- User mutations deliberately emit `X-carcare-*` while lookup/configuration mutations emit
  `X-carcareApp-*`; these namespaces must not be unified
  (`src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java:19`).
- User list/detail and lookup/configuration GETs are authenticated-user surfaces, while mutations
  rely on method-level ADMIN checks
  (`src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:28`).
- Empty pagination's `last` link clamps to `page=0`, an accepted migration correction rather than
  an accidental baseline drift
  (`src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java:18`).
- The frozen client only reads the canonical lookup collection routes and does not consume their
  creation Locations or mutation routes
  (`../client/src/main/webapp/app/modules/carcare/vehicle/vehicle.reducer.ts:203`).
- `SessionFixtures` already owns the generated-id-safe golden dataset and reference date
  (`src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:37`).
- Reminder selection is synchronous up to the typed `MailService` calls even though actual mail
  delivery is asynchronous
  (`src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java:41`).

## What We're NOT Doing

- Extracting services from lookup or reminder-advance controllers.
- Adding or changing database schema, Liquibase changelogs, repositories, or entity validation.
- Moving lookup reads behind ADMIN or changing the established security matrix.
- Unifying `X-carcare-*` and `X-carcareApp-*` headers or changing live error headers.
- Adding lookup/configuration detail GET endpoints; corrected item Locations may still return 405
  when followed with GET.
- Adding update endpoints, sorting, or pagination to lookup/configuration lists.
- Characterizing unstable duplicate, null, referenced-delete, repeated-population, negative-day, or
  oversized-random-count persistence failures.
- Making test-data population idempotent or random generation deterministic.
- Adding UI for lookup maintenance, test-data generation, or manual reminder dispatch.
- Redesigning reminder idempotency, retries, locking, sent markers, scheduling, timezone policy, or
  mail-delivery observability.
- Changing audit date-boundary semantics, including the inclusive next-midnight behavior.

## Implementation Approach

Use the established application `MockMvc` boundary throughout so the tests exercise the real
filter chain, ObjectMapper, exception advice, transactions, and method-security proxies. Extend the
two existing resource ITs where ownership is already clear, and add focused IT classes for
lookup/configuration, test-data operations, and reminder selection instead of building one
monolithic admin test.

Keep the approved route corrections in the resources that already own URI construction. Introduce
a single Spring-managed `Clock` defaulting to `Clock.systemDefaultZone()` and have
`ReminderServiceImpl` derive its current date from that clock. This preserves runtime behavior
while giving the full-path reminder test a deterministic, scoped seam without static mocking.

Reuse `SessionFixtures.seedGoldenDataset()` and the existing `MailService` mock context pattern.
Compare captured typed invocations by symbolic owner, vehicle, and event handles so generated H2
ids and repository order cannot affect parity.

## Critical Implementation Details

### Route compatibility

The corrected Locations are `/api/fuel-type/{type}`, `/api/insurance-type/{type}`, and
`/api/reminder-advance/{days}`. This is an intentional API-contract correction for unknown external
consumers; status, body, alert headers, uppercase type normalization, and all other mutation
semantics remain unchanged.

### Persisted principals

Locale and owner-dependent tests must authenticate as the seeded usernames `admin` or `user`, not
merely attach an authority to the default mock principal. Persist and flush the intended language
key, then evict the login cache before asserting localized output.

### Reminder determinism

The production clock must retain the JVM system-default zone to match today's `LocalDate.now()`.
Golden tests replace only that clock, seed data inside the rollback transaction, reset mail mock
interactions between the typed and full-path cases, normalize actual calls to symbolic handles,
and compare all six calls without depending on repository iteration order.

## Phase 1: User and Audit Contracts

### Overview

Strengthen the two client-facing administration suites without changing production behavior. This
phase pins complete payloads, exact success headers, pagination links, security boundaries, and the
accepted empty-page behavior before broader operational coverage is added.

### Changes Required:

#### 1. User administration integration coverage

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java`

**Intent**: Extend the established full-context suite to cover every client-consumed field and
controller-owned response contract that is currently implicit.

**Contract**: Pin create 201 with `Location: /api/users/{login}`, raw `User` body,
`X-carcare-alert: userManagement.created`, encoded `X-carcare-params`, activated/generated-password
semantics, and one `sendCreationEmail` call. Pin update 200 with complete `UserDto` and
`userManagement.updated` headers; list/detail fields including authorities and audit metadata;
nonempty and empty `X-Total-Count`/`Link` headers with empty `last page=0`; deletion 204 headers for
both existing and nonexistent logins; anonymous 401; and USER 403 for POST, PUT, DELETE, and
authorities while retaining USER list/detail access.

#### 2. Audit integration coverage

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java`

**Intent**: Turn the existing audit smoke tests into an exact client-facing paging and payload
contract.

**Contract**: Assert `timestamp`, `principal`, `type`, and `data` for list and detail responses;
nonempty and empty `X-Total-Count`; `first`/`last` and applicable `next`/`prev` links; accepted empty
`page=0`; and retention/encoding of `page`, `size`, `sort`, `fromDate`, and `toDate` query parameters.
Retain ADMIN 200/404, USER 403, anonymous 401, current default-zone date conversion, and inclusive
upper-bound semantics.

### Success Criteria:

#### Automated Verification:

- Targeted user and audit integration verification passes with JDK 17 and the required Byte Buddy
  agent: `export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem && ./mvnw verify -Dit.test=UserResourceIT,AuditResourceIT -DargLine='-javaagent:/Users/kacper/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.5/byte-buddy-agent-1.14.5.jar -Djava.security.egd=file:/dev/./urandom -Xmx256m -Duser.timezone=UTC'`.
- The modified test sources pass whitespace validation: `git diff --check`.

#### Manual Verification:

- With the frozen client served, an ADMIN can open `/admin/user-management`, page through users,
  view a user, and complete create/update/delete flows without response-shape regressions.
- An ADMIN can open `/admin/audits`, apply date filters, and page/sort the results without count or
  rendering regressions.

**Implementation Note**: After automated verification, pause for confirmation that both browser
smokes passed before proceeding.

---

## Phase 2: Lookup and Configuration Routes

### Overview

Correct the approved URI defects and add deterministic full-context coverage for fuel types,
insurance types, and reminder advances, including localization, exact alert headers, authorization,
and repository effects.

### Changes Required:

#### 1. Canonical fuel-type creation Location

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/FuelTypeResource.java`

**Intent**: Make the creation response identify the resource under its actual hyphenated route
without altering persistence or response semantics.

**Contract**: `POST /api/fuel-type` continues to uppercase the type and return 201, the uppercase
string body, and `X-carcareApp-*` creation headers; only Location changes from
`/api/fuelType/{TYPE}` to `/api/fuel-type/{TYPE}`.

#### 2. Canonical insurance-type creation Location

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/InsuranceTypeResource.java`

**Intent**: Align the creation Location with the resource's actual hyphenated item route.

**Contract**: `POST /api/insurance-type` retains uppercase type normalization, 201 string body, and
`X-carcareApp-*` creation headers; only Location changes from `/api/insuranceType/{TYPE}` to
`/api/insurance-type/{TYPE}`.

#### 3. Canonical reminder-advance routes

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/ReminderAdvanceResource.java`

**Intent**: Correct reminder creation's unrelated insurance Location and make the existing DELETE
handler reachable.

**Contract**: `POST /api/reminder-advance/{days}` returns Location
`/api/reminder-advance/{days}` while retaining its 201 integer body and creation headers.
`DELETE /api/reminder-advance/{days}` binds the template and parameter consistently, returning 200
empty plus deletion headers when present and 404 empty when absent.

#### 4. Lookup/configuration integration suite

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/LookupMaintenanceResourceIT.java`

**Intent**: Add one transactional full-context suite for the three repository-backed resources,
sharing principal, locale, and cleanup setup while keeping operational test-data/reminder behavior
out of this class.

**Contract**: Cover authenticated English and Polish GET responses with
`{type, translation}`/integer arrays and `X-Total-Count`; ADMIN POST status, canonical Location,
body, uppercase normalization, exact `X-carcareApp-alert`/`X-carcareApp-params` values, and persisted
row; fuel/insurance DELETE 200/header/state and 404; reminder DELETE 200/header/state and 404; USER
403 for every mutation with unchanged counts; USER 200 for reads; and representative anonymous 401.
Do not assert ordering or unstable persistence-exception bodies.

### Success Criteria:

#### Automated Verification:

- Targeted lookup/configuration integration verification passes with JDK 17 and the required Byte
  Buddy agent: `export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem && ./mvnw verify -Dit.test=LookupMaintenanceResourceIT -DargLine='-javaagent:/Users/kacper/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.5/byte-buddy-agent-1.14.5.jar -Djava.security.egd=file:/dev/./urandom -Xmx256m -Duser.timezone=UTC'`.
- The canonical Locations and reminder DELETE behavior are asserted by enabled tests, with no
  disabled characterization placeholder.
- Production and test changes pass whitespace validation, including the new Phase 2 source:
  `git add --intent-to-add src/test/java/com/kasztelanic/carcare/web/rest/LookupMaintenanceResourceIT.java && git diff --check`.

---

## Phase 3: Operational Endpoints and Reminder Selection

### Overview

Cover the side-effecting test-data API and the complete reminder-dispatch path. This phase consumes
both captured reminder fixtures and finishes with targeted and full-suite verification.

### Changes Required:

#### 1. Test-data resource integration suite

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/TestDataResourceIT.java`

**Intent**: Pin the stable, deterministic parts of the production test-data surface without
blessing one-shot failures or random field values.

**Contract**: In separate rollback tests, assert ADMIN population returns `true` and adds exactly
seven fuel types or three insurance types. For random vehicles, populate fuel types once, request
one vehicle, assert `true`, a count delta of one, and owner `admin` without asserting template or
random identifiers. Assert USER 403 and representative anonymous 401 for all three routes with
repository counts unchanged.

#### 2. Application clock seam

**File**: `src/main/java/com/kasztelanic/carcare/config/ClockConfiguration.java`

**Intent**: Provide a single injectable source of calendar time for deterministic reminder
selection while preserving the current runtime zone semantics.

**Contract**: Expose one Spring `Clock` bean backed by `Clock.systemDefaultZone()`. No property,
schema, scheduling, or timezone-policy change is introduced.

#### 3. Reminder service clock usage

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java`

**Intent**: Replace the hard-coded current-date lookup with the configured clock and leave
selection, query, ordering, logging, scheduling, and typed mail dispatch unchanged.

**Contract**: `sendReminders()` derives `now` with `LocalDate.now(clock)`, calculates configured
advance dates, and calls the same three typed methods. Match the class's established dependency
injection style and do not refactor unrelated fields or loops.

#### 4. Reminder golden selection suite

**File**: `src/test/java/com/kasztelanic/carcare/golden/ReminderSelectionParityIT.java`

**Intent**: Consume both orphaned reminder references at the actual service/resource seam and prove
that ADMIN dispatch selects the same six typed mail calls as the captured baseline.

**Contract**: Reuse `SessionFixtures.seedGoldenDataset()` and
`SessionFixtures.GOLDEN_REFERENCE_DATE`, replace the clock with a fixed
`2026-04-15` system-compatible zone, and mock `MailService`. For
`golden/reminders/typed-seam.json` call the three explicit-date service methods; for
`golden/reminders/full-path.json` call `GET /api/reminder/send` as ADMIN and assert 200 empty. In
both cases normalize captured arguments to event type, owner login/language, symbolic vehicle/event
handle, due date, and day difference; compare all six entries order-independently; assert no extra
mail calls. Also assert USER 403 and anonymous 401 do not dispatch.

#### 5. Existing golden fixture reuse

**Files**:

- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`
- `src/test/resources/golden/reminders/full-path.json`
- `src/test/resources/golden/reminders/typed-seam.json`

**Intent**: Reuse the committed dataset mirror and captured references as authoritative inputs,
adding only the smallest test-side handle lookup support if argument normalization cannot be
expressed cleanly in the new test.

**Contract**: Do not rewrite expected JSON, load the high-id SQL fixture into H2, or duplicate the
golden dataset. Any added fixture helper must remain test-profile-only, preserve existing
`GOLDEN_HANDLES`, and return generated-id-safe mappings.

### Success Criteria:

#### Automated Verification:

- Targeted operational and reminder verification passes with JDK 17 and the required Byte Buddy
  agent: `export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem && ./mvnw verify -Dit.test=TestDataResourceIT,ReminderSelectionParityIT -DargLine='-javaagent:/Users/kacper/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.5/byte-buddy-agent-1.14.5.jar -Djava.security.egd=file:/dev/./urandom -Xmx256m -Duser.timezone=UTC'`.
- Both reminder JSON resources are loaded and compared by enabled tests, each proving exactly six
  typed mail invocations.
- The complete unit and integration suite passes with JDK 17 and the required Byte Buddy agent:
  `export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem && ./mvnw verify -DargLine='-javaagent:/Users/kacper/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.5/byte-buddy-agent-1.14.5.jar -Djava.security.egd=file:/dev/./urandom -Xmx256m -Duser.timezone=UTC'`.
- The complete change passes whitespace validation, including all planned-new sources:
  `git add --intent-to-add src/test/java/com/kasztelanic/carcare/web/rest/LookupMaintenanceResourceIT.java src/test/java/com/kasztelanic/carcare/web/rest/TestDataResourceIT.java src/test/java/com/kasztelanic/carcare/web/rest/golden/ReminderSelectionParityIT.java src/main/java/com/kasztelanic/carcare/config/ClockConfiguration.java && git diff --check`.

---

## Testing Strategy

### Unit Tests:

- No new isolated controller unit tests; the contracts depend on security proxies, persistence,
  configured JSON, transactions, and HTTP headers.
- If reminder-call normalization is extracted into a pure test helper, add focused unit coverage
  only for handle resolution or fixture parsing that cannot be made obvious in the IT.

### Integration Tests:

- Use `@SpringBootTest` plus `@AutoConfigureMockMvc`, never standalone MockMvc.
- Keep every database-mutating case transactional and rollback-safe.
- Authenticate with persisted logins where resources resolve current-user locale or ownership.
- Verify status, content type/body, exact controller-owned headers, and repository effects together.
- Assert method-security denials leave repository state and mail interactions unchanged.
- Compare reminder calls by symbolic handles and order-independent value equality.

### Manual Testing Steps:

1. Run the application with the frozen client artifact and log in as ADMIN.
2. Open `/admin/user-management`; verify list count/paging, detail rendering, create, update, and
   delete.
3. Open `/admin/audits`; apply a date range, sort/page the list, and open an audit entry if the UI
   exposes detail.
4. Confirm ordinary authenticated workflows still load fuel types, insurance types, and reminder
   advances.

## Performance Considerations

Runtime impact is limited to one singleton `Clock` lookup during reminder dispatch; repository
queries and mail fan-out are unchanged. Group full-context ITs by coherent context shape and reuse
the existing `MailService` mock pattern to limit additional Spring context starts. Keep golden
comparison in memory over six calls and avoid browser automation for server-only operational APIs.

## Migration Notes

No database or data migration is required. The three Location corrections and reminder DELETE
repair are the explicitly authorized S-02 compatibility exception and intentional API behavior
changes; rollback consists of reverting those four controller-owned route strings/bindings.
Unknown external scripts that assert malformed Locations or expect reminder DELETE to fail may
need adjustment, while client 1.2.5 is unaffected because it does not consume those mutations.

## References

- Related research: `context/changes/admin-surface-parity/research.md`
- Roadmap slice: `context/foundation/roadmap.md:329`
- Security boundary: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:28`
- User resource: `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java:95`
- Audit resource: `src/main/java/com/kasztelanic/carcare/web/rest/AuditResource.java:26`
- Lookup resources: `src/main/java/com/kasztelanic/carcare/web/rest/FuelTypeResource.java:32`,
  `src/main/java/com/kasztelanic/carcare/web/rest/InsuranceTypeResource.java:32`,
  `src/main/java/com/kasztelanic/carcare/web/rest/ReminderAdvanceResource.java:25`
- Operational resources: `src/main/java/com/kasztelanic/carcare/web/rest/TestDataResource.java:16`,
  `src/main/java/com/kasztelanic/carcare/web/rest/ReminderResource.java:11`
- Reminder selection: `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java:41`
- Golden dataset builder: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:37`
- Reminder references: `src/test/resources/golden/reminders/full-path.json`,
  `src/test/resources/golden/reminders/typed-seam.json`
- Frozen client lookup consumers:
  `../client/src/main/webapp/app/modules/carcare/vehicle/vehicle.reducer.ts:203`,
  `../client/src/main/webapp/app/modules/carcare/insurance/insurance.reducer.ts:177`,
  `../client/src/main/webapp/app/modules/carcare/events/events.reducer.ts:74`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: User and Audit Contracts

#### Automated

- [x] 1.1 Targeted user and audit integration verification passes — 5c38b47
- [x] 1.2 Modified test sources pass whitespace validation — 5c38b47

#### Manual

- [x] 1.3 Frozen-client user management smoke passes — 5c38b47
- [x] 1.4 Frozen-client audit history smoke passes — 5c38b47

### Phase 2: Lookup and Configuration Routes

#### Automated

- [x] 2.1 Targeted lookup/configuration integration verification passes — d9af05b
- [x] 2.2 Canonical Locations and enabled reminder DELETE behavior are asserted — d9af05b
- [x] 2.3 Production and test changes pass whitespace validation — d9af05b

### Phase 3: Operational Endpoints and Reminder Selection

#### Automated

- [x] 3.1 Targeted operational and reminder verification passes
- [x] 3.2 Both reminder references prove exactly six typed mail invocations
- [x] 3.3 Complete unit and integration verification passes
- [x] 3.4 Complete change passes whitespace validation
