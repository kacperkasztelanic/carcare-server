<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Jakarta Platform Migration

- **Plan**: `context/changes/jakarta-platform-migration/plan.md`
- **Scope**: Phases 1–3 (full plan)
- **Date**: 2026-08-25
- **Commits**: `8848e36` (p1), `4542b32` (p2), `c379620` (p3), `188536b` (epilogue); baseline `bfd3973`
- **Verdict**: REJECTED at review → **all 10 findings fixed at triage (2026-08-25)**
- **Findings**: 2 critical, 4 warnings, 4 observations — 10 fixed, 0 skipped

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Success criteria re-verification

All 15 automated criteria were re-run independently on `clean` builds (not incremental):

| Criterion | Result |
|---|---|
| 1.1 no `javax.{persistence,validation,servlet,transaction,mail}` in `src/main/java` | PASS |
| 1.2 no removed Spring Security 5 APIs | PASS |
| 1.3 exactly one `SecurityFilterChain`; `JwtConfigurer` deleted | PASS (count = 1) |
| 1.4 / 2.3 / 3.2 `./mvnw clean compile` | PASS |
| 2.1 no `tech.jhipster` refs (scoped) | PASS |
| 2.2 `jhipster-framework` absent from dependency graph | PASS |
| 2.4 `-Pno-liquibase process-resources` | PASS (`-Pdev,no-liquibase` → `active: dev,no-liquibase`) |
| 2.5 no `carcareApp` in `src/main/java` or `src/test/java` | PASS |
| 3.1 no Hibernate 5 / removed naming / legacy dialect / Validation 2 refs | PASS |
| 3.3 `./mvnw clean -PIDE compile` | PASS (15 metamodel classes generated) |
| 3.4 `./mvnw clean -Pprod -Dmaven.test.skip=true package` | PASS (`carcare-1.3.11.war`) |
| 3.5 roadmap + change.md record F-03/S-02/F-04 boundaries | PASS |
| 3.6 no Boot 2 actuator/MVC property names; prometheus block present | PASS |

Notes:
- Zero MapStruct `*MapperImpl` are generated, which is **correct** — the `service/mapper/` classes are
  hand-written `@Service` beans, not MapStruct interfaces. `AGENTS.md`'s description of them as
  MapStruct mappers is stale.
- `hibernate.hbm2ddl.auto: validate` and `ddl-auto: none` are unchanged from baseline; the Liquibase
  changelog directory has zero diff.

## Findings

