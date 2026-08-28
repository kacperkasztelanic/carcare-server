# English Reminder Fix — Implementation Plan

## Overview

Correct one user-visible defect in the English service-reminder e-mail — placeholder indices in
`email.service.text1` that disagree with the argument vector its Thymeleaf template passes — plus
four latent defects found in the accompanying sweep of all three i18n bundles. Guard the fix with
the first render-level test coverage the mail pipeline has ever had.

The bug is not an edge case: `Constants.DEFAULT_LANGUAGE` is `"en"`
(`src/main/java/com/kasztelanic/carcare/config/Constants.java:17`) and `UserService` assigns it to
every self-registered account, so the broken string is what the default population receives.

## Current State Analysis

**The defect.** `src/main/resources/i18n/messages_en.properties:35` numbers its `MessageFormat`
placeholders `{0}…{5}` in ascending order, but `serviceReminderEmail.html:10` passes
`(make, model, licensePlate, diff, nextByDate, details)`. An English recipient therefore reads:

> Please bear in mind that in **Toyota** days i.e. **Corolla** the following services should be
> carried out for your vehicle **3** **2026-04-18** (**WX 12345**): Oil change.

The root bundle `messages.properties:35` already carries the correct ordering, and the two files
are byte-identical on all 83 keys *except* this one. `messages_pl.properties:34` is also correct —
its ascending order is right for Polish word order, which is precisely why a cross-locale
"placeholder order must match" lint is not a sound invariant here.

**Why nothing caught it.** `MailServiceIT` is stock JHipster scaffolding: ten tests, all rendering
`mail/testEmail` (a two-token stub), with the four that touch real templates asserting only
`isNotEmpty()`. The three typed reminder methods (`MailService.java:85-113`) have zero coverage.
The `golden-baseline-capture` reminder fixtures do not close the gap either — per
`context/changes/golden-baseline-capture/reference.md:202-211` the capture runner intercepted a
synchronous `MailService` subclass *at* the typed send methods, deliberately above template
rendering; both `typed-seam.json` and `full-path.json` hold selection metadata only, with zero
rendered message text, and neither is read by any test today.

**The other four sweep findings.** A double-encoded Polish word (`poniÅ¼szy` → `poniższy`) at
`messages_pl.properties:17`; a dead `email.reset.greeting` present in root/en `:21` but absent from
`pl`; an `email.greeting` trailing-comma drift between `pl` and `en`/root; and a whitespace key/value
separator on `reports.vehicle.main.certificate` at root/en `:47` that **is not a bug** —
`PropertyResourceBundle` accepts unescaped whitespace as a separator, so it already resolves
correctly, and normalizing it to `=` is cosmetic only.

## Desired End State

Every reminder e-mail renders its placeholders in the correct slots in both supported languages,
the Polish bundle is free of encoding corruption, and `MailServiceIT` asserts the rendered body of
all three reminder templates in both locales so a future re-divergence fails the build instead of
reaching a user's inbox.

Verified by: `./mvnw verify` green with the new assertions in place, and a re-run of the sweep
script reporting no placeholder-order differences, no key-presence gaps, no mojibake, and no
whitespace-separator entries.

### Key Discoveries

- **`MailServiceIT` can reach the real bundle.** `setup()` (`MailServiceIT.java:64-73`) installs a
  `ResourceBundleMessageSource` with basenames `("i18n/test-messages", "i18n/messages")` and
  `UTF_8` default encoding. `test-messages_{en,pl}.properties` define only `email.test.title`, so
  every reminder key falls through to the **real** `messages_*.properties`. The test also builds
  `new MailService(...)` directly rather than injecting the bean, which bypasses the `@Async` proxy
  and makes the typed reminder methods run synchronously.
- **Fixtures are cheap.** `Vehicle` (`Vehicle.java:87-98`) and `RoutineService`
  (`RoutineService.java:97-108`) expose Lombok `@Builder` over private constructors with no
  build-time validation, so a minimal builder with `fuelType` / `vehicleDetails` / `owner` left null
  works. `Insurance` and `Inspection` need only `validThru`.
- **Rendering is deterministic.** `LocalDate` is neither a `Number` nor a `java.util.Date`, so
  `MessageFormat` falls through to `toString()` → a locale-independent `2026-04-18`. Thymeleaf's
  `th:text` escapes at markup-significant level only, so Polish diacritics pass through unescaped —
  which is what makes the mojibake fix assertable.
- **The `pl` reminder assertions guard the greeting fix, not the mojibake.** All three reminder
  templates greet via `#{email.greeting(${user.login})}` at line 9, so they cover
  `messages_pl.properties:9`. The mojibake lives in `email.creation.text1`, referenced only by
  `creationEmail.html:12` — a separate assertion is required for it. (This corrects
  research.md decision 3, which claimed the reminder coverage locked in the mojibake fix.)
