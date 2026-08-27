---
date: 2026-08-27T17:07:57+0200
researcher: Kacper Kasztelanic
git_commit: 8e4c354089b81e44ba7de2d1d7cafa63b6c11617
branch: refactor
repository: carcare/server
topic: "What is required to confidently plan golden-baseline-capture (roadmap F-02)"
tags: [research, codebase, golden-baseline, reports, statistics, reminders, F-02, FR-016, 6e19b96]
status: complete
last_updated: 2026-08-27
last_updated_by: Kacper Kasztelanic
---

# Research: What is required to confidently plan `golden-baseline-capture` (F-02)

**Date**: 2026-08-27T17:07:57+0200
**Researcher**: Kacper Kasztelanic
**Git Commit**: 8e4c354089b81e44ba7de2d1d7cafa63b6c11617
**Branch**: refactor
**Repository**: carcare/server

## Research Question

Research what is required to confidently plan the `golden-baseline-capture` change (roadmap
item F-02 / PRD FR-016): capture reference report values, statistics figures, and reminder
selection from the last runnable pre-migration commit `6e19b96`, so post-migration output
(S-03 `report-parity`, S-04 `english-reminder-fix`) can be compared against it at **value
level**, not byte level.

## Summary

**F-02 is a "write the contract down before the thing it guards changes" change**, structurally
identical to `context/archive/resolvable-build/error-contract.md`. It produces two artefacts:
a **deterministic dataset** and a **captured reference of every value** the report / statistics
/ reminder surfaces emit from that dataset on commit `6e19b96`, plus the small extractor +
comparison harness that S-03/S-04 will reuse.

Four things are now known that make planning possible:

1. **Booting `6e19b96` is verified feasible on this machine, fully offline.** A throwaway
   `git worktree` build produced `target/carcare-1.3.5.war` with `JAVA_HOME` set to Temurin
   17.0.20; the enforcer allows JDK 17; the React client artifact `1.2.3` and the entire
   Spring Boot 2.6.6 dependency tree are already in `~/.m2`. Docker/colima is up and
   `mariadb:10.11.6` is cached. The only unverified step is context boot itself (needs a
   running MariaDB), and `AGENTS.md` already asserts `6e19b96` "builds and runs".

2. **The full capture surface is catalogued** (§3, §4): `GET /api/reports/vehicle/{id}`
   (6-sheet XLSX), `POST /api/reports/costs` (1-sheet XLSX), `POST /api/stats/{consumption/per-period,
   consumption/per-refuel,mileage,cost}` (JSON), and reminder selection (observable only by
   capturing the `MailService.send*ReminderEmail(owner, vehicle, event, diff)` calls — there
   is no endpoint that returns the selected set).

3. **`src/main/java` and the Liquibase changelogs were byte-identical between `3e91ed4` and
   HEAD at roadmap-authoring time.** That is what makes `6e19b96` a valid reference. The
   migration work (F-03) has since changed `src/main` — `git diff 6e19b96 HEAD -- src/main/java`
   is now non-empty — but F-02 runs *on* `6e19b96`, so this does not affect it; the
   byte-identical fact only ever mattered for *attribution* (any post-migration value
   difference is the migration's fault, not intervening feature work).

4. **Three decisions must be made before a plan can be written** (§8): the dataset route,
   the reminder-clock mechanism, and the artefact shape / comparison-harness location.

The single biggest planning insight: **the golden dataset must be reproducible in the S-03/S-04
test context, not just at capture time.** S-03 and S-04 will encode the captured values as
full-context MockMvc assertions running against **H2 + `SessionFixtures`** at HEAD (that is the
test layer `session-parity` built). A reference captured from an ad-hoc production-data restore
is close to useless to them unless the exact rows are also frozen and re-seedable. This argues
for a **small curated deterministic fixture** that can be expressed both as SQL (for MariaDB
capture on `6e19b96`) and as `SessionFixtures` seed calls (for H2 comparison at HEAD).

## Detailed Findings

### 1. What F-02 must deliver — the contract with FR-016, S-03, S-04

From `context/foundation/prd.md:351-358` (FR-016) and `context/foundation/roadmap.md:141-163`
(F-02 item):

- **Outcome:** "reference report values, statistics figures, and reminder selections exist,
  produced from commit `6e19b96` … and are comparable against post-migration output"
  (`roadmap.md:143-145`).
- **Unlocks:** S-03 (`report-parity`) and S-04 (`english-reminder-fix`); the value-level
  comparison FR-013 depends on; and it "is also the verification path behind the rollback
  plan, since the parallel-run environment and the baseline share their setup"
  (`roadmap.md:148-150`).
- **Comparison is value-level, never byte-level** — "XLSX writers emit version-dependent bytes
  and costs are widened from integer cents to floating point on output" (`prd.md:338-341`,
  `roadmap.md:364-366`).

**What S-03 expects to be handed** (`roadmap.md:353-371`, `session-parity/plan.md:110-112`):
reference values for both XLSX reports (every asserted cell) + all four statistics endpoints +
each response's content type, all from `6e19b96`. S-03 must also adjudicate two decisions
`session-parity` deferred to it against this baseline:
- zero-mileage consumption now returns `0.0` rather than failing — "conflates unknown
  consumption with a real zero" (`roadmap.md:369-370`, `session-parity/change.md:78-81`);
