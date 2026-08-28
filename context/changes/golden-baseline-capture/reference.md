# Golden Baseline Reference

## Provenance

- **Baseline commit:** `6e19b96` (`Update SpringBoot to 2.7.0`)
- **Capture profile:** `dev`
- **JDK:** Temurin `17.0.20` (host build and container runtime)
- **Database:** MariaDB `10.11.6`, isolated on host port `33077`
- **Timezone:** UTC (`-Duser.timezone=UTC` and `TZ=UTC`)
- **Reference date:** `2026-04-15`
- **Clock mechanism:** `libfaketime` in a disposable Temurin 17 container image, with
  `FAKETIME=@2026-04-15 12:00:00` and `FAKETIME_DONT_FAKE_MONOTONIC=1`.

The clock mechanism is necessary because `ReminderServiceImpl.sendReminders()` calls
`LocalDate.now()` directly. Changing the date with container `SYS_TIME` capability was not
persistent under Docker Desktop; `libfaketime` is. The WAR log and its Liquibase run both show
`2026-04-15`, and JWT authentication completed successfully under that same fixed date.

## Verified Phase 1 findings

- `6e19b96` builds fully offline with Temurin 17.0.20.
- The WAR starts against a fresh `dev` MariaDB database and `/management/health` returns `UP`.
- `admin` / `admin` and `user` / `user` both return a JSON body containing `id_token` from
  `POST /api/authenticate`.
- After Liquibase migration, `fuel_types`, `insurance_types`, and `reminder_advances` each
  contain zero rows. The fixture must therefore insert its own lookup and advance rows.
- The worktree is outside the repository at `/private/tmp/carcare-golden-baseline-6e19b96`.
  Docker Desktop cannot bind-mount the WAR from that path on this machine; use `docker cp` below.

## Golden fixture inventory

`src/test/resources/golden/golden-dataset.sql` is loaded only after a fresh Liquibase migration.
It reserves the `900000+` id range; Liquibase-seeded users remain `admin` / id `3` and `user` /
id `4`. Loaded row counts are: fuel types 1, insurance types 1, reminder advances 2, vehicles 3,
refuels 6, repairs 2, inspections 4, insurances 3, and routine services 4.

| Handle → capture id | Purpose |
| --- | --- |
| `fuel-type:diesel` → 900001 | Required vehicle lookup |
| `insurance-type:oc` → 900011 | Required insurance lookup |
| `reminder-advance:three-days` → 900021; `reminder-advance:seven-days` → 900022 | Global due-date offsets |
| `owner:admin-en` → 3; `owner:user-pl` → 4 | Report locales and ownership isolation |
| `vehicle:en-primary` → 900101 | Main EN report/statistics vehicle |
| `vehicle:pl-primary` → 900102 | PL report and foreign-owner path |
| `vehicle:zero-consumption` → 900103 | Isolated one-refuel consumption failure |
| `refuel:en-first` → 900401; `refuel:en-second` → 900402; `refuel:en-boundary` → 900403 | Three in-range fills for `skip(1)` consumption and inclusive bounds |
| `refuel:zero-volume` → 900404 | `Infinity` unit-price workbook cell |
| `refuel:pl-only` → 900405; `refuel:zero-consumption` → 900406 | One-fill per-refuel/period paths |
| `repair:same-date-low-mileage` → 900501; `repair:range-before` → 900502 | Same-date merge and excluded lower boundary |
| `inspection:same-date-high-mileage` → 900601; `inspection:en-reminder-plus-three` → 900602; `inspection:pl-reminder-plus-seven` → 900603; `inspection:reminder-minus-one` → 900604 | Mileage winner and reminder selection/boundary |
| `insurance:en-reminder-plus-three` → 900701; `insurance:pl-reminder-plus-seven` → 900702; `insurance:reminder-plus-one` → 900703 | Insurance reminder selection/boundary |
| `routine-service:null-next-date` → 900301; `routine-service:null-next-mileage` → 900302; `routine-service:en-reminder-plus-three` → 900303; `routine-service:pl-reminder-plus-seven` → 900304 | Null cells and routine-service reminder selection |

