---
date: 2026-08-30T22:49:45+02:00
researcher: Codex
git_commit: fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6
branch: refactor
repository: carcare-server
topic: "Everything the 10x-plan could need to plan correctly the request-body-limit change as per roadmap.md"
tags: [research, request-boundary, servlet, spring-security, tomcat, json, multipart]
status: complete
last_updated: 2026-08-30
last_updated_by: Codex
---

# Research: Request-body limit for roadmap slice S-03

## Research Question

What does `/10x-plan` need to plan the `request-body-limit` change correctly according to
`context/foundation/roadmap.md`, including every relevant
servlet/filter/security registration, Spring Boot/Tomcat/container setting, request wrapper or
`Content-Length` handling, and the safest insertion point for rejecting oversized bodies before
buffering? How do JSON, multipart, static-resource, actuator, and error paths differ?

## Summary

- S-03 is explicitly a standalone request-boundary slice. The roadmap says the server must refuse a
  body above a ceiling before buffering or writing it, and specifically calls for a new
  pre-buffer `Content-Length` filter rather than a property-only change
  (`context/foundation/roadmap.md:181-199`).
- There is no existing request-size filter, servlet filter registration, request wrapper,
  `Content-Length` check, or body-reading guard in application code. The only application filter
  is the JWT `GenericFilterBean`; it reads the Authorization header and delegates without reading
  the body (`src/main/java/com/kasztelanic/carcare/security/jwt/JwtFilter.java:21-37`).
- The safest global insertion point is one dedicated servlet-container filter registered once for
  `/*`, on `REQUEST` dispatches, before the Spring Security chain and therefore before the
  `DispatcherServlet`, Jackson message conversion, multipart resolution, service mapping, and
  image-file writes. Adding the check only with
  `SecurityConfiguration.filterChain()` would miss paths ignored by Spring Security.
- The vehicle image request is JSON, not multipart: `VehicleDetailsDto.image` is a base64-decoded
  `byte[]` nested in `VehicleDto`, and the mapper writes that byte array to disk after Jackson has
  materialized the request object (`VehicleDetailsDto.java:13-20`, `VehicleDetailsMapper.java:40-54`).
  This is the high-value path for the limit, but the body limit should remain independent of image
  code and apply to every request body unless the owner narrows the scope.
- No Spring Boot or embedded Tomcat setting in this repository bounds this JSON request shape. The
  prior measurements tried `server.tomcat.max-http-form-post-size`, `server.tomcat.max-swallow-size`,
  and `spring.servlet.multipart.max-request-size`; each allowed the JSON body through
  (`context/changes/security-baseline/oq-resolution.md:219-222`, `:304-308`).
- The committed NGINX `client_max_body_size 4M` is only a proxy-side guard and is in a shared `http`
  block (`src/main/docker/reverseproxy/nginx.conf:5-25`). The verified live deployment uses a
  separate private Compose/NGINX configuration with the same shared 4M default, so this change must
  not edit the repository proxy file or assume that the proxy is always in the path
  (`context/foundation/roadmap.md:193-195`; `context/changes/security-baseline/oq-resolution.md:173-181`).
- A filter rejection will not reach `ExceptionTranslator`, because that advice runs after the
  `DispatcherServlet`. The filter should write a direct `application/problem+json` 413 response
  using the same `ObjectMapper`/ProblemDetail style as the security entry point and access-denied
  handler (`src/main/java/com/kasztelanic/carcare/security/ProblemDetailAuthenticationEntryPoint.java:30-41`,
  `ProblemDetailAccessDeniedHandler.java:30-41`). Avoid relying on `sendError(413)` if the API error
  contract must be predictable; the repository also contains a generic HTML error template
  (`src/main/resources/templates/error.html:1-7`, `:146-152`).

## Detailed Findings

### Investigation method and graph evidence

The repository was indexed first with codebase-memory in full mode. The graph project was
`carcare-server` with 5,775 nodes and 15,997 edges. Graph searches and traces were
used before targeted source inspection:

- A class search for `.*Filter.*` found only
  `com.kasztelanic.carcare.security.jwt.JwtFilter` in the application code. Searches/traces for
  `OncePerRequestFilter`, `FilterRegistrationBean`, request wrappers, and body-length handling found
  no application implementation.