- **Line-number correction:** `email.reset.greeting` is at line **21** of both `messages.properties`
  and `messages_en.properties`, not line 20 as recorded in research.md.

## What We're NOT Doing

- **Not deleting `messages_en.properties`.** Collapsing the duplicate English bundle is the
  structural fix, but it is unsafe as a standalone edit:
  `spring.messages.fallback-to-system-locale` is unset, so Boot's default `true` applies and the
  `ResourceBundle` candidate chain is `requested → JVM default → base`. Deleting the file would make
  an explicitly-requested `en` resolve `messages_en` (absent) → **`messages_pl`** → base, so on any
  JVM whose default locale is Polish, English users would receive Polish mail. If ever revisited,
  the deletion and `fallback-to-system-locale: false` must land in the **same** change.
- **Not adding a bundle-parity drift guard.** No test asserting root/en value equality or
  cross-bundle placeholder-set equality. The render assertions cover the strings that actually reach
  users, which is the failure mode that mattered.
- **Not touching `messages_pl.properties:34`.** Its ascending `{0}…{5}` order is correct for Polish.
- **Not fixing `sendCreationEmail`'s subject key.** `MailService.java:124` passes
  `"email.activation.title"`, so account-creation mails are subject-lined "CarCare account
  activation". Pre-existing, unrelated to bundle content, recorded so it is not later rediscovered
  as a regression from this change.
- **Not building the reminder parity slice.** The golden fixtures stay unread by tests.
- **No production Java, schema, Liquibase, or configuration changes.**

## Implementation Approach

Test-first, in two phases. Phase 1 adds render assertions and **demonstrates them failing** on the
live bugs; Phase 2 applies the bundle edits and turns them green.

The red step is the point of the exercise. This bug survived from `e6a553f` to HEAD precisely
because nothing rendered a reminder template, so a test that merely passes alongside the fix proves
nothing — a subtly wrong assertion (one that encoded the buggy substring order, say) would pass
just as happily. Observing the predicted failure set, and only that set, is what establishes the
test actually detects this class of defect.

## Critical Implementation Details

**Number formatting on `diff`.** `{3}` is a plain `int`, so `MessageFormat` applies
`NumberFormat.getInstance(locale)`. A day count of `1000` renders as `1,000` under `en` and
`1 000` under `pl`. Keep fixture values under four digits — the plan uses `3`.

**The mojibake fix must be written as real bytes.** `messages_pl.properties:17` currently stores
`c3 85 c2 bc` where `c5 bc` (`ż`) belongs. Write `poniższy` as genuine UTF-8 and verify with
`hexdump`/`xxd` rather than trusting the editor's rendering — re-introducing the double encoding is
invisible in most terminals.

**Do not disturb `MailServiceIT`'s `@AfterEach`.** `restoreTemplateEngineMessageSource()`
(`MailServiceIT.java:75-82`) resets `templateEngineMessageSource` to `null` because the
`SpringTemplateEngine` is a context singleton shared with every other test class in the cached
context. New tests inherit this and must not add their own teardown.

**Toolchain.** Java 17 exactly — `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` before
any `./mvnw` invocation, or the enforcer fails before compilation.

---

## Phase 1: Red — render coverage that fails on the live bugs

### Overview

Add render assertions for the three reminder templates in both locales, plus a Polish assertion on
the creation mail, and record that they fail exactly where the bugs are.

### Changes Required:

#### 1. Reminder render tests