The reference date, `2026-04-15`, is neither a month/year boundary nor 29 February. Its configured
advances select 18 April (`+3`) and 22 April (`+7`); 19 April and 21 April are deliberate `+1` and
`-1` non-matches. The branch coverage is:

| Captured behaviour / code location | Covering handles |
| --- | --- |
| Vehicle reports: EN and PL localisation, all fields, and empty event sheets (`VehicleReport`) | `vehicle:en-primary`, `vehicle:pl-primary`, `vehicle:zero-consumption` |
| Refuel unit-price divide by zero (`VehicleReport`) | `refuel:zero-volume` |
| Cost/consumption inclusive period and `skip(1)` (`CostCalculatorImpl`, `AverageConsumptionCalculatorImpl`) | `refuel:en-first`, `refuel:en-second`, `refuel:en-boundary`; `repair:range-before` excluded |
| One-refuel / zero-mileage baseline failure captured separately | `vehicle:zero-consumption`, `refuel:zero-consumption` |
| Same-date mileage `TreeMap` highest-mileage merge (`MileageServiceImpl`) | `repair:same-date-low-mileage`, `inspection:same-date-high-mileage` |
| Routine-service absent vs present `nextByDate`, and absent `nextByMileage` | `routine-service:null-next-date`, `routine-service:null-next-mileage` |
| Ownership and both report locales | `owner:admin-en`, `owner:user-pl`, `vehicle:en-primary`, `vehicle:pl-primary` |
| Insurance, inspection, and routine-service exact due-date selection for both owners | all `*-reminder-plus-three` and `*-reminder-plus-seven` handles |
| Reminder exclusion boundaries and nullable routine due date | `inspection:reminder-minus-one`, `insurance:reminder-plus-one`, `routine-service:null-next-date` |

## Reproducible fixed-clock boot

Run the following commands in order from the repository root. They create the only capture
worktree and the capture database/app containers; keep all three through Phases 2–4.

```bash
git worktree add --detach /private/tmp/carcare-golden-baseline-6e19b96 6e19b96
cd /private/tmp/carcare-golden-baseline-6e19b96
export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem
./mvnw -o -B -DskipTests clean package
```

Create the disposable fixed-clock image (the Dockerfile is intentionally outside the repository):

```bash
mkdir -p /private/tmp/carcare-golden-faketime-build
cat > /private/tmp/carcare-golden-faketime-build/Dockerfile <<'EOF'
FROM eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends libfaketime \
    && rm -rf /var/lib/apt/lists/*
EOF
docker build --tag carcare-golden-faketime:temurin17 /private/tmp/carcare-golden-faketime-build
```

Start the fresh database and wait until it accepts connections:

```bash
docker run --rm -d --name carcare-golden-baseline-mariadb-clock \
  -e MARIADB_ROOT_PASSWORD=pass -e MARIADB_DATABASE=carcare \
  -p 33077:3306 mariadb:10.11.6
docker exec carcare-golden-baseline-mariadb-clock \
  mariadb-admin ping -h localhost -uroot -ppass --silent
```

Create the app container, copy in the WAR, and start it. The explicit copy is required on this
Docker Desktop host; a bind mount of `/private/tmp/...war` becomes an empty directory.

```bash
docker create --name carcare-golden-baseline-app-clock -p 18082:18081 \
  -e TZ=UTC -e SERVER_PORT=18081 -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:33077/carcare \
  -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=pass \
  carcare-golden-faketime:temurin17 bash -lc '
    export LD_PRELOAD="$(find /usr/lib -name libfaketime.so.1 -print -quit)";
    export FAKETIME="@2026-04-15 12:00:00";
    export FAKETIME_DONT_FAKE_MONOTONIC=1;
    date -u;
    exec java -Duser.timezone=UTC -jar /carcare.war'
docker cp target/carcare-1.3.5.war carcare-golden-baseline-app-clock:/carcare.war
docker start carcare-golden-baseline-app-clock
curl --fail --silent --show-error http://localhost:18082/management/health
```

