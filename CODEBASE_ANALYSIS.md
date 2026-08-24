# CarCare Server — Codebase Analysis for Refactor Roadmap

> **Purpose.** This is a self-contained technical and product briefing for the agent responsible for preparing the refactor roadmap. It describes the repository as inspected on branch `refactor` at commit `16eb931` (2026-06-22). Findings are based on the source tree, Maven configuration, CI/deployment files, Liquibase changelogs, and a fresh code graph index (3,122 nodes and 9,683 relationships). It is not a production-runtime audit: database contents, deployed configuration, CI history, and the sibling client source were outside this repository and were not inspected.

## Executive summary

CarCare is a single-deployment, JWT-secured vehicle/fleet-management backend. Its product scope is coherent: each user owns vehicles and records five kinds of lifecycle events (refuels, repairs, routine services, inspections, and insurance); the server derives costs and fuel/mileage statistics, creates Excel reports, and sends e-mail reminders.

The project has a sound *intended* JHipster monolith structure—web → service → repository → domain—with ownership-aware repository queries, DTOs, transaction boundaries, database migrations, tests, and Docker deployment. It is nevertheless currently **not buildable**. `./mvnw compile` fails during Maven model construction because eleven dependencies no longer receive versions from the declared JHipster dependency BOM. Even after that is corrected, source still contains 184 legacy `javax.*`/old Spring Security usages incompatible with its declared Spring Boot 3.1.5, Hibernate 6.2, and Spring Security 6 generation.

This is therefore not a routine cleanup. The first roadmap milestone must restore a reproducible, green build and establish a modern, internally consistent platform baseline before behavior-preserving refactoring. Several product-risk items should be protected with regression tests during that work, notably an evidently broken refuel-update method and vehicle deletion in the presence of non-cascading database foreign keys.

## Product and business capabilities

### Primary users and ownership model

The application is a multi-user personal/fleet tool. `Vehicle` is owned by a JHipster `User`; normal business reads and writes generally use Spring Data queries constrained with `vehicle.owner.login = ?#{principal.username}`. This is the principal data-isolation mechanism. `ROLE_USER` and `ROLE_ADMIN` exist; administrative functions cover user administration, audit access, lookup-table maintenance, test-data generation, and manually triggering reminder delivery.

### Fleet domain

`Vehicle` is the aggregate root in practice. It contains:

- identity and basic description: make, model, licence plate;
- a `FuelType` lookup and an embedded `VehicleDetails` value object (VIN, registration/card data, engine dimensions/power, year, image filename, notes, etc.);
- an owner reference.

Five independent event tables point at a vehicle rather than being represented as a polymorphic event hierarchy:

| Event | Core business data | Derived/related capability |
| --- | --- | --- |
| `Refuel` | date/mileage, cost in cents, volume in cm³, station | average consumption per period and per refill; costs |
| `Repair` | date/mileage, cost, station, free-text details | costs and vehicle report |
| `RoutineService` | date/mileage, cost, station, details, next date/mileage due | upcoming events and reminders |
| `Inspection` | date/mileage, cost, station, details, valid-through date | upcoming events and reminders |
| `Insurance` | date/mileage, valid-from/valid-through, cost, number, insurer, details, `InsuranceType` | upcoming events and reminders |

`VehicleEvent` is an embedded value object shared by all five event types and contains a non-negative mileage and a date. Costs are persisted as integer cents and converted to `double` only for aggregate output. `FuelType`, `InsuranceType`, and `ReminderAdvance` are global lookup/configuration tables; translations are stored for English and Polish.

### User-visible feature flows

#### Identity and account lifecycle

1. A visitor registers, activates an account via an e-mail key, then authenticates.
2. `UserJwtController` creates a JWT; Spring Security runs statelessly.
3. An authenticated user reads or changes their account/profile and password, or initiates/completes a password reset.
4. An administrator manages users/authorities and views persisted audit events.

Mail templates and i18n messages support English and Polish. `MailService` sends account and reminder mail asynchronously.

#### Vehicle and event management

