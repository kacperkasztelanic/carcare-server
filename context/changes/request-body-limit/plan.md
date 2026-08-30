# Request Body Limit Implementation Plan

## Overview

Implement roadmap slice S-03, `request-body-limit`, as an application-level request admission
boundary. The server will accept request bodies up to and including 4 MiB and reject larger bodies
or HTTP/1.1 requests that explicitly use transfer coding without a known length before Spring
Security, MVC/Jackson binding, service execution, database writes, or image storage.

The change is intentionally standalone. It adds a servlet-container filter and its tests; it does
not change the frozen client, image handling, database schema, or the live/shared NGINX deployment.

## Current State Analysis

The application has no request-body limit, request wrapper, `Content-Length` guard, or explicit
servlet filter registration for admission control. `WebConfigurer` handles startup logging, MIME
mappings, static assets, and CORS, while `SecurityConfiguration` adds CORS and JWT filters only.
Spring Security also ignores `OPTIONS` and several static/documentation paths, so a check inserted
only with `addFilterBefore` would not be global.

Vehicle images are base64-encoded inside JSON `VehicleDto` requests. Jackson materializes the
nested `VehicleDetailsDto.image` byte array before `VehicleDetailsMapper` calls
`ImageStorageService.save`, so a storage-layer check would be too late for the roadmap outcome.
The same boundary should cover the other JSON body routes without coupling to image code.

The repository's Spring Boot/Tomcat and multipart properties do not constrain this JSON shape. The
historical repository NGINX file and the live proxy both have a 4 MiB guard, but the live value is
shared at `http` scope in a private deployment project and is not an application guarantee.

The existing error architecture has two layers: `ExceptionTranslator` handles MVC-dispatched
exceptions, while the security entry point and access-denied handler serialize ProblemDetail
directly from the filter chain. A pre-dispatch body rejection must use the latter style itself.

## Desired End State

Every servlet `REQUEST` dispatch passes through one explicit request-body filter before the complete
Spring Security chain. A request with a known `Content-Length` of 4,194,304 bytes or less continues
normally; a larger request is rejected immediately with HTTP 413, without opening its body stream
or invoking downstream filters.

An HTTP/1.1 request for which `getContentLengthLong() == -1` and a `Transfer-Encoding` header is
present is rejected before the chain. Ordinary bodyless requests without that explicit framing
signal—including static GETs, preflight requests, and existing path-only POSTs—remain unaffected.
Headerless HTTP/2 or HTTP/3 bodies cannot be distinguished from bodyless requests through the
portable Servlet API without reading the stream; they are outside this pre-read guarantee and the
policy must be revisited if either protocol is enabled.

The rejection response is valid UTF-8 `application/problem+json`, uses the existing ProblemDetail
conventions, contains no body contents or sensitive request data, and increments a low-cardinality
rejection metric. A safe, rate-limited warning provides the request path and declared length only.

### Key Discoveries:

- `SecurityConfiguration.webSecurityCustomizer()` bypasses the Spring Security chain for `OPTIONS`
  and several static/documentation paths; the boundary must be registered at the servlet-container
  level (`src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:45-94`).
- Boot 3.1.5's default Security filter registration order is `-100`; the new registration must be
  explicitly earlier and must not rely on `FilterRegistrationBean`'s default order.
- `VehicleDetailsDto.image` is a JSON/base64 `byte[]`, and filesystem storage occurs after MVC
  binding (`src/main/java/com/kasztelanic/carcare/service/dto/VehicleDetailsDto.java:13-20`,
  `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java:40-54`).
- The application already has direct filter-layer ProblemDetail serialization in
  `ProblemDetailAuthenticationEntryPoint` and `ProblemDetailAccessDeniedHandler`.
- Full-context `@SpringBootTest` + `@AutoConfigureMockMvc` is the repository's REST integration
  convention; raw servlet tests are needed to prove that an oversized stream is never opened.
- The recommended 2 MiB value was changed during planning to a fixed 4 MiB ceiling, matching the
  owner's decision and the existing proxy capacity. The application remains authoritative and the
  proxy is unchanged.

## What We're NOT Doing