- the duplicate-`vehicleId` merge tiebreak in `POST /api/events` ("keep first", the second
  silently dropped — `session-parity/plan.md:576-578`).

**What S-04 expects to be handed** (`roadmap.md:373-395`): the reminder **selection** (which
reminders, for which owners, on a given reference date) from `6e19b96`, so the English-template
fix can prove "**only rendering changed**" — Polish output and selection semantics unchanged.
S-04 carries an explicit stop-condition if the English fault turns out to be message-source
*configuration* rather than template content.

> Note from §4: the English fault is almost certainly neither — it is **wrong positional
> argument indices in one key** (`email.service.text1`) of `messages_en.properties:35`. That
> makes S-04 a one-line bundle edit and makes F-02's "selection unchanged" proof exactly the
> thing that de-risks it.

**Artefact-shape precedent:** `context/archive/resolvable-build/error-contract.md` — a table of
every response key per handler, "Written before Phase 2's code changes landed", with the exact
command that produced it. F-02 should produce the equivalent living doc under
`context/changes/golden-baseline-capture/` (it moves to `context/archive/…` on `/10x-archive`).

### 2. Booting commit `6e19b96` — verified runbook

**Build feasibility — VERIFIED** by an actual `git worktree` build at `6e19b96`:

| Fact | Value | Evidence |
| --- | --- | --- |
| `java.version` | 17 | `git show 6e19b96:pom.xml` |
| Declared `spring-boot.version` | `2.7.0` (plugin/AP path only) | pom property |
| **Effective Spring Boot** | **2.6.6** | `jhipster-dependencies` 7.8.1 BOM → `jhipster-parent-7.8.1.pom` |
| `jhipster-dependencies.version` | 7.8.1, imported as **BOM** (no `<parent>`) | pom |
| `carcare-client.version` | **1.2.3** (== the retrievability floor; `<1.2.3` gone from registry) | pom; `~/.m2/repository/com/kasztelanic/carcare/client/1.2.3/` present (also 1.2.4, 1.2.5) |
| `poi-ooxml.version` | 5.2.2 | pom |
| Enforcer `requireJavaVersion` | `[1.8,18)` → **JDK 17 passes**, 18+ rejected | pom ~line 713-720 |
| Enforcer `requireMavenVersion` | `[3.8.5,)`; wrapper is Maven 3.8.5 at this commit | `.mvn/wrapper/maven-wrapper.properties` |
| Versionless-dependency failure (the thing that breaks HEAD) | **does not occur** — 7.8.1 BOM versions all 11 | successful offline build |

```
JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw -B -DskipTests clean package
  → BUILD SUCCESS, target/carcare-1.3.5.war (98 MB, executable war), fully offline from ~/.m2
```

**Run requirements:**

- **MariaDB is mandatory.** The Maven `dev` profile filters `spring.profiles.active=dev`;
  `application-dev.yml` datasource is `jdbc:mariadb://localhost:3306/carcare`, user `root`,
  password `pass`, dialect `MySQL5InnoDBDialect`, `ddl-auto: none`, `liquibase.contexts: dev`.
  **No `h2`/`dev-h2` Spring profile exists at `6e19b96`** — running on H2 would be a source
  change. `application-prod.yml` is also MariaDB.
- **No external services needed.** `application-dev.yml` points mail at `localhost:25` with
  empty creds; `MailService.sendEmail` catches `Exception` and every mail method is `@Async`,
  so a missing SMTP server fails nothing. The dev JWT `base64-secret` is hard-coded (24h
  validity); no env var required.
- **Liquibase `dev` context seeds only users.** `master.xml` @ `6e19b96` includes two
  changelogs. The initial schema loads `data/{user,authority,user_authority}.csv` → users
  `system`, `anonymoususer`, `admin` (**`admin`/`admin`**, both roles), `user`
  (**`user`/`user`**, `ROLE_USER`). The second changelog creates `fuel_types`,
  `insurance_types`, `inspections`, `insurances`, `refuels`, `reminder_advances`, `repairs`,
  `routine_services`, `vehicles` — **with no `loadData`**. ⇒ after `dev` migration,
  `fuel_types`, `insurance_types`, and `reminder_advances` are **empty**.
- **Environment now:** no MariaDB running (`nc -z localhost 3306` closed, no client on PATH,
  `docker ps` empty). Docker/colima **is** running (aarch64); `mariadb:10.11.6` cached,
  `mariadb:10.6.7` (the `6e19b96` pin) is not. `~/carcare` does not exist → clean slate.
  Schema is Liquibase-managed, so the 10.6→10.11 delta is low risk.

**Verified vs assumed in the runbook:**

