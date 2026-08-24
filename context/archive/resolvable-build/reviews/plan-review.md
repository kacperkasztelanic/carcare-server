<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Resolvable Dependency Graph (F-01)

- **Plan**: `context/changes/resolvable-build/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-24
- **Verdict**: REVISE → SOUND (all 7 findings fixed in plan.md, 2026-08-25)
- **Findings**: 2 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING → PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | FAIL → PASS |
| Plan Completeness | WARNING → PASS |

## Grounding

14/14 paths ✓, brief↔plan ✓, Progress↔Phase ✓ (9/3, 5/4, 5/3 criteria bullets → steps 1.1–1.12, 2.1–2.9, 3.1–3.8; exactly one `## Progress` heading; no stray checkboxes in phase blocks).

Phase 1 verified against `~/.m2/repository`:

- `spring-boot-dependencies` 3.1.5 is present and manages `javax.cache:cache-api` 1.1.1 ✓
- It manages `org.hibernate.orm:*` including `hibernate-jpamodelgen` ✓
- Its `hibernate.version` is 6.2.13.Final, matching `pom.xml:37` ✓ (the pom defines the property locally, so the `annotationProcessorPaths` entry at `pom.xml:420` keeps resolving after the BOM swap)
- `commons-io`, `logstash-logback-encoder`, `jjwt-*`, `springdoc-*` are **not** managed by the Boot BOM ✓ — the four pins are necessary and correctly scoped
- All pinned artifacts except jjwt 0.12.3 are already in the local repo (`springdoc-openapi-starter-webmvc-ui` 2.2.0, `jhipster-framework` 8.0.0, `logstash-logback-encoder` 7.4, `commons-io` 2.15.0)

**Phase 1 is sound. All findings are in Phases 2 and 3.**

## Findings

### F1 — Zalando supplies four handler categories Spring's base class does not

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 2 §3 — `ExceptionTranslator`
- **Detail**: Verified by `javap -v` on spring-webmvc 6.0.13: `ResponseEntityExceptionHandler` declares exactly ONE `@ExceptionHandler`, covering 16 named types. There is no `Throwable`/`Exception` catch-all, no handling of `@ResponseStatus`-annotated exceptions, no `ConstraintViolationException`, no `AccessDeniedException`/`AuthenticationException`. `ExceptionTranslator` gets all of those today from `implements ProblemHandling, SecurityAdviceTrait` (`ExceptionTranslator.java:33`). Phase 2's contract enumerates only `handleExceptionInternal`, `handleMethodArgumentNotValid` and the existing custom handlers, so removing Zalando silently drops:
  - any unhandled `RuntimeException` → today 500 `application/problem+json` with title `Internal Server Error` and `message: error.http.500`; after the change it falls through to Boot's `/error` → `{"timestamp","status","error","path"}` as `application/json`. Asserted by `ExceptionTranslatorIT:114-121`.
  - `@ResponseStatus`-annotated exceptions → today 400 problem+json with `title` from `reason`; after, the `ResponseStatusExceptionResolver` `sendError` path. Asserted by `ExceptionTranslatorIT:105-112`.
  - `ConstraintViolationException` → the `violations` key the Phase 2 §1 contract document promises to record. Low practical exposure (no method-level `@Validated` exists in this tree, so the key is already unreachable), but the contract document should not promise it.
- **Fix**: Extend Phase 2 §3's contract to add an explicit `@ExceptionHandler(Throwable.class)` catch-all that (a) honours a `@ResponseStatus` annotation on the thrown type via `AnnotatedElementUtils.findMergedAnnotation`, using its `reason` as title, and (b) otherwise emits 500 with `message: error.http.500`. Drop `violations` from the contract document, or add a `ConstraintViolationException` handler alongside it.
  - Strength: Restores exactly the two paths `ExceptionTranslatorIT` already asserts, so F-04 gets a real regression check instead of two known-broken tests.
  - Tradeoff: A catch-all advice masks stack traces from Boot's default error page; needs a deliberate logger call to keep 500s diagnosable.
  - Confidence: HIGH — the absent handler list is read off the compiled 6.0.13 class, and the lost assertions are named in the existing IT.
  - Blind spot: Whether `/error` remains reachable under the current `SecurityConfiguration` wasn't traced — irrelevant once the catch-all exists.
- **Decision**: FIXED — applied to plan.md

