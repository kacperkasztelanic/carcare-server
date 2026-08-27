---
change_id: client-server-contract-trial
title: Trial and fix client-server contract issues
status: impl_reviewed
created: 2026-08-27
updated: 2026-08-27
archived_at: null
---

## Notes

Exercise the frozen React client 1.2.5 against a clean local MariaDB-backed server through
Playwright, record concrete UI-to-server contract failures, and fix only the confirmed issues.
Keep the work separate from `session-parity`, whose Phase 7 manual gate exposed the need for this
focused trial.

## Phase 3 trial environment (2026-08-27)

Client version: **1.2.5** (`client-1.2.5.jar` inside the WAR; bundle
`app/vendors.aeca5ce7ffebc06fb0ac.chunk.js`, identical to the `session-parity` smoke run).
Server: `target/carcare-1.3.11.war`, built after both Phase 1 and Phase 2 landed — verified by the
presence of `ProfileInfoContributor.class` and
`config/liquibase/changelog/20260827153000_client_contract_changelog.xml` inside the archive.

### Reproducible setup

```bash
# 1. Disposable MariaDB — tmpfs-backed, so every run starts clean.
docker rm -f carcare-trial-db 2>/dev/null
docker run -d --name carcare-trial-db \
  -e MYSQL_ROOT_PASSWORD=trialpw -e MYSQL_DATABASE=carcare \
  -p 13306:3306 --tmpfs /var/lib/mysql:rw,size=1g \
  mariadb:10.11.6 mysqld --lower_case_table_names=1 --skip-ssl \
  --character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
  --explicit_defaults_for_timestamp --max_allowed_packet=32505856

# 2. WAR on port 18080 against that database.
export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem
$JAVA_HOME/bin/java \
  -Dspring.profiles.active=prod -Dserver.port=18080 -Duser.timezone=UTC \
  -DSPRING_DATASOURCE_URL="jdbc:mariadb://localhost:13306/carcare?useLegacyDatetimeCode=false&serverTimezone=UTC" \
  -DSPRING_DATASOURCE_PASSWORD=trialpw \
  -jar target/carcare-1.3.11.war

# 3. One-shot lookup seeding under the admin session (NOT idempotent).
TOKEN=$(curl -s -X POST http://localhost:18080/api/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin","rememberMe":false}' \
  | sed -E 's/.*"id_token":"([^"]+)".*/\1/')
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/test-data/populate-fuel-types
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/test-data/populate-insurance-types
```

If either populate call fails or is retried, drop and recreate the container before re-seeding.

### Setup results

| Check | Result |
|---|---|
| Liquibase applied `20260827153000_client_contract_changelog.xml` | yes, on a fresh schema |
| `SHOW COLUMNS FROM vehicles LIKE 'license_plate'` | `varchar(20)` — Phase 2 confirmed on MariaDB |
| `GET /management/info` (anonymous) | `200 {"activeProfiles":["prod"],"display-ribbon-on-profiles":"dev"}` — Phase 1 confirmed |
| `GET /api/account` (anonymous) | `401` — existing contract intact |
| `populate-fuel-types` / `populate-insurance-types` | both returned `true`; 7 fuel types, 3 insurance types persisted |
| Client bundle served at `/` | `200`, frozen 1.2.5 chunk hash |

All CRUD assertions below run under the seeded `user`/`user` account.

## Phase 3 browser trial results (2026-08-27, client 1.2.5)

Driven with Playwright against the environment above. Every mutation below records its HTTP status
from the browser's own network log.

### CRUD matrix

| Flow | Create | List | Details popover | Edit (GET-by-ID → PUT) | Delete | Toasts observed | Verdict |
|---|---|---|---|---|---|---|---|
| Vehicle | `POST /api/vehicle` 201 | shown | n/a | `GET /api/vehicle/1` 200 → `PUT` 200 | `DELETE` 200 (event-free) | added / updated / deleted | pass |
| Repair | `POST /api/repair/1` 201 | shown | renders | `GET /api/repair/1` 200 → `PUT` 200 | `DELETE` 200 | added / updated / deleted | pass |
| Routine service | `POST /api/routine-service/1` 201 | shown | renders | `GET` 200 → `PUT` 200 | `DELETE` 200 | added / updated / deleted | pass |
| Inspection | `POST /api/inspection/1` 201 | shown | renders | `GET` 200 → `PUT` 200 | `DELETE` 200 | added / updated / deleted | pass |
| Insurance | `POST /api/insurance/1` 201 | shown | renders | `GET` 200 → `PUT` 200 | `DELETE` 200 | added / updated / deleted | pass (server); see C-2 |
| Refuel | `POST /api/refuel/1` 201 | shown, unit cost computed | n/a (no details column) | `GET` 200 → `PUT` 200 | `DELETE` 200 | added / updated / deleted | pass |

Every row was walked twice: once on the first clean database, and again end-to-end on a second
freshly recreated database, the second pass additionally capturing the success toast for each
mutation via a `MutationObserver` on the toast container. Each edit was checked both for the value
the form pre-filled from `GET`-by-ID and for the value the list rendered after `PUT`, so the whole
round trip is covered rather than just the HTTP status.