1. An authenticated user creates a vehicle; the service assigns the current user as owner, regardless of client input.
2. The user lists or fetches only owned vehicles, then creates, reads, updates, or deletes each event under an owned vehicle.
3. Each event service does an ownership-aware lookup before mutation. Event DTOs and handwritten mappers separate REST payloads from JPA entities.
4. Vehicle updates delete the previous image file and replace `VehicleDetails`; image bytes are persisted in a configured local data directory, with only a generated filename in the database.

#### Planning and notifications

1. The client submits a list of `(vehicleId, dateFrom, dateTo)` selections to `/api/events`.
2. `EventService` loads only the caller's requested vehicles, builds rich vehicle DTOs, filters insurance/inspection expiry and next routine-service dates, and returns chronologically sorted forthcoming events.
3. Every day at 08:00 server-local time, `ReminderService` reads global reminder-day offsets, calculates target dates, queries qualifying insurance/inspection/routine-service records, and queues localized e-mails to each vehicle owner.
4. An administrator can invoke the same reminder dispatch manually.

#### Analysis and reporting

1. Statistics endpoints calculate period consumption, per-refuel consumption, mileage information, and cost breakdowns.
2. A cost calculation gathers every event category inside the requested inclusive date range for each caller-owned vehicle.
3. Reports generate in-memory Excel (`.xlsx`) bytes: one detailed vehicle report and one multi-vehicle cost report. The REST resource returns them as an Excel download.

## REST surface at a glance

The source graph identifies 86 mapped REST handler methods across 18 resource/controller classes. The API is organized around the following resource groups rather than a versioned API contract:

| Area | Main path(s) | Operations |
| --- | --- | --- |
| Authentication/account | `/api/register`, `/api/activate`, `/api/authenticate`, `/api/account/**` | registration, activation, JWT, profile/password/reset |
| Admin and audit | `/api/users`, `/api/audits`, `/api/test-data/**` | user/authority management, audit history, controlled fixture generation |
| Vehicles | `/api/vehicle`, `/api/vehicle/all` | owned vehicle CRUD |
| Events | `/api/refuel`, `/api/repair`, `/api/routine-service`, `/api/inspection`, `/api/insurance` | owned event CRUD; list-by-vehicle |
| Lookups/configuration | `/api/fuel-type`, `/api/insurance-type`, `/api/reminder-advance` | localized lookup reads; admin mutation |
| Planning/reminders | `/api/events`, `/api/reminder/send` | upcoming-event calculation; admin manual mail dispatch |
| Analytics/reports | statistic paths and `/api/reports/vehicle/{id}`, `/api/reports/costs` | consumption/mileage/cost calculations and XLSX downloads |

All `/api/**` routes require authentication by default; public account paths and management health/info/prometheus are explicitly exempted. Method-level `@PreAuthorize` further guards administration, lookup changes, reminder dispatch, and test-data endpoints.

## Technical architecture

### Deployment view

```text
React/TypeScript client (separate repository; consumed here as Maven artifact)
                         |
                    NGINX reverse proxy
                         |
        Spring Boot WAR / embedded Tomcat (one monolith)
   REST + JWT + services + JPA/Hibernate + Ehcache + Liquibase
             |                 |                 |
        local image volume   SMTP server       MariaDB
```

The server is packaged as a WAR. Docker builds an Eclipse Temurin 17 Alpine runtime image and runs it as a non-root `jhipster` user. Compose-style files start application, MariaDB, and NGINX. Production binds a host data directory into the container for images. The client is not built here: version `1.2.5` is fetched from a GitLab Maven registry and serves as static content from the WAR.

### Application layers

```text
web/rest (controllers, errors, request/view models)
    ↓
service (use cases, calculators, reporting, mail, DTOs, mappers)
    ↓
repository (Spring Data JPA, current-user SpEL query filters)
    ↓
domain (JPA entities, embedded values, JHipster user/audit model)
    ↓
MariaDB schema managed only by Liquibase
```

This is conventionally layered and `ArchTest` prevents `service` and `repository` code from depending on `web`. The approach is mostly pragmatic rather than strict DDD: controllers are thin for the central aggregate/event CRUD, calculator classes are isolated, and the services coordinate persistence and mapping. Some small lookup/configuration resources still access repositories directly, which is explicitly marked with TODO comments.

