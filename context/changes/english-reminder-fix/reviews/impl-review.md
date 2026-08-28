<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: English Reminder Fix

- **Plan**: `context/changes/english-reminder-fix/plan.md`
- **Scope**: Phases 1–2 of 2 (full plan)
- **Date**: 2026-08-28
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 3 observations
- **Commits reviewed**: `440850d` (p1 red), `7dae6f1` (p2 green), `5ecede6` (epilogue)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## What was verified independently

**Plan adherence** — all 9 planned edits present, none extra:

- `messages_en.properties:35` → `{3}`/`{4}` lead, `{0} {1} ({2})`, `{5}`
- `messages_pl.properties:9` → trailing comma added
- `messages_pl.properties:17` → `xxd` confirms `c5 bc` (`poniższy`), not a re-introduced double encoding
- `email.reset.greeting` deleted from root + en
- `reports.vehicle.main.certificate` separator normalized in root + en
- 3 new reminder tests + tightened `testCreationEmail`

**Scope guardrails** — all six "What We're NOT Doing" items intact:

- `messages_en.properties` still exists
- no bundle-parity drift test added
- `messages_pl.properties:34` untouched (diff shows only `:9` and `:17`)
- `MailService.java:124` still passes `"email.activation.title"`
- golden reminder fixtures still unread by tests
- diff touches only `i18n/*.properties` + `MailServiceIT` — zero production Java, schema, Liquibase, or config

**Automated criteria** — re-run, not taken on trust:

- `./mvnw verify` green: 38 unit (1 skipped), 193 IT (0 failures, 0 errors)
- `MailServiceIT`: 13 tests, 0 failures (was 10)
- All three bundles valid UTF-8, exactly 82 keys each
- `diff messages.properties messages_en.properties` → identical
- Zero mojibake, zero whitespace-separator entries remaining

**Mutation check** — bundles reverted to `440850d^`, `MailServiceIT` re-run:

- 5 failing assertion cases / 3 passing — exactly the Phase 1 prediction, confirming Progress item 1.2 was honestly recorded
- The `en` service failure prints the scrambled rendering verbatim: `in Toyota days i.e. Corolla … WX 12345 3 (2026-04-18)`. The tests detect *this* defect, not something incidental.
- Working tree restored to HEAD, index reset, tree clean

**Independent sweep** — placeholder/arg agreement re-derived for all 5 parameterized keys × 3 bundles against all 9 template refs. Every slot mapping agrees. The original sweep missed nothing.

**Dead-key removal is safe** — `reset.greeting` appears nowhere in `src/` or `../client`.

## Findings

### F1 — Root messages.properties has no render coverage

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/i18n/messages.properties`
- **Detail**: `MailServiceIT` resolves `en` → `messages_en` and `pl` → `messages_pl`. The root bundle is only ever reached as a final fallback, so no test renders it. The two English files are now byte-identical, so a future edit to one and not the other re-opens exactly the class of divergence this change fixed — and the new guard would not catch it, because it only reads `messages_en`. This is the residual risk of the plan's deliberate deferral of the bundle collapse, not a defect in the work.
- **Fix**: None now — correctly out of scope. Carry into the bundle-collapse follow-up ("delete `messages_en.properties` + set `spring.messages.fallback-to-system-locale: false` in one change"), where deleting `messages_en` makes the existing `en` assertions cover the root bundle for free.
- **Decision**: ACCEPTED — deferred to the bundle-collapse follow-up; rationale recorded in [follow-ups/review-fixes.md](../follow-ups/review-fixes.md). Not actionable inside this change.

### F2 — Vehicle/User fixture duplicated across three tests

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java:225,261,296`
- **Detail**: The identical 11-line block (User login/email + Vehicle builder with Toyota/Corolla/WX 12345) is copy-pasted verbatim in all three new reminder tests. The `new User()` half matches the existing class idiom; the Vehicle builder is new and repeated 3×.
- **Fix**: Extract private `reminderVehicle()` / `reminderUser()` helpers. Cosmetic — the tests are correct as written.
- **Decision**: FIXED — extracted private `reminderUser()` / `reminderVehicle()` helpers; the three reminder tests now share one fixture.

### F3 — atLeastOnce() doesn't pin a send per loop iteration

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java:234,269,304`
- **Detail**: Inside the `LANGUAGES` loop, `verify(javaMailSender, atLeastOnce())` followed by `messageCaptor.getValue()` never asserts that a *new* send happened on this iteration. `MailService.sendEmail:56` catches `Exception` and only logs, so a swallowed send would leave the captor holding the previous locale's message and the assertion would fail with a misleading diff rather than "no mail sent". Practical risk is low: `javaMailSender` is a mock and won't throw, and template rendering happens before the swallowing catch, so render errors still propagate. Also inherits the idiom already used by `testSendLocalizedEmailForAllSupportedLanguages`.
- **Fix**: Use `times(i + 1)` with an indexed loop, or reset the mock per iteration, so a missing send fails as a missing send.
- **Decision**: FIXED — the three reminder tests use an indexed loop with `verify(javaMailSender, times(i + 1))`. `testSendLocalizedEmailForAllSupportedLanguages` deliberately left on the pre-existing `atLeastOnce()` idiom (unflagged code).
