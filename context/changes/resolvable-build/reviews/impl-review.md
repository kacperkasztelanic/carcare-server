<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Resolvable Dependency Graph (F-01)

- **Plan**: `context/changes/resolvable-build/plan.md`
- **Scope**: Phases 1–3 of 3 (full plan)
- **Date**: 2026-08-25
- **Verdict**: NEEDS ATTENTION → ALL FINDINGS TRIAGED (9 fixed, 1 recorded; 2026-08-25)
- **Findings**: 0 critical, 3 warnings, 7 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria verification

All 22 automated criteria were **re-run**, not taken from the Progress checkboxes:

- `./mvnw validate` exits 0 on default, `-Pprod`, `-PIDE`; zero `'dependencies.dependency.version' ... is missing` in each.
- `./mvnw dependency:resolve` exits 0.
- `./mvnw compile` fails with first source-location error `domain/Authority.java:[10,25] package javax.persistence does not exist` — a `.java` file, not `pom.xml`.
- 796 raw `^\[ERROR\] /` lines; `[INFO] 398 errors` — confirms `migration-surface.md`'s raw/unique dedup claim exactly, and is below Phase 1's 882/441.
- `grep -c 'jhipster-dependencies' pom.xml` = 0; `problem-spring-web` = 0; `org.zalando` across `src/` + `pom.xml` = 0; `SignatureAlgorithm|parserBuilder|parseClaimsJws` across `src/` = 0.
- `web/rest/errors/`, `io.jsonwebtoken`, `jwt/TokenProvider.java` each contribute 0 diagnostics.
- `AccountResource`, `UserResource`, `UriUtil`, `UserService` absent from `git diff --stat 5134fcf..HEAD`.
- All six Hibernate groupId sites on `org.hibernate.orm` (`pom.xml:232,236,241,374,430,975`); `hibernate-validator` correctly still `org.hibernate.validator` at `:245`.
- `-Xmaxerrs 10000` present at `pom.xml:407-410` with removal comment; `jhipster-framework` F-03 comment at `:52` and `:168`.
- Only the pre-existing `commons-compress` `DependencyConvergence` warning remains.
- `plan.md`'s 60 changed lines are confined to `## Progress` (lines 669-720): checkbox flips plus commit SHAs. Zero step titles renamed, zero contract text altered.

## Verified clean (not assumed)

- **jjwt migration is behaviour-preserving.** Key derivation untouched by the diff; `alg=none` still rejected (0.12.3 requires explicit `unsecured()` opt-in); `verifyWith(SecretKey)` is stricter than the old `setSigningKey(Key)`; all five catch branches keep their `SecurityMetersService` mappings; `Jwts.SIG.HS512` enforces the same ≥512-bit key rule.
- **All six custom handlers route through `handleExceptionInternal`** — none constructs a `ResponseEntity` directly.
- **Four security handlers exist and genuinely share one builder** (`SecurityProblemDetails.forSecurityError`), no duplicated logic.
- **No ambiguous `@ExceptionHandler`**; `ExceptionDepthComparator` guarantees the catch-all never shadows a specific handler.
- **Zero "What We're NOT Doing" violations** across all five prohibitions.
- **`ExceptionTranslatorIT` changed exactly one line** — `testMethodNotSupported`'s 405 detail string, the divergence the plan predicted; every surviving assertion holds under the new implementation.
- **ArchUnit**: the rule constrains only `..service..`/`..repository..` → `..web..`. The new edge into `web.rest.errors` is outside it and will not fail. (Post-triage: F10 relocated the two writers `config` → `security`, so the edge is now `security` → `web.rest.errors`; still unconstrained.)
- **`error-contract.md` and `migration-surface.md` both exceed their contracts** — bytecode-verified corrections, a fourth error category the plan did not anticipate, and a second do-not-convert entry.

## Findings

