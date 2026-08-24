# Error Wire Contract — `ExceptionTranslator`

> Written before Phase 2's code changes landed, then corrected once bytecode inspection of the
> Zalando classes (`org.zalando.problem:0.27.1`, `problem-spring-web:0.27.0`) turned up two
> behaviours the plan's prose had gotten wrong. Both corrections are called out below with the
> verification method, per `AGENTS.md`'s "verify against compiled output... never conclude from
> source alone" rule. Derived from `ExceptionTranslator.java` (pre-Phase-2, commit `7cd2e41`)
> and `ExceptionTranslatorIT.java:45-120`.

## Response shape

`application/problem+json`, keys: `type`, `title`, `status`, `detail`, `path`, `message`,
`params`, `fieldErrors` (`objectName` / `field` / `message`). The
`https://www.jhipster.tech/problem/*` `type` URIs from `ErrorConstants` are unchanged —
`ErrorConstants.PROBLEM_BASE_URL` is preserved verbatim (not a Phase 2 concern; see
`plan.md`'s "What We're NOT Doing"). `X-carcare-error` / `X-carcare-params` headers come from
`HeaderUtil.createFailureAlert` and are unchanged.

`violations` (Zalando's `ConstraintViolationProblem` key) is excluded from this contract: it
requires method-level `@Validated`, which nothing in this tree uses. Already unreachable dead
contract — see `plan.md` Phase 2 §1.

## Correction 1: not all six custom handlers added `path` and a `message` fallback

**What the plan said** (`plan.md` Phase 2 §3): "Zalando's `AdviceTrait.create(...)` calls
`process(...)` on every response the advice builds, which is why all six handlers get `path`,
the `message` fallback, and the default-type substitution today."

**What bytecode inspection showed**: `ExceptionTranslator.process(...)` only transforms the
entity when `problem instanceof ConstraintViolationProblem || problem instanceof DefaultProblem`
(`ExceptionTranslator.java:52`, pre-Phase-2). Verified via `javap -c` on
`org.zalando.problem.ProblemBuilder.build()`: it returns a `DefaultProblem`. Verified via
`javap -c` on `AdviceTrait.create(ThrowableProblem, NativeWebRequest, HttpHeaders)`: it
delegates to `create(Throwable, Problem, NativeWebRequest, HttpHeaders)` passing the *same*
object as both parameters, and that overload calls `process(...)` at the end regardless.

Of the six handlers:
- `handleNoSuchElementException` and `handleConcurrencyFailure` build via `Problem.builder()`
  → `DefaultProblem` → **did** get `path` + message-fallback + type-substitution today.
- `handleEmailAreadyUsedException`, `handleUsernameAreadyUsedException`,
  `handleInvalidPasswordException`, `handleBadRequestAlertException` pass an
  `AbstractThrowableProblem` subclass directly (`EmailAlreadyUsedException`,
  `LoginAlreadyUsedException`, `InvalidPasswordException`, the thrown `BadRequestAlertException`
  itself) → **not** `DefaultProblem` → `process()` was a no-op for these → **no** `path`, and
  for `InvalidPasswordException` specifically (which sets no Zalando `parameters` map) **no**
  `message` key at all.

No `ExceptionTranslatorIT` test exercises the exact body shape of these four handlers today —
there is no `testEmailAlreadyUsed`, `testUsernameAlreadyUsed`, `testInvalidPassword`, or generic
`testBadRequestAlert` method — so this gap was untested, not a deliberate contract.

**Resolution**: Phase 2's `ExceptionTranslator` overrides `handleExceptionInternal` once,
centrally, and every custom handler (all of NoSuchElementException, EmailAlreadyUsed,
UsernameAlreadyUsed, InvalidPassword, BadRequestAlertException, ConcurrencyFailure, plus the new
AccessDenied/Authentication/catch-all handlers) routes through it. This **normalizes** the four
previously-inconsistent handlers to also receive `path` and the `message` fallback. This is a
deliberate behavior change from the literal pre-Phase-2 state, adopted because: (a) it was
unintentional inconsistency, not a documented contract; (b) no test locks in the narrower
behavior; (c) uniform handling is what the plan's design (one `handleExceptionInternal`
override) produces without special-casing four of six handlers to reproduce a gap. Flagged here
per the "Every divergence from that contract is listed and accepted" manual verification item.

## Correction 2: `testMethodNotSupported`'s `$.detail` string changes

Confirmed by decompiling `spring-web-6.0.13`'s `HttpRequestMethodNotSupportedException`
bytecode (`javap -v`, `BootstrapMethods` section): the 3-arg constructor used by Spring's
`ResponseStatusExceptionResolver` path builds its `ProblemDetail` detail from the template
`"Method '%s' is not supported."` — different from the `ServletException` message template
`"Request method '%s' is not supported"` (used for `getMessage()`, not the response body).

| | Old (Zalando) | New (Spring 6) |
| --- | --- | --- |
| `$.detail` for 405 | `Request method 'POST' not supported` | `Method 'POST' is not supported.` |

This was already anticipated and flagged as "the one likely genuine divergence" in
`plan.md` Phase 2 §6. `ExceptionTranslatorIT.testMethodNotSupported` updated accordingly.

## Correction 3: the catch-all is registered for `Exception`, not `Throwable`

*Added post-implementation during the implementation review (finding F4); it should have been
recorded here when the divergence was made.*

**What the plan said** (`plan.md` Phase 2 §3): "An `@ExceptionHandler(Throwable.class)` catch-all
emitting 500 … Without it, an unhandled `RuntimeException` falls through to Boot's `/error`,
which returns `{"timestamp","status","error","path"}` as `application/json` — a different body
*and* a different content type."

**What was implemented**: `@ExceptionHandler(Exception.class)`
(`ExceptionTranslator.java:160`). `Throwable` is not registrable here — every route out of this
class goes through `handleExceptionInternal`, whose Spring-declared signature takes an
`Exception`, so a `Throwable` handler could not both catch `Error` and share the single
post-processing hook that gives every body its `path`, `message` fallback and type substitution.

**Consequence, accepted**: the plan's stated goal is met for the case it actually cared about —
an unhandled `RuntimeException` is caught and rendered as `application/problem+json`. A bare
`Error` (`OutOfMemoryError`, `StackOverflowError`, …) is *not* caught and does still reach Boot's
`/error` with the different body and content type. That is the correct outcome regardless: a JVM
`Error` means the process is already in an undefined state, and rendering a tidy problem
document over it would be misleading. The remaining exposure is limited to response *shape*
during a fault that is not survivable anyway.

**Related**: the catch-all's logging is level-switched on `status.is5xxServerError()` — `error`
for genuine faults, `warn` for a deliberate 4xx carried by `@ResponseStatus`. Zalando's
`AdviceTrait` made the same distinction; logging every 4xx at `error` would have been a
regression in log signal-to-noise.

## Per-handler contract (post-Phase-2)

| Handler | Status | `type` | `title` | `detail` | `message` | `path` |
| --- | --- | --- | --- | --- | --- | --- |
| `handleMethodArgumentNotValid` | 400 | `CONSTRAINT_VIOLATION_TYPE` | "Method argument not valid" | — | `ERR_VALIDATION` | added |
| `handleNoSuchElementException` | 404 | DEFAULT_TYPE | — | — | `ENTITY_NOT_FOUND_TYPE` (URI, preserved verbatim — pre-existing quirk, not a bug fix) | added |
| `handleEmailAlreadyUsedException` | 400 | `EMAIL_ALREADY_USED_TYPE` | "Email is already in use!" | — | `error.emailexists` | added (new) |
| `handleUsernameAlreadyUsedException` | 400 | `LOGIN_ALREADY_USED_TYPE` | "Login name already used!" | — | `error.userexists` | added (new) |
| `handleInvalidPasswordException` (service) | 400 | `INVALID_PASSWORD_TYPE` | "Incorrect password" | — | `error.http.400` (new — was absent) | added (new) |
| `handleBadRequestAlertException` | 400 | caller-supplied | caller-supplied | — | `error.<errorKey>` | added (new) |
| `handleConcurrencyFailure` | 409 | DEFAULT_TYPE | — | — | `ERR_CONCURRENCY_FAILURE` | added |
| `handleAccessDenied` | 403 | DEFAULT_TYPE | — | `ex.getMessage()` | `error.http.403` | added |
| `handleAuthentication` | 401 | DEFAULT_TYPE | — | `ex.getMessage()` | `error.http.401` | added |
| `handleUncaught` (catch-all) | 500 or `@ResponseStatus` value | DEFAULT_TYPE | "Internal Server Error" or `@ResponseStatus.reason()` | — | `error.http.<status>` | added |
| Spring built-ins (missing param/part, method not supported, media type, etc.) | per exception | about:blank→DEFAULT_TYPE | Spring's own | Spring's own (see Correction 2) | `error.http.<status>` (fallback, since Spring's own body has no `message` property) | added |

"added (new)" marks the four handlers affected by Correction 1.

`handleMethodArgumentNotValid`'s `type` is set explicitly rather than left to fall through to
`DEFAULT_TYPE`. The first cut of Phase 2 omitted the `setType` call, which silently changed the
URI on every `@Valid @RequestBody` failure — the most common error the client sees — and left
`ErrorConstants.CONSTRAINT_VIOLATION_TYPE` unreferenced. Caught in the implementation review
(finding F1) and restored; no test asserted `$.type`, so nothing else would have caught it.

## Security handlers (both layers, `SecurityProblemDetails.forSecurityError`)

Both the advice-level handlers (`ExceptionTranslator.handleAccessDenied` /
`.handleAuthentication`) and the filter-chain beans
(`security.ProblemDetailAccessDeniedHandler` / `security.ProblemDetailAuthenticationEntryPoint`)
call the same static builder, so a 401/403 produces an identical body regardless of which layer
intercepted it. Only the advice layer is exercised by `ExceptionTranslatorIT`
(`MockMvcBuilders.standaloneSetup(...)` has no Spring Security filter chain); the filter-chain
pair has no executable coverage until F-04 and is reviewed by inspection instead (manual
verification item).

## Deviation from the plan's literal text: shared builder visibility

`plan.md` Phase 2 §4 calls the shared `ProblemDetail` builder "a package-private helper." That is
not achievable as written: the advice-level handlers live in `web.rest.errors` and the
filter-chain beans live in `config` — two different packages. `SecurityProblemDetails` is
declared `public` instead, in `web.rest.errors` alongside `ErrorConstants`. The functional
intent — one code path so the two layers cannot drift apart — is preserved; only the visibility
modifier differs from the plan's literal text.