| Step | State |
| --- | --- |
| Build `6e19b96` war offline with Temurin 17.0.20 | **VERIFIED** |
| Client `1.2.3` + full dependency tree cached | **VERIFIED** |
| Docker up, MariaDB image cached | **VERIFIED** |
| `admin`/`admin`, `user`/`user` are the seeded credentials | **VERIFIED** (stock JHipster bcrypt in `data/user.csv`) |
| Context boot (Liquibase `dev` + bean wiring + Spring Security 5 / JWT) | **ASSUMED** — not exercised live; `AGENTS.md` asserts it runs |
| `/api/authenticate` request/response contract | **ASSUMED** (standard JHipster, permitted path) |
| Which dataset to load | **OPEN** — planning decision (§5, §8) |
| Reminder-selection capture mechanism | **OPEN** — planning decision (§8) |

`git diff --stat 6e19b96 HEAD -- pom.xml .mvn src/main/docker .gitlab`: Maven wrapper
3.8.5→3.9.6, `pom.xml` (BOM 7.8.1→8.0.0, SB 2.7.0→3.1.5, client 1.2.3→current, enforcer,
jakarta groundwork), docker image tags, `mariadb:10.6.7→10.11.6`. `.gitlab/` unchanged.

### 3. Report & statistics output surface (capture catalogue)

All paths `src/main/java/com/kasztelanic/carcare/`. `src/main/java` for this code is materially
unchanged from `6e19b96` (walk the code at HEAD; verify against `git show 6e19b96:…` when
planning the assertions).

**Common facts:**
- **Auth:** `SecurityConfiguration.java:80` — `/api/**` → `.authenticated()`. No role check;
  any `ROLE_USER` may call every endpoint below. Ownership is enforced in the repository JPQL
  (`?#{principal.username}`); unowned ids are silently dropped (list endpoints) or 404
  (single-vehicle endpoints).
- **Locale:** reports pull sheet names / headers / labels from `i18n/messages{,_pl}.properties`
  via `Locale.forLanguageTag(user.getLangKey())` (`ReportServiceImpl.java:43,59`); an
  unknown/null langKey → root locale → English fallback. **Statistics endpoints are
  locale-independent** (pure numbers/dates).
- **POI:** `poi-ooxml` 5.2.2 → genuine `.xlsx` (`XSSFWorkbook`). Numeric cells written as Java
  `double`; a shared `"0.00"` `DataFormat` is a *display* style on money/volume/price cells
  only — the stored value is full-precision double.
- **Monetary storage vs emission:** every event entity stores `Integer costInCents` (column
  `cost_in_cents`); refuel volume is `Integer volume` in `volume_in_cm3` (millilitres).
  Mappers pass these through 1:1 as `Integer`. **Integer→double crossover points:**
  `CostCalculatorImpl.java:37` (`sum()` of int cents `/ 100.0`), `CostResult.getSum()` (sums
  five doubles), `CostReport.sumCosts` (`CostReport.java:92-94`), `VehicleReport` per cell
  (`costInCents / 100.0`, `volume / 1000.0`, `costInCents * 10.0 / volume`),
  `AverageConsumptionCalculatorImpl` (`volume / 1000.0`). The **only** explicit rounding is
  `AverageConsumptionResult.getAverageConsumption()` — `BigDecimal.setScale(1, HALF_UP)`.

#### 3a. `GET /api/reports/vehicle/{id}` — single-vehicle workbook
`ReportResource.java:35-42` → `ReportServiceImpl.java:40-54` → `VehicleReport.java:45-54`.
- `id` path var; **no date range** — every event of every type is included.
- `200` + XLSX bytes when owned; `404` empty body when `findByIdAndOwnerIsCurrentUser` empty;
  POI/IO failure → `ReportGenerationException` thrown out of the controller.