### F1 — Validation errors lost their distinct `type` URI

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:77`
- **Detail**: The old handler built its problem with `.withType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE)`. The new one calls `ProblemDetail.forStatus(status)` with no type, so the `handleExceptionInternal` override at `:58` rewrites `about:blank` → `DEFAULT_TYPE`. Every `@Valid @RequestBody` failure — the most common error the client sees — changes from `https://www.jhipster.tech/problem/constraint-violation` to `.../problem-with-message`. `ErrorConstants.CONSTRAINT_VIOLATION_TYPE` is now referenced nowhere in `src/` except its own declaration (`ErrorConstants.java:15`). `ExceptionTranslatorIT` never asserts `$.type`, so nothing catches it. `error-contract.md` records the new value in its per-handler table (`:82`) but does not flag it as a divergence the way Corrections 1 and 2 are — and `error-contract.md:14` actively asserts "the `https://www.jhipster.tech/problem/*` type URIs from ErrorConstants are unchanged", which this contradicts. Plan §6 required every divergence to carry a matching contract-doc entry.
- **Fix A ⭐ Recommended**: Restore the type — `problemDetail.setType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE);` after line 77.
  - Strength: One line. Keeps the wire contract genuinely unchanged, which is Phase 2's stated goal, and revives a now-dead constant.
  - Tradeoff: None material.
  - Confidence: HIGH — verified the old value in `5134fcf` and the constant's orphaned state by grep.
  - Blind spot: Haven't checked whether the client actually branches on `type`; if it does, this is the difference between working and broken validation UX.
- **Fix B**: Accept and document as Correction 3 in `error-contract.md`.
  - Strength: `DEFAULT_TYPE` is arguably more honest now that the Zalando violations shape is gone.
  - Tradeoff: Leaves a client-visible change resting on the assumption nothing consumes `type`; contradicts `error-contract.md:14`, which would also need amending.
  - Confidence: MEDIUM — depends on unverified client behaviour.
  - Blind spot: The client lives in `../client` and was not inspected.
- **Decision**: FIXED via Fix A — `setType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE)` restored at `ExceptionTranslator.java:79`; `error-contract.md` per-handler table and rationale updated.

### F2 — 401/403 filter-chain writers emit ISO-8859-1 as JSON

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/config/ProblemDetailAuthenticationEntryPoint.java:34-36`, `src/main/java/com/kasztelanic/carcare/config/ProblemDetailAccessDeniedHandler.java:34-36`
- **Detail**: `setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)` sets no charset, and nothing else does — Boot 3.1.5's `ServerProperties.Encoding.shouldForce(RESPONSE)` defaults to `false` and `TomcatServletWebServerFactory` never calls `setResponseCharacterEncoding`, so `response.getWriter()` encodes with Tomcat's default ISO-8859-1. Any non-ASCII in `detail` is mangled and the bytes are not the UTF-8 a JSON client assumes. The advice-level twin is unaffected — it goes through `MappingJackson2HttpMessageConverter`. Ordering (status → content type → writer) is otherwise correct. Latent today since the messages are English, but this is a `pl`-enabled application.
- **Fix**: Add `response.setCharacterEncoding(StandardCharsets.UTF_8.name());` before `getWriter()` in both classes, or write to `response.getOutputStream()` (Jackson defaults to UTF-8 there).
- **Decision**: FIXED — `response.setCharacterEncoding(StandardCharsets.UTF_8.name())` added before `getWriter()` in both writers.

### F3 — 401 body echoes user-enumeration text

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/SecurityProblemDetails.java:21` (fed from `ExceptionTranslator.java:137,148` and both filter-chain writers at `:33`)
- **Detail**: `forSecurityError` puts the caller's `ex.getMessage()` straight into `detail`. `UserNotActivatedException` extends `AuthenticationException` **directly** (not `UsernameNotFoundException`), so Spring Security's `hideUserNotFoundExceptions` does not suppress it — its message, `"User " + lowercaseLogin + " was not activated"` (`DomainUserDetailsService.java:51`), reaches an unauthenticated caller and distinguishes "no such account" from "wrong password" from "not activated". This is **not a regression**: Zalando's `AdviceTrait.prepare(...)` used `throwable.getMessage()` as detail too, and the migration actually removes a worse leak (it dropped Zalando's `setStackTrace(...)` and `withDetail(getMessage())` on the 500 path). But the change re-implements the behaviour deliberately in a brand-new shared helper, which is the cheapest moment this will ever be to close.
- **Fix A ⭐ Recommended**: Fix the message at the source — change `UserNotActivatedException`'s message to a constant ("User was not activated").
  - Strength: Keeps `detail` useful for 403 and genuine auth errors while removing the enumeration signal; touches one line in one file.
  - Tradeoff: The specific login is lost from logs too, unless logged separately at the throw site.
  - Confidence: HIGH — traced the exception hierarchy and both call sites directly.
  - Blind spot: Haven't audited every `AuthenticationException` subtype Spring itself may throw into this path.
