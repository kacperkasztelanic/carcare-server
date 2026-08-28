---
date: 2026-08-28T16:52:02+02:00
researcher: Codex
git_commit: 0681ba36cce3d817a5bd54c1e540534eec0a3a2d
branch: refactor
repository: carcare-server
topic: "Implementation requirements for admin-surface-parity"
tags: [research, codebase, admin, security, rest, parity]
status: complete
last_updated: 2026-08-28
last_updated_by: Codex
---

# Research: Implementation requirements for admin-surface-parity

**Date**: 2026-08-28T16:52:02+02:00

**Researcher**: Codex

**Git Commit**: 0681ba36cce3d817a5bd54c1e540534eec0a3a2d

**Branch**: refactor

**Repository**: carcare-server

## Research Question

Research whatever is needed to implement `admin-surface-parity` according to the codebase.

## Summary

This should be a test-focused parity change. The scoped controllers and services retain the
`6e19b96` baseline logic; their production diffs are mechanical Jakarta imports, replacement of
the three removed JHipster web utilities, and the Spring Security 6 mechanism migration. No current
evidence calls for a production refactor.

The slice contains 22 handlers across seven resources:

- user CRUD and authority listing;
- audit list, date-filtered list, and detail;
- fuel type, insurance type, and reminder-advance maintenance;
- deterministic lookup population and nondeterministic vehicle generation;
- manual reminder dispatch.

Implementation should strengthen the existing full-context `UserResourceIT` and `AuditResourceIT`,
then add one or more full-context admin-support integration tests. Those tests must pin exact
status, payload, pagination, headers, and role behavior while preserving legacy quirks instead of
repairing them in a parity slice, except for the four API corrections explicitly authorized by
the admin-surface-parity plan.

Four rules are easy to violate:

1. There are two intentional alert-header families. `UserResource` emits baseline
   `X-carcare-*`; lookup/reminder-advance mutations emit `X-carcareApp-*`.
2. User list/detail and all three lookup/config reads are available to any authenticated user.
   Only their mutation paths are ADMIN-only.
3. Empty-page pagination now emits `page=0`, not baseline JHipster's `page=-1`. This was an
   explicitly accepted migration fix and should be recorded and pinned, not silently reverted.
4. Several malformed `Location` values and the broken reminder-advance DELETE are baseline debt.
   The admin-surface-parity plan explicitly authorizes correcting these four mutation contracts;
   this is the exception to the preserve/characterize rule for this slice. Other legacy quirks
   remain characterization-only.

## Detailed Findings

### Scope and ownership