- Headers: `Content-Type: application/vnd.ms-excel` (legacy `.xls` MIME on a real `.xlsx`
  body — a pre-existing quirk; assert it so it can't silently change);
  `Content-Disposition: form-data; name="<f>"; filename="<f>"` where
  `<f> = licensePlate.replaceAll("\\s+","_") + ".xlsx"`; `Cache-Control: must-revalidate,
  post-check=0, pre-check=0`.
- **6 sheets, fixed order** (names localized): `main, insurance, inspection, service, repair,
  refuel` (`VehicleReport.createSheets`).
  - **main** (`createMainSheet:65-87`): col A label / col B value, rows 0-12: title, make,
    model, modelSuffix, licensePlate, yearOfManufacture (numeric), fuelType.translation,
    engineVolume (numeric), enginePower (numeric), weight (numeric), vin, vehicleCard,
    registrationCertificate.
  - **insurance** (`:111-149`): header row (9 cols) + data rows sorted by `vehicleEvent.date`
    asc: date (`yyyy-MM-dd` string), mileage (numeric), cost (`costInCents/100.0`, styled),
    type.translation, number, insurer, validFrom, validThru, details.
  - **inspection** (`:151-182`): 6 cols — date, mileage, cost, station, dateNext
    (`validThru`), details.
  - **service** (`:184-221`): 7 cols — date, mileage, cost, nextByMileage (blank if null),
    nextByDate (cell not created if null), station, details.
  - **repair** (`:223-250`): 5 cols — date, mileage, cost, station, details.
  - **refuel** (`:252-284`): 6 cols — date, mileage, cost (`costInCents/100.0`), volume
    (`volume/1000.0`, litres), unit price (`costInCents * 10.0 / volume`, PLN/litre —
    **`Infinity`/`NaN` if volume == 0**), station.

#### 3b. `POST /api/reports/costs` — multi-vehicle cost workbook
`ReportResource.java:44-51` → `ReportServiceImpl.java:56-75` → `CostReport.java:39-49`.
- Body `CostRequest` = `{ "vehicleIds": [Long…], "dateFrom": "yyyy-MM-dd", "dateTo":
  "yyyy-MM-dd" }`. `dateFrom`/`dateTo` inclusive filter on each event's `vehicleEvent.date`.
- Always `200` on success; filename constant `cost.xlsx`; same header set as 3a.
- **1 sheet** (name `reports.costs`): header row `A1:G1` =
  `Costs (PLN) | Insurance | Inspection | Routine Service | Repair | Refuel | Sum`; one data
  row per owned vehicle in `vehicleIds` (order = `findAllByIdAndOwnerIsCurrentUser` result
  order — **no `ORDER BY`**), col A = `"<make> <model> - <plate>"`, cols B-G = the six doubles
  from `CostResult` (`insuranceCosts, inspectionCosts, routineServiceCosts, repairCosts,
  refuelCosts, sum`), all styled `"0.00"`; final **Sum row** = column totals via
  `costs.stream().mapToDouble(...).sum()`.

#### 3c-3f. Statistics endpoints (all `POST`, JSON)
| Endpoint | Body | Response | Key computed value |
| --- | --- | --- | --- |
| `/api/stats/consumption/per-period` | `PeriodVehicle` | `200` `AverageConsumptionResult` `{periodVehicle, volume (L), mileage (int km), averageConsumption}` | `volume = filteredRefuels.sortedDesc.skip(1).sum(volume)/1000`; `mileage = max−min mileage`; `averageConsumption = mileage==0 ? 0.0 : BigDecimal(volume*100/mileage).setScale(1,HALF_UP)` |
| `/api/stats/consumption/per-refuel` | `PeriodVehicle` | `200` `List<AverageConsumptionResult>` + `X-Total-Count` header; **list is deterministically ordered** (sorted asc by date in code) | one element per consecutive refuel pair; `volume = refuels[i+1].volume/1000`; `mileage = refuels[i+1].mileage − refuels[i].mileage` |
| `/api/stats/mileage` | `PeriodVehicle` | `200` `MileageResult` `{periodVehicle, mileageByDate: Map<LocalDate,Integer>}`; **`404` empty body if vehicle not found/owned** | events of all 5 types, date-filtered, sorted by mileage asc, collected to `TreeMap` with merge `(v1,v2)->v2` → **highest mileage on a shared date wins**; keys emitted date-ascending |
| `/api/stats/cost` | `CostRequest` | `200` `List<CostResult>` + `X-Total-Count`; order = query order (**no `ORDER BY`**) | same `CostCalculatorImpl` as 3b — `sumCostsBetweenDates` = `stream.filter(inclusive dates).mapToInt(costInCents).sum()/100.0`; `getSum()` = plain double addition of the five |

`PeriodVehicle` = `{ "vehicleId": Long, "dateFrom": "yyyy-MM-dd", "dateTo": "yyyy-MM-dd" }`.
Date filters are inclusive at both ends throughout. `LocalDate` fields serialize ISO
(`yyyy-MM-dd`) via Jackson JavaTimeModule — **a Spring Boot 3 / Jackson change could flip this
format; that is exactly the kind of regression F-02 exists to catch.**

### 4. Reminder selection output surface

`src/main/java` + `templates/mail` + `i18n` are **byte-identical to `6e19b96`** for the whole
reminder path except `MailService.java`, whose diff is purely mechanical (`spring5`→`spring6`,
`javax.mail`→`jakarta.mail`, `JHipsterProperties`→`ApplicationProperties`). **No selection
logic changed.**

**Endpoints:** `GET /api/reminder/send` (`ReminderResource.java:22-26`, `ROLE_ADMIN`, calls
`reminderService.sendReminders()` synchronously — mail dispatch itself is `@Async`, returns
`void`/200 empty). `POST /api/reminder-advance/{days}` and `GET /api/reminder-advance`
(list `Integer` offsets). `DELETE /api/reminder-advance/{type}` has a `@PathVariable` name
mismatch and is effectively broken (record, do not fix — parity). **There are no reminder
DTOs.**

**Selection algorithm** (`ReminderServiceImpl.java:41-85`):
1. `LocalDate now = LocalDate.now()` (`:45`) — system default zone, **no injectable `Clock`**.
2. `dates` = every `reminder_advances` row → `.map(days).map(now::plusDays)` → `Set<LocalDate>`
   (global, not per-user).
3. Per type: `insuranceRepository.findByValidThruIn(dates)`,
   `inspectionRepository.findByValidThruIn(dates)`,
   `routineServiceRepository.findByNextByDateIn(dates)` (nullable — null rows never match).
4. For each matched row: `diff = (int) ChronoUnit.DAYS.between(now, dueDate)`;
   `mailService.send{Insurance|Inspection|RoutineService}ReminderEmail(owner, vehicle, row,
   diff)`.

**Firing rule:** the due-date field is **exactly equal** to `today + N` for some configured
advance `N`. Pure equality via SQL `IN` — no date window, no stale-row filtering, no per-owner
grouping, no dedup (one email per matching row). No `ORDER BY` on the derived queries.

**Observability:** selection is observable **only** by capturing the
`MailService.send*ReminderEmail(owner, vehicle, event, diff)` invocations (mock/spy
`MailService`, `ArgumentCaptor`). The `ReminderService` interface also exposes the three
per-type methods taking `(Set<LocalDate> dates, LocalDate now)` — a harness can inject a fixed
`now` and a fixed `dates` set, bypassing both `LocalDate.now()` and the `reminder_advances`
table.

**The 08:00 job:** `@Scheduled(cron = "0 0 8 * * *")` on `ReminderServiceImpl.java:42` (server
default TZ, no `zone`), enabled by `@EnableScheduling` on `AsyncConfiguration.java:20`.
On-demand via `GET /api/reminder/send` (same method). No test hook / profile flag.

**English fault hypothesis (feeds S-04):** message-bundle content bug — wrong positional
indices in `messages_en.properties:35` (`email.service.text1`). Base/PL consume args as
`{3} days … {4} … vehicle {0} {1} ({2}) … {5}`; EN consumes `{0} days … {1} … {2} {3} ({4})
… {5}`, so the English service-reminder body renders scrambled. `diff messages.properties
messages_en.properties` → **line 35 is the only difference**. Insurance/inspection keys are
byte-identical across bundles and already correct. The identical bug is present at `6e19b96`
(`git show 6e19b96:src/main/resources/i18n/messages_en.properties`). `MessageSource` config is
stock and unchanged (`spring.messages.basename: i18n/messages`). ⇒ S-04 is a one-line edit;
selection code never reads the bundle, which is what F-02's selection baseline must prove.

### 5. Dataset options (the decision that gates the plan)

There is **no deterministic event-data generator at `6e19b96`.** `GET
/api/test-data/random-vehicles/{n}` (`RandomDataServiceImpl`, `VehicleGenerator`) uses
unseeded `Collections.shuffle` / `new Random()` / `RandomStringUtils` **and creates only
`Vehicle` rows — zero events** → reports/statistics would be empty. Not usable.

`GET /api/test-data/populate-{fuel,insurance}-types` **are** deterministic (static classpath
JSON: DIESEL/PETROL/LPG/CNG/HYBRID/ELECTRIC/OTHER; LI/CC/OTHER).

| Route | What it is | Pros | Cons / risks |
| --- | --- | --- | --- |
| **A. Curated deterministic SQL fixture** (recommended) | Small hand-authored dataset — ~2 owners (langKey `en` + `pl`), ~3-4 vehicles, event rows placed relative to one fixed reference `LocalDate`; committed under the change folder / `src/test/resources`. Loaded into MariaDB at `6e19b96` for capture; re-expressed as `SessionFixtures` calls for S-03/S-04 under H2. | Reproducible on both sides (the key insight). Explicit PKs → stable `vehicleId`/`eventId` in snapshots. Can be designed to exercise every branch (null `nextByDate`, volume==0, same-date mileage merge, <2 refuels, on/±1-day reminder boundaries). Lives in the repo forever. | Must be authored deliberately; must cover every code path in §3/§4 or coverage gaps ship. |
| **B. `src/main/resources/testdata/exampleData.sql`** (586-line `mysqldump`) | Exists at `6e19b96`: `fuel_types` 7, `insurance_types` 3, `reminder_advances` 4 (`3,7,14,30`), `jhi_user` incl. `testUser` (id 5), `vehicles` 3 (owner 5), events on vehicles 1 & 3 (refuels many, repairs 3, inspections, insurances, routine_services). Credentials `testUser`/`testPassword`. | Ready-made events; realistic volume; deterministic once curated. | **Schema drift** — dumped from a superseded changelog set. `fuel_types` column order differs (dump `id,type,english,polish` vs Liquibase `id,english,polish,type`); `insurance_types`/`routine_services` need the same per-table check. Needs: strip `DROP/CREATE` + `DATABASECHANGELOG*` + `jhi_*`, remap `owner_id 5 → 3` (or insert `testUser`), verify every table's column order. Reminder dates are absolute → not clock-stable (see §8 Q2). |
| **C. Restored production data on MariaDB** | The route `shape-notes.md:49,919-934` and `prd.md:403-408` name for the **parallel-run rollback**; uses `src/main/scripts/{backup,restore}.sh`. | Highest-fidelity for the rollback comparison; "shares setup with FR-016". | Not reproducible for S-03/S-04's H2 test context unless the exact rows are also frozen — so it does **not** replace A. Needs an actual prod dump (availability unknown to the planner). Real PII. Better treated as a *separate* parallel-run concern that reuses F-02's extractor, not as F-02's dataset. |

**Recommendation to carry into planning:** Route **A**, optionally seeded *from* a curated
subset of Route B's `vehicles` + event INSERTs. Capture under the **`dev` profile on MariaDB**
(closest to production, and what post-migration parity is judged against — `dev` uses
`MySQL5InnoDBDialect`). Keep Route C as the parallel-run rollback step that reuses the F-02
extractor + comparison harness.

### 6. Artefact shape & comparison harness

**There is no golden-file / fixture resource layer today.** `src/test/resources/` holds only
`config/`, `i18n/`, `logback.xml`, `templates/mail/testEmail.html`. No `golden/`, `fixtures/`,
`__snapshots__/`, `.xlsx` references; no approval-testing / snapshot library on the classpath.

**The test layer S-03/S-04 will extend** (built by `session-parity` + `client-server-contract-trial`),
under `src/test/java/com/kasztelanic/carcare/`:
- `fixtures/SessionFixtures.java` — `@Component @Profile("test") implements ApplicationRunner`;
  idempotently seeds `fuel_types`/`insurance_types`, exposes builder methods
  (`vehicleFor`, `vehicleWithEventsFor`, `refuelFor`, `repairFor`, `routineServiceFor`,
  `inspectionFor`, `insuranceFor`) persisting via repositories; owners are the Liquibase-seeded
  `user`/`admin` (no test creates a user).
- `web/rest/AbstractSessionIT.java` — `@SpringBootTest(classes = CarcareApp.class)` +
  `@AutoConfigureMockMvc` + `@Transactional`; injects `MockMvc` + `SessionFixtures`; provides
  `json(Object)` via a **local** `ObjectMapper().registerModule(new JavaTimeModule())`
  (deliberately not `TestUtil.convertObjectToJsonBytes`, whose `NON_EMPTY` inclusion breaks
  the request-side `.trim()` mappers).
- One `*ResourceIT extends AbstractSessionIT` per resource; `OwnerIsolationIT` already parses
  the cost-report XLSX with POI to prove no foreign `licensePlate` leaks. Full-context MockMvc
  throughout; **one shared Spring context is a hard constraint** — no `@MockBean`, no
  `@DirtiesContext`.

**Implication for F-02's harness:** an XLSX→value extractor (POI: `DataFormatter` off; read
`getNumericCellValue()` / `getStringCellValue()` / `cellStyle.getDataFormatString()`) plus a
JSON reference format. Store one reference file per endpoint (e.g.
`src/test/resources/golden/reports/*.json`), numbers as fixed-precision decimal strings,
XLSX rows sorted by a stable key before storage to neutralise the missing `ORDER BY`. The
reference doc under the change folder records the exact capture command + dataset + profile +
clock, `error-contract.md`-style.

