---
change_id: resolvable-build
title: "Resolvable dependency graph"
status: plan_reviewed
created: 2026-08-24
updated: 2026-08-25
roadmap_item: F-01
prd_refs: [FR-001, FR-002]
---

# Resolvable dependency graph

Foundation item **F-01** from `context/foundation/roadmap.md`. Restores Maven model
construction so the compiler runs and emits a real error list, replacing guesswork about
the migration surface with measured output.

Scope was widened during planning at the owner's direction, beyond the roadmap's pom-only
framing: `org.zalando:problem-spring-web` is removed and error handling is rewritten onto
Spring 6 `ProblemDetail`, and jjwt moves to 0.12.3 with the corresponding `TokenProvider`
rewrite. Both decisions are recorded in `plan-brief.md`.

- Plan: `plan.md`
- Brief: `plan-brief.md`