- The graph traces the security boundary from
  `SecurityConfiguration.filterChain` to the CORS filter and `new JwtFilter(tokenProvider)`;
  `JwtFilter.doFilter` then resolves the token and calls `filterChain.doFilter` without consuming
  the request body.
- The graph traces the JSON image path from `VehicleResource.editVehicle` through
  `VehicleServiceImpl.editVehicle`, `VehicleMapper.vehicleDtoToVehicle`, and
  `VehicleDetailsMapper.vehicleDetailsDtoToVehicleDetails` to `ImageStorageService.save`.
- Concrete source was then inspected for exact registration, configuration, route, error, and test
  line numbers. No source files under `src/main` were changed during this investigation.

### Request-entry topology

#### Application bootstrap and servlet deployment

- `CarcareApp` is the Spring Boot entry point and enables only `ApplicationProperties`; it contains
  no servlet/filter registration (`src/main/java/com/kasztelanic/carcare/CarcareApp.java:20-22`,
  `:54-59`).
- The project packages a WAR (`pom.xml:9`) and `ApplicationWebXml` only delegates an external WAR
  boot to `CarcareApp`; it does not register filters or servlets
  (`src/main/java/com/kasztelanic/carcare/ApplicationWebXml.java:11-16`).
- `WebConfigurer` implements `ServletContextInitializer` and
  `WebServerFactoryCustomizer<WebServerFactory>`, but `onStartup` only logs active profiles and
  configured status (`src/main/java/com/kasztelanic/carcare/config/WebConfigurer.java:30-43`). It
  does not call `ServletContext.addFilter`, `addServlet`, or otherwise establish a body boundary.
- `WebConfigurer.customize` only configures MIME mappings and the static document root
  (`WebConfigurer.java:48-53`, `:67-97`). A new request-boundary registration should not be hidden
  in the static-resource customization code; a dedicated configuration keeps lifecycle and order
  visible.

#### Spring Security registrations

`SecurityConfiguration` has two distinct effects that matter to placement:

1. `webSecurityCustomizer()` makes these requests bypass the Spring Security filter chain entirely:
   all `OPTIONS`, `/app/**/*.{js,html}`, `/i18n/**`, `/content/**`, and `/swagger-ui/**`
   (`src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java:45-53`).
2. `filterChain()` adds the CORS filter before `UsernamePasswordAuthenticationFilter`, adds a new
   `JwtFilter(tokenProvider)` at the same relative anchor, configures stateless security, and then
   applies route authorization (`SecurityConfiguration.java:55-94`). The management rules are
   public for health/info/prometheus and admin-only for other `/management/**`
   (`SecurityConfiguration.java:81-84`).

`WebConfigurer.corsFilter()` is a `@Bean` serving `/api/**`, `/management/**`, `/api-docs`, and
`/swagger-ui/**` (`WebConfigurer.java:99-110`). It is explicitly inserted into the Spring Security
chain by `SecurityConfiguration.filterChain()`. There is no explicit `FilterRegistrationBean` in
the repository to control a separate servlet-container mapping/order for it. A new body-limit
filter must use one registration mechanism only: do not make it both an auto-discovered filter bean
and a separately registered `FilterRegistrationBean`, and do not add it to the security chain as
well as the container unless duplicate execution is intended and tested.

`JwtFilter` is not a suitable body-limit location. Its `doFilter` implementation only checks the
Authorization header, validates a token when present, sets the security context, and delegates
(`src/main/java/com/kasztelanic/carcare/security/jwt/JwtFilter.java:27-37`). It runs too late for
ignored Spring Security paths and couples a resource-protection concern to admission control.

#### Recommended insertion point

Use a dedicated filter class and explicit servlet-container registration:

- Implement the boundary as a `OncePerRequestFilter` (or an equivalent plain servlet `Filter`) in
  a request/web cross-cutting package, not in the JWT package and not in a controller.
