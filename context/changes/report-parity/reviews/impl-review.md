<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Report Parity (S-03)

- **Plan**: `context/changes/report-parity/plan.md`
- **Scope**: Phases 1–4 of 4 (full plan)
- **Date**: 2026-08-28
- **Verdict**: NEEDS ATTENTION → **TRIAGED 2026-08-28** (4 of 4 fixed)
- **Findings**: 1 critical, 2 warnings, 1 observation

The rubric would read a CRITICAL Safety FAIL as REJECTED. This review does not call it that: F1 is a
pre-existing production defect that the implementation concealed rather than introduced, and every
planned deliverable landed green and verified.

## Verification run

`export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem && ./mvnw -o -B verify`

**BUILD SUCCESS** — 36 unit tests (1 skipped), 190 integration tests (1 skipped).
`ReportParityIT` contributes 12, `GoldenDatasetMirrorIT` 2, `MailServiceIT` 10,
`GoldenReferenceTest` 5, `ResourceLayeringTest` 1. `git diff --stat src/test/resources/golden/`
is empty across the change. Every automated success criterion in the plan passes.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING (see F1) |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Parity suite deletes a fixture row that hides a live 500 on `/api/events`

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality / Scope Discipline
- **Location**: `src/test/java/com/kasztelanic/carcare/golden/ReportParityIT.java:158`;
  `src/main/java/com/kasztelanic/carcare/service/impl/EventServiceImpl.java:88-97`;
  `src/main/java/com/kasztelanic/carcare/service/dto/ForthcomingEvent.java:33`
- **Detail**: `forthcomingEventsKeepTheCapturedOrdering` opens with an undocumented
  `routineServiceRepository.deleteById(ids.get("routine-service:null-next-date"))` that appears
  nowhere in the plan's Phase 4 §4 contract. Removing that line and re-running the test reproduces:

  ```
  Status expected:<200> but was:<500>
  NullPointerException: Cannot invoke "java.time.LocalDate.compareTo(...)" because "this.dateThru" is null
    at ForthcomingEvent.compareTo(ForthcomingEvent.java:33)
    at EventServiceImpl.findForthcomingEvents(EventServiceImpl.java:54)
  ```

  `findForthcomingRoutineServices` passes a routine service through when `getNextByDate() == null`,
  then builds a `ForthcomingEvent` with a null `dateThru`; the subsequent `.sorted()` dereferences
  it. Any user with one routine service lacking a next-service date gets a 500 from `/api/events`
  for every vehicle they own.

  A second latent NPE sits beside it: `mileageThru` is built from the nullable `getNextByMileage()`,
  and `compareTo` unboxes it via `mileageThru > 0`. `routine-service:null-next-mileage` would reach
  it, but is filtered out by date in this fixture.

  The bug is pre-existing (original JHipster-era code, untouched by this plan). What this plan did
  was reach the endpoint for the first time, hit the 500, and delete the row rather than record it —
  so Progress item 4.2's "recorded before §1/§2 and still green after" documents an ordering
  captured from an already-truncated fixture.
- **Fix A ⭐ Recommended**: Keep the deletion, but comment it with the NPE it sidesteps and file the
  production bug as a follow-up.
  - Strength: Matches the plan's own discipline — behaviour fixes on the event/reminder path are
    explicitly deferred (S-04 owns reminder selection, S-07 the client-visible contracts), and the
    divergence test directly above it is the in-repo precedent for "assert the defect, name the
    owner, don't fix here".
  - Tradeoff: A known 500 stays shipped until S-04 picks it up.
  - Confidence: HIGH — reproduced directly; the deferral pattern is already used twice in this file.
  - Blind spot: Haven't checked whether real production data contains null-`nextByDate` routine
    services, so severity in the field is unmeasured.
- **Fix B**: Fix the NPE now — null-guard the comparator (nulls last) and correct
  `findForthcomingRoutineServices`' filter precedence (`a == null || !b && !c` parses as
  `a == null || (!b && !c)`).
  - Strength: Removes a real 500 and lets the fixture row stay, so the ordering assertion covers the
    shape it was written for.
  - Tradeoff: Production behaviour change outside S-03's stated scope, on the exact code path S-04's
    reminder goldens will baseline against — risks pre-empting that slice's captures.
  - Confidence: MEDIUM — the fix is small, but its interaction with the uncaptured S-04 reminder
    goldens is unverified.
  - Blind spot: No golden currently covers `/api/events`, so nothing would catch a value regression
    from this edit.
- **Decision**: FIXED via Fix B. `ForthcomingEvent.compareTo` now orders null `dateThru` last and
  null-checks `mileageThru` before unboxing; `findForthcomingRoutineServices`' filter reads
  `getNextByDate() != null && …`, excluding dateless services instead of admitting them. The
  `deleteById` was removed from `ReportParityIT` — the fixture row that used to 500 the endpoint now
  stays in place, so the ordering test proves the fix rather than avoiding the shape. Blast radius
  confirmed to be `/api/events` alone: `EventService`'s only consumer is `EventResource`, and
  `ReminderService` does not reach it, so S-04's reminder goldens are unaffected.

