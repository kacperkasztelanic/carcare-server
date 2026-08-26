---
date: 2026-08-26T20:42:14+0000
researcher: Claude (Opus 5)
git_commit: 28298793466c42199d64440b97225e37eb64e5e9
branch: refactor
repository: carcare/server
topic: "S-01 session-parity: what must be proven, what actually moved, and what blocks the first test"
tags: [research, codebase, session-parity, owner-isolation, rest-contract, client-1.2.5, hibernate6, test-profile]
status: complete
last_updated: 2026-08-26
last_updated_by: Claude (Opus 5)
---

# Research: S-01 `session-parity`

**Date**: 2026-08-26T20:42:14+0000
**Researcher**: Claude (Opus 5)
**Git Commit**: `2829879`
**Branch**: `refactor`
**Repository**: `carcare/server` (roadmap item S-01, prerequisite F-04 archived 2026-08-26)

## Research Question

What must S-01 actually prove, and what stands in the way? Specifically: the REST contract
client 1.2.5 consumes, the owner-isolation guarantee, what the Jakarta/Boot 3 migration
changed on this surface, and what test foundation exists to build the first
business-behaviour tests this project has ever had.

**Scope decisions taken with the owner before research began:** include the sibling
`../client` as authoritative evidence; cover the S-01 surface plus the shared
statistics/report read paths (to prevent a coverage gap falling between S-01 and S-03);
and use a live probe rather than static reading alone.

## Summary

Five findings change how S-01 should be planned. The first two invert the roadmap's stated
risk model; the third is a hard blocker; the fourth is a confirmed production regression;
the fifth is a decision the slice cannot avoid.

1. **The migration never touched the S-01 surface.** All nine in-scope controllers and the
   entire `repository/` package are byte-identical to the last known-good pre-migration
   commit `6e19b96`. The roadmap and PRD both assume the migration "touches every one of
   those queries" — at source level that is false. S-01's real risk is behavioural drift
   *underneath* unchanged source.
2. **Owner isolation is structurally sound** — no reachable hole was found on any of the
   ~31 handlers. But it rests on one runtime-resolved bean (`SecurityEvaluationContextExtension`)
   that is not declared anywhere in `src/main` and has never been exercised by a test.
3. **BLOCKER: the test profile cannot execute a single query against any of the five event
   tables.** `hibernate.auto_quote_keyword: true`, added by F-04, makes every
   `VehicleEvent`-bearing query fail with `Column "R1_0.date" not found`. A validated
   two-line fix exists and is proven green against the full existing suite.
4. **CONFIRMED REGRESSION: the `X-carcareApp-*` → `X-carcare-*` header rename breaks client
   1.2.5**, on exactly the create/update/delete paths S-01 owns. F-03's implementation
   review accepted this rename on reasoning that conflated the header name with the i18n
   key namespace.
5. **`DELETE /api/vehicle/{id}` is broken for any vehicle with history** and S-01 must
   decide explicitly what "delete parity" means against an endpoint that never worked.

## Detailed Findings

### 1. The migration never touched this surface

`git diff 6e19b96 HEAD -- src/main/` over the S-01 surface returns **empty** for every one of:

- `web/rest/VehicleResource.java`, `RefuelResource.java`, `RepairResource.java`,
  `RoutineServiceResource.java`, `InspectionResource.java`, `InsuranceResource.java`,
  `EventResource.java`, `StatisticResource.java`, `ReportResource.java`
- the **entire** `repository/` package — all 13 `?#{principal.username}` queries unedited
- every vehicle/event service, mapper, and DTO (only `AuditEventService`, `MailService`,
  and `UserDto` differ anywhere in `service/`)

Domain entities changed by import line only — `domain/Vehicle.java`'s complete diff is nine
`javax.*` → `jakarta.*` imports.

**Consequence for planning.** Every parity risk on this surface is behavioural, not textual:
Hibernate 6 semantics over unchanged JPQL, the Spring Security 6 filter chain, Spring MVC 6
/ Jackson serialization, and the `ProblemDetail` error contract. A code-review-style diff
audit of the vehicle/event layer would find nothing, because there is nothing there. Tests
against a *running* context are the only instrument that can detect what moved.