- No changes to `VehicleDto`, `VehicleDetailsDto`, image mapping, image storage, image format
  validation, image write ordering, or path containment; those are separate roadmap slices.
- No client or client-artifact update. Client 1.2.5's known silent false-success rendering of a
  413 remains documented behavior.
- No database schema, Liquibase, entity, repository, or transaction changes.
- No change to JWT authentication, authorization rules, CORS policy, or Spring Security's ignored
  path list.
- No `server.tomcat.*` or `spring.servlet.multipart.*` setting as a substitute for the filter.
- No edits to the historical `src/main/docker` deployment files and no changes to the live private
  NGINX configuration. Proxy alignment is explicitly outside S-03.
- No support for explicitly transfer-coded HTTP/1.1 request bodies whose length is unknown; they
  are rejected before application code. Headerless HTTP/2 and HTTP/3 bodies are outside this slice.
- No request-body content, query string, authorization header, or other sensitive value in logs or
  metric labels.

## Implementation Approach

Add a dedicated `OncePerRequestFilter` in a web-filter package and register it exactly once through a
`FilterRegistrationBean`. The registration maps to `/*`, is restricted to `DispatcherType.REQUEST`,
and uses an explicit order immediately before Spring Security's servlet filter registration.

The filter performs an O(1) admission check using the servlet request's long content-length value.
Known lengths at or below the fixed limit continue unchanged. Known lengths above the limit are
rejected before either `getInputStream()` or `getReader()` is called. The unknown-length rejection
predicate is exactly `request.getContentLengthLong() == -1`, `request.getProtocol()` equal to
`HTTP/1.1`, and a present `Transfer-Encoding` header. A request without that explicit HTTP/1.1
framing signal continues so existing GET, preflight, and path-only POST behavior is preserved.
Malformed or conflicting framing is left to the servlet container.

The filter writes the 413 response directly using the application's `ObjectMapper` and
`SecurityProblemDetails` conventions. At construction it pre-registers two Micrometer counters
named `http.server.request-body.rejections`, described as rejected HTTP request bodies, with base
unit `requests` and one `reason` tag whose only values are `declared-too-large` and
`unknown-length`. Every rejection increments the matching counter.

Warning throttling is process-global across both reasons: log the first rejection immediately, then
at most one warning in each 60-second interval even under concurrent requests. Implement the gate
with atomic state driven by monotonic nanoseconds rather than wall-clock time. Production supplies
`System::nanoTime`; expose a constructor-level `LongSupplier` seam so unit tests can advance time
without sleeping. Suppressed warnings never suppress counter increments.

## Critical Implementation Details

The filter must run before the entire `springSecurityFilterChain`, not merely before
`UsernamePasswordAuthenticationFilter`; otherwise an anonymous oversized API request would become
401 and ignored paths would bypass the limit. Use one explicit `FilterRegistrationBean` only.
Construct `RequestBodyLimitFilter` inside that registration-bean factory from its dependencies; do
not annotate it as a component, expose it as a separate Spring `Filter` bean, or add it to
`SecurityConfiguration`.

The strict unknown-length rule applies only to HTTP/1.1 requests where the Servlet API reports
`getContentLengthLong() == -1` and exposes a `Transfer-Encoding` header, such as a valid chunked
request accepted by the container. A request without that explicit signal must still pass,
including the existing bodyless POST route. Do not treat `TE: trailers` on HTTP/2 as request-body
framing; HTTP/2 and HTTP/3 require a separate policy if enabled later.

The 413 response cannot reach `ExceptionTranslator` because the filter returns before
`DispatcherServlet`. Serialize it directly as `application/problem+json` with UTF-8, status 413,
the shared default problem type, a fixed safe detail, `message=error.http.413`, and the request URI
as `path`; never use `sendError(413)` as the API response mechanism.

## Phase 1: Production Filter and Registration

### Overview

Create the application-level admission filter, its direct error response, its rejection telemetry,
and its explicit servlet registration. Keep the implementation independent of controllers, DTOs,
image storage, and the Spring Security chain internals.

### Changes Required:

#### 1. Request admission filter

**File**: `src/main/java/com/kasztelanic/carcare/web/filter/RequestBodyLimitFilter.java`