- Register it exactly once with a `FilterRegistrationBean`, mapped to `/*`, restricted to
  `DispatcherType.REQUEST`, and ordered before Spring Security. A dedicated configuration class is
  clearer than extending `WebConfigurer`; either way, the registration must be explicit so order
  and dispatcher scope are reviewable.
- At the first invocation, inspect `HttpServletRequest.getContentLengthLong()` only. For a known
  length above the ratified limit, set the 413 ProblemDetail response and return without obtaining
  the input stream or invoking the downstream chain. This is the only path that can reject before
  application-side body buffering.
- If unknown-length/chunked bodies are allowed, wrap the stream with a counting limit as defense in
  depth. This cannot make an unknown-length request a strictly pre-buffer rejection: bytes may be
  read before the cap is reached, and the resulting exception must have a deliberate 413 path. If
  the product requirement is a strict guarantee for all bodies, reject unknown length before the
  chain instead; this is an owner decision because it may reject legitimate chunked clients.
- Do not use `ContentCachingRequestWrapper`; it is designed to cache a request body and contradicts
  the admission-control goal.
- Do not register only through `.addFilterBefore(...)` in `SecurityConfiguration`: ignored static
  paths and `OPTIONS` bypass that chain. A container-level filter reaches those paths and still
  lets their normal zero-body requests pass.

The key ordering consequence should be pinned by tests: a container filter ordered before Spring
Security returns 413 for an oversized anonymous API request before authentication can produce 401.
That is the expected DoS/admission boundary, but it should be an explicit contract rather than an
accidental result.

### Body formats and buffering points

#### JSON endpoints

Production request bodies are Spring MVC `@RequestBody` values. Relevant routes include:

- Public/authentication JSON: `/api/register` and `/api/account/...` in
  `AccountResource.java:38-40`, `:60-62`, `:116-117`, `:143-144`, `:157-158`, `:176-177`; and
  `/api/authenticate` in `UserJwtController.java:30-38`.
- Vehicle create/update: `/api/vehicle` in `VehicleResource.java:26-27`, `:53-70`.
- Event and statistics batches: `/api/events` in `EventResource.java:17-30`, `/api/stats/**` in
  `StatisticResource.java:20-50`, and `/api/reports/costs` in `ReportResource.java:23-45`.
- Other event/lookup/user resources also use `@RequestBody`; the repository-wide source search
  found body parameters in fuel type, routine service, insurance, refuel, inspection, repair,
  user, and insurance-type resources.

The image-bearing shape is particularly important:

- `VehicleDetailsDto` has `byte[] image` and `String imageContentType`
  (`src/main/java/com/kasztelanic/carcare/service/dto/VehicleDetailsDto.java:13-20`). Jackson
  decodes the base64 JSON value into the byte array while binding the `@RequestBody`; the filter
  must run before that message-converter work.
- `VehicleMapper` delegates nested details mapping
  (`src/main/java/com/kasztelanic/carcare/service/mapper/VehicleMapper.java:35-47`).
- `VehicleDetailsMapper.vehicleDetailsDtoToVehicleDetails` calls
  `imageStorageService.save(byte[], contentType)` (`VehicleDetailsMapper.java:40-54`).
- `ImageStorageServiceImpl.save` turns the declared type into an extension, creates a UUID path,
  and calls `FileUtils.writeByteArrayToFile` (`src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:26-45`).
  The body limit belongs before the controller; it should not be implemented in this mapper or
  storage service, and it does not replace the later image-format/path hardening slices.

The limit is a wire/request-body limit, so JSON syntax, property names, base64 overhead, and other
metadata count toward it. The security-baseline measurement found a largest raw stored image of
about 108 KB and an encoded JSON body of about 144 KB; the recommended 2 MiB ceiling leaves wide
operational slack while bounding the observed 60 MB abuse case
(`context/changes/security-baseline/oq-resolution.md:204-217`, `:304-308`). The exact 2 MiB value
remains an owner unknown and must be ratified before the plan freezes it
(`context/foundation/roadmap.md:190-192`).

#### Multipart

- There are no production `@RequestPart` or `MultipartFile` endpoints and no
  `spring.servlet.multipart.*` configuration in the repository. The only `@RequestPart` is in the
  test-only `ExceptionTranslatorTestController` and is used to exercise a missing-part error
  (`src/test/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslatorTestController.java:22-27`).