**File**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`

**Intent**: Give the three typed reminder methods their first coverage, asserting the rendered
message body rather than `isNotEmpty()`, so the placeholder contract and the greeting punctuation
are both pinned in `en` and `pl`.

**Contract**: Three new test methods — `testSendRoutineServiceReminderEmail`,
`testSendInsuranceReminderEmail`, `testSendInspectionReminderEmail` — each looping over the existing
`LANGUAGES` array (`{"en", "pl"}`), following the `verify(javaMailSender, atLeastOnce())` +
`messageCaptor.getValue()` idiom already used by `testSendLocalizedEmailForAllSupportedLanguages`
(`MailServiceIT.java:211-232`). Each asserts the captured `MimeMessage` content **contains** the
fully-rendered reminder sentence and the rendered greeting, and that the subject equals the
locale's title key.

Shared fixture: `user.login = "john"`, `vehicle = (make "Toyota", model "Corolla", licensePlate
"WX 12345")`, `diff = 3`, and `LocalDate.of(2026, 4, 18)` for `nextByDate` / `validThru`;
`routineService.details = "Oil change"`.

Expected rendered sentences after Phase 2 (six cases):

```
en service    Please bear in mind that in 3 days i.e. 2026-04-18 the following services should be carried out for your vehicle Toyota Corolla (WX 12345): Oil change.
pl service    W systemie zaznaczono, że w pojeździe Toyota Corolla (WX 12345) w ciągu 3 dni, tj. 2026-04-18, powinna zostać wykonana następująca obsługa: Oil change.
en insurance  Please bear in mind that the insurance for your vehicle Toyota Corolla (WX 12345) will expire in 3 days i.e. 2026-04-18.
pl insurance  Ważność polisy ubezpieczeniowej dla Twojego pojazdu Toyota Corolla (WX 12345) zakończy się w ciągu 3 dni, tj. 2026-04-18.
en inspection Please bear in mind that the technical inspection of your vehicle Toyota Corolla (WX 12345) should be carried out in 3 days i.e. 2026-04-18.
pl inspection Ważność przeglądu technicznego Twojego pojazdu Toyota Corolla (WX 12345) zakończy się w ciągu 3 dni, tj. 2026-04-18.
```

Greeting assertion: `Hello john,` (`en`) and `Cześć john,` (`pl` — the trailing comma is what
Phase 2 adds).

#### 2. Creation-mail Polish assertion

**File**: `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`

**Intent**: Give the mojibake fix a guard. The reminder templates never reference
`email.creation.text1`, so without this the Phase 2 encoding correction ships untested.

**Contract**: Tighten the existing `testCreationEmail` (`MailServiceIT.java:175-188`) — currently
`assertThat(...).isNotEmpty()` — to also exercise `langKey = "pl"` and assert the rendered body
contains `kliknij poniższy link`. Keep the existing `en` (`Constants.DEFAULT_LANGUAGE`) path and
its recipient/from/content-type assertions intact.

### Success Criteria:

#### Automated Verification:

- Test class compiles and runs: `./mvnw verify -Dit.test=MailServiceIT`
- The observed failure set matches the prediction **exactly** — five failing cases (`en` service on
  placeholder order; `pl` service, insurance, and inspection on the missing greeting comma; `pl`
  creation on the mojibake) and three passing (`en` insurance, `en` inspection, `en` creation)
- The nine pre-existing `MailServiceIT` tests are unaffected and still pass

#### Manual Verification:

- The `en` service failure message shows the scrambled rendering (`in Toyota days i.e. Corolla …`),
  confirming the assertion detects *this* defect rather than failing for an unrelated reason
- No other test class is disturbed by the shared-context template engine (spot-check a second IT in
  the same run)

**Implementation Note**: This phase intentionally ends with failing tests. Commit with a message
that makes the red state explicit so it is not mistaken for a broken build, and pause for
confirmation that the failure set matches before proceeding to Phase 2.

---

## Phase 2: Green — the five bundle edits

### Overview

Apply the sweep fixes. All five are single-line edits or deletions in properties files; no Java,
config, or template changes.

### Changes Required:

#### 1. The placeholder fix (primary)

**File**: `src/main/resources/i18n/messages_en.properties`

**Intent**: Make line 35 agree with the argument vector `serviceReminderEmail.html:10` passes,
which is exactly the value the root bundle already holds.

**Contract**: `email.service.text1` becomes byte-identical to `messages.properties:35` —
placeholders `{3}` (days) and `{4}` (date) in the leading clause, `{0} {1} ({2})` for the vehicle,
`{5}` for details. After this edit the two files differ on **zero** keys.

#### 2. Polish greeting punctuation

**File**: `src/main/resources/i18n/messages_pl.properties`

**Intent**: Align `email.greeting` punctuation with `en`/root.

**Contract**: Line 9 gains a trailing comma: `Cześć {0},`.

#### 3. Polish mojibake

**File**: `src/main/resources/i18n/messages_pl.properties`

**Intent**: Repair the double-encoded word in `email.creation.text1` so Polish users receive
readable creation mail.

**Contract**: Line 17's `poniÅ¼szy` becomes `poniższy` — the `c3 85 c2 bc` byte sequence replaced by
`c5 bc`. The rest of the line, including the correctly-stored `zostało` (`c5 82`), is untouched.

#### 4. Dead key removal

**Files**: `src/main/resources/i18n/messages.properties`,
`src/main/resources/i18n/messages_en.properties`

**Intent**: Remove `email.reset.greeting`, which no template and no Java code references —
`passwordResetEmail.html` greets with `email.greeting` like every other mail — and which is the only
key with a cross-bundle presence gap.

**Contract**: Line 21 deleted from both files. All three bundles then hold 82 keys with no gaps.

#### 5. Separator normalization (cosmetic)

**Files**: `src/main/resources/i18n/messages.properties`,
`src/main/resources/i18n/messages_en.properties`

**Intent**: Normalize `reports.vehicle.main.certificate` to the `=` separator used by every other
entry. **This is not a bug fix** — the whitespace separator already resolves correctly — so it must
not be reported as one.

**Contract**: Line 47 becomes `reports.vehicle.main.certificate=Registration certificate`. The
resolved value is unchanged, so `VehicleReport` behavior is identical before and after.

### Success Criteria:

#### Automated Verification:

- All Phase 1 assertions now pass: `./mvnw verify -Dit.test=MailServiceIT`
- Unit tests unaffected: `./mvnw test` (22 tests, 1 intentionally `@Disabled`)
- Full suite green: `./mvnw verify`
- Sweep script re-run reports zero placeholder-**order** differences on `email.service.text1`, zero
  key-presence gaps, zero mojibake hits, and zero whitespace-separator entries
- All three bundles remain valid UTF-8 and each holds 82 keys
- `diff messages.properties messages_en.properties` reports no differences

#### Manual Verification:

- Native-Polish read of all 82 keys in `messages_pl.properties`, confirming the one mojibake was the
  only Polish defect — the sweep detector only recognizes the Latin-1-through-UTF-8 signature and is
  blind to ordinary typos. Any typos found are folded into this phase.
- `hexdump` confirmation that line 17 stores `c5 bc`, not a re-introduced double encoding
- Optional live check: trigger `GET /api/reminder/send` as `ROLE_ADMIN`
  (`ReminderResource.java:22-26`) against a dev instance and read an actual English service reminder

---

## Testing Strategy

### Integration Tests

- Three new render assertions in `MailServiceIT`, each covering `en` and `pl`, exercising the typed
  reminder methods end-to-end from `MailService` through Thymeleaf, the real `messages_*.properties`
  bundles, and `MimeMessage` assembly.
- A tightened `testCreationEmail` covering the Polish creation body.

### What is deliberately not tested

Cross-bundle structural invariants (value equality, placeholder-set equality) — see
"What We're NOT Doing". Placeholder *ordering* in particular is legitimately language-dependent and
would produce a false positive on `pl`.

### Manual Testing Steps

1. Run `./mvnw verify -Dit.test=MailServiceIT` after Phase 1 and record the failure set.
2. Apply Phase 2 edits; re-run and confirm green.
3. Read `messages_pl.properties` end to end as a native speaker.
4. Optionally trigger a live reminder dispatch and inspect a delivered English service reminder.

## Performance Considerations

None. Properties-file edits and test additions only; no change to the daily
`@Scheduled(cron = "0 0 8 * * *")` dispatch path in `ReminderServiceImpl`.

## Migration Notes

None. No schema, no Liquibase changelog, no configuration change. The fix takes effect on the next
application start with no data migration — reminder mails are generated fresh on each dispatch.

## References

- Research: `context/changes/english-reminder-fix/research.md`
- Golden capture methodology (why rendering is outside the reference):
  `context/changes/golden-baseline-capture/reference.md:202-211`
- Test-context constraints inherited by `MailServiceIT`: `AGENTS.md`, "Test context restoration (F-04)"
- Sweep script: `scratchpad/i18n_sweep.py` (reproduction documented in research.md appendix)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Red — render coverage that fails on the live bugs

#### Automated

- [x] 1.1 Test class compiles and runs: `./mvnw verify -Dit.test=MailServiceIT` — 440850d
- [x] 1.2 Observed failure set matches the prediction exactly (5 fail, 3 pass) — 440850d
- [x] 1.3 The nine pre-existing `MailServiceIT` tests still pass — 440850d

#### Manual

- [x] 1.4 `en` service failure message shows the scrambled rendering — 440850d
- [x] 1.5 No other test class disturbed by the shared-context template engine — 440850d

### Phase 2: Green — the five bundle edits

#### Automated

- [x] 2.1 All Phase 1 assertions pass: `./mvnw verify -Dit.test=MailServiceIT` — 7dae6f1
- [x] 2.2 Unit tests unaffected: `./mvnw test` — 7dae6f1
- [x] 2.3 Full suite green: `./mvnw verify` — 7dae6f1
- [x] 2.4 Sweep script reports zero order differences, gaps, mojibake, and whitespace separators — 7dae6f1
- [x] 2.5 All three bundles valid UTF-8, 82 keys each — 7dae6f1
- [x] 2.6 `diff messages.properties messages_en.properties` reports no differences — 7dae6f1

#### Manual

- [x] 2.7 Native-Polish read of all 82 keys in `messages_pl.properties` — 7dae6f1
- [x] 2.8 `hexdump` confirms line 17 stores `c5 bc` — 7dae6f1
- [x] 2.9 Optional live check via `GET /api/reminder/send` — 7dae6f1