**Intent**: Add the single enforcement point that rejects oversized or body-bearing unknown-length
requests with explicit HTTP/1.1 transfer coding before any downstream body consumer can buffer or
persist data. Keep the class in a cross-cutting web package and implement the repository's direct
filter-layer response convention.

**Contract**: Expose one fixed maximum of `4_194_304` request-body bytes; accept declared lengths in
the inclusive range `0..4_194_304`; reject larger declared lengths and framed unknown lengths before
calling the chain or obtaining either body accessor. Pass accepted requests through without replacing
their input stream or reader. Reject an unknown length only when the protocol is exactly HTTP/1.1
and `Transfer-Encoding` is present; pass headerless unknown-length requests without reading them.
Write HTTP 413 as UTF-8 `application/problem+json` using the existing ProblemDetail helpers, with
status 413, the shared default type, a fixed non-echoing detail, `message=error.http.413`, and
`path=request.getRequestURI()`.

The filter must pre-register and increment the two fixed
`http.server.request-body.rejections{reason=...}` counters described above. Its single process-global
warning gate logs the first rejection immediately and permits at most one warning per 60 seconds
thereafter, using atomic monotonic-time state and the injected `LongSupplier` test seam. Warnings
contain only the path and declared length. Do not add success alert headers, log bodies, or include
request paths as metric labels.

#### 2. Servlet-container registration

**File**: `src/main/java/com/kasztelanic/carcare/config/RequestBodyLimitConfiguration.java`

**Intent**: Register the filter once at the servlet boundary so it also covers Spring Security
ignored paths and remains valid for the embedded application and WAR packaging.

**Contract**: Provide one `FilterRegistrationBean<RequestBodyLimitFilter>` with URL pattern `/*`,
dispatcher type `DispatcherType.REQUEST`, and an explicit order before the effective Spring
Security filter registration (use the Boot 3.1.5 Security default order relationship rather than
the `FilterRegistrationBean` default). Instantiate the filter only inside this bean factory; the
application context must contain exactly one relevant registration bean and no standalone
`RequestBodyLimitFilter` bean. Do not modify `WebConfigurer`, `SecurityConfiguration`, `CarcareApp`,
`ApplicationWebXml`, `pom.xml`, or NGINX files, and do not expose a second independent registration
for the same filter.

### Success Criteria:

#### Automated Verification:

- The new filter and registration compile under the repository's required Java 17 toolchain without dependency or configuration changes outside the two new production files.
- The filter's known-length path performs an admission decision before downstream filters, body accessors, MVC binding, or service execution.
- The registration exposes the intended `/*`, `REQUEST`, and pre-Security ordering metadata through the Spring Boot 3.1.5 servlet registration model.

#### Manual Verification:

- Review the production diff and confirm the boundary is outside `SecurityConfiguration`, image storage, DTO validation, multipart settings, and proxy configuration.
- Review the rejection telemetry and confirm it contains no body, query string, authorization value, or high-cardinality metric label.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation that the registration and telemetry boundaries are correct before proceeding to the test-contract phase.

## Phase 2: Filter and Registration Test Contract

### Overview

Add focused tests that prove the filter's byte-level contract independently of MVC and prove that the
Spring Boot registration is not accidentally duplicated, misordered, or mapped to the wrong
dispatcher types.

### Changes Required:

#### 1. Raw filter behavior tests

**File**: `src/test/java/com/kasztelanic/carcare/web/filter/RequestBodyLimitFilterTest.java`

**Intent**: Exercise the filter with raw servlet mocks so the pre-buffering guarantee is observable,
including cases that ordinary MockMvc content builders cannot model.

**Contract**: Cover zero, below-limit, exactly-at-limit, and limit-plus-one declared lengths; assert
that accepted requests call the chain and preserve body bytes for both `getInputStream()` and
`getReader()` paths in separate tests. For rejected requests, assert status 413, UTF-8
`application/problem+json`, valid ProblemDetail fields, no chain invocation, and no call to either
body accessor. Cover an HTTP/1.1 request with `Transfer-Encoding: chunked` and unknown length as
rejected, an unframed bodyless request as passed, and an HTTP/2 request with `TE: trailers` as passed.
Use UTF-8 multibyte content in the reader case to ensure the policy is based on wire bytes, not
character count, and verify rejection counters use only the two fixed reason values.