- The tried multipart properties therefore do not solve the current JSON problem. If multipart is
  added later, a container filter that checks `Content-Length` before the `DispatcherServlet` is
  still the right early boundary and will run before Spring's multipart resolver. A stream cap is
  more subtle because multipart parsing may already have consumed some bytes; test it separately.
- Do not add multipart-only configuration as a substitute for the global body boundary.

#### Static resources and preflight

- `/app/**/*.{js,html}`, `/i18n/**`, `/content/**`, and `/swagger-ui/**`, plus all `OPTIONS`, are
  ignored by Spring Security (`SecurityConfiguration.java:45-53`). A filter registered only inside
  the security chain will not see them.
- The root SPA path is not ignored; it is permitted by the final `anyRequest().permitAll()` branch
  (`SecurityConfiguration.java:85-89`) and is covered by the static-root test
  (`src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:28-36`).
- A global container filter will see static requests and preflights. Normal static `GET` and
  preflight requests have no body and should pass unchanged. If a nonstandard body-bearing request
  is sent to a static path, the global policy should either reject it or explicitly scope the filter
  to API paths; this global-vs-API-only choice must be documented and tested.

#### Actuator/management

- The management base path is `/management` and the exposed set includes configprops, env, health,
  info, threaddump, and logfile (`src/main/resources/config/application.yml:9-17`). Prometheus
  export/HTTP metrics are configured separately (`application.yml:25-44`).
- Security permits health/info/prometheus and protects other management endpoints as admin-only
  (`SecurityConfiguration.java:81-84`). Existing integration tests pin health and info behavior
  (`src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:48-63`).
- Current management calls are effectively GET/read paths, so a global body filter should be
  transparent for them. Keeping it container-wide avoids an accidental bypass if a body-capable
  actuator endpoint is later enabled. If the product intentionally wants API-only limiting, make
  that a path policy rather than relying on current actuator methods.

### Configuration and runtime limits

#### Application configuration

- `ApplicationProperties` binds only `application.*` and currently exposes data-directory, CORS,
  cache, security, mail, logging, and audit settings; there is no request-size property
  (`src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:13-26`, `:61-94`).
- Shared `application.yml` contains management, Spring, session-cookie, springdoc, and CSP settings
  but no request-body or multipart limit (`src/main/resources/config/application.yml:9-17`,
  `:46-100`, `:106-118`). Dev, test, and prod profiles set ports/data/JWT/mail/runtime values but
  no request-size boundary (`src/main/resources/config/application-dev.yml:59-93`,
  `src/main/resources/config/application-test.yml:62-65`,
  `src/main/resources/config/application-prod.yml:78-110`).
- Production compression is response-only (`application-prod.yml:78-83`); it does not constrain
  request bodies.
- If the limit is operator-configurable, add a type-safe, validated property in the application
  configuration and make the effective value visible in tests. If the roadmap's “fixed ceiling” is
  literal, use one named constant after the owner ratifies the value. Do not silently introduce a
  second proxy-only value or a profile-dependent default.

#### Spring Boot/Tomcat

- The application uses Spring Boot 3.1.5 (`pom.xml:37`), `spring-boot-starter-web`
  (`pom.xml:294-300`), and embedded Tomcat through the web starter/profile dependencies
  (`pom.xml:849-888`). It also packages as a WAR, so the design should remain valid under an
  external servlet container (`pom.xml:9`, `ApplicationWebXml.java:11-16`).
- No current code sets `server.tomcat.max-http-form-post-size`, `server.tomcat.max-swallow-size`,
  `spring.servlet.multipart.max-request-size`, `spring.servlet.multipart.max-file-size`, or an
  equivalent JSON body limit. The measured findings explicitly show those candidates do not bound
  this JSON request (`context/changes/security-baseline/oq-resolution.md:219-222`, `:304-308`).
- `server.tomcat.max-http-form-post-size` targets form posts, not arbitrary JSON; Tomcat swallow size
  controls what happens after a request is rejected/connection handling, not admission; multipart
  limits apply only to multipart parsing. None is a safe implementation substitute for the filter.