Verify authentication without printing the JWTs:

```bash
curl --fail --silent --show-error --output /private/tmp/carcare-golden-admin-auth.json \
  --header 'Content-Type: application/json' \
  --data '{"username":"admin","password":"admin","rememberMe":false}' \
  http://localhost:18082/api/authenticate
curl --fail --silent --show-error --output /private/tmp/carcare-golden-user-auth.json \
  --header 'Content-Type: application/json' \
  --data '{"username":"user","password":"user","rememberMe":false}' \
  http://localhost:18082/api/authenticate
rg -q '"id_token"' /private/tmp/carcare-golden-admin-auth.json
rg -q '"id_token"' /private/tmp/carcare-golden-user-auth.json
docker exec carcare-golden-baseline-mariadb-clock mariadb -uroot -ppass -N -e '
SELECT "fuel_types", COUNT(*) FROM carcare.fuel_types
UNION ALL SELECT "insurance_types", COUNT(*) FROM carcare.insurance_types
UNION ALL SELECT "reminder_advances", COUNT(*) FROM carcare.reminder_advances;'
```

The application log starts with `Wed Apr 15 12:00:00 PM UTC 2026`, and all Spring Boot and
Liquibase timestamps use `2026-04-15`. Do not use `docker exec ... date` as proof of the fake
time: a new exec process does not inherit the app process's `LD_PRELOAD` environment.

## Phase 3 capture record

The throwaway driver is `/private/tmp/carcare-golden-reducer/capture.sh`. It authenticates after
the fixture load, because the application caches users by login; the app was restarted once after
the load to clear the pre-fixture user cache. It resolves these capture-side ids only at HTTP
execution time: `vehicle:en-primary` → 900101, `vehicle:pl-primary` → 900102, and
`vehicle:zero-consumption` → 900103. Golden JSON contains handles, never those ids.

All captures use `2026-03-01` through `2026-03-31`. The shared cost request is
`{"vehicleIds":["vehicle:en-primary","vehicle:pl-primary","vehicle:zero-consumption"],"dateFrom":"2026-03-01","dateTo":"2026-03-31"}`.
The shared EN period request substitutes `vehicle:en-primary`; the zero-consumption request
substitutes `vehicle:zero-consumption`; the unowned mileage request substitutes
`vehicle:pl-primary`. All are made as `owner:admin-en`, except `vehicle-pl.json`, which is made
as `owner:user-pl`.

| Golden file | HTTP call | Expected status |
| --- | --- | --- |
| `src/test/resources/golden/reports/vehicle-en.json` | `GET /api/reports/vehicle/{vehicle:en-primary}` | 200 |
| `src/test/resources/golden/reports/vehicle-pl.json` | `GET /api/reports/vehicle/{vehicle:pl-primary}` | 200 |
| `src/test/resources/golden/reports/costs-en.json` | `POST /api/reports/costs` with shared cost request | 200 |
| `src/test/resources/golden/reports/vehicle-unowned.json` | admin `GET /api/reports/vehicle/{vehicle:pl-primary}` | 404 |
| `src/test/resources/golden/stats/consumption-period-en.json` | `POST /api/stats/consumption/per-period` with EN period request | 200 |
| `src/test/resources/golden/stats/consumption-period-zero.json` | `POST /api/stats/consumption/per-period` with zero-consumption request | 500 |
| `src/test/resources/golden/stats/consumption-refuel-en.json` | `POST /api/stats/consumption/per-refuel` with EN period request | 200 |
| `src/test/resources/golden/stats/consumption-refuel-zero.json` | `POST /api/stats/consumption/per-refuel` with zero-consumption request | 200 |
| `src/test/resources/golden/stats/mileage-en.json` | `POST /api/stats/mileage` with EN period request | 200 |
| `src/test/resources/golden/stats/mileage-unowned.json` | admin `POST /api/stats/mileage` with unowned request | 404 |
| `src/test/resources/golden/stats/cost-en.json` | `POST /api/stats/cost` with shared cost request | 200 |