### F2 — MailServiceIT mutates the shared template engine and never restores it

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java:67-71`
- **Detail**: `setup()` calls `templateEngine.setTemplateEngineMessageSource(testMessageSource)` on
  the autowired `SpringTemplateEngine` — a context singleton. There is no `@DirtiesContext` and no
  teardown, so the test message source stays bound for every later test class sharing the cached
  context. The mutation is genuinely needed (`testEmail.html` resolves `#{email.test.title}` through
  the engine, not through `MailService`'s injected `MessageSource`), and the leak is currently
  benign because `i18n/test-messages` layers over `i18n/messages` — it only adds a key. But the
  plan's Phase 2 §3 contract described constructing `MailService` with the layered source, not
  rebinding a shared bean, and the next person to add a test-only key inherits a silent cross-class
  override.
- **Fix**: Capture the engine's existing message source in `setup()` and restore it in an
  `@AfterEach`.
- **Decision**: FIXED. `@AfterEach restoreTemplateEngineMessageSource()` resets
  `templateEngineMessageSource` to `null`. `SpringTemplateEngine` exposes no getter for it, but
  `null` is verifiably its pristine state — Boot's `ThymeleafAutoConfiguration` never sets it, and
  nothing else in `src/main` or `src/test` calls the setter — so the engine falls back to the
  `MessageSourceAware` source Spring injected. MailServiceIT 10/10 green.

### F3 — `/api/events` ordering assertion has no length guard

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/golden/ReportParityIT.java:161-171`
- **Detail**: Plan Phase 4 §4 specified "asserting the full response sequence by
  `(eventType, dateThru)`". The test asserts `$[0]` through `$[3]` with no
  `jsonPath("$", hasSize(4))`. The four assertions do happen to cover every element today (admin
  owns `en-primary` and `zero-consumption`; the 2026-04-15..22 window yields exactly one inspection,
  two insurances, one routine service), but a regression that appends a fifth event — precisely the
  class of change §1's `LinkedHashSet` could cause — passes unnoticed.
- **Fix**: Add `.andExpect(jsonPath("$", hasSize(4)))` to close the sequence.
- **Decision**: FIXED. `hasSize(4)` added directly after `status().isOk()`.

### F4 — Delimiter-aware raw replace shipped with no test

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java:297-303`
- **Detail**: `normalizeRawHandles` moved from `String.replace` to a lookaround-guarded
  `replaceAll`. The plan's Phase 1 §1 mandated it as a correctness fix, but §3's test contract only
  covered namespace scoping — so the regex has zero coverage, and the plan itself notes nothing in
  this slice exercises the raw path. The next golden with a textual body is the first thing to
  depend on it. This is a gap in the plan, not drift from it.
- **Fix**: Add a `GoldenReferenceTest` case asserting a two-digit vehicle id is not rewritten inside
  a date or a status code.
- **Revised during triage**: writing that test showed the finding understated the problem — the
  guard does not do what the plan specified. Probing the regex directly: id `31` rewrites
  `"2026-03-31"`, id `500` rewrites `"error.http.500"`, id `2026` rewrites the year; only id `26`
  (inside `2026`) is correctly suppressed. `(?<!\d)…(?!\d)` stops a match inside a longer digit
  run, but an id flanked by non-digits still matches. The goldens use `2026-03-01`..`2026-03-31`
  and carry `error.http.500`, so those ids are live hazards. This is drift from Phase 1 §1's stated
  contract, not merely a coverage gap.
- **Decision**: FIXED as test-only, per user direction. Two `GoldenReferenceTest` cases now pin the
  real behaviour — `rawHandleReplacementGuardsAnIdEmbeddedInALongerNumber` (id 26, matches) and
  `rawHandleReplacementDoesNotGuardAnIdDelimitedByNonDigits` (id 31, does not match) — with the
  second labelled a known limitation. `normalizeRawHandles` carries a javadoc stating the real
  contract and naming field-anchoring as the fix. The plan's Phase 1 §1 claim is corrected in place
  with a dated impl-review note. The regex itself is unchanged: no golden consumes the raw path
  today.

## Verified as correct

- The `vehicle:` namespace filter returns the filtered map into both rewrite paths — `safeHandles`
  flows through `compareJson` into `normalizeJson` and `normalizeRawHandles` as the plan's Phase 1
  §1 contract required.
- `GoldenDatasetMirrorIT.goldenHandleMapIsAcceptedByGoldenReference` genuinely exercises validation:
  `validateHandleMap` runs before body parsing, so the empty-body call is not vacuous.
- `ResourceLayeringTest` walks the source trees rather than `target/`, so a stale build directory
  cannot mask a collision; the intersection is empty with no exception list, as planned.
- `test-messages_{en,pl}.properties` each carry exactly `email.test.title` with the values the
  deleted stubs used; UTF-8 encoding is set explicitly, matching Boot's autoconfigured source.
- `VehicleRichMapper`'s five `LinkedHashSet` collectors and `VehicleReport`'s five
  `thenComparing(::getId)` tiebreakers moved no captured value — all ten golden matches still pass.
- No golden file was edited.

## Triage outcome (2026-08-28)

| Finding | Decision |
|---|---|
| F1 — /api/events 500 masked by fixture deletion | FIXED via Fix B (production fix) |
| F2 — shared template engine mutated | FIXED |
| F3 — missing length guard | FIXED |
| F4 — raw-replace guard weaker than specified | FIXED test-only; plan claim corrected |

Verification after triage: `./mvnw -o -B verify` → **BUILD SUCCESS**, 38 unit tests (was 36),
190 integration tests (1 skipped). `ReportParityIT` still contributes 12, now with
`routine-service:null-next-date` present in the fixture. `git diff --stat src/test/resources/golden/`
is empty — all ten golden matches hold across the production change.