### F2 — `path` and `message` enrichment stops applying to the six custom handlers

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 §3 — "The `process(...)` post-processing hook becomes an override of `handleExceptionInternal`"
- **Detail**: These are not equivalent hooks. Zalando's `AdviceTrait.create(...)` calls `process(...)` on every response the advice builds, which is why all six `@ExceptionHandler` methods at `ExceptionTranslator.java:96-143` currently receive `path`, the `error.http.<status>` message fallback, and the `about:blank` → `ErrorConstants.DEFAULT_TYPE` substitution (`ExceptionTranslator.java:55-72`). Spring's `handleExceptionInternal` is invoked only from within `ResponseEntityExceptionHandler`'s own 16 handlers; a subclass `@ExceptionHandler` returning a `ResponseEntity` never passes through it. After the rewrite `handleNoSuchElementException`, `handleConcurrencyFailure`, `handleBadRequestAlertException` and the rest emit no `path`, no message fallback, and `type: about:blank` instead of `https://www.jhipster.tech/problem/problem-with-message`. `ExceptionTranslatorIT:47` asserts `$.message` on the concurrency path; `:92` asserts `$.path`.
- **Fix**: Have every custom `@ExceptionHandler` build its `ProblemDetail` and return `handleExceptionInternal(ex, problemDetail, headers, status, request)` rather than constructing `ResponseEntity` directly, and put the path / message-fallback / default-type logic in that single override. State this explicitly in the contract.
  - Strength: One enrichment point, same shape as today's `process()`, and it keeps the `HeaderUtil.createFailureAlert` headers flowing through the same call.
  - Tradeoff: Every handler signature changes to thread `HttpHeaders` and `WebRequest`.
  - Confidence: HIGH — dispatch behaviour follows directly from the compiled class: `handleExceptionInternal` is protected and called only by the base handlers.
  - Blind spot: Whether Spring 6.0.13's `handleExceptionInternal` already sets `instance` wasn't checked; irrelevant, since the contract key is `path`, not `instance`.
- **Decision**: FIXED — applied to plan.md

### F3 — `ExceptionTranslatorIT`'s 401/403 assertions cannot survive as written

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Phase 2 §4 and §6
- **Detail**: §6 says the assertions at lines 45-120 are "preserved verbatim". Two of them cannot be. `ExceptionTranslatorIT:36` builds `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(exceptionTranslator)` — no Spring Security filter chain exists in that harness. `testAccessDenied` and `testUnauthorized` pass today only because `SecurityAdviceTrait` handles `AccessDeniedException` and `AuthenticationException` *inside the advice*. Phase 2 moves that behaviour to an `AuthenticationEntryPoint` and an `AccessDeniedHandler` in the filter chain, which `standaloneSetup` never invokes. Production behaviour is fine — `ExceptionTranslationFilter` catches both types propagating out of the dispatcher — but the test breaks, and with it the only executable evidence (post-F-04) that the two new handlers are correct. Manual criterion 2.8 ("reviewed by inspection") then becomes the sole check, permanently. Note also that the wiring inside `configure(HttpSecurity)` is write-only work: F-03 rewrites that method into a `SecurityFilterChain`.
- **Fix A ⭐ Recommended**: Keep advice-level `@ExceptionHandler` methods for `AccessDeniedException` and `AuthenticationException` *in addition to* the two filter-chain beans, both delegating to one shared `ProblemDetail` builder.
  - Strength: Both tests keep passing verbatim; the filter-chain beans and the advice provably emit the same body because they share the builder.
  - Tradeoff: Two entry points into the same logic; a reader must know why both exist.
  - Confidence: HIGH — mirrors what `SecurityAdviceTrait` + `SecurityProblemSupport` already do together today, which is why both layers exist in the current code.
  - Blind spot: Ordering between the advice and `ExceptionTranslationFilter` for exceptions raised by method security wasn't traced end to end.
- **Fix B**: Accept the divergence — record it in `error-contract.md` and rewrite the two tests as full-context `@SpringBootTest` + `@AutoConfigureMockMvc` cases in F-04.
  - Strength: Removes a `standaloneSetup` harness that AGENTS.md already flags as unreliable.
  - Tradeoff: Leaves the two security handlers with zero automated coverage across F-01 through F-03, on a client-visible surface.
  - Confidence: MEDIUM — depends on F-04 actually converting the harness.
  - Blind spot: F-04's scope hasn't been read.