### F1 — Security chain has no terminal rule; app cannot serve its own frontend

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:74-86`
- **Detail**: `authorizeHttpRequests` covers only `/api/**` and `/management/**`, with no
  `.anyRequest()` terminal rule. The baseline had the same matcher list, but fall-through semantics
  inverted between Spring Security 5 and 6. Verified at bytecode level in the jars on this classpath:
  - `spring-security-core-5.7.3` `AbstractSecurityInterceptor.beforeInvocation`: offset 76
    `CollectionUtils.isEmpty(attributes)` → offset 120 `"Authorized public object %s"` → offset 143
    `aconst_null; areturn` = **PERMIT**.
  - `spring-security-web-6.1.5` `RequestMatcherDelegatingAuthorizationManager.check`: loop at offset
    143 exhausts → offset 175 `getstatic DENY; areturn` = **DENY**.

  Served root assets come from `client-1.2.5.jar`'s `static/` root. These match neither an
  `ignoring()` rule nor any chain rule: `/`, `/index.html`, `/favicon.ico`, `/manifest.webapp`,
  `/service-worker.js`, `/precache-manifest.<hash>.js`, `/robots.txt`. (`/app/**` is safe — it
  contains only `.js`, covered by the bypass.)
- **Failure scenario**: unauthenticated `GET /` on the prod WAR → DENY →
  `ProblemDetailAuthenticationEntryPoint` → 401 JSON instead of the application. Every user is locked
  out, including holders of a valid token, since they can never load the page that would send it.
- **Fix A ⭐ Recommended**: Append `.anyRequest().permitAll()`
  - Strength: Restores the SS5 fall-through exactly; provably a no-op against known-good `6e19b96`.
  - Tradeoff: Keeps a permissive default; every future unmatched path is public.
  - Confidence: HIGH — the two bytecode paths make the equivalence mechanical.
  - Blind spot: Not runtime-confirmed; test context still broken by `FixedH2Dialect` (F-04's gate).
- **Fix B**: Enumerate the seven root assets, then `.anyRequest().denyAll()`
  - Strength: Fail-closed; a new unguarded endpoint cannot leak.
  - Tradeoff: Behavior change beyond migration scope; breaks when the client adds a root asset.
  - Confidence: MEDIUM — depends on the client asset list staying stable across version bumps.
  - Blind spot: Haven't audited what future client builds emit at root.
- **Note**: F-04 owns "runtime security assertions" and would eventually catch this, so it is not a
  broken promise by F-03 — but the fix belongs in the code this change touched.
- **Decision**: FIXED via Fix A — appended `.anyRequest().permitAll()` with a comment recording the
  SS5→SS6 fall-through inversion so it is not "tidied away" later. Still not runtime-confirmed;
  F-04's full-context MockMvc work should assert `GET /` → 200 explicitly.

### F2 — Header rename also changed the i18n key, exceeding the accepted exception

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/util/HeaderUtil.java:57-67`
- **Detail**: The plan accepted exactly one client-visible change: the alert header *name*
  (`plan.md:43` — "The one deliberate exception is the alert/error header *name*"). Two things
  actually changed, because `applicationName` feeds both the header name and the translation key:

  ```
  createEntityCreationAlert("vehicle", id)
    → header  X-carcare-alert          (accepted)
    → value   carcare.vehicle.created  (NOT accepted — was carcareApp.vehicle.created)
  ```

  Both break the client independently:
  1. `../client/src/main/webapp/app/config/notification-middleware.ts:28` matches by suffix —
     `k.toLowerCase().endsWith('app-alert')`. `'x-carcare-alert'` does not end with `'app-alert'`.
     Toasts stop firing silently. Same for `app-params` (:30) and `app-error` (:60).
  2. `../client/src/main/webapp/i18n/en/carcare.json` defines
     `carcareApp.<entity>.{created,updated,deleted}`. The `carcare` subtree has no such keys — it
     holds `entity`/`title`/`edit`/`delete` only. Even with the header detected, the key resolves
     to nothing.

  The plan assigned "confirming the client does not key on the old `carcareApp` prefix" to S-02. The
  answer is that it does, twice — and the second was never in scope to change.
- **Failure scenario**: user creates a vehicle → 201 with `X-carcare-alert: carcare.vehicle.created`
  → middleware finds no matching header → no success toast. Silent; no error, no console warning.
- **Fix A ⭐ Recommended**: Decouple the two uses of `applicationName`
  - Strength: Restores the i18n key prefix to `carcareApp` (never an accepted change) while keeping
    the accepted header rename. Shrinks the client's required change to one line:
    `endsWith('app-alert')` → `endsWith('-alert')`.
  - Tradeoff: Header name and key prefix stop being the same string; needs a comment to survive edits.
  - Confidence: HIGH — restores the plan's actual stated contract.
  - Blind spot: Haven't checked for other `carcareApp` key consumers in `../client` beyond this file.
- **Fix B**: Keep both renames; do the coordinated client change
  - Strength: Ends with one consistent namespace everywhere.
  - Tradeoff: Requires a `../client` change (suffix match + i18n root rename), a release, and a
    `carcare-client.version` bump here before this server build is deployable.
  - Confidence: MEDIUM — client release cadence is outside this repo.
  - Blind spot: Pinned client is 1.2.5 and prebuilt; coupling is a version bump, not a same commit.
- **Decision**: FIXED via Fix A — introduced `HeaderUtil.TRANSLATION_KEY_NAMESPACE = "carcareApp"`,
  separate from the `applicationName` used for header names, with javadoc explaining why the two
  must not be merged. Verified: `createEntityCreationAlert("vehicle","1")` emits
  `X-carcare-alert: carcareApp.vehicle.created`, so the header rename the plan accepted stands while
  the i18n keys resolve against the client's existing bundles again.

  **Still outstanding for S-02 (one line, in `../client`)**: `notification-middleware.ts` matches
  headers by suffix — `endsWith('app-alert')` / `'app-params'` / `'app-error'` at `:28`, `:30`,
  `:60`. Those must become `endsWith('-alert')` / `'-params'` / `'-error'` for toasts to fire, then
  bump `carcare-client.version` here. The i18n bundles now need **no** change.

### F3 — Locale-cookie write path was never implemented

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: `src/main/java/com/kasztelanic/carcare/config/QuotedCookieLocaleResolver.java:30-51`
- **Detail**: `plan.md:187` is explicit — the resolver "both **reads and writes**
  `NG_TRANSLATE_LANG_KEY` ... and re-emits the quoted form on the write path that
  `LocaleChangeInterceptor` triggers." The class overrides only `resolveLocale` (:30),
  `resolveLocaleContext` (:36), and a private read helper (:51). Neither `setLocaleContext` nor
  `toLocaleValue` is overridden, so the inherited `CookieLocaleResolver` write path emits the bare
  value. Corroborated by `javap`: `jhipster-framework-7.8.1`'s `AngularCookieLocaleResolver` has an
  `addCookie` override that did the quoting; 8.0.0 lost it (`quote()` is dead code); `spring-webmvc
  -6.0.13` `setLocaleContext` → `toLocaleValue` → `ResponseCookie` with no re-quoting.
- **Failure scenario**: `GET /api/vehicle?language=pl` sets `NG_TRANSLATE_LANG_KEY=pl` where 7.8.1
  set `%22pl%22`. The read path tolerates both, so server-side round trips look correct and no test
  covers it; the Angular client JSON-decodes the value and sees the difference.
- **Fix**: Override the two Spring 6 hooks instead of hand-rolling the traversal —
  `parseLocaleValue(String)` (strip `%22`, delegate to `super`) and `toLocaleValue(Locale)`
  (delegate to `super`, wrap in `%22`). Restores write-side quoting, inherits Spring's
  invalid-cookie guard and error-dispatch handling, and drops ~40 lines.
- **Note**: Found independently by both review agents.
- **Decision**: FIXED — rewrote the class on `parseLocaleValue`/`toLocaleValue` (76 → 37 lines).
  Verified behaviorally with a throwaway probe against the real Spring 6 resolver:
  `write(pl)` → `%22pl%22`, `write(en)` → `%22en%22`, round-trip `pl` → `pl`, reads of `%22pl%22`,
  `pl`, `en`, `%22en-US%22` → `en_US`, and `-` → default all correct. A malformed cookie is now
  rejected by Spring's inherited `rejectInvalidCookies` guard (which also handles the `/error`
  dispatch) instead of the previous uncaught `IllegalArgumentException`. The `-` → `_`
  normalization from 7.8.1 was preserved explicitly.

### F4 — JWT remember-me validity now defaults to 0

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:88-91`
- **Detail**: `tokenValidityInSeconds` and `tokenValidityInSecondsForRememberMe` are declared with no
  initializer, so both default to `0`. The replaced `JHipsterProperties` carried 1800 / 2592000.
  Dev (`application-dev.yml:88`) and prod (`application-prod.yml:105`) set the remember-me key, so
  production is unaffected; `src/test/resources/config/application.yml` sets
  `token-validity-in-seconds` (:103) but never the remember-me key.
- **Failure scenario**: in the test context, `POST /api/authenticate` with `rememberMe:true` →
  `new Date(now + 0)` → `exp` equals the issue instant → the next request 401s. Dormant behind the
  broken context; surfaces as a mystery failure during F-04's test repair.
- **Fix**: Initialize the two fields to `1800` and `2592000` in `ApplicationProperties.Jwt`, matching
  the removed `JHipsterDefaults`. This is also the general defense: `ignoreUnknownFields = true`
  (:13) means a mistyped canonical key binds to nothing and silently takes the field default, so
  field defaults are now the only safety net.
- **Decision**: FIXED — `tokenValidityInSeconds = 1800`, `tokenValidityInSecondsForRememberMe = 2592000`.

### F5 — `JHIPSTER_..._JWT_SECRET` alias missing; silent fallback to a committed key

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/config/application-prod.yml:98-102`
- **Detail**: No new secret was committed — the literals are byte-identical to `bfd3973` and were
  already in git. The gap is coverage: `plan.md:24` promises aliases for "all consumed legacy
  `JHIPSTER_*` environment variables", but the alias list at `:155` omits `.secret`, which
  `TokenProvider.java:48` does consume. Baseline `application-prod.yml:105` explicitly directed
  operators to `JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET`; under the `application.*` prefix that
  variable binds to nothing, and `ignoreUnknownFields` means it does not fail startup either.
- **Failure scenario**: a deployment setting that variable starts cleanly and signs tokens with the
  publicly committed base64 default — anyone reading the repo can mint an admin token.
  `TokenProvider.afterPropertiesSet` warns only when `secret` is present, so the fallback is silent.
- **Scope of exposure**: grepped `src/main/docker/`, `.gitlab/`, `src/main/scripts/` — only
  `JHIPSTER_SLEEP` appears; the bare `_JWT_SECRET` variable is used nowhere in-repo. Risk is to
  out-of-band deployment config. **Escalate to CRITICAL if any live deployment sets it.**
- **Fix A ⭐ Recommended**: Add `secret: ${JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET:}`
  - Strength: Closes the gap the plan's own end-state promised; one line per profile, consistent
    with the 15 existing aliases.
  - Tradeoff: Extends the alias window on a key nothing in-repo uses.
  - Confidence: HIGH — same mechanism already proven for `base64-secret`.
  - Blind spot: Cannot enumerate real deployment environments from here.
- **Fix B**: Drop the alias layer; document only `APPLICATION_*` names
  - Strength: Ends the dual-namespace window outright.
  - Tradeoff: Turns a silent fallback into silent breakage for every `JHIPSTER_*`-configured
    deployment, not just this key.
  - Confidence: LOW — the plan deliberately kept the window open; retiring it was scoped as a
    separate change (`plan.md:45`).
  - Blind spot: Same — deployment inventory is out of view.
- **Decision**: FIXED via Fix A — added `secret: ${JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET:}` to
  `application-dev.yml`, `application-prod.yml`, and `src/test/resources/config/application.yml`.
  Empty default means `base64-secret` is still used when the variable is unset
  (`TokenProvider:49` branches on `!StringUtils.isEmpty(secret)`), so effective behaviour is
  unchanged unless a deployment actually sets the legacy variable.

### F6 — Test fixture reads a property this change deleted

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java:96`
- **Detail**: `@Value("${jhipster.clientApp.name}")` — the only remaining `@Value` on a jhipster key
  in the repo. The property was removed from the test YAML (plan-sanctioned), so it resolves nowhere.
  `ignoreUnknownFields = true` does not cover this: that flag applies to `@ConfigurationProperties`
  binding, whereas an unresolvable `@Value` placeholder throws `IllegalArgumentException` at bean
  init. `UserResourceIT` is outside §7's six-file list, so nothing touched it, and neither
  `change.md` nor the roadmap's F-04 paragraph records it.
- **Failure scenario**: masked today by the `FixedH2Dialect` context failure; F-04 hits it
  immediately after fixing the dialect, with no note explaining where it came from.
- **Fix**: Change to `@Value("${spring.application.name}")` and add it to the F-04 handoff in
  `change.md`.
- **Decision**: FIXED — swapped to `${spring.application.name}`; recorded in `change.md` review-fix note.

### F7 — `jhipster.http` survives in `application-tls.yml`

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `src/main/resources/config/application-tls.yml:16-18`
- **Detail**: Still carries `jhipster.http.version: V_2_0`, which §1's "remove unconsumed
  `jhipster.http`" clause covers — the file was simply absent from the item's file list. Zero runtime
  impact (nothing binds prefix `jhipster` now). Note that gate 2.1 greps for `tech\.jhipster`, so a
  bare `jhipster:` YAML block is invisible to it.
- **Fix**: Delete the three-line block.
- **Decision**: FIXED — block removed from `application-tls.yml`.

### F8 — `prometheus step: 60` binds as 60 milliseconds

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/config/application.yml:30`
- **Detail**: `PrometheusProperties.step` has no `@DurationUnit` (defaultValue `"1m"`), so a bare
  `60` binds as 60ms, not 60s. Not introduced here — the literal meant the same under Boot 2.7 — but
  the key is live again after the rename, so it is now doing something.
- **Fix**: Write `step: 60s` (or drop the key to take the `1m` default).
- **Decision**: FIXED — `step: 60s`.

### F9 — Stale `/test/**` full-chain bypass

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:53`
- **Detail**: `/test/**` remains in `ignoring()` — a full-chain bypass with no CSP, no frame options,
  no auth. No controller maps under it; every resource is `/api/*` or `/management/audits`. It is as
  stale as the `/h2-console/**` rule deliberately removed one line up. The plan's contract does list
  `/test/**` as a preserved bypass, so removing it is a scope decision, not a defect fix.
- **Fix**: Drop the matcher, or record why it stays.
- **Decision**: FIXED — `/test/**` removed from `ignoring()`. **Deliberate deviation from the plan
  contract**, which listed `/test/**` as a preserved bypass (`plan.md:93`); accepted by the user at
  triage on the grounds that nothing maps under it. Worth noting in the F-04 handoff so the
  divergence is not mistaken for an implementation slip.

### F10 — `PaginationUtil` emits `page=-1` on an empty page

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/util/PaginationUtil.java:21-36`
- **Detail**: On an empty page `getTotalPages()` is 0, so the `last` link is built with `page=-1`.
  Inherited verbatim from JHipster's implementation; now live on `AuditResource:47,67` and
  `UserResource:152`.
- **Fix**: Clamp to `Math.max(0, totalPages - 1)`.
- **Decision**: FIXED — `Math.max(0, page.getTotalPages() - 1)`.

## Triage outcome (2026-08-25)

All ten findings triaged; all ten fixed. Re-verified after the fixes:

| Check | Result |
|---|---|
| `./mvnw clean compile` | PASS |
| `./mvnw -PIDE compile` | PASS (15 metamodel classes) |
| `./mvnw clean -Pprod -Dmaven.test.skip=true package` | PASS (`carcare-1.3.11.war`) |
| `./mvnw -Pno-liquibase process-resources` | PASS |
| Gates 1.1, 1.2, 1.3, 2.1, 3.6a, 3.6b | PASS |
| Gate 2.5 (`! rg carcareApp src/main/java src/test/java`) | **now fails by design — see below** |

Two behaviours were verified by running the real classes against Spring's mock request/response
rather than by inspection alone:

- `QuotedCookieLocaleResolver`: writes `%22pl%22` / `%22en%22`, round-trips `pl`, reads `%22pl%22`,
  `pl`, `en`, `%22en-US%22` → `en_US`, `-` → default, and rejects a malformed cookie through
  Spring's inherited guard.
- `HeaderUtil`: emits `X-carcare-alert: carcareApp.vehicle.created` (plus `.updated` / `.deleted`
  and `X-carcare-error: error.idexists`).

### Gate 2.5 no longer holds literally

`! rg -n 'carcareApp' src/main/java src/test/java` was written to prove the **old alert-header
namespace** was gone. F2's fix deliberately reintroduces the string `carcareApp` in exactly one
place — `HeaderUtil.TRANSLATION_KEY_NAMESPACE` and its javadoc — as an **i18n key namespace**, which
is a different thing from the header name. The gate's intent (one header namespace, `X-carcare-*`)
still holds and is unaffected.

If the gate is kept, it should be narrowed to the header form, e.g.:

```
! rg -n 'X-.*carcareApp|"carcareApp" *\+' src/main/java src/test/java
```

### Deviation from the plan contract

F9 removed `/test/**` from `WebSecurityCustomizer.ignoring()`. `plan.md:93` lists it as a
**preserved** bypass, so this is a deliberate, user-approved divergence rather than an
implementation slip — recorded here so a later reader does not "restore" it.

## Limits of this review

- No HTTP behavior was exercised. Every security, header, and locale verdict rests on source and
  bytecode comparison, because the test context remains broken by `FixedH2Dialect` (F-04's gate).
  This is the plan's own stated position, not a gap in the implementation.
- `plan.md:195` claims ten resources import `HeaderUtil`, including `Event`. `EventResource` only
  ever imported `ResponseUtil`. The implementation covered everything that actually existed; the
  count repeated in `plan.md`, `roadmap.md`, and `change.md` is wrong.
- `AGENTS.md` describes `service/mapper/` as MapStruct mappers; they are hand-written `@Service`
  beans. Worth correcting so future agents don't chase absent generated code.
