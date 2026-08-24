# Resolvable Dependency Graph — Implementation Plan

> Roadmap item: **F-01** (`context/foundation/roadmap.md`) · PRD refs: FR-001, FR-002

## Overview

`./mvnw` fails during Maven model construction — before compilation, before the enforcer —
because 11 dependencies receive no version from the declared `jhipster-dependencies` 8.0.0
BOM. Nothing downstream of that is trustworthy: the compiler never runs, so the size and
shape of the Jakarta EE migration is an estimate rather than a measurement.

This change restores a resolvable dependency graph so javac runs and emits a real error
list, and absorbs two library migrations the owner chose to land here rather than defer:
Zalando Problem is removed in favour of Spring 6 `ProblemDetail`, and jjwt moves from
0.11.x to 0.12.3 with the corresponding `TokenProvider` rewrite.

It does **not** attempt a green compile. 152 `javax.*` imports in `src/main` remain
untouched; converting them is F-03.

## Current State Analysis

**The failure is reproducible and precise.** `./mvnw validate` on `refactor` reports 11
`'dependencies.dependency.version' ... is missing` errors at `pom.xml` lines 182, 207, 211,
219, 223, 228, 290, 298, 302, 307, 362.

The 11 fall into three distinct causes, which is why a single blanket fix does not apply:

| Cause | Dependencies | Why the BOM stopped supplying a version |
| --- | --- | --- |
| groupId rename | `hibernate-core`, `hibernate-envers`, `hibernate-jcache`, `hibernate-jpamodelgen` | Hibernate 6.x moved from `org.hibernate` to `org.hibernate.orm`; Boot 3.1.5 manages the new coordinates only |
| groupId mismatch | `jakarta.cache:cache-api` | JSR-107 never moved to the `jakarta` groupId; Boot manages `javax.cache:cache-api` |
| artifact rename | `springdoc-openapi-webmvc-core` | springdoc 2.x replaced the 1.x artifact set; this artifact does not exist in 2.x |
| dropped from JHipster 8 | `commons-io`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson`, `problem-spring-web` | JHipster 8 no longer manages these; Boot 3.1.5 never did |

**Removing the BOM is far cheaper than the roadmap assumed.** The roadmap's F-01 risk note
says dropping `jhipster-dependencies` makes "roughly 40 dependency versions hand-managed."
Measured directly by swapping the BOM in a sandbox and running `validate`, only **three**
additional dependencies lose their version: `logstash-logback-encoder`,
`spring-cloud-starter-bootstrap`, and `jhipster-framework`. Boot's nested BOMs
(jackson-bom, spring-security, spring-data, micrometer, assertj, dropwizard-metrics) cover
the rest. The total surface is **14**, not 40.

**A working configuration has been verified end to end.** With the BOM swapped and all 14
resolved, `./mvnw validate` passes model construction and `./mvnw compile` reaches javac,
which emits `package javax.persistence does not exist` and 199 further diagnostics. F-01's
stated outcome is achievable exactly as scoped.

**The migration surface, measured statically:**

| Namespace | `src/main` | Note |
| --- | --- | --- |
| `javax.persistence` | 101 | F-03 |
| `javax.validation` | 35 | F-03 |
| `javax.servlet` | 8 | F-03 |
| `javax.transaction` | 4 | F-03 |
| `javax.annotation` | 2 | F-03 |
| `javax.mail` | 1 | F-03 |
| `javax.sql` | 1 | **JDK-owned — must not be converted** (`LiquibaseConfiguration.java:18`) |
| **total** | **152** | plus 20 in `src/test` |

18 files in `src/main` and 6 in `src/test` reference `tech.jhipster.*`.

## Desired End State

`./mvnw validate` completes model construction with zero missing-version errors, on the
default profile and on `prod` and `IDE`. `./mvnw compile` fails as a *compilation* error,
not a *model* error — javac runs, resolves every classpath entry, and reports the full
Jakarta namespace surface. No `org.zalando` import remains anywhere in the tree, no jjwt
0.11.x API call remains, and the residual diagnostics are recorded as F-03's input.

Verify by: running `./mvnw validate` (expect BUILD SUCCESS), then `./mvnw compile` (expect
BUILD FAILURE whose first `[ERROR]` line names a `.java` file and a `javax.*` package, not
a `pom.xml` line).

### Key Discoveries:

- `spring-boot-dependencies` 3.1.5 is already in the local repository and covers all but
  three of the currently BOM-supplied versions — measured, not assumed.
- `pom.xml:420` declares `org.hibernate:hibernate-jpamodelgen` inside
  `annotationProcessorPaths` **with** an explicit version. Model construction therefore
  passes while compilation fails on artifact resolution. The same wrong groupId appears in
  the `IDE` profile (`pom.xml:964`). Fixing only the `<dependencies>` block leaves the build
  broken in a way that looks unrelated.
- **springdoc has zero Java usages.** It is configured only at `application.yml:111`
  (`springdoc.api-docs.path: /api-docs`) and switched by the `api-docs` Spring profile,
  which `application-dev.yml:23` activates. Migrating the artifact is therefore a
  dependency change with no code impact.
- **No `bootstrap.yml` or `bootstrap.properties` exists in the tree**, so
  `spring-cloud-starter-bootstrap` provides nothing. Dropping it avoids importing the entire
  `spring-cloud` BOM for a single unused starter.
- `org.zalando:problem-spring-web` transitively supplies `org.zalando:problem` and
  `problem-violations`. Removing it breaks **8 files** (`grep -rl 'org\.zalando' src/`), not
  the 3 that import the Spring integration directly.
- Only **5** classes extend `AbstractThrowableProblem` directly (`BadRequestAlertException`,
  `InternalServerErrorException`, `InvalidPasswordException`, `EmailNotFoundException`,
  `CustomParameterizedException`). `EmailAlreadyUsedException` and
  `LoginAlreadyUsedException` extend `BadRequestAlertException`, and
  `UnparseableUriException` is a plain `RuntimeException` with no zalando dependency.
- Callers of the error classes are contained: `AccountResource`, `UserResource`,
  `UserService`. Preserving each constructor signature and the
  `getEntityName()` / `getErrorKey()` accessors keeps all three untouched. `UriUtil` is
  **not** among them — it references only `UnparseableUriException`
  (`UriUtil.java:3,17`), which has no zalando dependency.
- `ExceptionTranslator` obtains its alert headers from `tech.jhipster.web.util.HeaderUtil`.
  That import stays in this change — `jhipster-framework` is still on the classpath, and
  removing `tech.jhipster.*` is F-03's job.
- javac stops after its default `-Xmaxerrs`, so the observed 200 errors across 12 files are
  a first round, not an inventory. Without raising the limit, Phases 2 and 3 cannot be
  verified — javac never reaches the error or security packages.

## What We're NOT Doing

- **Not converting any `javax.*` import.** All 152 in `src/main` and 20 in `src/test` are
  F-03 and F-04.
- **Not converting `SecurityConfiguration` to a bean-based `SecurityFilterChain`.** That is
  FR-004 and F-03, and it carries the unanswered frame-options decision (Open Roadmap
  Question 1) that currently blocks F-03. This change strips `SecurityProblemSupport` from
  the class and leaves it otherwise intact — still extending `WebSecurityConfigurerAdapter`,
  still not compiling. Pulling FR-004 forward would inherit the block.
- **Not removing `tech.jhipster.*` usages or the `jhipster-framework` artifact.** FR-002 is
  completed by F-03; this change pins the artifact explicitly as a stated temporary bridge.
- **Not replacing `jackson-datatype-hibernate5` with `hibernate6`.** `Hibernate5Module` is
  referenced in `JacksonConfiguration.java:32`; swapping it is a code change belonging to
  F-03's Hibernate 6 work.
- **Not changing `ErrorConstants.PROBLEM_BASE_URL`.** It reads
  `https://www.jhipster.tech/problem` and appears in the `type` field of every error
  response. It is a string, not a dependency, and the client may match on it. Preserved
  verbatim; recorded below as a follow-up.