- The plan must define behavior for missing/unknown `Content-Length`. The browser trial used a
  `Content-Length`, but chunked/direct-to-Tomcat behavior was not measured
  (`context/changes/security-baseline/oq-resolution.md` open measurement notes). This is the main
  technical gap in claiming a strict “before buffering” guarantee.

#### Container and proxy

- The repository Docker image exposes application port 8080
  (`src/main/docker/Dockerfile:1-17`). The historical app Compose file maps the app and proxy, but
  the file is explicitly marked not deployed (`src/main/docker/app.yml:1-14`, `:17-46`).
- The committed reverse proxy has `client_max_body_size 4M` at the `http` level
  (`src/main/docker/reverseproxy/nginx.conf:5-25`). Git history shows that setting was introduced by
  `4c34836` (“Alter max request size for reverse proxy”), so it is an old proxy guard, not evidence
  of an application-level guarantee.
- Verified production is a separate Compose project and private NGINX configuration. Its 4M limit
  is shared by CarCare and three unrelated services; the roadmap explicitly says any proxy change
  must be scoped to a CarCare `server`/`location` block
  (`context/foundation/roadmap.md:193-195`). The S-03 plan should leave that external configuration
  untouched unless the owner separately authorizes a deployment change.
- Keep the application filter even if the proxy is later aligned: it covers direct-to-Tomcat access,
  alternate ingress, tests, and future topology changes. A proxy limit can be a coarse outer guard;
  it is not the in-application guarantee.

### Error-path behavior and response contract

#### What does not catch a filter rejection

`ExceptionTranslator` is a `@ControllerAdvice` extending `ResponseEntityExceptionHandler`
(`src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:42-43`). Its
`handleExceptionInternal` normalizes ProblemDetail, adds `path`, and supplies a fallback
`message=error.http.<status>` (`ExceptionTranslator.java:59-72`). This advice is reached through the
`DispatcherServlet`; an early filter that returns without calling the chain cannot be handled there.

The class comment in the security handlers records the same split: security entry-point and access-
denied responses happen in the filter chain and have a separate direct-writing implementation
(`ProblemDetailAuthenticationEntryPoint.java:19-22`, `:30-41`; `ProblemDetailAccessDeniedHandler.java:19-22`,
`:30-41`). The body-limit response should follow this established filter-layer precedent.

#### Recommended 413 response

For a known oversized body, write directly from the filter:

- status `413 Payload Too Large`;
- content type `application/problem+json` and UTF-8;
- a safe fixed title/detail, without echoing body content;
- `message` property consistent with `error.http.413` and `path` set to
  `request.getRequestURI()`, matching the existing ProblemDetail conventions
  (`src/main/java/com/kasztelanic/carcare/web/rest/errors/SecurityProblemDetails.java:18-24`,
  `src/main/java/com/kasztelanic/carcare/web/rest/errors/ErrorConstants.java:13-24`);
- the application `ObjectMapper`, injected like the existing security handlers.

Prefer setting the response and returning over `response.sendError(413)`. `sendError` delegates
formatting to the container/error mechanism and may produce the generic HTML error resource rather
than the API's ProblemDetail contract. If a bounded stream throws after the chain has started, the
plan must define how that exception becomes the same 413 response; a generic JSON parse failure
would otherwise become 400 through the MVC advice.

The frozen client 1.2.5 treats a 413 from the vehicle update as a silent false success (modal closes
and navigation occurs, but the old image remains). This is an accepted client limitation, not a
server response reason to return 2xx; it is recorded in the roadmap and PRD
(`context/foundation/roadmap.md:200-203`, `context/foundation/prd.md:431-438`).

### Tests and verification needed by the plan

Existing conventions and harnesses:

- `JwtFilterTest` builds `MockHttpServletRequest`/`MockHttpServletResponse` and a
  `MockFilterChain`, which is a good unit-test shape for chain-not-called and header-only behavior
  (`src/test/java/com/kasztelanic/carcare/security/jwt/JwtFilterTest.java:52-115`).