### Key implementation patterns

- **Data isolation:** repositories embed the currently authenticated login in JPQL. This avoids relying solely on controller checks and is used in normal vehicle/event access paths.
- **DTO mapping:** entity serialization is avoided; manually written mappers and some MapStruct usage translate JPA models to DTOs. `VehicleRichMapper` constructs report/event-analysis DTOs by loading all five event collections.
- **Transaction model:** reads/writes in most services have `@Transactional(readOnly = true)` and `@Transactional`; a few direct repository-backed resources are transactional at controller level.
- **Persistence:** MariaDB in runtime, H2 in tests; Liquibase XML declares the schema. The master changelog includes only the initial schema and the 2019 business changeset (despite a stray unreferenced `201901...` file).
- **Caching:** Hibernate second-level cache with Ehcache, primarily `NONSTRICT_READ_WRITE`, and read-only lookup/reminder entries. Query cache is disabled.
- **Operations:** Actuator runs below `/management`, JSON Logstash support is configurable, Prometheus dependencies exist, and a `sonar.yml` deployment file is present.

## Technology stack

| Concern | Technology/configured version | Notes |
| --- | --- | --- |
| Language/runtime | Java 17 | Target/source set to 17; production image is Temurin 17 JRE. |
| Framework | Spring Boot 3.1.5, JHipster dependencies 8.0.0 | Declared target platform, but source and dependency selection are not yet compatible with it. |
| Web/security | Spring MVC, Spring Security, JWT (`jjwt`), Zalando Problem | Stateless JWT and controller REST API. |
| Data | Spring Data JPA, Hibernate 6.2.13, HikariCP, MariaDB | H2 supports tests. |
| Schema | Liquibase 4.20 XML | DDL is versioned; schema evolution has been dormant since 2019. |
| Mapping/code generation | Lombok 1.18.30, MapStruct 1.5.3 | IDE profile enables processors. |
| Reporting/files | Apache POI 5.2.5, Apache Tika 2.7, Commons IO | XLSX export; local filesystem image storage. |
| Supporting libraries | Vavr 0.10.3, Guava 31.1, Apache Commons | Vavr is used for `Option`/`Either`/`Try`, but usage is inconsistent. |
| Testing/quality | JUnit 5, Spring test/security test, ArchUnit, JaCoCo, Surefire/Failsafe, Sonar Maven plugin | Tooling is declared but cannot currently run. |
| Delivery | Maven Wrapper 3.9.6, GitLab CI, Docker/DinD, NGINX | CI only runs on tags and not branch/MR validation. |

The source carries clear fingerprints of JHipster 5/Spring Boot 2: `javax.persistence`, `javax.validation`, `javax.servlet`, `WebSecurityConfigurerAdapter`, `antMatchers`, old Hibernate/Jackson module names, and legacy Spring properties. Spring Security 6 documentation uses bean-based `SecurityFilterChain` plus `requestMatchers`, not the adapter/chained configuration present here.

## Strengths worth preserving

1. **Clear business focus.** The aggregate/event model maps directly to vehicle-operation workflows, while date/mileage/cost facts are explicit.
2. **Ownership enforced in the persistence boundary.** Most event and vehicle repositories include the principal constraint, substantially reducing accidental cross-user access.
3. **Low accidental complexity in CRUD services.** Event services share a recognisable, small CRUD shape; mutating operations do not blindly trust a vehicle ID from the payload.
4. **Useful separation of concerns.** REST DTOs, mappers, reporting, notifications, calculators, error translation, and audit are separated from JPA entities.
5. **Good baseline operational ingredients.** Liquibase, H2 test profile, non-root containers, reverse proxy, health/info endpoints, audit retention, e-mail templates, and i18n are all already present.
6. **Architecture enforcement exists.** ArchUnit creates a guardrail against a common monolith failure mode: services/repositories importing web code.
7. **A security model exists and is mostly centralised.** JWT is stateless, public paths are narrowly listed, admin-only routes use method security, and normal data ownership is repository-constrained.

## Health check and current codebase state