The complete changed surface that can affect an S-01 session is therefore:
`config/SecurityConfiguration.java`, `config/JacksonConfiguration.java`,
`config/WebConfigurer.java`, `config/LocaleConfiguration.java` + new
`config/QuotedCookieLocaleResolver.java`, `security/jwt/*`, `web/rest/errors/*`,
`web/rest/util/*`, and the entity import lines.

### 2. Owner isolation — sound, with one untested dependency

No reachable isolation hole exists. Verified positively:

- All 13 owner-scoped queries constrain on `?#{principal.username}`
  (`repository/VehicleRepository.java:17,20,23` and the five event repositories).
- **Create cannot be hijacked.** `VehicleDto` has no owner field at all
  (`service/dto/VehicleDto.java:15-22`), and `VehicleServiceImpl.java:45` calls
  `setOwner(user)` unconditionally with the principal-derived `User`. All five event types
  resolve the parent vehicle through `findByIdAndOwnerIsCurrentUser` *first*
  (`RefuelServiceImpl.java:43-46` and four siblings), so user A cannot create an event
  against user B's `vehicleId`.
- **Create cannot become overwrite.** `Vehicle.java:38` and four of the five event entities
  declare `private final Long id` with no setter and no builder parameter. `Insurance.java:44`
  is the sole mutable-id outlier — not exploitable today (no mapper sets it) but the one
  place where a future edit would turn create into overwrite.
- **Update and delete both re-verify ownership** in all six services; no `update*` helper
  touches the ownership edge, so an event cannot be re-parented.
- Images carry no ownership check and need none: no endpoint serves an image by filename,
  filenames are UUID-generated server-side (`ImageStorageServiceImpl.java:35`), and bytes
  are inlined into `VehicleDetailsDto` behind owner-filtered loads.

**The one real fragility.** `?#{principal.username}` requires `SecurityEvaluationContextExtension`,
which is **not declared anywhere in `src/main`**. It arrives implicitly via Boot's
`SecurityAutoConfiguration` → `SecurityDataConfiguration`, with `spring-security-data` on the
classpath (`pom.xml:203`). If that implicit chain ever breaks, all 13 queries throw at runtime
and nothing in the suite would notice. **A single authenticated `GET /api/vehicle/all`
returning 200 is the highest-value assertion in the slice** — and my probe confirms it
resolves correctly today.

Secondary: `VehicleRichMapper.java:64-80` calls the five *unfiltered* `findByVehicleId`
variants, safe only because its `Vehicle` argument always arrives from an owner-filtered
load. Those five, plus the collection-valued `findAllByIdAndOwnerIsCurrentUser`, are
reachable **only** through statistics/reports/events — so if S-01 tests only
`/api/{type}/all/{id}`, they get zero coverage from anyone. **S-03 must own them explicitly.**

### 3. BLOCKER — the test profile cannot query any event table

**Observed, not inferred.** An authenticated `GET /api/refuel/all/{id}` against the delivered
test profile returns **500**:

```
org.h2.jdbc.JdbcSQLSyntaxErrorException: Column "R1_0.date" not found; SQL statement:
select r1_0.id,r1_0.cost_in_cents,r1_0.station,r1_0.vehicle_id,r1_0."date",
       r1_0.mileage,r1_0.volume_in_cm3 from refuels r1_0 join ...
```

**Root cause, established exactly.** The physical H2 schema is inconsistent because Liquibase
quotes reserved words and does not quote others:

| Column | Liquibase declaration | Physical H2 identifier | Hibernate must emit |
|---|---|---|---|
| `refuels.date` (and 4 siblings, from embedded `VehicleEvent`) | `<column name="date">` (`changelog/20190922082653_changelog.xml:36`) | `DATE` (unquoted → folded upper) | `date` **unquoted** |
| `jhi_persistent_audit_evt_data.value` | `<column name="value">` (`changelog/00000000000000_initial_schema.xml:114`) | `"value"` (quoted lower — Liquibase quotes it as reserved) | `"value"` **quoted** |

