# Request Body Limit — Plan Brief

> Full plan: `context/changes/request-body-limit/plan.md`
> Research: `context/changes/request-body-limit/research.md`

## What & Why

Roadmap slice S-03 adds a server-side request-body ceiling so oversized payloads are refused before Spring Security, MVC/Jackson binding, service code, or image storage can buffer or persist them. The owner-selected ceiling is 4 MiB, measured against raw HTTP request bytes rather than decoded image size.

## Starting Point

CarCare has no application-level body limit. Vehicle images arrive as base64 JSON and are decoded before disk storage; existing Tomcat, multipart, and proxy settings do not provide the required application guarantee. The live proxy is external and its 4 MiB default is shared with unrelated services.

## Desired End State

Requests at or below 4 MiB continue normally. Requests above 4 MiB, and HTTP/1.1 requests with unknown length and an explicit `Transfer-Encoding` header, receive direct 413 ProblemDetail before authentication or controller work. Bodyless static, preflight, management, and path-only requests remain unaffected. Headerless HTTP/2 and HTTP/3 bodies are outside the portable Servlet-level pre-read guarantee and require reassessment if those protocols are enabled.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Ceiling | Fixed 4 MiB / 4,194,304 bytes | Matches the owner's capacity choice and current proxy ceiling while retaining an application guard. | Plan |
| Boundary | Accept `≤ 4 MiB`; reject `> 4 MiB` | Makes the limit intuitive and gives a precise off-by-one test. | Plan |
| Unknown length | Reject HTTP/1.1 requests where `getContentLengthLong() == -1` and `Transfer-Encoding` is present | Preserves pre-buffering without claiming that the Servlet API can distinguish headerless HTTP/2 or HTTP/3 bodies from bodyless requests. | Plan |
| Scope | Global `/*`, `REQUEST` dispatches | Covers ignored Security paths and future body-bearing endpoints. | Plan |
| Order | Explicitly before Spring Security | Ensures oversized anonymous requests return 413 rather than 401. | Research / Plan |
| Error | Direct UTF-8 `application/problem+json` 413 | Matches filter-layer conventions because MVC advice cannot run yet. | Research / Plan |
| Proxy | No proxy change | The live proxy is private/shared and outside this repository's S-03 scope. | Roadmap / Plan |
| Telemetry | Fixed rejection counters plus one process-global warning per 60 seconds | Counts every rejection while bounding safe warning output under concurrency. | Plan |

## Scope

**In scope:**

- One request-body filter and explicit servlet registration.
- Fixed 4 MiB wire-size check, explicit HTTP/1.1 transfer-coding rejection, direct 413 response, and safe telemetry.
- Raw filter tests, registration tests, full-context integration tests, and complete Maven verification.

**Out of scope:**

- Client changes, image hardening, database migrations, security-rule changes, and multipart configuration.
- Historical deployment files and live/private NGINX configuration.

## Architecture / Approach

```text
HTTP request → RequestBodyLimitFilter ── reject 413 (no stream read) ──→ response
                            ↓ accepted
              Spring Security → DispatcherServlet → Jackson → controller/service/storage
```

The filter is constructed only inside one `FilterRegistrationBean` on `/*`, `DispatcherType.REQUEST`, ordered before Spring Security. Known `Content-Length` is checked in constant time; bodyless requests without framing pass, while HTTP/1.1 requests with unknown length and a `Transfer-Encoding` header are rejected.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Production filter and registration | 4 MiB boundary, direct 413 contract, telemetry, and pre-Security registration. | Duplicate or late registration could leave paths unprotected. |
| 2. Filter and registration test contract | Raw stream-read proof, boundary/error tests, and lifecycle metadata tests. | MockMvc alone cannot prove the body was never opened. |
| 3. Full-context integration and final verification | Application-order proof, no-side-effect regressions, and full suite. | Frozen client misrenders 413; proxy behavior remains external. |

**Prerequisites:** Completed research, Java 17, and the existing Spring Boot test harness. No schema, client, or deployment prerequisite is required.

**Estimated effort:** Three implementation phases across roughly 2–3 focused sessions; production code is small, but servlet boundary semantics require careful testing.

## Open Risks & Assumptions

- The 4 MiB limit counts JSON and base64 overhead as wire bytes.
- An HTTP/1.1 transfer-coded unknown-length body is rejected; ordinary no-body requests without framing are preserved. Headerless HTTP/2 and HTTP/3 bodies require a separate policy if enabled later.
- Client 1.2.5 may navigate as if an oversized update succeeded even though the server returned 413; the previous image remains and retry should work.
- The live proxy remains a separate shared 4 MiB defense and is not modified by S-03.

## Success Criteria (Summary)

- Oversized requests receive truthful HTTP 413 before authentication, body binding, or domain/file side effects.
- At-limit and normal requests retain existing behavior, including bodyless routes and security responses.
- Raw servlet, registration, full-context integration, and complete Java 17 verification all pass.
