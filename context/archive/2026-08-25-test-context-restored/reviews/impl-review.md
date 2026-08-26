<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Test Context Restored

- **Plan**: `context/changes/test-context-restored/plan.md`
- **Scope**: Phases 1–4 (all)
- **Date**: 2026-08-26
- **Verdict**: NEEDS ATTENTION → RESOLVED (all 6 findings triaged 2026-08-26; no code changes required)
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Verification performed

Reproduced independently during this review:

- `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify` → **BUILD SUCCESS**, 115 integration
  tests, 0 failures, 0 errors.
- Jakarta import guard: only `TokenProviderTest.java:18:import javax.crypto.SecretKey;` remains.
- Standalone-MockMvc guard: no `standaloneSetup`/`MockMvcBuilders` in any of the five converted REST ITs.
- Layered-resource guard: `target/test-classes/config/` contains only `application.properties` and
  `application-test.yml`.
- `git diff --check 5d2bf0d^..HEAD` → clean.

All four phases' automated success criteria reproduce. Every warning below concerns changes made
outside the declared scope and how the close-out record describes them — not broken code.

## Findings

### F1 — Production sources changed under a test-only phase contract

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: src/main/java/com/kasztelanic/carcare/domain/PersistentAuditEvent.java:30, pom.xml:28
- **Detail**: Phase 4's contract restricts repairs to "Only test sources or test resources implicated by
  an observed failing test", and adds: "If a failure … requires production domain/API changes, stop and
  route it to the owning S-01–S-04 slice instead of masking it." Three production/build files changed
  anyway: `PersistentAuditEvent` gained `@EqualsAndHashCode(of = "id")` (23802d8, Phase 2) so the
  untouched `testPersistentAuditEventEquals` assertions would pass — a JPA entity semantics change, since
  transient instances with null ids now compare equal; `pom.xml` gained `-Duser.timezone=UTC` on
  `argLine` (5d2bf0d, Phase 1); and `CacheConfiguration.createCache()` (1a54b2f, Phase 4), which *is*
  disclosed and reasoned about. Mitigating evidence verified during review: every other entity in
  `domain/` already carries `@EqualsAndHashCode(of="id")` (Vehicle, Repair, User, …), so the change
  aligns `PersistentAuditEvent` with the house convention AGENTS.md documents, and `PersistentAuditEvent`
  is never held in a Set or Map anywhere in `src/main` — runtime risk is negligible.
- **Fix A ⭐ Recommended**: Keep the change; record it explicitly.
  - Strength: The edit is convention-conforming and provably inert at runtime; reverting it would force
    deleting or rewriting a scaffolding test for no behavioral gain.
  - Tradeoff: The plan's stated boundary was crossed without an in-flight decision, so the precedent
    stands unless the record names it.
  - Confidence: HIGH — verified all 12 domain entities and all `PersistentAuditEvent` usages in `src/main`.
  - Blind spot: None significant.
- **Fix B**: Revert the entity and adjust the test instead.
  - Strength: Restores the stated scope boundary literally.
  - Tradeoff: Leaves `PersistentAuditEvent` as the sole entity without the house equals contract, and
    requires weakening a test that currently asserts correct behavior.
  - Confidence: MEDIUM — the test would need real rewriting, not a one-line expectation flip.
  - Blind spot: Haven't checked whether S-01–S-04 would want this entity's equals contract anyway.
- **Decision**: FIXED via Fix A — entity change kept deliberately; now enumerated in change.md, AGENTS.md, and the plan's Progress re-affirmation.

