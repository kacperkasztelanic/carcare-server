---
date: 2026-08-25T19:14:44+02:00
researcher: Claude (Opus 5)
git_commit: a125960adacfbea7d72a0ee772d2c8db740e75ff
branch: refactor
repository: server (gitlab.com/kkasztel_carcare/server)
topic: "F-04 — restoring the integration-test Spring context under H2"
tags: [research, codebase, test-context, hibernate6, h2, liquibase, spring-security-6, schema-validation]
status: complete
last_updated: 2026-08-25
last_updated_by: Claude (Opus 5)
---

# Research: F-04 — restoring the integration-test Spring context under H2

**Date**: 2026-08-25T19:14:44+02:00
**Researcher**: Claude (Opus 5)
**Git Commit**: `a125960adacfbea7d72a0ee772d2c8db740e75ff`
**Branch**: `refactor`
**Repository**: server

## Research Question

What stands between HEAD and `./mvnw verify` booting a Spring context and running the
integration suite on its merits? Scope agreed up front: context-load blockers **plus** the
`standaloneSetup`-versus-full-context-MockMvc question. Evidence method agreed up front:
**boot a probe context** rather than reason statically.

## Method — why these findings are stronger than the prior record

`src/main` compiles green; only `src/test` does not. That asymmetry makes the application
context bootable *without fixing a single test source*: a throwaway `main` was compiled
against a snapshot of `target/classes` and run against the resolved test-scope classpath
with `--spring.config.location` pointed at a copy of the test YAML. Each failure was fixed
in the copy and the boot repeated, yielding an **ordered, observed** blocker chain instead
of a predicted one.

The probe deliberately calls `new SpringApplication(CarcareApp.class).run(...)` and **not**
`CarcareApp.main()`, because `main()` calls `DefaultProfileUtil.addDefaultProfile(app)`
(`CarcareApp.java:56`) which would activate the `dev` profile. `@SpringBootTest` never calls
`main()`, so tests run with **no** active profile — the probe reproduces that, and the final
run confirms `activeProfiles=` (empty).

Probe artifacts live in the session scratchpad, not the repo. Nothing under `src/` or
`context/` was modified during research.

## Summary

**The context now loads — 598 bean definitions.** Five conclusions, in order of how much
they should change the plan:

1. **The context-load blockers are three configuration lines. The compile errors are a
   separate workstream.** Zero test sources were touched to get a context up. The 37 javac
   errors block `test-compile`, not context load, so the two can be planned and executed in
   parallel rather than sequenced.

2. **Two of the three blockers are previously-unrecorded F-03 regressions**, and they share
   one root cause: the test YAML *shadows* main's instead of layering onto it, while F-03
   moved two settings out of Java and into main's YAML only. This is a systemic defect with
   four observed consequences, not two isolated bugs.

3. **The CLOB unknown — the roadmap's single "fix is not yet known" item — is solved, and it
   was never an H2-version problem.** `columnDefinition = "clob"` sets only the DDL type
   *name*; Hibernate 6 validates JDBC type *codes*, and `length = 65535` resolves to VARCHAR
   because `H2Dialect.getMaxVarcharLength()` is `1048576`. One dialect-level change clears
   all five columns simultaneously — verified.