- **Not running any test.** No Spring context can load until F-04. Every test change here
  is written to a documented contract and executed later.
- **Not touching the enforcer's `DependencyConvergence` rule**, which is already warn-only.

## Implementation Approach

Three phases, ordered by dependency and by verifiability.

Phase 1 is pom-only and independently checkable: it is the only phase whose success can be
observed today by a Maven exit code. It also raises `-Xmaxerrs` so javac reports every
diagnostic rather than stopping early — without that, Phases 2 and 3 have no verification
mechanism at all, because javac aborts inside the domain package and never reaches the
files they change.

Phases 2 and 3 are independent of each other and both depend only on Phase 1. Each removes
one library's API surface from the tree, and each is verified by grepping the source for the
old API and by grepping javac's full diagnostic output for errors attributable to that
library. Neither can be verified by running a test until F-04.

## Critical Implementation Details

**`-Xmaxerrs` is a prerequisite, not a nicety.** `pom.xml` already configures
`<compilerArgs>` with `-parameters`. Phase 1 must add `-Xmaxerrs 10000` there. Without it
javac stops at its default cap inside `domain/`, so no diagnostic from `web/rest/errors/` or
`security/jwt/` is ever emitted and Phases 2 and 3 are unverifiable.

**Removing `SecurityProblemSupport` silently changes 401 and 403 response bodies.** It is
currently wired as both `authenticationEntryPoint` and `accessDeniedHandler` inside
`configure(HttpSecurity)`. Those exceptions are thrown in the Spring Security filter chain,
*before* the `DispatcherServlet`, so a `@ControllerAdvice` cannot handle them — deleting the
field without a replacement drops `ExceptionTranslatorIT`'s asserted `application/problem+json`
401/403 bodies to Spring Security's defaults. Phase 2 must supply replacement
`AuthenticationEntryPoint` and `AccessDeniedHandler` beans that write a `ProblemDetail`, and
wire them at the same two call sites. F-03 carries those beans across into the
`SecurityFilterChain` rewrite.

**Do not enable `spring.mvc.problemdetails.enabled`.** Spring Boot 3 autoconfigures its own
`ResponseEntityExceptionHandler` at order 0 when that property is set. This change declares
its own `@ControllerAdvice` extending the same class; enabling the property would give the
application two handlers competing for the same exceptions.