#### 2. Registration tests

**File**: `src/test/java/com/kasztelanic/carcare/config/RequestBodyLimitConfigurationTest.java`

**Intent**: Verify the registration contract directly instead of relying only on MockMvc's filter
assembly, which does not fully model servlet dispatcher-type lifecycle behavior.

**Contract**: Load the configuration in an application context and assert exactly one relevant
`FilterRegistrationBean`, no standalone `RequestBodyLimitFilter` bean, URL mapping `/*`, only
`DispatcherType.REQUEST`, an order before Spring Security's effective registration, and the expected
contained filter type. Invoke that registration against the `MockServletContext` spy seam used by
`WebConfigurerTest` and verify exactly one `ServletContext.addFilter` call. Together the context-shape
and dynamic-registration assertions prove there is no auto-discovered duplicate path.

### Success Criteria:

#### Automated Verification:

- Filter unit tests pass for under-limit, exact-limit, over-limit, HTTP/1.1 transfer-coded unknown-length, HTTP/2 `TE: trailers`, and unframed bodyless requests.
- Rejection tests prove the downstream chain and both request-body accessors are untouched, and parse the complete 413 ProblemDetail response.
- Pass-through tests prove input-stream and UTF-8 reader contents remain unchanged for accepted requests.
- Registration tests prove one relevant registration bean, no standalone filter bean, one dynamic `/*` `REQUEST` registration, pre-Security order, and no duplicate path.
- Telemetry tests prove the fixed meter contract, one counter increment per rejection, immediate first warning, process-global 60-second suppression under concurrency, and re-enablement using a fake monotonic-time source without sleeping.

#### Manual Verification:

- Review the boundary tests and confirm the inclusive 4 MiB rule, explicit HTTP/1.1 transfer-coding predicate, HTTP/2 exclusion, and preserved bodyless-request rule are all explicit rather than incidental.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation that the raw servlet contract matches the intended request boundary before proceeding to full-context integration coverage.

## Phase 3: Full-Context Integration and Final Verification

### Overview

Prove the filter is active in the real application context, precedes authorization and MVC, prevents
domain side effects, and leaves existing small requests and bodyless endpoints unchanged. Finish
with the repository's complete Java 17 verification command.

### Changes Required:

#### 1. Application integration tests

**File**: `src/test/java/com/kasztelanic/carcare/config/RequestBodyLimitIT.java`

**Intent**: Exercise the registered filter through the repository's standard
`@SpringBootTest` + `@AutoConfigureMockMvc` harness, using both a side-effect-free body route and
the image-bearing vehicle routes.

**Contract**: Include these scenarios:

- An anonymous oversized request to a protected API route returns 413 rather than 401, proving the
  admission filter precedes the complete Spring Security chain.
- An oversized valid JSON request to a body-bearing test route returns 413 before validation or
  controller execution.
- An authenticated oversized vehicle create leaves the vehicle row count unchanged and writes no
  image file.
- An authenticated oversized vehicle update leaves the existing vehicle details and image state
  unchanged; no mapper, repository update, image save, or image delete is reached.
- A small valid JSON request still reaches its controller successfully, preserving existing vehicle
  and authentication/account request behavior.
- Root/static behavior, ignored `OPTIONS`, `/management/health`, `/management/info`, and the
  existing bodyless path-only POST route continue to work.

Use existing `@WithMockUser`, `JdbcTemplate`, `SessionFixtures`, and MockMvc patterns. Keep side
effect assertions isolated from the shared repository data directory; rely on transactional database
state and a temporary or mocked storage seam where needed. Do not treat MockMvc alone as proof that
Tomcat or NGINX did not buffer bytes before the application boundary.

#### 2. Repository verification and handoff

**File**: `context/changes/request-body-limit/plan.md` (Progress only)

**Intent**: Run the complete project verification and record the exact automated/manual acceptance
criteria for the next implementation agent.