- **Fix B**: Suppress `detail` for 401 in the shared helper.
  - Strength: Closes the whole class of leak at one choke point, whatever the exception.
  - Tradeoff: Diverges from `error-contract.md:90`, which documents `detail` = `ex.getMessage()` for 401 — the contract doc would need a matching entry. Also blinds legitimate client-side auth diagnostics.
  - Confidence: MEDIUM — correct but broader than the actual defect.
  - Blind spot: `ExceptionTranslatorIT:91-93` asserts on `$.detail` for `testUnauthorized`; this would break that test.
- **Decision**: FIXED via Fix A — `UserNotActivatedException` message is now the constant "User was not activated"; the login is retained at `log.debug` in `DomainUserDetailsService.java:54`.

### F4 — Catch-all is `Exception`, not the mandated `Throwable`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:159`
- **Detail**: Plan §3 mandated `@ExceptionHandler(Throwable.class)`; the implementation registers `Exception.class`. The javadoc at `:153-158` gives a sound rationale (`handleExceptionInternal` takes an `Exception`, so a `Throwable` handler cannot route through it), but the resulting gap — a bare `Error` falls through to Boot's `/error` with `application/json`, exactly what the plan's bullet existed to prevent — is not listed in `error-contract.md` despite manual item 2.7 ("every divergence listed and accepted") being ticked. Separately, `log.error("Unhandled exception", ex)` at `:161` now fires for 4xx `@ResponseStatus` exceptions; Zalando's `AdviceTrait` logged 4xx at warn/debug and reserved error for 5xx.
- **Fix**: Add both as a Correction 3 in `error-contract.md`; consider level-switching the log on `status.is5xxServerError()`.
- **Decision**: FIXED — Correction 3 added to `error-contract.md`; catch-all logging level-switched on `status.is5xxServerError()` (error for 5xx, warn for a deliberate 4xx).

### F5 — Re-based exceptions now have a null `getMessage()`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/` — `BadRequestAlertException`, `InternalServerErrorException`, `InvalidPasswordException`, `EmailNotFoundException`, `CustomParameterizedException`
- **Detail**: Every `ErrorResponseException` constructor bottoms out in `NestedRuntimeException(null, cause)`, so `getMessage()` returns `null`, where `AbstractThrowableProblem.getMessage()` returned title+detail. `ExceptionTranslator` was correctly updated to `getBody().getTitle()` at `:96`, `:104`, `:118` — but `log.error("Unhandled exception", ex)` and any other logging of these five now emits a message-less stack trace. Diagnostics-only, but it degrades exactly the logging F-03/F-04 will lean on.
- **Fix**: Override `getMessage()` to return `getBody().getTitle()` on the five re-based classes.
- **Decision**: FIXED — `getMessage()` overridden on all five re-based classes. `CustomParameterizedException` returns the caller-supplied message key rather than its constant title. Verified the fix is wire-neutral: the `HeaderUtil` call sites already pass `getBody().getTitle()` explicitly, not `getMessage()`.