- **Decision**: FIXED via Fix A — applied to plan.md

### F4 — Phase 3's substitution list is missing the `Key` → `SecretKey` change

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1 and §2
- **Detail**: `TokenProvider.java:42` declares `private Key key;` (`java.security.Key`). jjwt 0.12's parser API is `JwtParserBuilder verifyWith(SecretKey secretKey)` — there is a separate `verifyWith(PublicKey)` overload — and `Jwts.SIG.HS512` is a `MacAlgorithm`, i.e. `SecureDigestAlgorithm<SecretKey, SecretKey>`, so `signWith(key, Jwts.SIG.HS512)` does not type-check against `java.security.Key`. The field must become `javax.crypto.SecretKey`; `Keys.hmacShaKeyFor` already returns one, so the assignment at `TokenProvider.java:58` is unaffected. `TokenProviderTest.java:103` declares `Key otherKey` and needs the same change. Left unstated, the phase produces non-compiling code and fails its own criterion 3.3 ("`TokenProvider.java` contributes no diagnostic"), on security-critical code with no test able to run.
- **Fix**: Add to the Phase 3 contract — `java.security.Key` → `javax.crypto.SecretKey` in `TokenProvider.java:42` and `TokenProviderTest.java:103`. Also note that `setSubject`/`setExpiration` are deprecated-but-present in 0.12.3, so the builder compiles with warnings; either migrate to `subject()`/`expiration()` or accept the warnings explicitly.
- **Decision**: FIXED — applied to plan.md

### F5 — Success criterion 3.4 is unachievable and contradicts the plan's own testing steps

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 Success Criteria (Progress step 3.4)
- **Detail**: 3.4 requires every remaining `[ERROR]` line naming a `.java` file to mention `javax.`, `tech.jhipster`, or `WebSecurityConfigurerAdapter`. That cannot hold. `SecurityConfiguration.java:45-51` and `:86-96` call `antMatchers(...)`, which spring-security-config 6.x removed — verified: `AbstractRequestMatcherRegistry` exposes only `requestMatchers` overloads. Those produce `cannot find symbol: method antMatchers`, matching none of the three tokens. Cascading `cannot find symbol` errors from the 152 unresolved `javax` types do the same. The plan already knows this: Manual Testing step 3 says "allowing for cascading `cannot find symbol` errors". Two parts of the document disagree.
- **Fix**: Restate 3.4 as a categorisation rather than a filter — e.g. "every remaining `[ERROR]` is attributable to an unconverted Jakarta namespace, a Spring Security 6 API removal, or a `tech.jhipster` symbol, and the attribution is recorded in `migration-surface.md`". Apply the same to criterion 2.3, whose `grep -v 'javax\.'` clause becomes vestigial once `ExceptionTranslator`'s three `javax` imports are gone.
- **Decision**: FIXED — applied to plan.md

### F6 — Three small factual inaccuracies in the Current State Analysis

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Key Discoveries; Phase 2 Overview and §3
- **Detail**:
  - "Removing it breaks **9 files**" — `grep -rl 'org\.zalando' src/` returns 8.
  - Phase 2 §3 says "The **five** `@ExceptionHandler` methods for …" and then lists six; the file has six (`ExceptionTranslator.java:96,105,114,123,129,136`).
  - `UriUtil` is listed among the callers that must stay untouched, but it references only `UnparseableUriException`, which has no Zalando dependency. Criterion 2.4 is therefore trivially true for it.
- **Fix**: Correct the three counts — they are the kind of detail an implementer checks the plan against and loses confidence over.
- **Decision**: FIXED — applied to plan.md

### F7 — Criterion 1.8's threshold is the number already observed without `-Xmaxerrs`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 Success Criteria (Progress step 1.8)
- **Detail**: Current State Analysis records 200 diagnostics observed *before* the flag and calls them "a first round, not an inventory". Criterion 1.8 then asks for "exceeds 200", satisfied by 201. It cannot distinguish "the cap was raised" from "the cap happened to sit just above 200".
- **Fix**: Make it a differential check — capture the count with and without `-Xmaxerrs` and require the flagged run to be strictly larger, or assert the absence of javac's "only showing the first N errors" note.
- **Decision**: FIXED — applied to plan.md
