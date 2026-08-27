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

## Teardown (after Phase 4)

```bash
docker rm -f carcare-golden-baseline-app-clock carcare-golden-baseline-mariadb-clock
git worktree remove /private/tmp/carcare-golden-baseline-6e19b96
```