Hibernate's `auto_quote_keyword` is a single global flag and cannot satisfy both. F-04 set it
to `true` for the audit column (`application-test.yml:34`, with a comment explaining exactly
that), and in doing so broke all five event tables — which no test covered, so it went unseen.

Proven by probe, both directions:

| Config | Five event endpoints | Audit write |
|---|---|---|
| `auto_quote_keyword: true` (as delivered) | **500** `Column "R1_0.date" not found` | OK |
| `auto_quote_keyword: false` | 200 | **fails** `Syntax error … [*]value` |
| `false` + `NON_KEYWORDS=VALUE` | 200 | **fails** `Column "VALUE" not found` |
| `false` + `CASE_INSENSITIVE_IDENTIFIERS=TRUE` | 200 | **fails** syntax error |
| **`false` + `NON_KEYWORDS=VALUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`** | **200** | **OK** |

**Validated fix — test-profile only, no production change:**

```yaml
# src/test/resources/config/application-test.yml
url: jdbc:h2:mem:carcare;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
hibernate.auto_quote_keyword: false
```

I applied this and ran the **full existing suite: 115 integration tests, 0 failures, 0 errors,
BUILD SUCCESS**, then reverted it. The tree is clean; this is S-01's work to land, and it is
Phase 1 — no event test can be written before it.

MariaDB is unaffected (neither setting exists in main), but note the consequence: the test
profile now diverges from production on identifier handling in a *third* way, on top of the
two F-04 already documented.

### 4. CONFIRMED REGRESSION — the header rename breaks client 1.2.5

Client 1.2.5 matches alert headers by **case-insensitive suffix**, not by full name
(`../client/src/main/webapp/app/config/notification-middleware.ts:27-33,59-65`):

```ts
if (k.toLowerCase().endsWith('app-alert'))  { alert = v; }
else if (k.toLowerCase().endsWith('app-params')) { alertParams = v; }
// and, on 400:
if (k.toLowerCase().endsWith('app-error'))  { errorHeader = v; }
```

- Baseline emitted `X-carcareApp-alert` → lowercases to `x-carcareapp-alert` → ends with
  `app-alert` → **matches** (`6e19b96:web/rest/util/HeaderUtil.java:15,19-20`).
- HEAD emits `X-carcare-alert` → `x-carcare-alert` → ends with `care-alert` → **no match**
  (`HeaderUtil.java:23,40-41`, seeded from `spring.application.name: carcare` via
  `config/HeaderUtilInitializer.java:15-21`).

**This lands squarely inside S-01.** `VehicleResource`, `RefuelResource`, `RepairResource`,
`RoutineServiceResource`, `InspectionResource`, and `InsuranceResource` all call
`HeaderUtil.createEntityCreationAlert/UpdateAlert/DeletionAlert`
(`VehicleResource.java:56,64,74`; `RefuelResource.java:54,64,74`; siblings alike). Every
create, update, and delete on the user session surface silently loses its toast. Nothing
throws; on a 400 the client falls through to a different branch.

The client-tree version is authoritative: `package.json:3` is `1.2.5`, and
`git diff 1.2.5 HEAD -- src package.json` is empty.

**Note the irony worth carrying into the plan.** `HeaderUtil.java:26-31` carries a javadoc
warning that renaming `TRANSLATION_KEY_NAMESPACE` "silently breaks every alert toast on the
client" — and the *value* namespace was indeed carefully preserved as `carcareApp.vehicle.created`.
The header **name**, which is what the client actually matches on, was renamed anyway.
F-03's review (`archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md`, F2)
accepted the rename on the reasoning that `carcareApp` survives as an i18n namespace — true,
but a different contract from the one that broke.

