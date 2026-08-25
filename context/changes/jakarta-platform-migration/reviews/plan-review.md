<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Jakarta Platform Migration

- **Plan**: `context/changes/jakarta-platform-migration/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-25
- **Verdict**: REVISE → **SOUND** after triage (all 8 findings fixed 2026-08-25)
- **Findings**: 1 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL |
| Lean Execution | WARNING |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

25/28 paths ✓ (3 are files the plan creates — `QuotedCookieLocaleResolver`, `config/logging/LoggingUtils`, `application-no-liquibase.yml` — none marked NEW in the plan).
Symbols: 18 main / 6 test `tech.jhipster` files ✓ exactly as claimed; `SpringPhysicalNamingStrategy` absent from `spring-boot-3.1.5.jar` ✓; `CamelCaseToUnderscoresNamingStrategy` + `SpringImplicitNamingStrategy` present ✓; `jackson-datatype-hibernate6` managed by `jackson-bom` 2.15.3 ✓; Progress↔Phase contract ✓ (1 heading, 3 phases, 15 steps, no stray checkboxes); brief↔plan ✓.

## Findings

### F1 — Phase 2 gate 2.1 cannot pass inside the plan's own scope

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Phase 2 Success Criteria (2.1) + Desired End State bullet 3
- **Detail**: Running the criterion verbatim, `rg -n 'tech\.jhipster' src/main src/test pom.xml` returns four hits the plan cannot legally clear:
  - `src/test/resources/config/application.yml:29` — `database-platform: tech.jhipster.domain.util.FixedH2Dialect`; "What We're NOT Doing" explicitly defers this to F-04.
  - `src/test/resources/logback.xml:9` — `<logger name="tech.jhipster" level="WARN"/>`; in no phase's file list.
  - `src/main/resources/config/application-dev.yml:14` (`tech.jhipster: DEBUG`) and `application-prod.yml:15` (`tech.jhipster: INFO`) — files are in Phase 2 §1's list, but the §1 contract never mentions logger levels.

  Desired End State bullet 3 ("Main and test sources contain no `tech.jhipster.*` references") makes the same unreachable claim. The implementer hits an unpassable gate at the end of Phase 2 and either widens into F-04's H2 work or quietly waters down the criterion.
- **Fix**: Narrow 2.1 to `! rg -n 'tech\.jhipster' src/main/java src/test/java pom.xml`, add the two `logging.level.tech.jhipster` lines and `src/test/resources/logback.xml` to Phase 2 §1/§7 explicitly, and reword the Desired End State bullet to "except the test-profile dialect, which F-04 owns."
- **Decision**: FIXED — grep narrowed, logger levels + logback.xml folded into Phase 2 §1/§7, end-state bullet reworded

### F2 — Two alert-header namespaces collide in the shared HeaderUtil

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 §1 Contract + Phase 2 §6
- **Detail**: The codebase has two different application names feeding alert headers today:
  - `web/rest/util/HeaderUtil.java:15` — `APPLICATION_NAME = "carcareApp"` (hardcoded, static) → emits `X-carcareApp-alert` / `-params` / `-error`. Imported by 10 resource classes (Vehicle, Repair, Refuel, Inspection, Insurance, InsuranceType, FuelType, RoutineService, ReminderAdvance, Event).
  - `UserResource.java:75` and `ExceptionTranslator.java:44` — inject `@Value("${jhipster.clientApp.name}")` = `'carcare'` → emits `X-carcare-alert`.

  Phase 2 §1 says "Use `spring.application.name` for alert-header application naming" (= `carcare`). Phase 2 §6 says "Extend the existing local utilities" while "Preserve exact alert/error header names." Applied literally to the shared static util, those two instructions rename the headers on 10 endpoints from `X-carcareApp-*` to `X-carcare-*` — a silent client-visible break in a change whose whole point is that nothing moved. The plan never mentions that the local util is used by 10 other files, or that it is `static` with no Spring injection (making it configurable means touching every call site).
- **Fix A ⭐ Recommended**: Leave the local util's `carcareApp` constant alone; add the JHipster-shaped overloads as separate methods taking an explicit `applicationName`, wired from `spring.application.name` only at the three admin/error call sites.
  - Strength: Byte-identical headers on both namespaces; the 10 resource classes are not touched at all, which matches Phase 2 §6's stated goal of covering "the JHipster overloads currently used by user, audit, and error endpoints" and nothing more.
  - Tradeoff: Two naming conventions persist in one class and will read like a bug to the next person; needs a comment explaining why.
  - Confidence: HIGH — both header names are directly readable in source today.
  - Blind spot: Whether client 1.2.5 actually consumes `X-carcareApp-*` is unverified; the client is a separate prebuilt artifact.
- **Fix B**: Unify both call paths on `spring.application.name` and record the header rename as a deliberate, documented contract change.
  - Strength: Removes the wart permanently; one naming rule.
  - Tradeoff: Client-visible; directly contradicts "What We're NOT Doing"; cannot be verified until F-04 restores a bootable context, so it ships unproven.
  - Confidence: MEDIUM — correct in principle, unverifiable within F-03's gates.
  - Blind spot: Client-side i18n alert-toast keys are keyed off these headers.
- **Decision**: FIXED via Fix B — headers unified on `spring.application.name`; recorded as an accepted contract change in §6, "What We're NOT Doing", Key Discoveries, Migration Notes, Phase 3 §5, and new gate 2.5

### F3 — Phase 3 promises to close Boot 2 config seams but only covers Hibernate

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 3 Overview vs. Phase 3 §1–§3
- **Detail**: Phase 3's overview says "Remove the remaining Boot 2/Hibernate 5 configuration seams," but §1–§3 only touch the Jackson module, naming/dialect, and the Liquibase plugin's validation-api. Stale Boot 2 actuator config survives and is silently ignored at runtime — no gate in this plan, and nothing in Phase 3 §4's F-04 handoff list, ever exercises it.

  Confirmed against Spring Boot's own configuration metadata: `application.yml:27-32` sets `management.metrics.export.prometheus.enabled` / `.step`, which carry deprecation level `error` with replacement `management.prometheus.metrics.export.*` since 3.0.0. The same sweep is needed for `management.metrics.enable.*`, `management.metrics.web.server.auto-time-requests` (`application.yml:33-48`), `spring.mvc.favicon.enabled` (`:83-85`, removed in Boot 2.2), and `management.endpoint.jhimetrics` (`:19-20`, which dies with the dependency).

  Net effect if ignored: Prometheus export quietly stops working in prod, and nothing in F-03, F-04, or S-02 would notice.
- **Fix**: Add a Phase 3 §4 "Boot 2→3 property sweep" covering `application.yml`, `-dev`, `-prod` and the test yml, with an automated criterion grepping for the known-removed keys. Renumber the current §4 to §5.
  - Strength: Keeps the fix inside the phase that already claims it; the property names are mechanical and verifiable from Boot's own metadata.
  - Tradeoff: Slightly widens Phase 3; none of it is compile-visible, so it adds work that the phase's own gates can't confirm.
  - Confidence: HIGH — the prometheus rename is confirmed at deprecation level `error` in Boot's `additional-spring-configuration-metadata.json`.
  - Blind spot: Whether anything currently scrapes `/management/prometheus` — note it isn't even in the exposure include list at `application.yml:14`.
- **Decision**: FIXED — new Phase 3 §4 property sweep + gate 3.6; old §4 renumbered to §5

### F4 — Gate 2.2 can never fail; gate 1.3 has no command

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 Success Criteria (2.2), Phase 1 (1.3)
- **Detail**: `./mvnw dependency:tree -Dincludes=tech.jhipster:jhipster-framework` exits 0 whether or not the artifact is on the graph — a non-matching filter just prints an empty tree. As an automated pass/fail gate it asserts nothing. Gate 1.3 ("Exactly one `SecurityFilterChain` bean is declared and the obsolete `JwtConfigurer` source is removed") sits under Automated Verification with no command at all.
- **Fix**: 2.2 → `! ./mvnw -q dependency:tree -Dincludes=tech.jhipster:jhipster-framework | grep -q jhipster-framework`. 1.3 → a concrete command (`rg -c 'SecurityFilterChain\s+\w+\(' src/main/java` returning exactly one hit, plus `! test -e src/main/java/com/kasztelanic/carcare/security/jwt/JwtConfigurer.java`), or move it to Manual.
- **Decision**: FIXED — 2.2 now negated and piped through grep; 1.3 given a runnable command

### F5 — Removed SpringPhysicalNamingStrategy survives in the test config, and is missing from the F-04 handoff

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 §2 (file list) and Phase 3 §4 (handoff list)
- **Detail**: `src/test/resources/config/application.yml:36` still sets `physical-strategy: org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy`. Confirmed by unzipping `spring-boot-3.1.5.jar`: `org/springframework/boot/orm/jpa/hibernate/` contains only `SpringImplicitNamingStrategy` and `SpringJtaPlatform` — the physical strategy is gone, exactly as the plan says for the main configs.

  Phase 3 §2's file list is main `application.yml`, `-dev`, `-prod` and `pom.xml` only. Phase 3 §4's F-04 handoff enumerates the H2 dialect, five CLOB/TEXT pairs, remaining test imports, MockMvc conversion, Liquibase/JPA ordering, and runtime security — this is not on it. F-04 will replace `FixedH2Dialect`, restart, and hit a *second* `ClassNotFoundException` it was never told about.
- **Fix**: Add `src/test/resources/config/application.yml` to Phase 3 §2 for the naming-strategy line only (the `database-platform` line stays F-04's), or name it explicitly in the §4 handoff list.
- **Decision**: FIXED in Phase 3 §2 — test yml added for the naming-strategy line only

### F6 — Legacy JHIPSTER_* alias mechanism is unspecified and collides with ignoreUnknownFields = false

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §1 Contract
- **Detail**: The contract requires "`APPLICATION_*` precedence and ... the corresponding `JHIPSTER_*` environment variable as a fallback alias" for every consumed legacy key, but names no mechanism — and this is one of the plan's own Key Decisions, so the implementer can't treat it as incidental.

  Two facts the plan doesn't record: `ApplicationProperties.java:13` is already `@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)` and already owns `application.data-directory.location` — a live key present in `application-dev.yml:112`, `-prod`, and the test yml, which the plan never mentions.

  With `ignoreUnknownFields = false`, any `application.*` YAML key added without a matching field hard-fails startup. And the only alias mechanism that actually works is a YAML placeholder default (`base64-secret: ${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:<value>}`) leaning on env-over-YAML precedence; a second `@ConfigurationProperties(prefix="jhipster")` shim would reintroduce the namespace the change exists to remove.
- **Fix**: State the placeholder-default mechanism explicitly in the §1 contract, enumerate the consumed legacy variables (cors, ehcache TTL/max-entries, jwt base64-secret + both validity values, mail from/base-url, audit retention, logging use-json-format + logstash block), and note that the existing `application.data-directory` binding must be preserved and every new key declared on the model.
  - Strength: Turns the plan's most-cited decision into something an implementer can execute without guessing; prevents a startup-time binding failure that neither Phase 2 gate would catch (compile only).
  - Tradeoff: Adds a dozen lines of enumeration to the plan.
  - Confidence: HIGH — `ignoreUnknownFields=false` and the existing `data-directory` key are both directly readable in the tree.
  - Blind spot: Which `JHIPSTER_*` variables the actual deployment sets is unknown; only `MAIL_BASE_URL` appears in `application-prod.yml`.
- **Decision**: FIXED — placeholder-default mechanism specified, legacy vars enumerated, data-directory recorded, ignoreUnknownFields relaxed to true

### F7 — The `dev` profile group including `api-docs` is dead config

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 2 §3 Contract
- **Detail**: Nothing in `src/main/java` reads the `api-docs` Spring profile, and `springdoc-openapi-starter-webmvc-ui` is an unconditional dependency (`pom.xml:199`), not scoped to the Maven `api-docs` profile. Springdoc is therefore already active in every profile including prod. Once JHipster's `@Profile("api-docs")` springdoc configuration is gone, the profile group is preserved ceremony with no effect. (AGENTS.md's "`api-docs`: enables springdoc/OpenAPI" is already out of date.)
- **Fix**: Drop the profile group. If the real intent is keeping `/api-docs` and `/swagger-ui` off in prod, gate it with `springdoc.api-docs.enabled: false` / `springdoc.swagger-ui.enabled: false` in `application-prod.yml` instead — note `SecurityConfiguration` currently *bypasses* the filter chain entirely for `/swagger-ui/**` (`:50`).
- **Decision**: FIXED — profile group dropped from Phase 2 §3; criterion 2.4 reworded

### F8 — Locale-resolver contract covers only the read side of the cookie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 §5 Contract
- **Detail**: §5 says only "The local resolver accepts normal and quoted `NG_TRANSLATE_LANG_KEY` values." `AngularCookieLocaleResolver` also *writes* the `%22`-quoted value, and `LocaleConfiguration.java:23-25` registers a `LocaleChangeInterceptor` on `?language=` that triggers that write path. A read-only replacement changes what the client reads back after a language switch.
- **Fix**: Extend the contract to the write path. Also note for the implementer that `CookieLocaleResolver`'s no-arg constructor and `setCookieName` are deprecated in Spring 6 — use `new CookieLocaleResolver("NG_TRANSLATE_LANG_KEY")`.
- **Decision**: FIXED — §5 contract extended to the write path + Spring 6 constructor note

## What holds up

The three-phase ordering (green compile with the bridge → replace ownership → close config seams) is the right shape, the Progress section is mechanically clean against `references/progress-format.md`, the file lists are accurate, and every claim that could be verified held up exactly: 18 main / 6 test `tech.jhipster` files, `SpringPhysicalNamingStrategy` gone from Boot 3.1.5, `CamelCaseToUnderscoresNamingStrategy` present in Hibernate 6.2.13, `jackson-datatype-hibernate6` managed by `jackson-bom` 2.15.3. The honesty about what a compile-only gate can't prove is the plan's strongest feature.