### F6 — Dead import left behind

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:9`
- **Detail**: `import org.springframework.context.annotation.Import;` survives after `@Import(SecurityProblemSupport.class)` was removed. Line 9 is the only occurrence of `Import` in the file.
- **Fix**: Delete line 9.
- **Decision**: FIXED — dead `Import` import deleted.

### F7 — Unauthorized public-API removal

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/CustomParameterizedException.java`
- **Detail**: `public static toProblemParameters(String, Map)` was deleted. Zero callers repo-wide, so nothing breaks — but the plan's §2 contract said public constructors and accessors are unchanged and did not authorise removing public surface. `serialVersionUID` was also dropped from all five re-based classes while `EmailAlreadyUsedException` / `LoginAlreadyUsedException` still declare theirs.
- **Fix**: Note in the plan's follow-ups; no code change needed.
- **Decision**: RECORDED — logged in the plan's "Follow-ups deliberately deferred" as a decision on the record. Method not restored (zero callers).

### F8 — No committed-response guard in the filter-chain pair

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/config/ProblemDetailAuthenticationEntryPoint.java:34`, `src/main/java/com/kasztelanic/carcare/config/ProblemDetailAccessDeniedHandler.java:34`
- **Detail**: The advice path gets this for free — `super.handleExceptionInternal` returns `null` when the response is already committed, and `ExceptionTranslator.java:57` correctly null-checks the result. The filter-chain half has no equivalent: if a downstream filter has already committed, `setStatus` silently no-ops and `getWriter()` throws `IllegalStateException` out of `commence(...)`. Low likelihood given where these run, but the asymmetry is avoidable.
- **Fix**: Add `if (response.isCommitted()) return;` to both.
- **Decision**: FIXED — `if (response.isCommitted()) return;` added to both filter-chain writers.

### F9 — Brittle request-URI extraction

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:172-174`
- **Detail**: `request.getDescription(false).substring(4)` depends on `ServletWebRequest` formatting its output as `"uri=" + getRequestURI()`. Correct today, and it is what allowed the `javax.servlet.http` import to be dropped (a deliberate, plan-mandated win) — but it is an undocumented string contract that silently yields garbage for any other `WebRequest` implementation.
- **Fix**: `request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : ""`.
- **Decision**: FIXED — replaced `getDescription(false).substring(4)` with an explicit `ServletWebRequest` instanceof pattern.

### F10 — Convention mismatches in the new classes

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/kasztelanic/carcare/config/ProblemDetail*.java:23`, `src/main/java/com/kasztelanic/carcare/web/rest/errors/SecurityProblemDetails.java:15-16,22-23`
- **Detail**: (a) The two new writers are the only `@Component` classes in `config/` — every sibling there is a `@Configuration`, and the project's `@Component` security infrastructure lives in `security/` (`TokenProvider`, `JwtFilter`). Moving them to `security/` would match convention *and* erase the new `config` → `web` dependency edge (absent at `5134fcf`, though not forbidden by ArchTest). (b) `SecurityProblemDetails:22-23` hard-codes `"message"` / `"path"` where `ExceptionTranslator.java:39-40` defines `MESSAGE_KEY` / `PATH_KEY` — odd in a class whose stated purpose is preventing the two layers from drifting. (c) Its hand-written private constructor, where sibling `ErrorConstants` uses Lombok's `@NoArgsConstructor(access = AccessLevel.PRIVATE)`.
- **Fix**: Promote `MESSAGE_KEY` / `PATH_KEY` to `ErrorConstants` and share them; optionally relocate the two writers to `security/`.
- **Decision**: FIXED (all three) — `MESSAGE_KEY`/`PATH_KEY` promoted to `ErrorConstants` and shared; `SecurityProblemDetails` switched to Lombok `@NoArgsConstructor(PRIVATE)`; both writers relocated `config` -> `security`. Note: the move relocates the dependency edge to `security` -> `web.rest.errors` rather than erasing it; ArchTest constrains neither.

## Adjacent note (not a finding)

`jackson-datatype-hibernate5` (`pom.xml:186-188`) and `JacksonConfiguration.hibernate5Module()` now sit against `org.hibernate.orm:hibernate-core` 6.2.13. It resolves and compiles, then fails at runtime against Hibernate 6 types. The plan lists this as a deferred F-03 follow-up, so it is correctly out of scope — but it is a *dependency* incoherence in a change whose goal was a coherent dependency graph. Worth pinning to the top of F-03.