Every golden record contains the status and only the controller-explicit headers: `Content-Type`,
`Content-Disposition`, `Cache-Control`, and `X-Total-Count` where present. `Date`,
`Content-Length`, and transfer headers are intentionally excluded: the baseline capture uses real
HTTP, while the later HEAD harness uses MockMvc.

Workbook values are reduced with raw POI cell types, values, and style data formats — never
`DataFormatter`. Money uses two decimal places; other numeric values use six. The `Costs` sheet's
per-vehicle rows are sorted by its vehicle-label composite key because the source collection is
unordered; every other sheet remains in workbook order and natural row order. POI represents the
zero-volume refuel's positive infinity as an Excel `#DIV/0!` error cell, so that error cell is
stored with type `ERROR`, data format `0.00`, and the explicit `Infinity` sentinel. The report
content type is the historical `application/vnd.ms-excel` despite its XLSX bytes. The final main
sheet row is the literal `reports.vehicle.main.certificate` message-key lookup; its localized
label and string value are captured as cells, not treated as a structured certificate object.

`consumption-period-zero.json` deliberately stores the 500 body as a raw string. Baseline Jackson
writes a partial result and then appends its problem document without a JSON delimiter when the
zero mileage produces `NaN`; this is captured behaviour, not a capture error. The raw string has
only its vehicle id normalized to the fixture handle.

Repeatability was verified from a newly created `carcare` database: stop the app container, run
`DROP DATABASE carcare; CREATE DATABASE carcare;` inside the dedicated MariaDB container, start
the app so Liquibase recreates the schema, reload `golden-dataset.sql`, then run the throwaway
driver and reducers again. All eleven reduced JSON files compared byte-for-byte with this set.

## Phase 4 reminder capture

The reminder runner is the throwaway
`src/test/java/com/kasztelanic/carcare/golden/ReminderCaptureMain.java` in the baseline
worktree. It executes the baseline `ReminderServiceImpl` directly, with repository proxies
returning the Phase 2 rows and a synchronous `MailService` subclass collecting the three typed
send methods. This removes the `@Async` proxy from the observation point while keeping the
selection code itself unchanged. The repository proxies apply the same `findBy…In` membership
filters as the JPA repositories, including the null-safe exclusion for
`routine-service:null-next-date`.

The exact runner preparation and fixed-clock invocation were:

```bash
cd /private/tmp/carcare-golden-baseline-6e19b96
export JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem
./mvnw -o -B -DskipTests test-compile

war_dir=$(mktemp -d /private/tmp/carcare-golden-war.XXXXXX)
unzip -q target/carcare-1.3.5.war -d "$war_dir"
docker exec carcare-golden-baseline-app-clock sh -lc \
  'mkdir -p /tmp/carcare-golden-war /tmp/carcare-golden-runner'
docker cp "$war_dir/WEB-INF" \
  carcare-golden-baseline-app-clock:/tmp/carcare-golden-war/
docker cp target/test-classes/com \
  carcare-golden-baseline-app-clock:/tmp/carcare-golden-runner/

docker exec carcare-golden-baseline-app-clock sh -lc '
  export LD_PRELOAD="$(find /usr/lib -name libfaketime.so.1 -print -quit)"
  export FAKETIME="@2026-04-15 12:00:00"
  export FAKETIME_DONT_FAKE_MONOTONIC=1
  date -u
  rm -rf /tmp/carcare-golden-reminders
  mkdir -p /tmp/carcare-golden-reminders
  exec java -Duser.timezone=UTC \
    -cp "/tmp/carcare-golden-war/WEB-INF/classes:/tmp/carcare-golden-war/WEB-INF/lib/*:/tmp/carcare-golden-runner" \
    com.kasztelanic.carcare.golden.ReminderCaptureMain \
    /tmp/carcare-golden-reminders/typed-seam.json \
    /tmp/carcare-golden-reminders/full-path.json
'
mkdir -p /private/tmp/carcare-golden-reminders
docker cp carcare-golden-baseline-app-clock:/tmp/carcare-golden-reminders/typed-seam.json \
  /private/tmp/carcare-golden-reminders/typed-seam.json
docker cp carcare-golden-baseline-app-clock:/tmp/carcare-golden-reminders/full-path.json \
  /private/tmp/carcare-golden-reminders/full-path.json
```