**Contract**: Use Java 17 from `/Users/kacper/.sdkman/candidates/java/17.0.20-tem` and run the
repository's normal `./mvnw verify`. No migration, proxy build, client build, or deployment change
is part of this slice. If a local direct-HTTP smoke test is performed, it must target the
application directly and document that the shared 4 MiB proxy remains unchanged.

### Success Criteria:

#### Automated Verification:

- `RequestBodyLimitIT` passes in the full Spring application context, including 413-before-401 ordering and no-controller/no-side-effect assertions.
- Existing small vehicle, authentication/account, report/event, static, management, and bodyless POST behavior remains green under the full test harness.
- `JAVA_HOME=/Users/kacper/.sdkman/candidates/java/17.0.20-tem ./mvnw verify` passes with the repository's complete unit and integration suite.
- The final diff contains no Liquibase changes, client changes, historical deployment edits, live proxy edits, or unrelated hardening work.

#### Manual Verification:

- Send a request just over 4 MiB directly to a locally running application and confirm HTTP 413 with the ProblemDetail media type and no application-side write; separately confirm a request at or below 4 MiB reaches normal handling.
- If exercising the frozen client, record its known behavior accurately: the 413 may close the edit flow and navigate as if successful while the old image remains; retry with a small request must still work.
- Confirm no production proxy configuration was changed and that the application-level limit remains the authoritative S-03 behavior.

**Implementation Note**: After all automated verification passes, pause for human confirmation of the direct-HTTP and frozen-client observations before treating S-03 as complete.

## Testing Strategy

### Unit Tests:

- Use the existing `MockHttpServletRequest`, `MockHttpServletResponse`, and `MockFilterChain` style
  from `JwtFilterTest`.
- Test `0`, below-limit, exactly `4_194_304`, and `4_194_305` declared byte lengths.
- Use a request double that fails if `getInputStream()` or `getReader()` is called on the rejected
  branch.
- Test input-stream pass-through and reader pass-through separately because
  `MockHttpServletRequest` does not allow both access modes on one request.
- Test UTF-8 byte semantics, HTTP/1.1 transfer-coded unknown-length rejection, HTTP/2 `TE: trailers`
  pass-through, unframed bodyless pass-through, and direct 413 response serialization.
- Test the fixed meter name, description, base unit, and reason values; assert one counter increment
  per rejection without a request-path label. Use a fake monotonic-time source to test the immediate
  first warning, process-global 60-second suppression including concurrent attempts, and subsequent
  re-enablement without sleeping.

### Integration Tests:

- Use full-context MockMvc, not a standalone controller harness, to exercise the actual servlet
  registration, Spring Security ordering, configured `ObjectMapper`, and controller advice boundary.
- Prove an anonymous oversized API request gets 413 before authentication would return 401.
- Prove oversized vehicle create/update requests do not change database state or image files and do
  not emit normal success alert headers.
- Preserve normal small JSON requests, authentication/account requests, reports/events, root/static
  resources, ignored preflight requests, management health/info, and the path-only POST route.
- Keep proxy behavior out of this integration class; MockMvc cannot test the live private NGINX
  project.

### Manual Testing Steps:

1. Run the application directly with the test or local profile and send a body at exactly 4 MiB and
   one at 4 MiB plus one byte, recording status, media type, and whether the application logs a
   rejection.
2. Send an HTTP/1.1 chunked/unknown-length body directly to the application and confirm it is
   rejected before the target endpoint runs; confirm normal bodyless GET, OPTIONS, and path-only
   POST requests still succeed.
3. Through client 1.2.5, attempt an oversized vehicle image update, verify that the stored image is
   unchanged despite the client's false-success navigation, then retry with a normal image.

## Performance Considerations

The normal known-length path is constant-time and does not read, copy, cache, or wrap the request
body. The 4 MiB value is a wire-size ceiling, so JSON envelope and base64 overhead count toward it.
HTTP/1.1 transfer-coded bodies with unknown length are rejected before reads, avoiding a counting
wrapper and late exceptions after partial buffering. Headerless HTTP/2 and HTTP/3 bodies remain
outside this portable Servlet-level guarantee. Metric labels remain fixed to prevent cardinality
growth; the process-global atomic 60-second warning gate prevents a probing burst from becoming a
logging bottleneck while every rejection remains visible in the counters.