### 7. Determinism hazards (consolidated — pin every one)

| # | Where | Hazard | Mitigation |
| --- | --- | --- | --- |
| 1 | `VehicleRepository.findAllByIdAndOwnerIsCurrentUser` (`:22-23`) | no `ORDER BY` → `/api/reports/costs` rows & `/api/stats/cost` list order is DB-dependent | sort snapshot rows by `vehicleId` / plate before compare |
| 2 | `VehicleReport` event sheets sort by **date only**; source collections are `HashSet` | equal-date events have unstable relative order | fixture uses unique dates per event type, or sort snapshot rows by `(date,cost,id)` |
| 3 | reminder `LocalDate.now()` (`ReminderServiceImpl.java:45`), no `Clock` | "today" varies every run | drive the typed `(dates, now)` methods with explicit `now`; or pin the JVM clock; choose a reference date away from month/year boundaries; avoid Feb 29 |
| 4 | `findBy…In` reminder queries — no `ORDER BY` | matched-row order varies by engine | impose a total sort on the captured selection: `(eventType, ownerLogin, vehicleId, eventId)` |
| 5 | timezone | `LocalDate.now()` uses `ZoneId.systemDefault()`; cron uses server TZ | capture with `-Duser.timezone=UTC` (the HEAD `pom.xml` `argLine` already pins this for tests); document the golden as a UTC artefact |
| 6 | locale | report sheet names / headers / `main.title` from `messages{,_pl}.properties`; bad langKey → English | pin `user.langKey`; capture **both** `en` and `pl` for reports |
| 7 | floating point | `getSum()` / `sumCosts` accumulate doubles; `costInCents*10.0/volume` | store numbers rounded to fixed precision (6 dp safe; money is 2 dp) |
| 8 | refuel unit-price cell (`VehicleReport.java` ~:279) | `volume == 0` → `Infinity`/`NaN` in the cell | fixture refuels have `volume > 0`, or snapshot the `NaN` case deliberately |
| 9 | XLSX container | OOXML zip entry order + `docProps/core.xml` timestamps + POI version → bytes not reproducible | compare extracted cell values only (the whole point of F-02) |
| 10 | `Content-Type: application/vnd.ms-excel` on a real `.xlsx` | pre-existing quirk | assert it in the header snapshot so it can't silently change |
| 11 | `messages*.properties:47` `reports.vehicle.main.certificate` | missing `=`, parses via space separator to `Registration certificate` | snapshot pins current behaviour; note it so a "fix" is caught |
| 12 | Jackson `LocalDate` / map-key serialization | SB3 / Jackson change could flip ISO format | snapshot the literal JSON — this is a target regression, not noise |
| 13 | `@Async` on `MailService` | with a real bean, `ArgumentCaptor` may miss calls | use `@MockBean`/spy (no proxy) or a synchronous executor |
| 14 | `jhi_user` identity counter under H2 2.x (S-03/S-04 side only) | manual-id inserts don't advance it | the existing `TestUserIdentitySequenceFixup` already handles this |