4. **F-03's `.anyRequest().permitAll()` fix is runtime-confirmed.** `GET /` returns **200**
   over a real socket through the real filter chain. The carried-in review item ("reasoned
   from bytecode but has never served a request") is discharged by this research.

5. **The suite contains no coverage of this application.** 14 of 19 controllers are
   untested; no test file references a vehicle, any of the five event types, reports,
   statistics, or reminders. Restoring these 11 ITs restores JHipster scaffolding. That
   reframes what "F-04 done" should mean and is a decision for framing/planning.

## Detailed Findings

### A. The verified blocker chain

Each row was observed by running the probe, not inferred. Fixing row *n* produced row *n+1*.

| # | Blocker | Observed failure | Fix applied in probe |
|---|---|---|---|
| 1 | `spring.liquibase.change-log` absent from test YAML | `Liquibase failed to start because no changelog could be found at 'classpath:/db/changelog/db.changelog-master.yaml'` | add `change-log: classpath:config/liquibase/master.xml` |
| 2 | `tech.jhipster.domain.util.FixedH2Dialect` unresolvable | `Unable to resolve name [tech.jhipster.domain.util.FixedH2Dialect] as strategy [org.hibernate.dialect.Dialect]` → `ClassNotFoundException` | point at an H2 dialect |
| 3 | Schema validation, 5 columns | `Schema-validation: wrong column type encountered in column [details] in table [inspections]; found [character (Types#CLOB)], but expecting [clob (Types#VARCHAR)]` | dialect with `getMaxVarcharLength()` < 65535 |
| 4 | `application.security.content-security-policy` null | `IllegalArgumentException: policyDirectives cannot be null or empty` at `SecurityConfiguration.java:67` | add the key |
| — | **result** | **`CONTEXT LOADED OK`, `beanDefinitions=598`, `activeProfiles=` (empty)** | — |

Blocker 2 is stronger than the roadmap records. `jhipster-framework` is not merely missing
`FixedH2Dialect` in 8.0.0 — the artifact is **absent from the dependency graph entirely**
(no match for `jhipster` in the resolved test classpath). The class is unreachable at any
version, so restoring it by hand would mean vendoring a copy.

Note also that blocker 1 masks blockers 2–4 completely: Liquibase is constructed before the
`EntityManagerFactory`, so the dialect error cannot surface until the changelog is found.
Anyone attacking this by fixing the dialect first — the order the roadmap implies — will see
no improvement at all and may wrongly conclude the dialect fix was wrong.

### B. Root cause of blockers 1 and 4 — the test YAML shadows main's

`src/test/resources/config/application.yml` and `src/main/resources/config/application.yml`
occupy the **same classpath location**, `config/application.yml`. Spring resolves that
location to a single resource, and `target/test-classes` precedes `target/classes`, so under
test **main's file never loads at all**. The test file is not an overlay; it is a total
replacement. Every key main's file provides and test's does not is simply absent.

That has always been true. What changed is what main's YAML became responsible for. F-03
moved two settings out of Java, where tests inherited them for free, into main's YAML, where
tests cannot see them:

- **Liquibase changelog.** At the known-good baseline, `LiquibaseConfiguration.java:43` did
  `liquibase.setChangeLog("classpath:config/liquibase/master.xml")` — hardcoded in Java,
  independent of any YAML. Commit `4542b32` deleted that file in favour of Boot auto-config,
  and the path now exists only at `src/main/resources/config/application.yml:57`.
- **Content-Security-Policy.** `ApplicationProperties.java:65` declares
  `private String contentSecurityPolicy;` with **no default**. JHipster's `JHipsterProperties`
  supplied one in Java. The value now exists only at
  `src/main/resources/config/application.yml` under `application.security`.

Two further consequences of the same shadowing were observed live, and neither prevents the
context from loading — which is exactly why they are dangerous:

- **Actuator moves.** The test YAML has **no `management:` block at all**, so
  `management.endpoints.web.base-path: /management` does not apply and the endpoints fall
  back to `/actuator`. Probe: `/management/health` → **404**, `/actuator/health` → responds.
  Exposure also reverts to defaults, so `/actuator/info` → **404**.
- **Health goes red.** Main sets `management.health.mail.enabled: false`; test does not, so
  the mail health indicator tries `localhost:25` and `/actuator/health` returns **503**.

Both are latent traps for precisely the full-context MockMvc conversion recommended in §F —
e.g. `AuditResourceIT` targets `/management/audits`, a path that does not exist under the
test configuration. Today `standaloneSetup` hides this by bypassing the servlet mapping.

**This is the single most consequential finding for planning**, because the fix is a
structural choice, not a line edit:

- **Option 1 — replicate the missing keys into the test YAML.** Minimal, explicit, matches
  what JHipster effectively did. Leaves the shadowing trap in place: every future setting
  added to main's YAML is a new landmine, discovered only when something breaks.
- **Option 2 — stop shadowing.** Rename the test file to `application-test.yml`, activate a
  `test` profile, and let main's `application.yml` load as the base with test values layered
  on top. Eliminates the entire bug class permanently and aligns with the `contexts: test`
  Liquibase setting already present. Costs an `@ActiveProfiles("test")` decision across the
  ITs (or a surefire/failsafe property) and a careful audit of which main values are
  genuinely wrong for tests (the MariaDB datasource above all).

Option 2 is the structurally correct answer; Option 1 is the smaller change. This is a real
tradeoff and belongs in the plan, not in an implementer's head.

### C. The CLOB/CHARACTER mismatch — solved, and mis-hypothesised until now

Two plausible-sounding explanations were tested and both are wrong. The prior record framed
this as "CLOB vs CHARACTER", suggesting an H2 1.x→2.x type-naming change; a second hypothesis
raised during this research held that H2 2.x reclassified `TEXT` from a CLOB alias to a
VARCHAR alias, so Liquibase's `type="TEXT"` now yields a VARCHAR column. **The live schema
refutes both.** Queried through `DatabaseMetaData` after Liquibase ran its real changelog:

| Table | Column | `TYPE_NAME` | code |
|---|---|---|---|
| `INSPECTIONS` | `DETAILS` | `CHARACTER LARGE OBJECT` | 2005 `Types.CLOB` |
| `INSURANCES` | `DETAILS` | `CHARACTER LARGE OBJECT` | 2005 `Types.CLOB` |
| `REPAIRS` | `DETAILS` | `CHARACTER LARGE OBJECT` | 2005 `Types.CLOB` |
| `ROUTINE_SERVICES` | `DETAILS` | `CHARACTER LARGE OBJECT` | 2005 `Types.CLOB` |
| `VEHICLES` | `NOTES` | `CHARACTER LARGE OBJECT` | 2005 `Types.CLOB` |

All five are genuine **CLOBs**. It is true that H2 2.x turns a *raw SQL* `TEXT` into
`CHARACTER VARYING`, but that never happens here: Liquibase does not pass `TEXT` through to
the database. Its own type system maps `TEXT` to `CLOB` on H2, so the column is created as a
LOB. Testing `CREATE TABLE t (a TEXT)` directly answers a different question than the one
that governs this schema.

The full error text points the same way:

```
wrong column type encountered in column [details] in table [inspections];
found [character (Types#CLOB)], but expecting [clob (Types#VARCHAR)]
```

Read the JDBC type codes, not the names. The database column *is* a CLOB (`Types#CLOB`). The
**entity mapping** resolves to `Types#VARCHAR`. The word `clob` in "expecting" is merely the
literal string from `columnDefinition`, which controls the DDL type name and nothing else.
The bare `character` on the "found" side is not a `CHARACTER` column either — Hibernate keeps
only the first token of the reported type name, so `CHARACTER LARGE OBJECT` prints as
`character`. Both halves of this message invite the wrong conclusion, which is most of why it
went unexplained for so long.

Mechanism, confirmed directly: all five fields declare `length = 65535`, and
`new H2Dialect().getMaxVarcharLength()` returns **`1048576`**. Since 65535 ≤ 1048576,
Hibernate 6 maps `String` → VARCHAR. Liquibase created real CLOB columns. Hence the mismatch.

The five columns — matching the roadmap's count exactly:

| Entity field | Table.column |
|---|---|
| `Inspection.java:84` | `inspections.details` |
| `Insurance.java:95` | `insurances.details` |
| `Repair.java:77` | `repairs.details` |
| `RoutineService.java:88` | `routine_services.details` |
| `VehicleDetails.java:69` | `vehicle_details.notes` |

All five carry the identical `@Column(..., length = 65535, columnDefinition = "clob")` shape,
so they share one root cause and one fix. Validation reports only the first failure, which is
why the record only ever named `inspections.details`. The fifth pair is the one a
table-scoped search misses: `VehicleDetails` is `@Embeddable`, so its `notes` column lands on
the `vehicles` table rather than on an event table of its own.

**Why restoring `FixedH2Dialect` by hand was never going to work.** Decompiling the 7.8.1 jar
shows the class has a single constructor whose entire body is
`registerColumnType(Types.FLOAT, "real")`. It is a Hibernate-5-era `FLOAT` registration fix
and **never touched CLOB, TEXT, or any character type**. The roadmap records that a
hand-restored copy "also failed" and treats that as evidence the problem is deep; in fact the
class was simply irrelevant to it. Its `FixedPostgreSQL*Dialect` siblings survive into 8.0.0
— only the H2 one was dropped. Worth noting the probe also settles the follow-on worry: the
full schema validates with **only** the max-varchar-length override, so Hibernate 6 needs no
equivalent of the old `FLOAT` fix.

**Dead changelog.** `changelog/20190102222057_changelog.xml` contains exactly the
`modifyDataType ... newDataType="clob"` changesets that would have made this consistent, but
`master.xml:16-17` includes only `00000000000000_initial_schema.xml` and
`20190922082653_changelog.xml`, and nothing anywhere references `20190102222057`. It is
orphaned — it has never run in any environment. Anyone reading the changelog directory
without checking `master.xml` will believe the columns were migrated to `clob`; they were not,
and the schema only ends up with CLOBs because Liquibase maps its own `TEXT` type that way.

**Verified fix**: a dialect subclass overriding `getMaxVarcharLength()` to `65534` promotes
all five mappings to CLOB. Validation then passes and the context loads. This is a real,
documented Hibernate 6 hook, and it is essentially what a "FixedH2Dialect" is.

Resolution options, with what is proven and what is not:

- **A — test-only dialect subclass (proven).** Zero production blast radius, keeps
  `hbm2ddl.auto: validate` strict as the ownership boundary requires. Weakness: it is a
  fixture whose purpose is to make validation pass.
- **B — annotate the five fields** (`@Lob`, or `@JdbcTypeCode(SqlTypes.CLOB)`). Makes the
  mapping honest — the columns really are LOBs. **Not tested here**; it changes prod runtime
  binding (MariaDB `LONGTEXT`) and so needs verification against a real MariaDB, which this
  research could not do.
- **C — change Liquibase to VARCHAR.** Rejected: a production schema migration to satisfy a
  test.
- **D — test-context-gated changeset.** Possible via the existing `contexts: test`, but makes
  the test schema differ from prod — the same objection as A with more machinery.
- **E — relax validation.** Explicitly forbidden by F-03's ownership boundary.

Recommendation: **A for F-04** (smallest blast radius, unblocks everything), with B recorded
as the honest long-term fix to be validated against MariaDB in a later slice.

**Residual risk worth stating plainly:** `hbm2ddl.auto: validate` appears **only** in the test
config. Main sets `ddl-auto: none`; dev and prod set no `hbm2ddl.auto` at all. Schema
validation is therefore a test-only gate, and **the MariaDB production schema has never been
validated against the entity model by anything**. Option A leaves that unchanged. F-04 should
not be read as evidence that the prod schema is correct.

### D. Compile surface — 37 errors, 7 files, one trap

`./mvnw test-compile` reports 37 errors: the 21 known import lines plus their downstream
`cannot find symbol` consequences.

| File | Errors |
|---|---|
| `repository/timezone/DateTimeWrapper.java` | 18 |
| `web/rest/TestUtil.java` | 6 |
| `service/MailServiceIT.java` | 7 |
| `web/rest/errors/ExceptionTranslatorTestController.java` | 4 |
| `web/rest/UserResourceIT.java` | 3 |
| `repository/CustomAuditEventRepositoryIT.java` | 1 |
| `security/jwt/TokenProviderTest.java` | **0** |

**The trap**: `TokenProviderTest.java:18` imports `javax.crypto.SecretKey`, which is
JDK-owned (`java.base`, JCE) and must **never** be rewritten to `jakarta`. It produces zero
errors precisely because it is already correct — mirroring the two JDK-owned imports F-01
flagged in main (`javax.sql.DataSource`, `javax.crypto.SecretKey`). A blind
`javax.*`→`jakarta.*` sweep breaks it. **20 of the 21 lines convert; this one does not.**

Everything else is mechanical, and — importantly — **needs no pom change**:

- `org.thymeleaf.spring5.SpringTemplateEngine` → `org.thymeleaf.spring6.SpringTemplateEngine`.
  The resolved artifact is `thymeleaf-spring6:3.1.2.RELEASE`, already on the classpath.
- `javax.mail.*` → `jakarta.mail.*`. Resolved via `org.eclipse.angus:jakarta.mail:1.1.0`,
  pulled by `spring-boot-starter-mail` at **main** scope, so already present.

No other Boot 3 / JUnit / Mockito breakage exists: no `@RunWith`/`SpringRunner` anywhere, no
`@MockBean` usage, and both `MockitoAnnotations.initMocks` and `MediaType.APPLICATION_JSON_UTF8`
are deprecated-but-present in the resolved jars.

### E. Configuration keys — dead, valid, and vestigial

- `hibernate.id.new_generator_mappings: true` (`application.yml:39`) — **removed in
  Hibernate 6**; the constant is absent from `AvailableSettings` in `hibernate-core-6.2.13`
  and present in every 5.x jar. Silently ignored, not fatal. Dead config; delete.
- `SpringImplicitNamingStrategy` and `CamelCaseToUnderscoresNamingStrategy` — **both valid**,
  confirmed present at those exact FQNs in the resolved `spring-boot-3.1.5` and
  `hibernate-core-6.2.13` jars. No change needed.
- `spring.main.allow-bean-definition-overriding: true` — **vestigial, confirmed by
  experiment.** Re-running the probe with `--spring.main.allow-bean-definition-overriding=false`
  still yields `CONTEXT LOADED OK`, 598 beans. Nothing depends on it; it can be deleted.
- `logback.xml` — the `javax.mail` and `javax.xml.bind` logger declarations no longer match
  any package under the Jakarta namespace, so that WARN suppression is now inert. Cosmetic.
- **Side finding**: JaCoco's `prepare-agent` binds to the `argLine` property, but neither the
  surefire nor failsafe `<configuration>` references `${argLine}`. Coverage instrumentation
  is likely not attached at all. Not a blocker; worth a follow-up.

### F. The `standaloneSetup` question — convert, but for a different reason than recorded

The recorded justification does not survive contact with the source, and the real reason is
stronger.

**The ObjectMapper story is largely wrong.** Four of the six files already inject the real,
Boot-autoconfigured converters: `AuditResourceIT.java:66-69`, `UserResourceIT.java:108-112`,
`ExceptionTranslatorIT.java:36-39` wire the autowired `MappingJackson2HttpMessageConverter`,
and `AccountResourceIT.java:79-82` wires the full autowired converter array. Only
`UserJwtControllerIT.java:49-51` and `AccountResourceIT`'s second instance (`:83-85`) fall
back to the standalone default. Moreover `LoginVm` is a plain `@Value` of
`String/String/Boolean` — no Vavr, no Hibernate proxies — and `ParameterNamesModule` (the
mechanism that resolves its generated constructor) is auto-registered in **both**
configurations. **The "3 LoginVm deserialization failures" attribution does not fit the
evidence.** AGENTS.md already records one prior false conclusion about `LoginVm`; this looks
like a second reading of the same tea leaves. Treat that history as unreliable.

**The real reason to convert is an authorization hole.** `UserResourceIT.java:106` constructs
`new UserResource(...)` directly — a raw POJO with no Spring AOP proxy. `UserResource` carries
`@PreAuthorize("hasRole(\"ROLE_ADMIN\")")` on four methods (`:96`, `:125`, `:164`, `:188`).
With no proxy, **method security is never enforced**, and none of those tests carry
`@WithMockUser` anyway. The same applies to `AuditResourceIT` against
`hasAuthority(ADMIN)`-gated `/management/audits`. These tests currently assert that
admin-only endpoints work while performing no authorization check whatsoever.

Corrections to two prior assumptions:

- **`WebConfigurerTest` does not belong in this group.** It has no `@SpringBootTest` and
  constructs `WebConfigurer` directly to unit-test `corsFilter()`. Its `standaloneSetup` use
  is an implementation detail of testing a filter. Leave it alone.
- **`ExceptionTranslatorIT` is not standalone-dependent**, contrary to the natural hypothesis.
  `ExceptionTranslator.java:139,150` declares `@ExceptionHandler` for `AccessDeniedException`
  and `AuthenticationException`, which `DispatcherServlet` resolves *before* Spring Security's
  `ExceptionTranslationFilter` would see them. It converts cleanly.

Conversion cost: `UserJwtControllerIT`, `AuditResourceIT`, `ExceptionTranslatorIT` trivial;
`AccountResourceIT` moderate (two MockMvc instances, one with a mocked `UserService`, needing
`@MockBean` with attendant context-caching care); **`UserResourceIT` risky** — converting it
will make `@PreAuthorize` actually fire, so currently-passing tests will fail until
`@WithMockUser(authorities = "ROLE_ADMIN")` is added. That is a genuine behavioural decision,
not a plumbing refactor.

**Recommendation: convert all except `WebConfigurerTest`**, and expect `UserResourceIT` to
change outcomes.

### G. Runtime security verification — the carried-in review item, discharged

Real HTTP requests against the real filter chain, at a real port:

| Path | Status | |
|---|---|---|
| `/` | **200** | the `.anyRequest().permitAll()` fix — confirmed |
| `/index.html`, `/favicon.ico`, `/robots.txt`, `/manifest.webapp`, `/service-worker.js` | **200** | all five |
| `/api/vehicle`, `/api/account` | **401** | correct |
| `/api/admin/users`, `/management/audits` | **401** | correct |
| `/management/health`, `/management/info` | **404** | see §B — actuator is at `/actuator` under test |

Assets confirmed present in `client-1.2.5.jar` under `static/`. Security headers on `/` all
present and correct: `content-security-policy`, `referrer-policy`,
`permissions-policy`, `x-frame-options: DENY`.

No existing test asserts any of this. Nothing in the suite boots the real `SecurityFilterChain`
— no `@AutoConfigureMockMvc`, no `TestRestTemplate`, no `WebTestClient` anywhere. The
regression F-03 fixed would not have been caught, and a future regression of the fix still
would not be.

### H. What the suite actually covers

`grep -rlE "Vehicle|Repair|RoutineService|Inspection|Insurance|Refuel|FuelType|ReminderAdvance|StatisticResource|ReportResource|CostReport|VehicleReport" src/test/java`
returns **zero matches**. 14 of 19 REST controllers have no test at any level. Untested:
all five vehicle event types, Excel/PDF report generation, statistics, and reminder
scheduling — i.e. every reason this application exists. The 11 ITs are JHipster scaffolding
around account/user/auth/audit/JWT/CORS/timezone/mail.

`MailServiceIT` tests the generic mail mechanism with hand-built `User` objects, not the
reminder trigger logic, so it does not cover S-04's subject matter either.

This does not change F-04's mechanics, but it should change expectations set by it: "the
suite runs green" and "the application is verified" are very different statements here.

## Code References

- `src/main/resources/config/application.yml:57` — `change-log`, absent from the test YAML
- `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:65` — `contentSecurityPolicy`, no default
- `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:67` — CSP consumer, throws on null
- `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:89` — `.anyRequest().permitAll()`, now runtime-verified
- `src/test/resources/config/application.yml:29` — `FixedH2Dialect`
- `src/test/resources/config/application.yml:39` — dead `hibernate.id.new_generator_mappings`
- `src/test/resources/config/application.yml:44` — `hbm2ddl.auto: validate`, the only place it is set
- `src/test/resources/config/application.yml:51` — vestigial `allow-bean-definition-overriding`
- `src/main/java/com/kasztelanic/carcare/domain/Inspection.java:84`, `Insurance.java:95`, `Repair.java:77`, `RoutineService.java:88`, `VehicleDetails.java:69` — the five CLOB columns
- `src/main/resources/config/liquibase/master.xml:16-17` — the only two changelogs that run
- `src/main/resources/config/liquibase/changelog/20190922082653_changelog.xml:27,71,137,162,199` — the five columns, declared `TEXT`, created as CLOB
- `src/main/resources/config/liquibase/changelog/20190102222057_changelog.xml` — orphaned `modifyDataType → clob`, never executed
- `src/main/java/com/kasztelanic/carcare/CarcareApp.java:56` — `addDefaultProfile`, why `main()` is not the test path
- `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java:106` — unproxied controller, `@PreAuthorize` never enforced
- `src/main/java/com/kasztelanic/carcare/web/rest/UserResource.java:96,125,164,188` — the unenforced `@PreAuthorize`
- `src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java:18` — do-not-convert `javax.crypto.SecretKey`

## Architecture Insights

- **Config shadowing is the defect class to design against.** Blockers 1 and 4 and both
  actuator surprises are one bug wearing four hats. Any future "move a default from Java into
  main's YAML" repeats it. §B Option 2 removes the class; Option 1 patches instances.
- **Test-only strictness inverts the usual risk.** The only schema validation anywhere is in
  tests. Tests are stricter than production, so a green suite proves less about prod than it
  appears to — and prod carries an unvalidated schema.
- **The suite tests the framework, not the product.** Combined with the above, "F-04 done"
  means "the scaffolding boots", which is a prerequisite for the slices rather than evidence
  about CarCare.

## Historical Context (from prior changes)

- `context/archive/jakarta-platform-migration/change.md` — F-04 handoff; ownership boundary
  assigning the dialect, the five CLOB pairs, test `javax.*`, MockMvc conversion and
  Liquibase ordering to F-04, and forbidding weakening `hbm2ddl.auto: validate`.
- `context/archive/jakarta-platform-migration/reviews/impl-review.md` — the ten findings;
  F1 (`anyRequest()`) is the one discharged in §G.
- Commit `4542b32` — deleted `LiquibaseConfiguration.java`, the proximate cause of blocker 1.
- Commit `6e19b96` — known-good baseline; `LiquibaseConfiguration.java:43` hardcoded the
  changelog path there.
- `context/foundation/roadmap.md:245-284` — the F-04 entry these findings answer.

## Related Research

None — `context/foundation/lessons.md` does not exist, and this is the first research
artifact for this change. F-02 (`golden-baseline-capture`) remains independently `ready`
and parallel.

## Open Questions

1. **Test config: patch or restructure?** §B Options 1 and 2. The plan must choose; this
   determines whether the bug class recurs.
2. **CLOB: test-only dialect (A) now, or honest entity annotations (B)?** A is proven and
   recommended for F-04. B needs a MariaDB run that this research could not perform.
3. **Does `UserResourceIT` assert authorization after conversion?** Converting makes
   `@PreAuthorize` fire for the first time. Adding `@WithMockUser` to keep tests green, or
   adding explicit 401/403 assertions, are different products.
4. **Should F-04 add the runtime security assertions §G performed ad hoc?** Nothing in the
   suite covers the filter chain, so the F-03 fix stays unguarded otherwise.
5. **Is "the 11 ITs pass" the right definition of done for F-04**, given §H? Scope call for
   framing — F-04 as stated is about the context loading, not about coverage.
6. **What happens to the orphaned `20190102222057_changelog.xml`?** Deleting it removes a
   standing trap for anyone reading the changelog directory; wiring it into `master.xml`
   would be a production schema change and is almost certainly wrong. Deletion is a
   documentation decision, not a schema one, but it should be deliberate.
7. **`UserServiceIT`'s uninitialized `@Mock DateTimeProvider`** (`:52-53`, used `:69`) with no
   `MockitoExtension` or `openMocks` — flagged as a latent NPE by static reading, unverifiable
   until the suite actually runs. Watch for it.
8. **JaCoco `argLine` appears unwired** into surefire/failsafe — coverage may not be
   instrumented at all. Out of F-04's scope; worth a follow-up.