### Overall rating: **critical / not releasable from this branch**

| Dimension | Rating | Evidence and effect |
| --- | --- | --- |
| Build reproducibility | **Critical** | `./mvnw compile` fails before compilation. Maven reports 11 dependencies with no version: Springdoc webmvc core, Commons IO, Jakarta Cache API, Hibernate cache/core/metamodel, Zalando Problem, three JJWT artifacts, and Hibernate Envers. No unit or integration test can run until the BOM/dependency setup is fixed. |
| Framework compatibility | **Critical** | 184 legacy `javax.*`/old security matches remain while the POM declares Boot 3/Hibernate 6. The security adapter and `antMatchers` will not survive the intended Spring Security 6 stack. |
| Functional correctness | **High** | `RefuelServiceImpl.editRefuel` queries `VehicleRepository.findByIdAndOwnerIsCurrentUser(id)` using a refuel ID, maps a `Vehicle` through a refuel updater, and passes it to `refuelRepository.save`. This is type-inconsistent and should fail compilation once Maven gets that far; it needs a targeted regression test and correction. |
| Data integrity | **High** | Vehicles are deleted directly, but all five event tables have foreign keys to vehicles with no cascade specified in Liquibase and no JPA cascade/orphan removal in the model. A vehicle with events will normally fail deletion at the database layer. Lookup deletion can likewise conflict with referenced rows. The desired business deletion/retention policy must be decided explicitly. |
| Security/secrets | **High** | A production JWT base64 secret is committed in `application-prod.yml`, contrary to its own comment. Rotate it and source secrets exclusively from protected deployment configuration. Development CORS permits all origins while allowing credentials; safe only for local development and must not leak into production. |
| Test confidence | **Low** | The test tree has 21 Java test files, mainly JHipster account/user/security/audit/configuration coverage. There are no identified tests for vehicle CRUD, any of the five event CRUD services/resources, reminder scheduling, reports, calculators, image storage, ownership-negative cases, or migration compatibility. Build failure makes all existing test results stale/unverified. |
| CI/release confidence | **Low** | GitLab CI runs `test` and package/deploy only on tags; branch/MR changes receive no configured compile/test gate. It skips integration verification and does not visibly enforce coverage, style, dependency vulnerability, or container scanning. |
| Performance/scalability | **Medium** | `VehicleRichMapper` loads insurance, inspection, routine-service, repair, and refuel collections separately per vehicle. Event lookup, multi-vehicle statistics, and reports therefore create at least five extra selects per vehicle (plus potentially lazy association reads), a textbook N+1 shape. Collection loading and calculation are in-memory rather than query/projection based. |
| Observability/operations | **Medium** | Actuator/audit/logging foundations exist, but production disables Prometheus export and there is no evidence of alerting, tracing, job observability, mail retry/outbox behavior, or CI-driven operational verification. |
| Maintainability | **Medium–High** | Code is compact and familiar, but platform drift, handwritten mapping duplication, mixed injection styles, controller-level persistence for lookup features, and stale TODOs leave the system expensive to safely change. |

### Confirmed build failure

The compile command was executed with the repository Maven wrapper. It did not reach Java compilation. Maven rejected the POM because the `jhipster-dependencies` 8.0.0 BOM does not manage the 11 coordinates currently left versionless. This means the already present `target/carcare-*.war` must be treated as stale and provides no evidence that the branch is runnable.

The first corrective investigation should establish whether the intended solution is to align the dependency set with JHipster 8/Boot 3, restore exact explicit compatible versions, or revert to a compatible framework BOM. Do not mass-convert imports first: an unparseable dependency graph prevents trustworthy compiler feedback and hides the real migration surface.

### Architecture and design debt

#### Platform migration is partial and internally inconsistent