**`javax.sql.DataSource` at `LiquibaseConfiguration.java:18` is JDK-owned.** It is not part
of Jakarta EE and must never be rewritten to `jakarta.sql`. It is listed here because Phase
1's inventory step counts `javax.*` imports and this one must be excluded from F-03's
worklist rather than handed over with the rest.

## Phase 1: Resolvable dependency graph

### Overview

Make Maven model construction succeed and get javac running, changing only `pom.xml`.

### Changes Required:

#### 1. dependencyManagement

**File**: `pom.xml`

**Intent**: Replace the JHipster BOM with Spring Boot's own so version management comes
from the platform the project actually declares, rather than from a generator's opinion
about it.

**Contract**: The single `<dependencyManagement>` import becomes
`org.springframework.boot:spring-boot-dependencies:${spring-boot.version}` (pom, import).
The `jhipster-dependencies.version` property is deleted. The comment at `pom.xml:34` that
ties `spring-boot.version` to whatever JHipster picked no longer applies and should go with
it.

#### 2. Hibernate coordinates

**File**: `pom.xml`

**Intent**: Point the four Hibernate artifacts at the groupId Hibernate 6 actually
publishes under, so they inherit their version from Boot's BOM.

**Contract**: `org.hibernate` → `org.hibernate.orm` for `hibernate-core`,
`hibernate-envers`, `hibernate-jcache`, and `hibernate-jpamodelgen`. Four sites in
`<dependencies>`, one in `annotationProcessorPaths` (~line 420, keeps its explicit
`${hibernate.version}`), and one in the `IDE` profile (~line 964). `hibernate-validator`
stays on `org.hibernate.validator` — that groupId did not change.

#### 3. JSR-107 cache API

**File**: `pom.xml`

**Intent**: Use the coordinates Boot manages. The JCache API kept its `javax.cache` groupId
and package through the Jakarta transition.

**Contract**: `jakarta.cache:cache-api` → `javax.cache:cache-api`, version inherited. No
code change: `CacheConfiguration.java` already imports `javax.cache.*` and stays correct.

#### 4. springdoc 1.x → 2.x

**File**: `pom.xml`

**Intent**: Move to the artifact set that supports Spring Boot 3, collapsing two
declarations into one starter.

**Contract**: Both `springdoc-openapi-webmvc-core` and `springdoc-openapi-ui` are replaced
by a single `org.springdoc:springdoc-openapi-starter-webmvc-ui`. `springdocs.version`
becomes `2.2.0` — the version `jhipster-dependencies` 8.0.0 itself pins. The
`springdoc.api-docs.path` key at `application.yml:111` is unchanged between 1.x and 2.x, so
no configuration edit is needed.

#### 5. Explicit pins for what no BOM manages

**File**: `pom.xml`

**Intent**: Give a version to each dependency that neither Boot nor any nested BOM supplies,
introducing one property per dependency so the values are visible in one place.

**Contract**: New properties and their pinned dependencies —
`commons-io.version` = `2.15.0` (deliberately matching the version POI already pulls
transitively, which removes one of the three `DependencyConvergence` warnings),
`logstash-logback-encoder.version` = `7.4` (the value JHipster 8 used),
`jhipster-framework.version` = `8.0.0`, and `jjwt.version` = `0.12.3` applied to all three
`jjwt-*` artifacts. The `jhipster-framework` entry carries an XML comment naming F-03 as its
removal point.

#### 6. Removals

**File**: `pom.xml`

**Intent**: Drop two dependencies that cannot or should not be pinned.

**Contract**: `org.springframework.cloud:spring-cloud-starter-bootstrap` is deleted — no
`bootstrap.yml` or `bootstrap.properties` exists, and keeping it would require importing the
whole spring-cloud BOM. `org.zalando:problem-spring-web` is deleted — no release of it
targets Spring 6, so no version would be correct. Phase 2 handles the resulting code
breakage.

#### 7. Compiler diagnostics

**File**: `pom.xml`

**Intent**: Make javac report the whole error set instead of stopping at its default cap, so
this change produces the measured migration surface F-03 needs.

**Contract**: Add `-Xmaxerrs` / `10000` to the existing `<compilerArgs>` block alongside
`-parameters`, with a comment noting it can be removed once the tree compiles.

#### 8. Migration surface inventory

**File**: `context/changes/resolvable-build/migration-surface.md` (new)

**Intent**: Record what the newly-working compiler reports, so F-03 starts from a
measurement instead of an estimate. This is the artifact that converts F-01's value from
"the build is less broken" into something the next change can plan against.

**Contract**: Captures, with the command used to produce each: the full javac diagnostic
list from `./mvnw compile`; per-package `javax.*` import counts for `src/main` and
`src/test`; the list of files referencing `tech.jhipster.*`; and an explicit
**do-not-convert** entry for `javax.sql.DataSource` at `LiquibaseConfiguration.java:18`.
Refreshed at the end of Phase 3 so the handoff reflects the final state rather than the
pre-Phase-2 one.

### Success Criteria:

#### Automated Verification:

