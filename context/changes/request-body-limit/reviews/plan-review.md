<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Request Body Limit Implementation Plan

- **Plan**: `context/changes/request-body-limit/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-30
- **Verdict**: SOUND
- **Findings**: 0 critical, 2 warnings, 1 observation
- **Triage**: 3 fixed, 0 skipped, 0 accepted, 0 dismissed

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS |

## Grounding

Grounding: 10/10 paths ✓ (5 planned additions correctly absent), 7/7 symbols ✓, brief↔plan ✓. Progress after triage: 3/3 phases and 18/18 criteria mapped ✓.

## Findings

### F1 — Unknown-length guarantee exceeds the Servlet API

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Desired End State; Implementation Approach; Critical Details
- **Detail**: The plan promises rejection of body-bearing requests whose length cannot be determined while preserving bodyless requests. Servlet API reports both cases as `Content-Length = -1`. HTTP/1.1 chunked requests can be recognized through `Transfer-Encoding`, but an HTTP/2 DATA body without `Content-Length` has no legal chunked header and cannot be distinguished from a bodyless request without reading it. Rejecting any `Transfer-Encoding` value would also be unsafe because HTTP/2 permits `TE: trailers`. The proposed behavior works for the current direct HTTP/1.1 Tomcat topology, but not for the protocol-independent guarantee stated in the plan.
- **Fix A ⭐ Recommended**: Narrow the guarantee to explicitly framed HTTP/1.1 unknown-length requests and define the exact header predicate.
  - Strength: Preserves O(1), no-body-read admission and the current deployment scope.
  - Tradeoff: Headerless HTTP/2 bodies are explicitly outside the guarantee.
  - Confidence: HIGH — this follows the standard Servlet information boundary.
  - Blind spot: The policy must be revisited if HTTP/2 or HTTP/3 is enabled later.
- **Fix B**: Buffer or count unknown-length streams up to 4 MiB plus one byte.
  - Strength: Enforces the byte ceiling regardless of framing headers.
  - Tradeoff: Loses the strict pre-read guarantee and complicates response ordering, request wrapping, and memory/resource behavior.
  - Confidence: HIGH — technically feasible but materially changes the design.
  - Blind spot: Security may respond before an unknown body is consumed.
- **Decision**: FIXED via Fix A

### F2 — Telemetry contract leaves implementation decisions unresolved

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — Request admission filter
- **Detail**: The plan requires a rate-limited warning but does not define the interval, global-versus-per-reason behavior, first-event behavior, concurrency mechanism, clock/test seam, or metric name and tag key. `SecurityMetersService` establishes a fixed-counter Micrometer pattern, but the codebase has no rate-limited logging precedent.
- **Fix**: Specify the metric name, description, base unit, tag key, and a deterministic limiter contract—for example, one global warning per 60 seconds using monotonic/atomic state, with the first rejection logged and every rejection counted.
  - Strength: Removes concurrency and observability guesswork and makes the behavior testable.
  - Tradeoff: Adds a small explicit policy decision to the plan.
  - Confidence: HIGH — existing Micrometer conventions are directly reusable.
  - Blind spot: The desired warning interval still requires owner selection.
- **Decision**: FIXED

### F3 — ServletContext call alone cannot prove no duplicate filter

- **Severity**: 🔎 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 — Registration tests
- **Detail**: Calling one `FilterRegistrationBean` against `MockServletContext` proves that registration's metadata, but not that another raw `Filter` bean will not be auto-adapted. Boot 3.1.5 deduplicates wrapped filter beans, but the proposed test must inspect the application context to prove the plan's "exactly once" assertion.
- **Fix**: Add assertions for exactly one `FilterRegistrationBean` and no independently registered `RequestBodyLimitFilter` bean, or construct the filter only inside the registration bean.
- **Decision**: FIXED