The toasts confirm the `X-carcareApp-alert` → client-notification contract described in `AGENTS.md`
is intact across every resource, e.g. vehicle delete returns
`X-carcareApp-alert: carcareApp.vehicle.deleted` with `X-carcareApp-params: 1`.

**No new server-side contract defect was reproduced.** Every server response in the matrix was
correct and consistent, so no additional regression test was required by the Phase 3 contract.
Final `./mvnw verify`: **175 tests, 0 failures, 1 skipped, BUILD SUCCESS**. The single skip is the
pre-existing `@Disabled` delete-with-history test owned by S-05.

### Phase 1 and Phase 2 re-confirmed in the browser

- Anonymous load reaches `#/login` with **no** `applicationProfile` / `activeProfiles.includes`
  exception. The only console error is the expected `/api/account` 401, which the plan keeps.
- Vehicle create persisted the 20-character plate `ABCDEFGHIJ1234567890`; vehicle edit persisted a
  second 20-character plate `ZYXWVUTSRQ0987654321`. Both rendered in full in the list, the detail
  header, and the edit form.

### Client-owned findings (evidence recorded, deliberately not fixed)

The frozen client is explicitly out of scope ("Rebuilding or modifying the frozen sibling client").
Each finding below was traced to the client with the server response captured as proof.

**C-1 — Cold-loading any route that mounts `VehicleDetails` crashes the client.**
A hard load of `#/app/details/:id` renders the whole page as "An unexpected error has occurred."
with `TypeError: Cannot read properties of undefined (reading 'modelSuffix')` in
`VehicleDetails.render`. The server is not at fault: `GET /api/vehicle/1` returns **200** with a
complete `vehicleDetails` object; the component simply renders before the request resolves. Reaching
the same route by in-app navigation (clicking the list row) works, because the store is already
populated.

The trigger is broader than a details deep-link: cold-loading `#/app/new` throws the same error,
because that route mounts `VehicleDetails` underneath the creation modal. Any entry into the app
that mounts that component without a populated store reproduces it. In-app navigation is unaffected
throughout, which is why the rest of the matrix passes. Server-side no action.

**C-2 — Insurance edit form multiplies cost by 100 on every save.**
The server returns `costInCents` consistently from both `GET /api/insurance/1` and
`GET /api/insurance/all/1` (e.g. `123000` for 1,230.00 PLN). The client's insurance **list** divides
by 100 and displays `1,230.00`, but the insurance **edit form** binds the raw `costInCents` into the
"Cost (PLN)" input, showing `123000`. Saving that form without touching a single field sends
`"costInCents":12300000` and the list then reads `123,000.00` — a silent 100× corruption on every
insurance edit.

This is isolated to the insurance form. The repair, routine-service, inspection, and refuel edit
forms all pre-filled their true PLN values (`1450.75`, `620.4`, `99`, `312.45`) from the identical
server `costInCents` field, so the server contract is uniform and only this one client form fails to
convert. Server-side no action; this needs a client fix and is worth flagging to whoever next
unfreezes the client, because it corrupts stored data rather than merely misdisplaying it.

The server half of the insurance edit path is sound: typing the intended PLN amount (`1450`) over the
bad pre-fill stores and re-renders `1,450.00` correctly. Only the value the form loads is wrong, so a
client-side fix to the pre-fill is sufficient and no server change is warranted.

**C-3 — Stale details popover blocks the delete modal.**
Opening a row's details popover and then clicking Edit/Delete leaves the popover mounted above the
modal, so the confirm button is not clickable (`popover-header … intercepts pointer events`).
Toggling the popover shut first, or reloading, clears it. Cosmetic, client-side, no server
involvement.

### Deferred to an owning slice

**S-05 (`vehicle-archiving`) — deleting a vehicle that has event history returns 500.**
Reproduced against real MariaDB, not just H2: with one repair attached,
`DELETE /api/vehicle/1` returns **500** (`error.http.500`, Problem Details body) because the event
foreign keys do not cascade. This matches the existing `@Disabled` test recorded in `session-parity`
and is S-05's to resolve. Per the plan this is recorded, not treated as a trial failure, and left
unchanged. Vehicle deletion itself is healthy — an event-free vehicle deletes with a clean 200.

### Repository hygiene

Playwright writes page snapshots and console logs into `.playwright-mcp/`. That directory is
generated trial output, not source, so it is added to `.gitignore` and never staged.

### Operational note for anyone repeating this trial

Do not run `./mvnw verify` while the trial WAR is running from the same `target/`. The build rewrites
`target/carcare-<version>.war` underneath the live JVM, and because Spring Boot's `WarLauncher` loads
classes lazily from that file, the already-started app then fails every request with
`NoClassDefFoundError` / `ClassNotFoundException` rather than dying outright — it looks like an
application defect but is purely a rebuilt-artifact artifact. Run the build first, or stop the app
before building. Note also that the app may not release port 18080 on `SIGTERM`; check with
`lsof -ti :18080` and `kill -9` any survivors before restarting, or the new instance will appear to
start while requests are still answered by the old broken one.