- Model construction succeeds: `./mvnw validate` exits 0 with no `'dependencies.dependency.version' ... is missing` in the output
- Same on the production profile: `./mvnw -Pprod validate` exits 0
- Same on the IDE profile, which declares its own `hibernate-jpamodelgen`: `./mvnw -PIDE validate` exits 0
- Every artifact resolves: `./mvnw dependency:resolve` exits 0
- The JHipster BOM is gone: `grep -c 'jhipster-dependencies' pom.xml` returns 0
- Zalando is gone from the build: `grep -c 'problem-spring-web' pom.xml` returns 0
- The compiler runs: `./mvnw compile` fails, and its first `[ERROR]` line naming a source location names a `.java` file, not `pom.xml`
- The error list is complete rather than truncated — measured differentially, since the pre-change count is itself 200 and a bare `> 200` threshold cannot distinguish "the cap was raised" from "the cap sat just above 200". Capture `./mvnw compile 2>&1 | grep -c '^\[ERROR\] /'` twice, once with the `-Xmaxerrs` arg commented out and once with it active; the active run must be **strictly greater**. Record both numbers in `migration-surface.md`. A flagged count that lands on a round value (100, 200) is a signal the flag is not binding, not a pass
- `context/changes/resolvable-build/migration-surface.md` exists and records a non-zero diagnostic count

#### Manual Verification:

- The four pinned versions (`commons-io` 2.15.0, `logstash-logback-encoder` 7.4, `jjwt` 0.12.3, `springdoc` 2.2.0) are reviewed and accepted
- Only the pre-existing `commons-compress` `DependencyConvergence` warning remains; the `commons-io` and `apiguardian` warnings are gone
- The `jhipster-framework` pin carries a comment naming F-03 as its removal point

**Implementation Note**: After completing this phase and all automated verification passes,
pause for manual confirmation before proceeding. Phase blocks use plain bullets — the
checkboxes live in `## Progress` at the bottom.

---

## Phase 2: Error handling on Spring `ProblemDetail`

### Overview

Remove every `org.zalando` reference and reproduce the existing error contract on Spring 6's
built-in RFC 7807 support. Nine existing files change, plus two new handler classes and one
new document. Nothing can be executed until F-04, so the contract is documented and the
integration test is written against it.

### Changes Required:

#### 1. The wire contract

**File**: `context/changes/resolvable-build/error-contract.md` (new)

**Intent**: Write down what the current implementation emits before changing it, so
"unchanged" is a checkable claim rather than an intention. This is the reference F-04 and
S-01 verify against.

**Contract**: Documents the response body keys as produced today —
`type`, `title`, `status`, `detail`, `path`, `message`, `params`, `fieldErrors`
(`objectName` / `field` / `message`) — the `application/problem+json` content
type, the `error.http.<status>` fallback for `message`, the
`https://www.jhipster.tech/problem/*` `type` URIs from `ErrorConstants`, and the
`X-carcare-error` / `X-carcare-params` headers produced by `HeaderUtil.createFailureAlert`.
Derived from `ExceptionTranslator.java:46-144` and `ExceptionTranslatorIT.java:45-120`.

Zalando's `violations` key is **excluded** and recorded as a deliberate omission: it is
produced only by `ConstraintViolationProblem`, which requires method-level `@Validated`
constraint violations. No `@Validated` class exists in this tree — the only validation is
`@Valid @RequestBody` (`UserJwtController`, `AccountResource`, `UserResource`), which raises
`MethodArgumentNotValidException` and yields `fieldErrors`, not `violations`. The key is
therefore already unreachable and reproducing it would be dead contract.

#### 2. Exception classes