The runner first calls `sendInsuranceReminders`, `sendInspectionReminders`, and
`sendRoutineServiceReminders` with `now = 2026-04-15` and
`dates = [2026-04-18, 2026-04-22]`, then clears the collector and calls `sendReminders()`. The
second call derives those dates from the two `reminder_advances` rows while `LocalDate.now()` is
held at the same value by `libfaketime`. Its startup line is
`Wed Apr 15 12:00:00 PM UTC 2026`; a separate `docker exec … date` is not evidence because exec
processes do not inherit the app/runner preload environment.

| Golden file | Capture path | Entries |
| --- | --- | --- |
| `src/test/resources/golden/reminders/typed-seam.json` | Explicit-date calls on all three typed methods | 6 |
| `src/test/resources/golden/reminders/full-path.json` | `sendReminders()` with fixed `LocalDate.now()` | 6 |

Both files carry `referenceDate`, the sorted `dates` set, the configured advances `[3, 7]`, and
entries sorted by `(eventType, ownerLogin, vehicleHandle, eventHandle)`. The selected rows are
the `+3` and `+7` insurance, inspection, and routine-service rows, once for each owner. The
`+1` insurance row (`insurance:reminder-plus-one`), the `-1` inspection row
(`inspection:reminder-minus-one`), and the null-date routine service
(`routine-service:null-next-date`) are absent from both files. The typed and full-path files were
compared entry-for-entry and byte-for-byte.

To repeat the run on a later host calendar day while preserving the reference date, keep the
same `FAKETIME` invocation and use a separate output directory:

```bash
docker exec carcare-golden-baseline-app-clock sh -lc '
  export LD_PRELOAD="$(find /usr/lib -name libfaketime.so.1 -print -quit)"
  export FAKETIME="@2026-04-15 12:00:00"
  export FAKETIME_DONT_FAKE_MONOTONIC=1
  rm -rf /tmp/carcare-golden-reminders-repeat
  mkdir -p /tmp/carcare-golden-reminders-repeat
  exec java -Duser.timezone=UTC \
    -cp "/tmp/carcare-golden-war/WEB-INF/classes:/tmp/carcare-golden-war/WEB-INF/lib/*:/tmp/carcare-golden-runner" \
    com.kasztelanic.carcare.golden.ReminderCaptureMain \
    /tmp/carcare-golden-reminders-repeat/typed-seam.json \
    /tmp/carcare-golden-reminders-repeat/full-path.json
'
docker cp carcare-golden-baseline-app-clock:/tmp/carcare-golden-reminders-repeat/typed-seam.json \
  /private/tmp/carcare-golden-reminders-repeat-typed.json
docker cp carcare-golden-baseline-app-clock:/tmp/carcare-golden-reminders-repeat/full-path.json \
  /private/tmp/carcare-golden-reminders-repeat-full.json
cmp -s /private/tmp/carcare-golden-reminders/typed-seam.json \
  /private/tmp/carcare-golden-reminders-repeat-typed.json
cmp -s /private/tmp/carcare-golden-reminders/full-path.json \
  /private/tmp/carcare-golden-reminders-repeat-full.json
```