Two fixes are available and the choice is the owner's: restore the header name server-side
(one line: decouple `HeaderUtil.applicationName` from `spring.application.name`), or ship a
client change. The PRD names client breakage as the pager event and freezes the client
(`prd.md:182-184`), which argues for the server-side fix.

### 5. Vehicle DELETE is broken for any vehicle with history

No `onDelete` attribute appears anywhere in any Liquibase changelog — all six FKs into
`vehicles` and the five event tables are non-cascading
(`changelog/20190922082653_changelog.xml:238,247,250,253,256`). `Vehicle` has no `@OneToMany`,
no `cascade`, no `orphanRemoval`, and `VehicleServiceImpl.java:63` issues a bare `deleteById`.
So `DELETE FROM vehicles WHERE id=?` runs against live child rows → `DataIntegrityViolationException`
→ no handler → **500 `application/problem+json`**. An event-free vehicle deletes fine (200 +
alert header).

This matches the PRD's own verified note (`prd.md:213-214`, `:559-561`).

**The decision S-01 owes.** "CRUD parity including delete" against an endpoint that never
worked has three honest readings: lock in the 500 as a characterization test; cover only the
working case and record the defect with a `@Disabled` placeholder; or fix it here. Fixing it
pre-empts S-05 `vehicle-archiving`, which exists precisely to replace hard delete. **Recommend
the middle option**, with the defect named in `change.md`.

A harness trap comes with it: under a class-level `@Transactional` IT the delete flushes at
*test rollback*, not during the request, so the FK violation may never surface as a 500 in the
`MvcResult`. The delete-with-events test must not be `@Transactional`.

### 6. The contract, as measured from a running context

84 handlers were dumped from a booted `RequestMappingHandlerMapping` (not inferred from
source); 31 are in S-01 scope. The five event types are perfectly regular:

```
POST   /api/{type}/{vehicleId}     201  + Location + X-carcare-alert
PUT    /api/{type}/{id}            200  + X-carcare-alert
DELETE /api/{type}/{id}            200  + X-carcare-alert, returns the deleted DTO
GET    /api/{type}/{id}            200  | 404 bodyless
GET    /api/{type}/all/{vehicleId} 200  + X-Total-Count (always 200, even for a foreign vehicle)
```

Probed behaviours worth pinning verbatim (all observed, status codes are real):