**Files**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/BadRequestAlertException.java`,
`InternalServerErrorException.java`, `InvalidPasswordException.java`,
`EmailNotFoundException.java`, `CustomParameterizedException.java`

**Intent**: Re-base the five direct subclasses of `AbstractThrowableProblem` onto Spring's
`ErrorResponseException`, keeping every public signature so callers stay untouched.

**Contract**: Each class extends `org.springframework.web.ErrorResponseException`, built
from a `ProblemDetail` carrying the same `type` URI from `ErrorConstants`, the same title
and detail text, and the same `HttpStatus` (`org.zalando.problem.Status` → `HttpStatus`,
same numeric values). Non-standard keys move to `ProblemDetail.setProperty` — `message` and
`params` for `BadRequestAlertException`, the parameter map for `CustomParameterizedException`.
Public constructors and the `getEntityName()` / `getErrorKey()` accessors are unchanged, so
`AccountResource`, `UserResource`, `UriUtil`, and `UserService` need no edit.
`EmailAlreadyUsedException` and `LoginAlreadyUsedException` extend `BadRequestAlertException`
and need no change. `UnparseableUriException` has no zalando dependency and is untouched.

#### 3. `ExceptionTranslator`

**File**: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: Replace the Zalando `ProblemHandling` / `SecurityAdviceTrait` advice with
Spring's `ResponseEntityExceptionHandler`, preserving the documented body.

**Contract**: `@ControllerAdvice` class extending
`org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler`. The
`process(...)` post-processing hook becomes an override of `handleExceptionInternal`, which
adds `path` (from the request URI), the `message` fallback (`error.http.<status>`), and the
`about:blank` → `ErrorConstants.DEFAULT_TYPE` substitution to the `ProblemDetail`'s
properties. `handleMethodArgumentNotValid` keeps producing the `fieldErrors` list of
`FieldErrorVm`. The six `@ExceptionHandler` methods
(`ExceptionTranslator.java:96,105,114,123,129,136`) for `NoSuchElementException`,
`EmailAlreadyUsedException`, `UsernameAlreadyUsedException`, `InvalidPasswordException`,
`BadRequestAlertException`, and `ConcurrencyFailureException`
keep their status codes and `message` keys. `tech.jhipster.web.util.HeaderUtil` stays — it
is still on the classpath, and removing it is F-03. The `javax.annotation` and
`javax.servlet.http` imports should not survive the rewrite: prefer `WebRequest` for the
request URI so this file does not need a Jakarta conversion in F-03.

**`handleExceptionInternal` is not a drop-in for `process(...)` — the six custom handlers
must be routed through it explicitly.** Zalando's `AdviceTrait.create(...)` calls
`process(...)` on every response the advice builds, which is why all six handlers get `path`,
the `message` fallback, and the default-type substitution today (`ExceptionTranslator.java:55-72`).
Spring's `handleExceptionInternal` is invoked only from inside `ResponseEntityExceptionHandler`'s
own sixteen handlers; a subclass `@ExceptionHandler` that returns a `ResponseEntity` directly
never passes through it. Each of the six must therefore build a `ProblemDetail` and return
`handleExceptionInternal(ex, problemDetail, headers, status, request)` — which also keeps the
`HeaderUtil.createFailureAlert` headers flowing through the same call — rather than
constructing a `ResponseEntity` itself. Their signatures gain `HttpHeaders` and `WebRequest`
accordingly. Without this, `ExceptionTranslatorIT:47` (`$.message`) and `:92` (`$.path`)
regress silently, and `type` becomes `about:blank`.

**`ResponseEntityExceptionHandler` covers strictly less than `ProblemHandling` — two
categories need new handlers.** Verified by `javap -v` on spring-webmvc 6.0.13: the class
declares exactly one `@ExceptionHandler`, listing sixteen named Spring MVC exception types.
It has no `Throwable`/`Exception` catch-all and no handling for exceptions carrying
`@ResponseStatus`. Both are supplied today by the Zalando traits at
`ExceptionTranslator.java:33`, and both are asserted by the existing IT. This change must
therefore add:

- An `@ExceptionHandler(Throwable.class)` catch-all emitting 500 with title
  `Internal Server Error` and `message` = `error.http.500`, preserving
  `ExceptionTranslatorIT:114-121`. Without it, an unhandled `RuntimeException` falls through
  to Boot's `/error`, which returns `{"timestamp","status","error","path"}` as
  `application/json` — a different body *and* a different content type. Log the throwable
  explicitly in this handler: unlike Boot's error page, a catch-all advice otherwise
  swallows the stack trace.
- Within that catch-all, honour a `@ResponseStatus` annotation on the thrown type via
  `AnnotatedElementUtils.findMergedAnnotation`, mapping its `value` to the status and its
  `reason` to the `title`, preserving `ExceptionTranslatorIT:105-112`. `ResponseStatusException`
  itself needs nothing — it extends `ErrorResponseException`, which is in the sixteen.

`ConstraintViolationException` is deliberately **not** given a handler: no method-level
`@Validated` exists in this tree, so the Zalando `violations` shape is already unreachable.
See §1 — it is dropped from the contract document rather than reproduced.

#### 4. Security error handlers

**Files**: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java`, plus a
new `ProblemDetail`-emitting entry point and access-denied handler in the same package

**Intent**: Preserve the 401 and 403 response bodies that `SecurityProblemSupport` produces
today, at **both** the layers Zalando covered — the filter chain and the controller advice.

**Contract**: `@Import(SecurityProblemSupport.class)`, the `problemSupport` field, and the
zalando import are removed. Two new components implement
`org.springframework.security.web.AuthenticationEntryPoint` and
`org.springframework.security.web.access.AccessDeniedHandler`, writing
`application/problem+json` bodies matching the contract document (`message` =
`error.http.401` / `error.http.403`, plus `path` and `detail`). They are wired at the two
call sites inside `configure(HttpSecurity)` where `problemSupport` was used. The class still
extends `WebSecurityConfigurerAdapter` and still does not compile — that is F-03's to fix,
and these two beans carry over into its `SecurityFilterChain`.

**The filter-chain beans alone are not sufficient.** `SecurityProblemSupport` was only half
of the arrangement: `ExceptionTranslator` also `implements SecurityAdviceTrait`
(`ExceptionTranslator.java:33`), which handles `AccessDeniedException` and
`AuthenticationException` *inside the advice*. That advice layer is what
`ExceptionTranslatorIT`'s `testAccessDenied` and `testUnauthorized` exercise — the test builds
`MockMvcBuilders.standaloneSetup(...)` (`ExceptionTranslatorIT.java:36`), a harness with no
Spring Security filter chain at all, so filter-chain beans are unreachable from it. Replacing
only `SecurityProblemSupport` therefore leaves both handlers with no executable coverage,
now or after F-04.