## Code References

- `web/rest/ReportResource.java:35-61` — both report endpoints + `prepareResponse` headers
- `service/impl/ReportServiceImpl.java:40-75` — report orchestration, 404 path, filename logic
- `service/reports/VehicleReport.java:45-284` — 6-sheet workbook, cell by cell
- `service/reports/CostReport.java:39-94` — cost workbook + `sumCosts` totals row
- `web/rest/StatisticResource.java:30-52` — 4 statistics endpoints
- `service/impl/StatisticServiceImpl.java:37-74` — statistics orchestration + 404 for mileage
- `service/impl/AverageConsumptionCalculatorImpl.java:19-54` — per-period (`skip(1)`) & per-refuel
- `service/impl/MileageServiceImpl.java:22-37` — TreeMap merge `(v1,v2)->v2`
- `service/impl/CostCalculatorImpl.java:17-38` — shared by `/api/stats/cost` and `/api/reports/costs`
- `service/dto/AverageConsumptionResult.java:19-26` — the only explicit rounding (`HALF_UP`, scale 1)
- `service/impl/ReminderServiceImpl.java:41-85` — selection algorithm; `@Scheduled` at `:42`; `now` at `:45`
- `web/rest/ReminderResource.java:22-26` — `GET /api/reminder/send`, `ROLE_ADMIN`
- `service/ReminderService.java:8-14` — the `(Set<LocalDate> dates, LocalDate now)` seam
- `web/rest/TestDataResource.java` + `service/impl/RandomDataServiceImpl.java` — deterministic lookup loaders; non-deterministic, event-free vehicle generator
- `src/main/resources/config/liquibase/master.xml` @ `6e19b96` + `changelog/00000000000000_initial_schema.xml` — user seed CSVs; no lookup/event seed
- `src/main/resources/testdata/exampleData.sql` @ `6e19b96` — 586-line dump, dataset Route B
- `src/main/resources/i18n/messages_en.properties:35` — the English reminder fault (S-04)
- `src/main/scripts/{backup,restore}.sh` — parallel-run plumbing (Route C)
- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`,
  `web/rest/AbstractSessionIT.java`, `web/rest/OwnerIsolationIT.java` — the test layer S-03/S-04 extend

## Architecture Insights

- **F-02 is a contract-capture change, not a code change.** Its deliverable is a committed
  dataset + a committed reference + a small extractor/comparison harness, plus the reference
  doc. No `src/main` edit. Model it on `context/archive/resolvable-build/error-contract.md`.
- **The dataset is the hard part, and it has two consumers with different runtimes:** capture
  needs MariaDB on `6e19b96`; S-03/S-04 need the same rows re-seedable under H2 +
  `SessionFixtures` at HEAD. Only a small curated deterministic fixture satisfies both.
- **Value-level, not byte-level, is a hard requirement** driven by three real mechanisms:
  OOXML byte instability, integer-cents→double widening, and Jackson date-format risk.
- **Reminder selection has no query surface** — it is only observable through `MailService`
  interactions. The `(dates, now)` interface methods are the deterministic seam.
- **`session-parity` deliberately deferred every computed-value judgement to S-03 against this
  baseline** (zero-mileage `0.0`, duplicate-`vehicleId` tiebreak). F-02 must capture the
  `6e19b96` behaviour for exactly those cases so S-03 can adjudicate rather than guess.
- **Plan/artefact conventions:** `plan.md` = executable contract (per-file `**Intent**` /
  `**Contract**`, per-phase `#### Automated` / `#### Manual` gates, immutable `## Progress`
  step numbers with commit SHAs appended). `plan-brief.md` = standalone executive summary with
  a `Decision | Choice | Why | Source` table. "Delivered" is recorded in `roadmap.md` + a
  `change.md` epilogue, not in `plan.md`. `context/foundation/lessons.md` does not exist.