The later pinned-clock run produced no `cmp` output (exit status 0) for either file.

## Teardown (after Phase 4)

```bash
docker rm -f carcare-golden-baseline-app-clock carcare-golden-baseline-mariadb-clock
git worktree remove /private/tmp/carcare-golden-baseline-6e19b96
```

## Phase 5 consumption harness

The committed reference is consumed from HEAD-side integration tests, not regenerated. The
test-only entry points are:

- `com.kasztelanic.carcare.golden.WorkbookValues.extract(byte[])` reduces an XLSX body to the
  `sheets → rows → cells` structure used by `golden/reports/*.json`.
- `com.kasztelanic.carcare.golden.GoldenReference.load("golden/<surface>/<name>.json")` loads a
  response envelope. `assertWorkbookMatches` compares an XLSX MockMvc `MvcResult` (or its
  `MockHttpServletResponse`) and `assertJsonMatches` compares a JSON MockMvc result/response.
- `SessionFixtures.seedGoldenDataset()` creates the SQL fixture inside the caller's transaction
  and returns the 28-entry symbolic-handle → generated-id map. It is never called by the
  `ApplicationRunner`; existing integration tests receive only the two ordinary lookup rows.
- `GoldenDatasetMirrorIT` is the field-by-field H2 mirror check. It compares the fixture
  definition, not report output; report and reminder parity assertions belong to S-03 and S-04.

The value normalisation policy is defined in `WorkbookValues` and `GoldenReference`: POI raw cell
types are retained, numeric cells with the shared `0.00` style use two decimal places, all other
numeric cells use six, and `Infinity`/`NaN` are explicit strings. Only the `Costs` sheet's middle
per-vehicle rows are sorted by their first (vehicle-label) cell; its header and final `Sum` row,
and every row in every other sheet, retain workbook order. JSON object key order and date strings
are retained. `GoldenReference` rewrites live numeric `vehicleId` values through the returned
handle map before exact comparison and reports the first differing JSON path or workbook cell
path. Metadata comparison is limited to status and `Content-Type`, `Content-Disposition`,
`Cache-Control`, and `X-Total-Count`; HTTP container headers are intentionally excluded because
capture uses real HTTP while HEAD consumption uses MockMvc.

## Expected divergences at HEAD

Commit `4ad88bd` contains five intentional post-baseline fix groups. They are not dataset or
Jakarta-migration regressions and must be considered before treating a difference as evidence of
platform drift:

| Intentional fix group | Files | Affected surface |
| --- | --- | --- |
| Zero-mileage consumption guard | `src/main/java/com/kasztelanic/carcare/service/dto/AverageConsumptionResult.java` | `POST /api/stats/consumption/per-period` for `vehicle:zero-consumption`: HEAD returns a finite zero instead of the baseline serialization failure. |
| Dual-shape insurance input | `src/main/java/com/kasztelanic/carcare/service/dto/InsuranceTypeDto.java` | Insurance request deserialization accepts the client's bare-string lookup form; no current golden response changes. |
| Duplicate event-period tiebreak | `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java` | `POST /api/events` now keeps the first entry for a duplicate `vehicleId`; this is outside the captured report/stat/reminder responses. |
| Invalid fuel/insurance lookup handling | `src/main/java/com/kasztelanic/carcare/service/mapper/FuelTypeMapper.java`, `src/main/java/com/kasztelanic/carcare/service/mapper/InsuranceTypeMapper.java` | Invalid lookup values are represented as the typed exception rather than an accidental null mapping. |
| Invalid lookup translation | `src/main/java/com/kasztelanic/carcare/service/exception/InvalidLookupTypeException.java`, `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java` | The typed lookup error is translated to HTTP 400 instead of the former 500; this is outside the captured surfaces. |

F-02 supplies the reference and this reusable harness. S-03 and S-04 own the parity assertions,
including the decision about the deliberate zero-mileage and duplicate-`vehicleId` divergences.