`ExceptionTranslator` accordingly keeps `@ExceptionHandler` methods for
`org.springframework.security.access.AccessDeniedException` (403) and
`org.springframework.security.core.AuthenticationException` (401). Both those methods and the
two filter-chain components delegate to **one shared `ProblemDetail` builder** — a package
-private helper taking status, detail and request URI — so the two layers cannot drift apart.
This mirrors the existing arrangement rather than adding one: `SecurityProblemSupport` and
`SecurityAdviceTrait` are both present today for the same reason.

#### 5. `JacksonConfiguration`

**File**: `src/main/java/com/kasztelanic/carcare/config/JacksonConfiguration.java`

**Intent**: Drop the two Jackson modules that exist only to serialize Zalando's `Problem`
type. Spring serializes `ProblemDetail` without help.

**Contract**: The `problemModule()` and `constraintViolationProblemModule()` beans and their
two `org.zalando` imports are deleted. `JavaTimeModule`, `Jdk8Module`, `VavrModule`, and
`Hibernate5Module` are untouched — the last is F-03's concern.

#### 6. `ExceptionTranslatorIT`

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorIT.java`

**Intent**: Keep the test asserting the documented contract, so that when F-04 makes tests
executable this file is a real regression check rather than dead code.

**Contract**: The existing assertions at lines 45-120 (`application/problem+json` content
type; `$.message`, `$.detail`, `$.title`, `$.path`, `$.fieldErrors[0].objectName|field|message`)
are preserved verbatim wherever the contract document says the value is unchanged. Any
assertion that must change is changed only with a matching entry in the contract document
explaining why. The test is **not executed** in this change.

All ten test methods should survive unmodified once §3's catch-all and §4's advice-level
security handlers are in place: `testAccessDenied` / `testUnauthorized` depend on §4's advice
handlers, `testInternalServerError` / `testExceptionWithResponseStatus` on §3's catch-all, and
`testConcurrencyFailure` / `testUnauthorized` on §3's `handleExceptionInternal` routing. If any
assertion still has to change, that is a signal one of those three pieces is missing — treat it
as a defect in the rewrite before treating it as a contract divergence. `testMethodNotSupported`
is the one likely genuine divergence: Spring 6 words the 405 `detail` differently from Zalando
("Method 'POST' is not supported." vs `Request method 'POST' not supported`). Confirm the exact
string during implementation and record it in the contract document.

### Success Criteria:

#### Automated Verification:

- No Zalando reference survives: `grep -rc 'org\.zalando' src/ pom.xml` returns 0 for every file
- No Zalando-attributable diagnostic remains: `./mvnw compile 2>&1 | grep -c 'org\.zalando'` returns 0
- The error package compiles clean: `./mvnw compile 2>&1 | grep -c 'web/rest/errors'` returns 0. No `javax.` exclusion clause is needed — `ExceptionTranslator.java:21-23` holds the only three `javax.*` imports in the package and this phase removes them
- Callers were genuinely untouched: `git diff --stat` shows no change to `AccountResource.java`, `UserResource.java`, `UriUtil.java`, `UserService.java`
- `context/changes/resolvable-build/error-contract.md` exists

#### Manual Verification:

- The contract document is reviewed against `ExceptionTranslator.java:46-144` and agreed to describe today's behaviour
- Every divergence from that contract is listed and accepted
- All four security handlers — the two filter-chain components and the two advice methods — plus the shared `ProblemDetail` builder they delegate to, are reviewed by inspection, since no test can exercise the filter-chain pair until F-04
- `ErrorConstants.PROBLEM_BASE_URL` still reads `https://www.jhipster.tech/problem`
- The `Throwable` catch-all and its `@ResponseStatus` branch are reviewed against `ExceptionTranslatorIT:105-121`, and the catch-all logs the throwable rather than swallowing it

**Implementation Note**: Pause for manual confirmation before proceeding to Phase 3.

---

## Phase 3: jjwt 0.12.3 in `TokenProvider`

### Overview

Move token signing and validation onto the jjwt 0.12 API pinned in Phase 1. Three files
change. Security-critical and unverifiable until F-04, so the work is deliberately small and
reviewed by inspection.

### Changes Required:

#### 1. `TokenProvider`

**File**: `src/main/java/com/kasztelanic/carcare/security/jwt/TokenProvider.java`

**Intent**: Replace the 0.11.x builder and parser calls with their 0.12 equivalents, keeping
the algorithm, the key derivation, and the claim set identical so existing tokens stay
valid.

**Contract**: The field type changes first: `TokenProvider.java:42` declares
`private Key key` (`java.security.Key`) and must become `javax.crypto.SecretKey`. 0.12's
parser exposes a type-safe `verifyWith(SecretKey)` alongside a separate
`verifyWith(PublicKey)`, and `Jwts.SIG.HS512` is a `MacAlgorithm` — i.e.
`SecureDigestAlgorithm<SecretKey, SecretKey>` — so neither `verifyWith(key)` nor
`signWith(key, Jwts.SIG.HS512)` type-checks against `java.security.Key`. Without this the
phase produces non-compiling code and cannot meet its own criterion 3.3.