| Request | Observed |
|---|---|
| anonymous `GET /api/vehicle/all` | 401 `application/problem+json`, body carries `type`,`title`,`status`,`detail`,`message`,`path` |
| anonymous `GET /` | **200** (guards F-03's `.anyRequest().permitAll()` fix) |
| `GET /api/vehicle/{unknown}` | **404, empty body, no Content-Type** — not a ProblemDetail |
| `GET /api/vehicle/` (trailing slash) | **404** — Boot 3 `PathPatternParser` dropped trailing-slash matching |
| `GET /api/vehicle/abc` | 400 ProblemDetail, `detail: "Failed to convert 'id' with value: 'abc'"` |
| `GET /api/authenticate` authenticated | 200 `text/plain;charset=UTF-8`, body = login |
| `POST /api/vehicle` missing `fuelType` | **500** (NPE in `FuelTypeMapper`) — pre-existing, mapper unchanged since baseline |
| `POST /api/events` with duplicate `vehicleId` | **500** `IllegalStateException: Duplicate key` (`EventServiceImpl.java:35`, `Collectors.toMap` with no merge fn) |
| `POST /api/events` with `[]` | 200 `[]` |

**Cross-user failure mode is uniform and must be asserted as such: 404 (or `200 []` for the
two list endpoints), never 403.** Ownership is a query predicate, not an authorization
decision, so a foreign id is indistinguishable from a missing one.

**Error-body parity is better than feared.** The client reads only `path`, `message`, `params`,
and `fieldErrors[]` from error bodies — never `title`, `detail`, `status`, `type`, `errorKey`,
or `entityName`. The baseline added `path` and `message` too
(`6e19b96:web/rest/errors/ExceptionTranslator.java:36-37,59,72`), and my probe confirms HEAD
still emits both. **The Zalando → `ProblemDetail` move preserves every field the client
actually consumes.**

### 7. Wire-contract details the client depends on

From `../client`, all `file:line`-verified:

- **Money is scaled ×100 on send, ÷100 on receive** for all five event types. Despite the name,
  `costInCents` is a true integer cent count on the wire.
- **Refuel `volume` is sent ×1000** — millilitres on the wire (`refuel.reducer.ts:167`).
- **Vehicle images are base64 inside the JSON body, never multipart.** No `FormData` anywhere.
- **Nulls are dangerous.** The client calls `.trim()` unconditionally on `station`, `details`,
  `insurer`, `number`, `make`, `model`, `licensePlate` and five `vehicleDetails` strings. If
  the server ever returns `null` instead of `""`, the client throws on the next save.
- **The JWT is read from the `Authorization` *response header*, not the body**
  (`authentication.ts:111-113`), and requires the literal `Bearer ` prefix. No refresh, no
  expiry handling.
- `DELETE` is always preceded by a `GET {base}/{id}` purely to read `vehicleId` off the
  response — **the GET must keep returning `vehicleId`** or the delete flow breaks.
- POST and PUT send *different shapes* for `insuranceType` on insurance (POST object-wraps,
  PUT does not).

### 8. Test foundation

Reusable, established by F-04: `@SpringBootTest(classes = CarcareApp.class)` +
`@AutoConfigureMockMvc`, with `@WithMockUser("<login>")` — whose principal *name* is exactly
what the 13 ownership queries key on, making it the natural seam for isolation tests.

Traps that will otherwise cost the implementer hours:

1. **`TestUtil`'s ObjectMapper sets `NON_EMPTY`** (`TestUtil.java:42`), so empty strings vanish
   from request bodies and then NPE in `VehicleDetailsMapper`'s `.trim()` chain → 500. Build
   S-01 request bodies with a local mapper or raw JSON.
2. **`POST /api/vehicle` calls `getUserWithAuthoritiesOrFail()`** (`VehicleResource.java:54`),
   so `@WithMockUser` alone gives 500 — the principal needs a real `jhi_user` row. The four
   Liquibase-seeded users (`system`, `anonymoususer`, `admin`, `user`) are the cheapest two
   owners; do not create users.
3. **Lookup tables are empty in tests.** `GET /api/fuel-type` returns `[]` — verified by probe.
   `fuel_types`/`insurance_types` are populated only at runtime from
   `src/main/resources/testdata/*.json` via an ADMIN-only endpoint. Every vehicle-creation test
   must seed them first, idempotently (all three columns are `unique`).
4. **H2 is shared JVM-wide** (`DB_CLOSE_DELAY=-1`). Use class-level `@Transactional` everywhere
   except the delete-with-events case.
5. **Stay in one Spring context** — no `@MockBean`, no `@DirtiesContext`. A second context
   re-enters the JCache territory F-04 fixed in `CacheConfiguration.createCache()`.
6. `TestUtil.equalsVerifier` had its assertion inverted by F-04 and requires a public no-arg
   constructor, which no `domain/` entity has. Unusable on S-01 entities.

## Code References

- `src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java:23,32,40-41` — the renamed header name vs. the preserved value namespace
- `src/main/java/com/kasztelanic/carcare/config/HeaderUtilInitializer.java:15-21` — seeds it from `spring.application.name`
- `src/main/resources/config/application.yml:47-48` — `spring.application.name: carcare`
- `src/test/resources/config/application-test.yml:15,34` — the JDBC URL and `auto_quote_keyword` to change
- `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:36` — the unquoted `date` column
- `src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml:114` — the quoted `value` column
- `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:238,247,250,253,256` — the five non-cascading FKs
- `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:43-46,58-65` — owner assignment on create; bare `deleteById`
- `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java:35` — `Collectors.toMap` duplicate-key 500
- `src/main/java/com/kasztelanic/carcare/service/dto/AverageConsumptionResult.java:19-23` — NaN-on-zero-mileage serialization failure
- `../client/src/main/webapp/app/config/notification-middleware.ts:27-33,59-65` — the suffix match that the rename breaks
- `../client/src/main/webapp/app/config/axios-interceptor.ts:9-12` — `Bearer` attach
- `context/archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md` — F2 accepted the header rename

## Architecture Insights

- **Isolation as a query predicate, not an authorization decision.** This is the defining
  design choice of the codebase and explains the whole failure surface: cross-user access
  returns 404 rather than 403, and list endpoints return `200 []`. It is why negative tests
  must assert 404, and why a dropped `AndOwnerIsCurrentUser` would leak silently rather than
  throw.
- **Immutability as accidental security.** `private final Long id` on five of six entities
  makes create-into-overwrite structurally impossible, independent of any check. `Insurance`
  is the one entity that opted out.
- **The `VehicleEvent` embeddable is the single point of schema fragility.** One `@Column(name = "date")`
  shared by five tables is why one Hibernate flag disables a third of the API.
- **Two independent `carcareApp` contracts** — a header-name prefix and an i18n key root —
  were conflated during F-03. Worth stating explicitly in `AGENTS.md` so it is not re-conflated.

## Historical Context (from prior changes)

- `context/archive/2026-08-25-test-context-restored/change.md:96-104` — F-04 states plainly that
  it proves "no CarCare business behavior" and that 14 of 19 controllers are untested. The
  `auto_quote_keyword` blocker is the direct, predictable consequence.
- `context/archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md` — F1 (the
  `.anyRequest().permitAll()` fix, now probe-confirmed as `GET / → 200`), F2 (the header rename,
  now shown to be a real regression), F9 (`/test/**` dropped from the bypass — deliberate).
- `context/archive/resolvable-build/error-contract.md` — the four handlers that newly emit
  `path`/`message`; none of those fields is one the client ignores, so the divergences are safe.

## Related Research

- `context/archive/2026-08-25-test-context-restored/research.md` — established the live-probe
  method reused here.
- `context/archive/resolvable-build/migration-surface.md` — the 398-diagnostic measurement that
  bounded F-03.

## Open Questions

Ordered by what they block.

1. ~~**Header rename: fix server-side or ship a client change?**~~ **RESOLVED 2026-08-26 —
   fix server-side.** Owner decision. Restore the emitted header names to `X-carcareApp-alert`
   / `-params` / `-error`; keep the value namespace as-is. `spring.application.name` must stay
   `carcare`, so this is not a property change. See `change.md` → Decided inputs. **No longer
   blocks.**
2. ~~**What does "delete parity" mean for a delete that never worked?**~~ **RESOLVED 2026-08-26 —
   cover the working case, `@Disabled` placeholder for the broken one**, with the defect named
   and S-05 `vehicle-archiving` recorded as the owner of the real fix. Owner decision. **No
   longer blocks.**
3. **Do the Liquibase-seeded `user`/`admin` passwords actually authenticate?** A real-JWT
   end-to-end test depends on it; not probed. If not, that test must create and activate its
   own user.
4. **Should S-01 fix the three pre-existing 500s it will inevitably trip over** —
   missing `fuelType` → 500, duplicate `vehicleId` in `POST /api/events` → 500, and
   `per-period` consumption NaN → 500? All three pre-date the migration (mappers and services
   byte-identical to baseline), so parity says characterize, don't fix. Recorded so the choice
   is deliberate.
5. **Who covers `findAllByIdAndOwnerIsCurrentUser` and the five unfiltered `findByVehicleId`?**
   Reachable only via stats/reports/events. If S-01 scopes them out, S-03 must scope them in
   explicitly or nobody tests the collection-valued SpEL variant.
6. **Does the test profile's third divergence from production matter?** The fix in §3 adds
   `NON_KEYWORDS` + `CASE_INSENSITIVE_IDENTIFIERS` to H2 only. MariaDB's identifier handling is
   never exercised by any test — an accepted, but now larger, gap.
