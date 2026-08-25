---
change_id: test-context-restored
title: Test context loads and the suite executes
status: plan_reviewed
created: 2026-08-25
updated: 2026-08-25
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

Roadmap entry **F-04** (`context/foundation/roadmap.md:245-284`). Prerequisite F-03
(`jakarta-platform-migration`) delivered 2026-08-25 and archived. Parallel with F-02
(`golden-baseline-capture`). Unlocks S-01, S-02, S-03, S-04 — the entire slice layer.

Research (2026-08-25, `research.md`) was carried out with a live probe rather than by
inspection: the application context was booted repeatedly out of `target/classes` against
H2, which is possible because `src/main` compiles green while `src/test` does not. The
context now loads, and the whole blocker chain is known rather than predicted.

Headline results:

1. **The context-load blockers are three config lines, not the compile errors.** Zero test
   sources had to change to get a context up (598 beans). The 39 javac errors block
   `test-compile`; they are a separate, parallel workstream.
2. **Two of the three blockers are previously-unrecorded F-03 regressions**, both from the
   same cause: `src/test/.../application.yml` *shadows* main's rather than layering on it,
   and F-03 moved two things that used to live in Java into main's YAML only —
   the Liquibase changelog path (was hardcoded at `LiquibaseConfiguration.java:43`, file
   deleted in `4542b32`) and the CSP string (`ApplicationProperties.java:65` has no default).
3. **The CLOB unknown is solved.** Not an H2-version problem: `columnDefinition = "clob"`
   sets only the DDL type *name*, while Hibernate 6 validates JDBC type *codes*, and
   `length = 65535` under `H2Dialect.getMaxVarcharLength() = 1048576` resolves to VARCHAR.
   All five columns clear at once. Two side facts worth keeping: `FixedH2Dialect`'s entire
   body was `registerColumnType(Types.FLOAT, "real")` — it never touched CLOB, so restoring
   it was never going to help — and `changelog/20190102222057_changelog.xml`, which holds the
   `modifyDataType → clob` changesets, is orphaned and has never run.
4. **F-03's `.anyRequest().permitAll()` fix is now runtime-confirmed** — `GET /` returns 200
   over a real socket through the real filter chain, with all four security headers present.
   The carried-in review item is discharged.
5. **The suite has zero coverage of this application.** 14 of 19 controllers are untested;
   no test file mentions a vehicle, any of the five event types, reports, statistics, or
   reminders. Restoring it restores JHipster scaffolding only — that reframes what "done"
   should mean for F-04 and is a decision for the framing/planning step.

Planning decisions (2026-08-25):

- Replace test-resource shadowing with a resource-activated `test` profile.
- Resolve the five H2 CLOB mappings with a test-only `TestH2Dialect`; do not change production
  entities, Liquibase, or strict schema validation.
- Convert all five REST ITs identified by research to application `MockMvc`; keep
  `WebConfigurerTest` as a focused standalone unit test.
- Add representative anonymous, USER, and ADMIN authorization coverage plus a targeted runtime
  security smoke matrix.
- Require a migration-scoped green `./mvnw verify`; defer CarCare business coverage to S-01–S-04.