Then the API substitutions: `io.jsonwebtoken.SignatureAlgorithm.HS512` → `Jwts.SIG.HS512`;
`Jwts.parserBuilder()` → `Jwts.parser()`; `setSigningKey(key)` → `verifyWith(key)`;
`parseClaimsJws(token)` → `parseSignedClaims(token)`, whose result exposes `getPayload()`
in place of `getBody()`. `setSubject` / `setExpiration` are deprecated but still present in
0.12.3, so the builder compiles with warnings; either migrate them to `subject()` /
`expiration()` or accept the warnings deliberately. `Decoders.BASE64.decode` and
`Keys.hmacShaKeyFor` are unchanged — the latter already returns a `SecretKey`, so the
assignment at `TokenProvider.java:58` needs no edit — so
the key is derived from the same `jhipster.security.authentication.jwt.base64-secret` and
tokens issued before the change still verify. The caught exception types
(`ExpiredJwtException`, `UnsupportedJwtException`, `MalformedJwtException`,
`io.jsonwebtoken.security.SignatureException`, `IllegalArgumentException`) and their
`SecurityMetersService` counters keep their current mapping. `tech.jhipster.config.JHipsterProperties`
stays — F-03 removes it.

#### 2. jjwt-using tests

**Files**: `src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java`,
`JwtFilterTest.java`

**Intent**: Apply the same API migration so the tests compile and remain meaningful when
F-04 makes them runnable.

**Contract**: Same substitutions as `TokenProvider`, including the type change —
`TokenProviderTest.java:103` declares `Key otherKey` and must become `SecretKey otherKey`
for `signWith(otherKey, Jwts.SIG.HS512)` at line 108 to compile. Assertions and test intent
are unchanged. Not executed in this change.

#### 3. Refresh the inventory

**File**: `context/changes/resolvable-build/migration-surface.md`

**Intent**: Regenerate the handoff so F-03 receives the diagnostic set that actually remains
after Phases 2 and 3, not the pre-Phase-2 snapshot.

**Contract**: Re-run the capture commands recorded in the document and replace its contents,
keeping the do-not-convert entry for `javax.sql.DataSource`.

### Success Criteria:

#### Automated Verification:

- The 0.11 API is gone: `grep -rc 'SignatureAlgorithm\|parserBuilder\|parseClaimsJws' src/` returns 0 for every file
- No jjwt-attributable diagnostic remains: `./mvnw compile 2>&1 | grep -c 'io\.jsonwebtoken'` returns 0
- `security/jwt/TokenProvider.java` contributes no diagnostic at all: `./mvnw compile 2>&1 | grep -c 'jwt/TokenProvider.java'` returns 0
- Every remaining `[ERROR]` line naming a `.java` file is **attributable** to one of three known categories, and the attribution is recorded in `migration-surface.md`: (a) an unconverted Jakarta namespace, (b) a Spring Security 6 API removal, (c) a `tech.jhipster` symbol. This is a categorisation, not a text filter — many lines match none of the three strings. `SecurityConfiguration.java:45-51,86-96` calls `antMatchers(...)`, removed in Spring Security 6 (`AbstractRequestMatcherRegistry` exposes only `requestMatchers`), and cascading `cannot find symbol` errors name only the missing symbol. Each such line is charged to the category that caused it
- `migration-surface.md` is regenerated and its diagnostic count is lower than Phase 1's

#### Manual Verification:

- Signing and validation logic reviewed line by line against the pre-change version — no test can exercise it until F-04
- Confirmed that the key derivation path is byte-identical, so tokens issued before the change still verify
- The residual diagnostic list in `migration-surface.md` is reviewed and accepted as F-03's starting point

---

## Testing Strategy

The honest position: **no test runs in this change.** No Spring context can load until F-04
restores it, and `./mvnw test` cannot even reach Surefire while `src/main` does not compile.
Verification here is compiler-driven and document-driven.

### Unit Tests:

- None added. `TokenProviderTest` and `JwtFilterTest` are migrated to the 0.12 API so they
  compile and stay meaningful, but are not executed.

### Integration Tests:

- `ExceptionTranslatorIT` is updated to the documented error contract and left unexecuted.
  It becomes the first real check of Phase 2's work when F-04 lands.

### Manual Testing Steps:

1. Run `./mvnw validate` on the default, `prod`, and `IDE` profiles and confirm each exits 0.
2. Run `./mvnw compile` and confirm the first source-location error names a `.java` file.
3. Read `migration-surface.md` and confirm the diagnostic categories match the static
   `javax.*` counts, allowing for cascading `cannot find symbol` errors.
4. Read `error-contract.md` against `ExceptionTranslator`'s pre-change behaviour and confirm
   each documented key is accounted for.
5. Diff `TokenProvider` before and after and confirm the algorithm, key derivation, claim
   names, and exception-to-meter mapping are unchanged.

## Performance Considerations

None. This change alters no runtime code path's complexity. The one measurable effect is on
build time: dropping `spring-cloud-starter-bootstrap` avoids resolving the spring-cloud BOM,
and `-Xmaxerrs 10000` makes failing compiles print more output, both negligible.

## Migration Notes

- **Token compatibility is preserved.** jjwt 0.12 changes the API, not the JWS format. The
  key still derives from the same base64 secret via `Keys.hmacShaKeyFor`, and the algorithm
  stays HS512, so tokens issued by the current production build remain verifiable. Nothing
  needs to be invalidated at deploy time.