### F2 — Close-out record understates what changed

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/test-context-restored/change.md:64, AGENTS.md:179
- **Detail**: `change.md:64` states "One migration-scoped repair beyond Phases 1–3:
  `CacheConfiguration.createCache()`". `AGENTS.md:179` states "no production entity or Liquibase changelog
  changed." Both are contradicted by the diff. Beyond `CacheConfiguration`, the change also carries: the
  `PersistentAuditEvent` entity annotation (F1), the `pom.xml` argLine pin (F5), an unplanned test
  component `TestUserIdentitySequenceFixup` (F4), an inverted assertion in the shared
  `TestUtil.equalsVerifier` helper (F6), and two Hibernate properties beyond Phase 1's contract (F3).
  Progress items 4.5 ("every additional repair is migration-scoped and evidence-backed"), 4.6 ("no
  production entity … was changed") and 4.7 ("AGENTS.md … match the delivered suite") are all marked `[x]`
  against this record; 4.6 is false as written. This matters more than usual here: AGENTS.md is this
  repo's stated defense against future agents reasoning from stale narrative, and it now asserts something
  the tree contradicts.
- **Fix**: Correct `change.md`'s Phase 4 close-out and `AGENTS.md:179` to list all production/build-file
  changes (`PersistentAuditEvent`, `pom.xml` argLine, `CacheConfiguration`) and the unplanned test
  fixture, then re-affirm 4.5–4.7 against the corrected text.
- **Decision**: FIXED — change.md Phase 4 close-out and AGENTS.md rewritten to list all three production/build changes; Progress 4.5–4.7 re-affirmed, 4.6 explicitly corrected.

### F3 — Two Hibernate properties beyond the Phase 1 contract

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/resources/config/application-test.yml:30,34
- **Detail**: Phase 1 enumerated exactly what `application-test.yml` should retain (H2, Liquibase test
  context, strict validation, JWT, task pool, mail). Two Hibernate properties were added beyond it:
  `hibernate.timezone.default_storage: NORMALIZE` and `hibernate.auto_quote_keyword: true` — both set in
  test only, neither present in main's config, neither mentioned in the change record. Blast radius
  verified as small: no production entity uses `ZonedDateTime`/`OffsetDateTime` (all use `Instant`), so
  `default_storage` affects only the test-only `DateTimeWrapper`; and `auto_quote_keyword` covers `value`,
  which H2 2.x reserves but MariaDB does not. Both are well-commented in the file itself.
- **Fix**: Mention both in the change record alongside the `TestH2Dialect` rationale, so the test/main
  config asymmetry is deliberate on the record rather than only in a YAML comment.
- **Decision**: FIXED — the two Hibernate properties and the test/main asymmetry are now recorded in change.md and AGENTS.md.

### F4 — Unplanned TestUserIdentitySequenceFixup component

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: src/test/java/com/kasztelanic/carcare/config/TestUserIdentitySequenceFixup.java
- **Detail**: A `@Profile("test")` `ApplicationRunner` issuing `ALTER TABLE jhi_user ALTER COLUMN id
  RESTART WITH …` landed in Phase 2 (23802d8). It is test-only, correctly scoped, and well-documented in
  its own javadoc (H2 2.x no longer auto-advances identity past Liquibase-seeded ids) — but it is a real
  behavioral fixture that appears in neither the plan nor the close-out record.
- **Fix**: Add it to the change record's list of delivered test fixtures, next to `TestH2Dialect`.
- **Decision**: FIXED — TestUserIdentitySequenceFixup recorded as a delivered test fixture in change.md and AGENTS.md.

### F5 — UTC pin removes what HibernateTimeZoneIT was there to prove

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: pom.xml:28
- **Detail**: `-Duser.timezone=UTC` pins the forked test JVM. The pom comment is honest about why
  (Hibernate 6's H2 binding uses `ZoneId.systemDefault()` rather than `hibernate.jdbc.time_zone`). But this
  host is Europe/Warsaw, so the pin is load-bearing: `HibernateTimeZoneIT` — the one test whose entire
  purpose is proving zone-independent storage — now only ever runs in the one timezone where the question
  is trivial. Determinism is a defensible trade, and the plan's own scope forbids chasing this into
  production mappings.
- **Fix**: Note the limitation in the change record's "what this suite does not prove" paragraph.
- **Decision**: FIXED — the UTC pin and its coverage cost for HibernateTimeZoneIT are now recorded in change.md and AGENTS.md.

### F6 — equalsVerifier assertion inverted, weakening the shared guard

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/kasztelanic/carcare/web/rest/TestUtil.java:130
- **Detail**: The shared helper flipped from asserting two fresh instances are NOT equal to asserting they
  ARE equal. Under this codebase's `@EqualsAndHashCode(of="id")` convention that is factually correct and
  the new comment says so — but the helper no longer detects the JPA pitfall it was originally written to
  catch. Only two callers exist (`User`, `PersistentAuditEvent`), so exposure is contained.
- **Fix**: Leave as-is; the comment already documents the reasoning. Flag only if S-01–S-04 adds entities
  where transient-instance identity matters.
- **Decision**: FIXED — equalsVerifier's inverted assertion and its narrowed guard recorded; code left as-is per the fix.

## Summary

The engineering is sound — green suite, real filter chain, real `@PreAuthorize` enforcement, no security,
performance, reliability, or data-safety defects found. The gap is that four small out-of-scope changes
accumulated across phases and the Phase 4 record was written as if only one had.