## Migration Notes

No schema or data migration is required. Existing stored images and accepted requests are unchanged.
The live proxy already rejects requests above its shared 4 MiB default, but S-03 does not modify that
external configuration; the application filter remains necessary for direct-Tomcat and future
HTTP/1.1 topologies. Enabling HTTP/2 or HTTP/3 requires reassessing headerless unknown-length bodies.
Rolling back the application commit removes the in-process guard without requiring data rollback or
client changes.

## References

- Related research: `context/changes/request-body-limit/research.md`
- Roadmap slice: `context/foundation/roadmap.md:181-203`
- PRD requirement: `context/foundation/prd.md` FR-005
- Prior measurements and client characterization: `context/changes/security-baseline/oq-resolution.md`
- Direct filter-layer error precedent: `src/main/java/com/kasztelanic/carcare/security/ProblemDetailAuthenticationEntryPoint.java:24-43`
- Security boundary and ignored paths: `src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:45-94`
- Vehicle JSON/image path: `src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java:53-70`,
  `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java:40-54`
- Filter unit-test precedent: `src/test/java/com/kasztelanic/carcare/security/jwt/JwtFilterTest.java:25-115`
- Full-context integration precedent: `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:21-64`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Production Filter and Registration

#### Automated

- [x] 1.1 The new filter and registration compile under the required Java 17 toolchain without unrelated dependency or configuration changes — 119991d
- [x] 1.2 The known-length admission path decides before downstream filters, body accessors, MVC binding, or service execution — 119991d
- [x] 1.3 The registration exposes `/*`, `REQUEST`, and pre-Security ordering metadata — 119991d

#### Manual

- [x] 1.4 Production diff review confirms the boundary is outside SecurityConfiguration, image handling, DTO validation, multipart settings, and proxy configuration — 119991d
- [x] 1.5 Telemetry review confirms no body, query string, authorization value, or high-cardinality metric label is emitted — 119991d

### Phase 2: Filter and Registration Test Contract

#### Automated

- [x] 2.1 Filter unit tests pass for under-limit, exact-limit, over-limit, HTTP/1.1 transfer-coded unknown-length, HTTP/2 `TE: trailers`, and unframed bodyless requests — 82e1738
- [x] 2.2 Rejection tests prove the downstream chain and both body accessors are untouched and parse the complete 413 ProblemDetail response — 82e1738
- [x] 2.3 Pass-through tests preserve input-stream and UTF-8 reader contents — 82e1738
- [x] 2.4 Registration tests prove one relevant registration bean, no standalone filter bean, one dynamic `/*` `REQUEST` registration, pre-Security order, and no duplicate path — 82e1738
- [x] 2.5 Telemetry tests prove the fixed meter contract, every-rejection counting, and deterministic process-global 60-second warning throttling under concurrency — 82e1738

#### Manual

- [x] 2.6 Boundary review confirms inclusive 4 MiB, the HTTP/1.1 transfer-coding predicate, HTTP/2 exclusion, and preserved bodyless-request semantics are explicit — 82e1738

### Phase 3: Full-Context Integration and Final Verification

#### Automated

- [x] 3.1 `RequestBodyLimitIT` passes with 413-before-401 ordering and no-controller/no-side-effect assertions — c34a370
- [x] 3.2 Existing small request, static, management, and bodyless POST regressions remain green — c34a370
- [x] 3.3 Java 17 `./mvnw verify` passes for the complete unit and integration suite — c34a370
- [x] 3.4 Final diff contains no Liquibase, client, historical deployment, live proxy, or unrelated hardening changes — c34a370

#### Manual

- [x] 3.5 Direct HTTP boundary smoke test confirms exactly-at-limit acceptance and over-limit 413 without application-side writes — c34a370
- [x] 3.6 Frozen-client behavior and successful retry are recorded accurately if client verification is performed — c34a370
- [x] 3.7 Human review confirms the external proxy was not changed and the application guard remains authoritative — c34a370