- **Error response bodies are the one client-visible change**, and only if Phase 2's contract
  is not reproduced exactly. This is why the contract is written down before the code is
  touched. The risk is real and unverifiable until F-04 — accepted deliberately by the owner
  when scoping the Zalando removal into this change.
- **No database, schema, or Liquibase change.** No data migration, no rollback data concern.
- **Rollback is `git revert`.** Every change is confined to `pom.xml`, nine files under
  `web/rest/errors/` and `config/`, three under `security/jwt/`, and two new documents.

## Follow-ups deliberately deferred

- `ErrorConstants.PROBLEM_BASE_URL` still points at `jhipster.tech`. Changing it alters the
  `type` field of every error response, which the client may match on. Belongs with S-01's
  parity work or later.
- `jackson-datatype-hibernate5` → `hibernate6` (F-03).
- `tech.jhipster:jhipster-framework` removal, completing FR-002 (F-03).
- The remaining `commons-compress` `DependencyConvergence` warning is pre-existing, from POI
  and Tika. Not introduced here.

## References

- Roadmap item F-01: `context/foundation/roadmap.md` (`## Foundations`)
- PRD FR-001, FR-002, FR-003, FR-004: `context/foundation/prd.md` (`### Build & platform`)
- Health check: `context/foundation/health-check.md`
- Stack assessment: `context/foundation/stack-assessment.md`
- Build and toolchain notes, including the required `JAVA_HOME`: `AGENTS.md`
- Current failure: `pom.xml` lines 182, 207, 211, 219, 223, 228, 290, 298, 302, 307, 362
- Error contract source: `ExceptionTranslator.java:46-144`, `ExceptionTranslatorIT.java:45-120`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Resolvable dependency graph

#### Automated

- [x] 1.1 `./mvnw validate` exits 0 with no missing-version errors — 7cd2e41
- [x] 1.2 `./mvnw -Pprod validate` exits 0 — 7cd2e41
- [x] 1.3 `./mvnw -PIDE validate` exits 0 — 7cd2e41
- [x] 1.4 `./mvnw dependency:resolve` exits 0 — 7cd2e41
- [x] 1.5 `grep -c 'jhipster-dependencies' pom.xml` returns 0 — 7cd2e41
- [x] 1.6 `grep -c 'problem-spring-web' pom.xml` returns 0 — 7cd2e41
- [x] 1.7 `./mvnw compile` first source-location error names a `.java` file, not `pom.xml` — 7cd2e41
- [x] 1.8 `-Xmaxerrs` binds: flagged diagnostic count strictly exceeds the un-flagged run's, both recorded — 7cd2e41
- [x] 1.9 `migration-surface.md` exists with a non-zero diagnostic count — 7cd2e41

#### Manual

- [x] 1.10 Four pinned versions reviewed and accepted — 7cd2e41
- [x] 1.11 Only the pre-existing `commons-compress` convergence warning remains — 7cd2e41
- [x] 1.12 `jhipster-framework` pin carries an F-03 removal comment — 7cd2e41

### Phase 2: Error handling on Spring `ProblemDetail`

#### Automated

- [x] 2.1 No `org.zalando` reference in `src/` or `pom.xml` — fae090b
- [x] 2.2 No Zalando-attributable compile diagnostic — fae090b
- [x] 2.3 `web/rest/errors/` contributes no diagnostic at all — fae090b
- [x] 2.4 `AccountResource`, `UserResource`, `UriUtil`, `UserService` unchanged in `git diff --stat` — fae090b
- [x] 2.5 `error-contract.md` exists — fae090b

#### Manual

- [x] 2.6 Contract document reviewed against pre-change `ExceptionTranslator` behaviour — fae090b
- [x] 2.7 Every divergence from the contract listed and accepted — fae090b
- [x] 2.8 All four security handlers and their shared `ProblemDetail` builder reviewed by inspection — fae090b
- [x] 2.9 `ErrorConstants.PROBLEM_BASE_URL` unchanged — fae090b
- [x] 2.10 `Throwable` catch-all and `@ResponseStatus` branch reviewed; catch-all logs the throwable — fae090b

### Phase 3: jjwt 0.12.3 in `TokenProvider`

#### Automated

- [x] 3.1 No `SignatureAlgorithm`, `parserBuilder`, or `parseClaimsJws` in `src/` — b8b191e
- [x] 3.2 No `io.jsonwebtoken`-attributable compile diagnostic — b8b191e
- [x] 3.3 `TokenProvider.java` contributes no diagnostic — b8b191e
- [x] 3.4 Every remaining error attributed in `migration-surface.md` to Jakarta, a Spring Security 6 removal, or `tech.jhipster` — b8b191e
- [x] 3.5 `migration-surface.md` regenerated with a lower diagnostic count than Phase 1 — b8b191e

#### Manual

- [x] 3.6 Signing and validation logic reviewed line by line — b8b191e
- [x] 3.7 Key derivation confirmed byte-identical; existing tokens still verify — b8b191e
- [x] 3.8 Residual diagnostic list accepted as F-03's starting point — b8b191e