The roadmap defines S-02 narrowly as preserving user/authority administration, audit history,
lookup maintenance, test-data generation, and reminder dispatch. It explicitly rejects service
extraction and removal of the production test-data surface from this change
([roadmap.md:329](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/context/foundation/roadmap.md#L329-L350)).

The production diff from `6e19b96` confirms that boundary:

- `FuelTypeResource`, `InsuranceTypeResource`, `ReminderAdvanceResource`, and
  `TestDataResource`: only `javax.transaction` to `jakarta.transaction`.
- `AuditResource`: only JHipster utility imports to local utility imports.
- `UserResource`: the same utility-import change, `javax.validation` to `jakarta.validation`,
  and `${jhipster.clientApp.name}` to `${spring.application.name}`; both names resolve to
  `carcare`.
- `ReminderResource`, `UserService`, `FuelAndInsuranceTypePopulatorImpl`,
  `RandomDataServiceImpl`, and `ReminderServiceImpl`: no behavioral diff.

Therefore production edits should occur only if an enabled parity test demonstrates an actual
migration regression.

### Authorization matrix

Method security is enabled, `/api/**` requires authentication, and `/management/**` requires
ADMIN except health/info/prometheus
([SecurityConfiguration.java:28](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java#L28-L95)).

| Surface | Anonymous | ROLE_USER | ROLE_ADMIN |
|---|---:|---:|---:|
| `GET /api/users`, `GET /api/users/{login}` | 401 | 200/404 | 200/404 |
| `POST/PUT/DELETE /api/users`, `GET /api/users/authorities` | 401 | 403 | baseline result |
| all `/management/audits` routes | 401 | 403 | 200/404 |
| lookup/reminder-advance GETs | 401 | 200 | 200 |
| lookup/reminder-advance POST/DELETE | 401 | 403 | baseline result |
| all `/api/test-data/**` routes | 401 | 403 | baseline result |
| `GET /api/reminder/send` | 401 | 403 | 200 empty |

Do not move the lookup GETs or user reads behind ADMIN. The normal client fetches fuel types,
insurance types, and reminder advances for authenticated user workflows.

Existing tests already prove representative filter-level 401 and management 403 behavior. New
tests should concentrate on each method-level ADMIN annotation that could independently disappear:
user PUT/DELETE/authorities, lookup mutations, test data, and reminder dispatch.

### User and authority administration

The full contract is implemented in
[UserResource.java:95](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java#L95-L195):

| Route | Preserved response contract |
|---|---|
| `POST /api/users` | 201, raw `User` body, `Location: /api/users/{login}`, `X-carcare-alert: userManagement.created`, encoded `X-carcare-params` |
| `PUT /api/users` | 200 + `UserDto` + `userManagement.updated`; 400 for duplicate login/email; 404 for an unknown id |
| `GET /api/users` | JSON array page content, `X-Total-Count`, RFC-style `Link` |
| `GET /api/users/authorities` | JSON string array containing `ROLE_USER` and `ROLE_ADMIN` |
| `GET /api/users/{login}` | full `UserDto`, or 404 |
| `DELETE /api/users/{login}` | 204 + `userManagement.deleted`, including nonexistent login |

Important service semantics are also baseline behavior:

- admin creation ignores the incoming password and activation flag, generates activation data,
  and creates an activated user;
- update changes activation and authorities but ignores the password;
- unknown authority strings are silently dropped;
- lists exclude `anonymousUser`.

`UserResourceIT` already covers successful CRUD, duplicate/id failures, list/detail/404, authority
membership, USER reads, anonymous list 401, and one USER mutation 403. It does not assert Location,
success headers, pagination headers, full client-consumed response shape, deletion of a nonexistent
login, or USER denial for PUT/DELETE/authorities
([UserResourceIT.java:50](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java#L50-L629)).

### Audit history

[AuditResource.java:26](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/AuditResource.java#L26-L84)
provides:

- `GET /management/audits`: paged/sorted array plus `X-Total-Count` and `Link`;
- the same route with both `fromDate` and `toDate`: start-of-day boundaries in the JVM default
  zone, with the upper boundary advanced by one day;
- `GET /management/audits/{id}`: `{timestamp, principal, type, data}` or 404.

The Spring Data `Between` predicate is inclusive, so an event exactly at midnight after `toDate`
is also included. This and the use of `ZoneId.systemDefault()` are baseline semantics, not cleanup
targets.

`AuditResourceIT` already covers list/detail/date/empty/404 and ADMIN/USER/anonymous access, but it
only asserts one payload field and an empty `X-Total-Count`. Add full response shape, nonempty count,
sort/page behavior, and `Link` assertions that retain date and sort query parameters
([AuditResourceIT.java:29](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java#L29-L147)).

### Header and pagination contracts

The split header namespace is intentional:

- User create/update/delete call `HeaderUtil.createAlert(applicationName, ...)`, producing
  `X-carcare-alert` and `X-carcare-params`.
- Fuel type, insurance type, and reminder-advance mutations call the entity helpers, producing
  `X-carcareApp-alert` and `X-carcareApp-params` with values such as
  `carcareApp.fuel-type.created`.

The distinction lives in
[HeaderUtil.java:19](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java#L19-L79).
The client notification middleware recognizes only names ending in `app-alert`, `app-params`, and
`app-error`. Consequently UserResource success responses do not produce a toast, while lookup
mutation headers would; the client has no UI for those mutations. The UserResource silence and
live error fallback to `data.message` are baseline behavior, not defects to repair in S-02
([client notification middleware](https://github.com/kacperkasztelanic/carcare-client/blob/67a82dd59f36a60176f85eb87b178d967cb07f03/src/main/webapp/app/config/notification-middleware.ts#L27-L83)).

The local `ResponseUtil.wrapOrNotFound` is behavior-equivalent to JHipster 7.8.1. The URI-aware
pagination helper is also equivalent for nonempty pages, including query-parameter retention and
comma/semicolon encoding
([PaginationUtil.java:18](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java#L18-L81)).

One deliberate divergence exists: baseline JHipster generated an empty page's `last` link with
`page=-1`; current code clamps it to `page=0`. The migration implementation review explicitly
accepted that correction
([impl-review.md:288](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/context/archive/jakarta-platform-migration/reviews/impl-review.md#L288-L298)).
The client consumes only `x-total-count`, not `Link`, so this is client-invisible. The recommended
test pins current `page=0` and labels it as an accepted historical deviation.

### Lookup and reminder-advance maintenance

All three resources bypass a service layer by design. Do not extract services in this change.

#### Fuel types

[FuelTypeResource.java:32](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/FuelTypeResource.java#L32-L88)
preserves:

- GET: localized `{type, translation}` array plus `X-Total-Count`, based on the persisted current
  user's language;
- POST: `{type, englishTranslation, polishTranslation}`, uppercase type, 201 string body,
  currently malformed `Location: /api/fuelType/{TYPE}`, creation headers. The
  admin-surface-parity exception corrects this to `/api/fuel-type/{TYPE}`.
- DELETE: case-sensitive lookup, 200 empty plus deletion headers, or 404.

#### Insurance types

[InsuranceTypeResource.java:32](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/InsuranceTypeResource.java#L32-L79)
has the same contract, with frozen malformed `Location: /api/insuranceType/{TYPE}`. The
admin-surface-parity exception corrects this to `/api/insurance-type/{TYPE}`. The intentional
bare-string `InsuranceTypeDto` creator belongs to insurance event requests, not this maintenance
request, and must not be copied to fuel types.

#### Reminder advances

[ReminderAdvanceResource.java:25](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/ReminderAdvanceResource.java#L25-L66)
preserves:

- GET: unsorted integer array plus `X-Total-Count`;
- POST: 201 integer body and the frozen wrong `Location: /api/insuranceType/{days}`; the
  admin-surface-parity exception corrects this to `/api/reminder-advance/{days}`;
- DELETE mapping `/{type}` paired with an unnamed `@PathVariable Integer days`; the
  admin-surface-parity exception repairs the binding so the intended delete operation is usable.

The DELETE variable-name mismatch is effectively broken and was already recorded as baseline debt.
The explicit scope decision in the admin-surface-parity plan authorizes repairing it and pinning the
intended behavior with an enabled test; it must not be generalized into unrelated controller cleanup.

All lookup columns are unique and non-null. There is no request validation or friendly duplicate
handling, and referenced fuel/insurance types are protected by foreign keys. Avoid duplicate,
null, negative, or referenced-delete cases in success-path parity tests unless their existing
failure behavior is being characterized deliberately.

### Test-data generation

All three routes are side-effecting GETs guarded at class level
([TestDataResource.java:16](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/TestDataResource.java#L16-L43)):

- `/api/test-data/populate-fuel-types` loads seven fixed rows;
- `/api/test-data/populate-insurance-types` loads three fixed rows;
- `/api/test-data/random-vehicles/{numberOfVehicles}` shuffles four templates, generates random
  identifiers and properties, and assigns every vehicle to the current persisted user.

The lookup loaders are one-shot, not idempotent: only resource/JSON I/O becomes boolean `false`;
database uniqueness failures escape. Run each once in a rollback transaction. Random generation
requires the seven fuel rows to exist first. For a one-vehicle success case, assert `true`, a count
delta of one, and owner identity; never assert the random template or generated strings.

Historical live-client trial evidence already confirms the two population routes return `true`
and persist 7/3 rows
([trial change.md:45](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/context/archive/2026-08-27-client-server-contract-trial/change.md#L45-L65)).

### Reminder dispatch

`GET /api/reminder/send` is ADMIN-only, synchronously invokes `ReminderService.sendReminders()`,
and returns 200 with an empty body
([ReminderResource.java:11](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/web/rest/ReminderResource.java#L11-L27)).
The service maps global reminder advances from `LocalDate.now()`, selects exact-date matches across
all owners, and invokes one typed mail method per matching insurance, inspection, or routine service
([ReminderServiceImpl.java:26](https://github.com/kacperkasztelanic/carcare-server/blob/0681ba36cce3d817a5bd54c1e540534eec0a3a2d/src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java#L26-L86)).

Mail sending is asynchronous, but selection and typed-method invocation are synchronous. Manual
retries can send duplicates; there is no sent marker, retry policy, distributed lock, or explicit
timezone. Those are accepted non-goals.

Two captured baseline selection fixtures already exist and are currently unused:

- `src/test/resources/golden/reminders/full-path.json`
- `src/test/resources/golden/reminders/typed-seam.json`

The preferred complete implementation consumes them in a focused reminder parity test using the
golden dataset, a test-only fixed `2026-04-15` date seam, and a mocked `MailService`. It should then
assert the six expected typed invocations and separately assert endpoint 200/empty plus USER 403.
If planning chooses only the smaller endpoint invocation test, it must explicitly record that the
captured selection reference remains unconsumed.

### Frozen client contract

Client 1.2.5 exposes only user management and audits under its ADMIN route. It does not expose UI
controls for lookup mutation, test-data generation, or manual reminder dispatch.

- User management calls `api/users`, `/authorities`, detail, create, update, and delete, and reads
  lowercase `x-total-count`
  ([user-management.reducer.ts:72](https://github.com/kacperkasztelanic/carcare-client/blob/67a82dd59f36a60176f85eb87b178d967cb07f03/src/main/webapp/app/modules/administration/user-management/user-management.reducer.ts#L72-L157)).
- Audits call `management/audits?page&size&sort&fromDate&toDate` and also read `x-total-count`
  ([administration.reducer.ts:101](https://github.com/kacperkasztelanic/carcare-client/blob/67a82dd59f36a60176f85eb87b178d967cb07f03/src/main/webapp/app/modules/administration/administration.reducer.ts#L101-L176)).
- Ordinary authenticated workflows read fuel types, insurance types, and reminder advances.

An optional browser smoke should therefore cover `/admin/user-management` and `/admin/audits`
only. The remaining contracts are direct API surfaces and belong in server integration tests.

### Recommended implementation shape

Use application `MockMvc`; do not reintroduce standalone controller tests.

1. Extend `UserResourceIT`:
   - assert create/update/delete Location and exact `X-carcare-*` headers;
   - assert complete client-consumed response fields;
   - assert nonempty and empty `X-Total-Count` plus `Link`;
   - add USER 403 for PUT, DELETE, and authorities;
   - characterize nonexistent delete as 204;
   - verify creation mail dispatch through the existing `@MockBean MailService`.
2. Extend `AuditResourceIT`:
   - assert `{timestamp, principal, type, data}`;
   - assert nonempty/empty count and `Link`;
   - prove date/sort query parameters survive into pagination links.
3. Add a transactional full-context admin-support IT:
   - fuel/insurance/reminder-advance GET and POST/DELETE contracts;
   - English and Polish lookup localization using persisted `admin` and `user` principals;
   - exact `X-carcareApp-*`, bodies, corrected canonical Locations, and 404s;
   - method-level USER 403 and authenticated read access;
   - one-shot 7/3 population counts;
   - one random vehicle count/owner invariant;
   - reminder endpoint role/status/invocation.
4. Prefer a separate reminder-selection parity IT consuming the two orphaned golden fixtures.
5. Run targeted ITs first, then the full suite.

Reuse the existing `@MockBean MailService` shape so Spring can reuse the context variant already
created by `AccountResourceIT` and `UserResourceIT`. Use the seeded login `admin` explicitly where a
resource resolves the persisted current user; an ADMIN authority attached to the default mock
principal is not enough for locale or owner-dependent behavior.

## Code References

- `context/foundation/roadmap.md:329-350` — S-02 outcome, risks, and non-goals.
- `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:28-95` — filter and method security.
- `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java:95-195` — user and authority routes.
- `src/main/java/com/kasztelanic/carcare/web/rest/AuditResource.java:26-84` — audit routes and pagination.
- `src/main/java/com/kasztelanic/carcare/web/rest/FuelTypeResource.java:32-88` — fuel lookup contract.
- `src/main/java/com/kasztelanic/carcare/web/rest/InsuranceTypeResource.java:32-79` — insurance lookup contract.
- `src/main/java/com/kasztelanic/carcare/web/rest/ReminderAdvanceResource.java:25-66` — reminder-advance contract and broken DELETE.
- `src/main/java/com/kasztelanic/carcare/web/rest/TestDataResource.java:16-43` — production test-data endpoints.
- `src/main/java/com/kasztelanic/carcare/web/rest/ReminderResource.java:11-27` — manual dispatch endpoint.
- `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java:26-86` — global selection and dispatch.
- `src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java:19-79` — split header families.
- `src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java:18-81` — pagination contract.
- `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java:50-629` — current user coverage.
- `src/test/java/com/kasztelanic/carcare/web/rest/AuditResourceIT.java:29-147` — current audit coverage.
- `src/test/resources/golden/reminders/full-path.json:1-54` — captured end-to-end selection.
- `src/test/resources/golden/reminders/typed-seam.json:1-54` — captured typed selection seam.
- `../client/src/main/webapp/app/modules/administration/user-management/user-management.reducer.ts:72-157` — client user calls.
- `../client/src/main/webapp/app/modules/administration/administration.reducer.ts:101-176` — client audit calls.

## Architecture Insights

- Authorization is deliberately split between broad filter rules and narrow method annotations.
  Tests need both layers; path inspection alone is insufficient.
- User and audit resources use services, while lookup/reminder-advance resources directly own
  repository operations. The asymmetry is known debt and part of the preserved architecture.
- Read-only lookup routes are shared user-facing configuration, not purely administrative routes.
- Full-context MockMvc is the established boundary because it exercises the real filter chain,
  configured ObjectMapper, exception advice, and method-security proxy.
- The stable client contract is narrower than the server's admin API: only users and audits are
  browser-exposed, while the other admin capabilities are direct operational endpoints.
- Golden reminder fixtures describe selection calls rather than rendered mail. `MailServiceIT`
  already owns rendering; selection parity belongs at the reminder service/resource seam.

## Historical Context (from prior changes)

- `context/archive/jakarta-platform-migration/` replaced the three JHipster utilities and migrated
  security. Its implementation review accepted empty-page `page=0` as a safe deviation.
- `context/archive/2026-08-25-test-context-restored/` converted User and Audit ITs to application
  MockMvc and established the ADMIN/USER/anonymous security pattern.
- `context/archive/2026-08-26-session-parity/` restored the fixed `X-carcareApp-*` family for ten
  business/lookup resources while intentionally leaving UserResource on baseline `X-carcare-*`.
- `context/archive/2026-08-27-golden-baseline-capture/` recorded malformed lookup Locations, broken
  reminder-advance DELETE, and reminder selection fixtures as baseline behavior.
- `context/archive/2026-08-27-client-server-contract-trial/` proved the two one-shot lookup loaders
  work against a clean MariaDB database.
- `context/archive/2026-08-28-english-reminder-fix/` fixed and tested mail rendering but deliberately
  did not consume the reminder selection fixtures.

## Related Research

- `context/archive/2026-08-25-test-context-restored/research.md`
- `context/archive/2026-08-26-session-parity/research.md`
- `context/archive/2026-08-27-golden-baseline-capture/research.md`
- `context/archive/2026-08-27-client-server-contract-trial/research.md`
- `context/archive/2026-08-28-english-reminder-fix/research.md`

## Open Questions

1. **Empty-page Link:** recommended resolution is to pin current `page=0` as the already accepted
   F-03 deviation. Reverting to baseline `page=-1` would undo an explicit reviewed fix for no client
   benefit.
2. **Reminder-advance DELETE:** the admin-surface-parity plan explicitly authorizes the production
   binding fix and an enabled test for the intended delete behavior; this exception is limited to
   the named route and does not authorize broader controller cleanup.
3. **Reminder goldens:** recommended resolution is to consume both fixtures in a dedicated selection
   IT. If implementation stays HTTP-only, record the unconsumed selection gap explicitly.
4. **Browser verification:** optional and limited to user management and audits; no frozen-client UI
   exists for the other admin operations.