## Historical Context (from prior changes)

- `context/foundation/shape-notes.md:33-52` — the baseline-commit search. Originally targeted
  `2a20e8a`; **verified in-session** that `2a20e8a` compiles on JDK 17 but all 102 ITs fail to
  load a context (`FixedH2Dialect` missing + H2 `inspections.details` schema mismatch). "**No
  commit in reachable history has a green `./mvnw verify`**." Best baseline: `6e19b96`
  (2022-05-20, 94/102) or `3e91ed4` (identical). Conclusion: "**FR-016 should capture its
  baseline against restored production data on MariaDB instead of via H2.**"
- `context/foundation/shape-notes.md:883-907` — the 8 pre-existing IT failures at `6e19b96`
  are `standaloneSetup` harness artefacts (bypass the app `ObjectMapper`), **not behaviour to
  reproduce**. F-04 replaced that harness with full-context MockMvc.
- `context/archive/resolvable-build/error-contract.md` — the "write the contract down before
  the code changes, with the exact command that produced it" precedent. F-02's direct model.
- `context/archive/2026-08-25-test-context-restored/change.md:15-16` — "Parallel with F-02
  … Unlocks S-01, S-02, S-03, S-04." F-04 is now `done`; F-02 is the last item gating Stream
  B → C.
- `context/changes/session-parity/plan.md:110-112,485-493,576-578,592-594` and
  `plan-brief.md:90-99` — every place S-01 punts a computed value to "S-03, which has the
  golden baseline to judge it against."