- The POM declares the modern platform but dependency management is inconsistent.
- JPA, Bean Validation, Servlet, transaction, nullable annotation, and test code still use `javax.*`; Spring Boot 3 requires the Jakarta namespace for the affected APIs.
- `SecurityConfiguration` extends `WebSecurityConfigurerAdapter` and chains `antMatchers`; this is legacy Spring Security configuration. It also contains duplicated session-management/header configuration blocks, including a `frameOptions().deny()` followed by `frameOptions().disable()` sequence that deserves a security-policy review.
- The configuration contains other era-specific properties/classes (`MySQL5InnoDBDialect`, `SpringPhysicalNamingStrategy`, legacy profile processing, old Spring MVC favicon setting) that must be validated against Boot 3 rather than copied forward blindly.

#### Domain and persistence boundaries need explicit decisions

- Event records are separate tables with duplicated lifecycle fields and handwritten near-identical services/mappers. This is understandable but makes cross-cutting changes (validation, deletion, filters, audit, pagination) fivefold.
- `VehicleRichMapper` is both a mapper and an orchestration/query component with twelve constructor dependencies. It crosses the intended mapper/persistence boundary, hides multiple database queries inside DTO conversion, and drives the N+1 behavior.
- There is no visible pagination or date-range query at the persistence layer for event lists; large fleets or long histories will be fully materialized.
- The current model does not encode a clear deletion policy. Foreign-key constraints protect integrity, but the REST service advertises a delete action that likely fails once a vehicle has history.
- Event-level mileage is accepted independently. There is no visible invariant enforcing non-decreasing mileage across a vehicle timeline, valid insurance date order, a required next service criterion, or sensible refuel-volume/mileage relationships.

#### API and validation debt

- Most business resource request bodies lack `@Valid`; only account/user/auth management paths are identified with Bean Validation entry-point annotations. Entity annotations alone do not validate an unannotated controller DTO payload.
- Routes use singular, inconsistent path naming (`/api/vehicle`, `/api/fuel-type`) and have no API version prefix. This is manageable internally, but compatibility must be planned if the client is separately versioned.
- Lookup and reminder-advance resources bypass a service layer. The code itself marks these with “extract service” TODOs.
- Response conventions are inconsistent: Vavr `Either` exceptions in report paths, `Optional`/404 in CRUD, and direct primitive/body responses in lookup paths. Standardize error and validation contracts during API hardening, but avoid breaking the client without a compatibility strategy.

#### Reporting, scheduling, and files

- Reports are generated in memory and returned as byte arrays. This is simple but has no explicit size limits, streaming strategy, background-job mechanism, or audit trail for large reports.
- The daily reminder process runs in-process and sends mail asynchronously. It has no idempotency record/outbox, retry strategy, timezone business rule, or distributed lock; a scaled or restarted deployment could duplicate/miss notifications. It currently bases reminders only on exact configured offsets.
- Image storage accepts raw bytes plus caller-provided MIME type, saves to a local shared volume, and returns an empty string on errors. There is no visible size limit, content verification/virus scan, transaction compensation, or storage abstraction. Updating any vehicle image deletes the old filename before the database change commits, creating a possible file/database inconsistency if the transaction later fails.

#### Delivery and configuration debt

- CI executes only on tags, uses Docker-in-Docker and deprecated GitLab token patterns, and publishes mutable `latest` images. It has no merge-request feedback loop.
- The app is coupled to a prebuilt sibling client artifact in a private GitLab Maven registry. Server build/release health depends on client artifact availability and compatible API contracts, neither of which is checked here.
- Product/app versions disagree in places (POM/app image `1.3.11`, API metadata `1.3.10`, client `1.2.5`). Establish a single release/versioning policy.
- Liquibase’s active business changes date to 2019. Schema is stable, but old XML schemas and database dialect assumptions raise migration risk. Add every future change in a new changelog; never rewrite deployed changesets.

### Code quality signals

The code graph finds no algorithmic complexity hotspot comparable to the platform and data-access concerns. Most services are only about 50–75 lines and have a simple call shape. The primary structural hotspot is `VehicleRichMapper`: it owns twelve dependencies and five event repository loads. The most reused paths include user login lookup, DTO event/cost accessors, and generic service save methods. This supports an incremental refactor: protect behavior and replace seams one at a time rather than attempting a wholesale rewrite.

Explicit source TODOs are limited but meaningful:

- finish the refactor/migration; add impersonation and maintenance mode; reconsider the domain model;
- repair English e-mails;
- move random-data code to test data;
- extract services from fuel type, insurance type, and reminder-advance resources;
- refactor image storage, mileage calculation, and `UuidProvider`.

## Recommended roadmap framing and sequencing constraints

This is not the implementation plan, but the following order is a strong constraint for any viable roadmap.

1. **Stabilize the platform and build.** Make the POM resolvable with one consistent Spring Boot/JHipster dependency set, delete stale target assumptions, then compile. Convert Jakarta APIs and security configuration in compiler-guided, test-backed slices. Establish a clean `./mvnw verify` baseline before changing business architecture.
2. **Create behavioral safety nets.** Add focused tests around authentication/authorization, owner isolation, each event CRUD operation, refuel update, vehicle deletion policy, reminder selection, report output/content type, calculators, and image failure behavior. Use real H2/Liquibase integration tests where ownership and foreign keys matter.
3. **Resolve correctness/security/data policies.** Fix the refuel edit defect; rotate/remove source-controlled production secrets; choose and implement a vehicle/event/lookup deletion policy; define validation and domain invariants. This phase should preserve client compatibility deliberately.
4. **Refactor persistence/read models.** Replace the rich-mapper repository fan-out with explicit query/projection/read-model services or bounded fetch plans; add pagination/date filters and query indexes as measurements warrant. Keep ownership filtering at a single auditable boundary.
5. **Harden API and business seams.** Move direct repository controllers behind application services, consolidate common event behavior only where it genuinely reduces duplication, standardize error/validation responses, and version/contract-test the server-client boundary.
6. **Modernize operations and delivery.** Add branch/MR compile-test-verify gates, dependency/security scans, immutable image publishing, health/readiness/metrics policy, secret management, and a robust reminder delivery model (idempotency/retry/observability) before scaling.

## Decisions the roadmap must obtain from stakeholders

The following cannot be safely inferred from code and materially affect the refactor:

- Is this one-person vehicle tracking, a multi-tenant fleet product, or both? That determines tenancy, roles, limits, and audit expectations.
- When a vehicle is deleted, should its history be blocked, soft-deleted, anonymized, cascaded, or archived? Are reports/audit legally retained?
- What are the authoritative API compatibility guarantees with the separately released React client? Can paths/payloads change, and how are coordinated releases made?
- Are scheduled reminders allowed to be approximate/at-least-once, or must they be exactly-once and timezone-aware? What happens after e-mail failure?
- What scale is expected for users, vehicles, events, attachments, and report size? This determines whether the present local image store/in-memory report model can remain.
- Is moving from a JHipster monolith to a more current generated baseline desired, or is the goal to keep the monolith and selectively modernize it? A big-bang regeneration is high risk while behavior is untested.

## Definition of a healthy baseline

A refactor should not be considered underway until all of the following are true:

- `./mvnw compile`, `./mvnw test`, and `./mvnw verify` run from a clean checkout and use the declared Java version;
- the Jakarta/Spring Security 6 migration has no legacy API compilation escape hatches;
- the current API has contract/integration coverage for all business resource groups and ownership-negative cases;
- a production secret scan finds no active credentials or signing keys in source/config templates;
- the deletion/retention and reminder-delivery policies are documented and enforced by tests/database constraints;
- CI runs the required verification on merge requests/branches, not only after a tag is created;
- production deployment config, app metadata, server version, and client artifact contract are aligned and traceable.

## Evidence trail

Primary evidence is in `pom.xml`, `src/main/java`, `src/test/java`, `src/main/resources/config`, `src/main/resources/config/liquibase`, `.gitlab/gitlab-ci.yml`, and `src/main/docker`. Particularly relevant source locations are `SecurityConfiguration`, the event service implementations, `VehicleRichMapper`, `VehicleServiceImpl`, `ReminderServiceImpl`, `ReportServiceImpl`, `StatisticServiceImpl`, and the Liquibase business changelog. The Spring Security compatibility assessment was cross-checked against the official Spring Security 6.5 reference, which configures `SecurityFilterChain` and `requestMatchers` rather than `WebSecurityConfigurerAdapter`/`antMatchers`.