- Full-context REST tests use `@SpringBootTest` plus `@AutoConfigureMockMvc`, not standalone
  controller harnesses (`src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:21-26`,
  `src/test/java/com/kasztelanic/carcare/web/rest/AbstractSessionIT.java:20-35`).
- `VehicleResourceIT` exercises JSON create/update with `MockMvc`, including the nested vehicle
  details/image DTO shape (`src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:58-88`,
  `:185-204`).
- `WebConfigurerTest` is a focused CORS/static harness and has no request-size coverage
  (`src/test/java/com/kasztelanic/carcare/config/WebConfigurerTest.java:34-46`, `:71-133`).
- `TestConfigurationIT` verifies profile layering and the shared management path
  (`src/test/java/com/kasztelanic/carcare/config/TestConfigurationIT.java:19-74`).

The implementation plan should include at least:

1. Filter unit tests for under-limit, exactly-at-limit, and over-limit `Content-Length`; assert the
   over-limit branch returns 413, writes ProblemDetail, never calls the chain, and never obtains the
   request input stream.
2. Tests for negative/unknown `Content-Length` and the chosen chunked policy. If a bounded wrapper is
   used, exercise the first read beyond the cap, exception translation, connection/error dispatch,
   and that the wrapper does not cache the entire body.
3. A full-context MockMvc test proving the filter is actually registered and ordered before security
   and MVC: an oversized JSON vehicle request must return 413 before validation/controller/service
   work, and no image file is written. Include an anonymous oversized API request to pin 413-vs-401
   order.
4. Regression checks for a normal JSON request, the authentication JSON route, static root/ignored
   resource behavior, and `/management/health`. If global scope is selected, add a body-bearing
   static/management request asserting the chosen policy; if API-only scope is selected, assert the
   intentional bypass.
5. Run the repository's normal Java 17 verification (`./mvnw verify`) after implementation. No
   Liquibase/database migration is expected for this slice.

### Constraints and non-goals

- Do not modify image mapping, image storage, format validation, or path containment in S-03;
  those are separate roadmap slices and the request-boundary slice is intentionally standalone
  (`context/foundation/roadmap.md:64-69`, `:181-188`).
- Do not rely on the frozen client to display a useful 413 error; preserve truthful HTTP semantics
  and document the accepted client behavior.
- Do not edit the shared live NGINX 4M setting as part of the application change. If proxy alignment
  is later requested, it needs a separate scoped deployment change and direct-to-Tomcat verification.
- Do not claim a strict all-body pre-buffer guarantee until the unknown-length/chunked policy is
  decided and tested.
- Do not use the historical `src/main/docker` deployment files as proof of the live topology; they
  are marked `HISTORICAL — NOT DEPLOYED` (`src/main/docker/app.yml:1-14`, plus the corresponding
  historical headers in `mariadb.yml`, `env-template`, and `deploy.sh`).

## Code References

The current source commit is pushed on `refactor`; the following references point to that
immutable commit:

- [`CarcareApp.java:19-92`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/CarcareApp.java#L19-L92) — Spring Boot entry point.
- [`ApplicationWebXml.java:11-16`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/ApplicationWebXml.java#L11-L16) — WAR bootstrap; no filter registration.
- [`WebConfigurer.java:30-112`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/config/WebConfigurer.java#L30-L112) — servlet customizer and CORS bean.
- [`SecurityConfiguration.java:45-94`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/config/SecurityConfiguration.java#L45-L94) — ignored paths and security filter chain.
- [`VehicleResource.java:53-70`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java#L53-L70) — image-bearing JSON create/update routes.
- [`VehicleDetailsDto.java:13-20`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/service/dto/VehicleDetailsDto.java#L13-L20) — nested `byte[] image` request field.
- [`VehicleDetailsMapper.java:40-54`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java#L40-L54) — mapping and storage call after body binding.
- [`ImageStorageServiceImpl.java:26-45`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java#L26-L45) — filesystem write.
- [`ExceptionTranslator.java:42-72`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java#L42-L72) — MVC-only ProblemDetail normalization.
- [`ProblemDetailAuthenticationEntryPoint.java:24-43`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/security/ProblemDetailAuthenticationEntryPoint.java#L24-L43) — direct filter-layer JSON response precedent.
- [`nginx.conf:5-25`](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/docker/reverseproxy/nginx.conf#L5-L25) — historical repository proxy and its 4 MiB `http`-scope limit.
- [`RequestBody` route inventory](https://github.com/kacperkasztelanic/carcare-server/blob/fd47cfafea4de73c3c9a70bfac6fd4838e6d92b6/src/main/java/com/kasztelanic/carcare/web/rest) — all production REST controllers; graph search found 28 body-bearing routes.

## Architecture Insights

- The correct boundary is the servlet container, not image storage, DTO validation, or a
  Spring Security-only filter. A `FilterRegistrationBean` mapped once to `/*`, limited to
  `REQUEST`, and explicitly ordered before Spring Security keeps admission control independent
  of authorization and of the ignored-path list.
- The current application has two response layers: filter-chain writers serialize directly, while
  `ExceptionTranslator` normalizes exceptions after dispatch. A 413 emitted before dispatch must
  choose and test its own response contract; `@ControllerAdvice` cannot be relied upon.
- The security property is a raw wire-body ceiling. Base64 expansion, JSON envelope, and multipart
  framing (if introduced later) count toward it. It is not a decoded-image or file-size setting.
- There are two possible defenses at runtime: a private proxy can reject known oversized bodies
  first, while the application filter remains authoritative for direct-Tomcat and future ingress
  paths. They must not be treated as one configuration source.
- This slice has no domain or schema impact. It must preserve all existing small JSON requests,
  response statuses/headers, authentication/authorization rules, and the image write path for
  accepted requests.

## Historical Context (from prior changes)

- `6272eb7` — shaping changed the requirement from an image-size limit to a request-body limit
  because images are base64 inside JSON.
- `d915934` — introduced PRD FR-005: reject an oversized body before memory buffering or volume
  writes while preserving the frozen client contract.
- `b79112d` — recorded measurements showing that the obvious Spring/Tomcat and multipart
  properties do not constrain JSON bodies; the bare app accepted an 83,886,457-byte request.
- `56427f0` — introduced S-03 as an independent, ready roadmap slice.
- `8c9e84b` — corrected the proxy assumptions: the live NGINX is outside this repository and its
  4 MiB default is shared at `http` scope.
- `4c34836` — added the repository’s historical 4 MiB NGINX setting in 2019; no application-level
  limit was added with it.
- `context/archive/resolvable-build/error-contract.md` — documents the project’s deliberate split
  between MVC advice and direct filter-chain ProblemDetail writers, including UTF-8 handling.

## Related Research

- [`context/foundation/roadmap.md`](../../foundation/roadmap.md) — canonical S-03 outcome, risks,
  unknowns, prerequisites, and non-goals.
- [`context/foundation/prd.md`](../../foundation/prd.md) — FR-005, compatibility constraints,
  and the accepted client 1.2.5 false-success behavior.
- [`context/changes/security-baseline/oq-resolution.md`](../security-baseline/oq-resolution.md) —
  production-volume inventory, property probes, proxy trial, and browser characterization.
- [`context/archive/resolvable-build/error-contract.md`](../../archive/resolvable-build/error-contract.md) —
  prior response-contract decisions relevant to a pre-dispatch 413.

## Open Questions

1. Ratify the ceiling: recommended 2 MiB (2,097,152 bytes), or another owner-selected value
   (`context/foundation/roadmap.md:190-192`).
2. Select policy for missing/unknown `Content-Length`: reject before the chain for a strict
   guarantee, or allow and enforce a counting stream cap with a defined late-413 path.
3. Confirm global `/*` scope versus API-only scope. Global is safest for the FR-005 wording and
   covers future body-capable endpoints; API-only avoids surprising non-API callers but must not be
   mistaken for a universal request boundary.
4. Confirm whether the limit is a fixed code-level constant or an operator-configurable application
   property. If configurable, define the property name, unit/type, default, validation, and which
   profiles inherit/override it.
5. Keep proxy alignment out of S-03 unless separately authorized; if authorized, scope it to
   CarCare's proxy block and decide whether the proxy value should be equal to or greater than the
   application value.