- `context/changes/session-parity/reviews/impl-review.md:135-143` (F5) — the
  `$.averageConsumption` assertion was changed from `== 0.0` to `.exists()` precisely so S-03
  can re-judge it against F-02.
- `context/archive/2026-08-27-client-server-contract-trial/plan.md:167-213` — the closest
  precedent for booting the real WAR against a fresh disposable MariaDB and seeding lookups
  via the ADMIN endpoints; F-02's data route reuses this pattern with `6e19b96`'s WAR.

## Related Research

- `context/changes/session-parity/research.md` — the event-CRUD / statistics-reachability map
  (`:118-122` — the collection-valued and five `findByVehicleId` queries reachable only
  through statistics/reports/events).
- `context/archive/2026-08-27-client-server-contract-trial/research.md:86` — confirms F-02
  "remains ready and provides historical reference values only for report/reminder work."
- `context/archive/resolvable-build/migration-surface.md` — companion to `error-contract.md`;
  the other example of a captured-measurement handoff artefact.

## Open Questions

Ordered by what they block. All are **planning-step** decisions — none blocks starting the
plan, but each must be resolved *in* it.

1. **Dataset route (§5).** Recommended: **A** — a small curated deterministic fixture
   (optionally seeded from a curated subset of `exampleData.sql`), committed to the repo,
   captured under the `dev` profile on MariaDB, and re-expressible as `SessionFixtures` calls
   for S-03/S-04. Confirm, and confirm the fixture must cover every branch in §3/§4
   (null `nextByDate`; refuel `volume == 0`; same-date differing-mileage merge; <2 in-range
   refuels; reminder due-dates exactly on and at ±1 day from an advance offset; ≥3 in-range
   refuels for a real consumption figure). Owner: planning step + user sign-off on scope.

2. **Reminder clock mechanism (§4, §7 #3).** Options: (a) capture via the typed
   `sendInsuranceReminders(dates, now)` / `sendInspectionReminders` / `sendRoutineServiceReminders`
   methods with an explicit fixed `now` and explicit `dates` set (cleanest, bypasses
   `LocalDate.now()` and the `reminder_advances` table); (b) craft event due-dates as
   `capture_date + advance` and run the full `GET /api/reminder/send` path; (c) pin the JVM
   clock. Recommended: **(a)** as the primary snapshot, optionally **(b)** once to also cover
   the `findAll → plusDays → Set` dedup step. Owner: planning step.

3. **Reference artefact shape & location (§6).** Proposal: per-endpoint JSON reference files
   under `src/test/resources/golden/…` (numbers as fixed-precision strings; XLSX reduced to
   sorted cell-value maps), plus a `reference.md` under the change folder recording the exact
   capture command, dataset, profile, clock, timezone, and locale — `error-contract.md`-style.
   Confirm the location (repo `src/test/resources` vs the change folder vs both). Owner:
   planning step.

4. **Capture profile.** Recommended `dev` (MariaDB, `MySQL5InnoDBDialect`) — closest to
   production and to what post-migration parity is judged against. Confirm F-02 does **not**
   invent a bespoke profile. Owner: planning step.

5. **Which user owns the golden vehicles?** Seeded `admin` (id 3) / `user` (id 4) vs the
   dump's `testUser` (id 5, needs manual insert). Report endpoints are owner-scoped. Two
   owners (langKey `en` + `pl`) are needed anyway for the locale dimension and for a reminder
   dataset that spans owners. Owner: planning step.

6. **Does F-02 also stand up the side-by-side (old vs new) comparison, or only capture the
   reference?** The roadmap says F-02 "shares setup" with the parallel-run rollback but does
   not require F-02 to build the diff harness for a *restored prod DB*. Recommended: F-02
   delivers the reference + the value extractor + a comparison function usable by S-03/S-04
   tests; the prod-restore parallel run (Route C) is a later step that reuses them. Confirm
   the scope fence. Owner: user.

7. **Is `6e19b96` context boot actually green here, or only asserted?** The build is verified;
   the boot is not (needs a running MariaDB). First plan phase should be a spike that boots
   `6e19b96` against a disposable MariaDB and authenticates, before any capture work.
   Owner: planning step (cheap to settle).

8. **`InsuranceTypeDto` bare-string contract.** If any golden insurance rows are created via
   `POST /api/insurance/*` rather than SQL, the request body may use client 1.2.5's
   bare-string form (`"OC"`). Prefer SQL seeding to sidestep this, or pin the object form.
   Owner: planning step.
