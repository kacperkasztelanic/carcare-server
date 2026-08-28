---
date: 2026-08-28T15:01:58+02:00
researcher: Kacper Kasztelanic
git_commit: 8fcea809899bd5bf21da0db29812c92f763f1c04
branch: refactor
repository: kkasztel_carcare/server
topic: "English reminder e-mails render placeholders in the wrong slots; full i18n bundle sweep"
tags: [research, codebase, i18n, mail, reminders, messages-properties, MailService]
status: complete
last_updated: 2026-08-28
last_updated_by: Kacper Kasztelanic
---

# Research: English reminder fix

**Date**: 2026-08-28T15:01:58+02:00
**Researcher**: Kacper Kasztelanic
**Git Commit**: `8fcea809899bd5bf21da0db29812c92f763f1c04` (`8fcea80`)
**Branch**: `refactor`
**Repository**: `kkasztel_carcare/server` (GitLab — no GitHub permalinks available)

## Research Question

`english-reminder-fix` was opened with no stated intent. Establish what is actually broken about
English reminder e-mails, then sweep all three i18n bundles for related defects.

**Agreed scope** (confirmed with the user before writing this document):
- **Fix scope**: full i18n bundle sweep — the placeholder bug plus the other bundle defects found.
- **Test scope**: render-assertion coverage for the three reminder templates in `MailServiceIT`.

## Summary

The English service-reminder e-mail is **user-visibly broken and has been since the feature
shipped**. `email.service.text1` in `messages_en.properties` numbers its `MessageFormat`
placeholders in a different order than the arguments the Thymeleaf template passes, so an English
recipient gets the vehicle make where the day count belongs and vice versa:

> Please bear in mind that in **Toyota** days i.e. **Corolla** the following services should be
> carried out for your vehicle **3** **2026-04-18** (**WX 12345**): Oil change.

`Constants.DEFAULT_LANGUAGE` is `"en"` (`src/main/java/com/kasztelanic/carcare/config/Constants.java:17`)
and `UserService` assigns it to every self-registered account, so this is the default population,
not an edge case.

Three facts make this a clean, low-risk fix:

1. The **root bundle `messages.properties` already has the correct ordering** — it and
   `messages_en.properties` are byte-identical on all 83 keys *except* this single line. The fix
   is to make line 35 match.
2. The **Polish bundle is correct** (its ascending `{0}…{5}` order is right for Polish word order),
   so only English is affected.
3. Nothing tests it. The bug survived from `e6a553f` to HEAD because **no test renders any reminder
   template**, and the golden reminder capture deliberately stops one layer above the rendering.

The sweep found three further bundle defects, all cosmetic or latent rather than user-visible, plus
one thing that *looks* like a defect but is not.

## Detailed Findings

### 1. The placeholder bug (primary — user-visible, English only)

The argument vector is fixed by the Thymeleaf template
(`src/main/resources/templates/mail/serviceReminderEmail.html:10`):

```html
<p th:text="#{email.service.text1(${vehicle.make}, ${vehicle.model}, ${vehicle.licensePlate},
                                  ${diff}, ${routineService.nextByDate}, ${routineService.details})}">
```

So the contract is `{0}`=make, `{1}`=model, `{2}`=licensePlate, `{3}`=diff (days),
`{4}`=nextByDate, `{5}`=details. Against that contract:

| Bundle | Line | Value | Correct? |
|---|---|---|---|
| `messages.properties` | 35 | `…in {3} days i.e. {4} … for your vehicle {0} {1} ({2}): {5}.` | ✅ |
| `messages_en.properties` | 35 | `…in {0} days i.e. {1} … for your vehicle {2} {3} ({4}): {5}.` | ❌ |
| `messages_pl.properties` | 34 | `…w pojeździe {0} {1} ({2}) w ciągu {3} dni, tj. {4}, … {5}.` | ✅ |

The Polish string uses placeholders in ascending order and is nonetheless correct, because Polish
puts the vehicle clause first. **This is why a naive "placeholder order must match across locales"
lint would produce a false positive on `pl` and would not reliably identify `en` as the broken
one** — the sweep script below confirms the placeholder *sets* are identical in all three bundles.
Only rendering against known arguments distinguishes right from wrong, which is what the agreed
render-assertion test does.

The other two reminder templates are fine — `insuranceReminderEmail.html:10` and
`inspectionReminderEmail.html:10` pass the same `(make, model, licensePlate, diff, validThru)`
vector, and all three bundles number `email.insurance.text1` / `email.inspection.text1`
consistently with it.

**Origin.** `git log -L` on the key shows the wrong string entered at `e6a553f` ("Finish
reminders") — and entered *under the wrong key too*, as a copy-paste of the service text stored as
`email.inspection.text1`. `6fad7ac` ("Version 0.9.2") renamed the key to `email.service.text1` but
left the indices untouched. The root bundle received the correct string at that same `e6a553f`
commit, so the two English files have disagreed from the moment the key existed.

**Resolution order.** Spring Boot's `MessageSourceAutoConfiguration` builds a
`ResourceBundleMessageSource` from `spring.messages.basename: i18n/messages`
(`src/main/resources/config/application.yml:77-78`); there is no custom `MessageSource` bean —
`LocaleConfiguration.java` only registers a `LocaleResolver`. For locale `en`, `ResourceBundle`
resolves `i18n/messages_en.properties` ahead of `i18n/messages.properties`, so the broken file wins
and the correct root value is never consulted.

### 2. Polish mojibake in `email.creation.text1` (secondary)

`src/main/resources/i18n/messages_pl.properties:17` contains a double-encoded word. Byte-verified:

```
… kliknij poni c3 85 c2 bc szy link …      ← U+00C5 U+00BC  ("poniÅ¼szy")
```

`c3 85 c2 bc` is the UTF-8 encoding of `Å¼`, which is itself `c5 bc` (`ż`) read as Latin-1. The
correct text is `poniższy`. The corruption is confined to that one word — the same line's
`zostało` is stored correctly as `c5 82`, and the file is otherwise valid UTF-8 throughout. The
identical phrase is spelled correctly at line 12 of the activation message, which is what makes
this obviously a corruption rather than a spelling choice.

Reaches the user through `mail/creationEmail.html` → `MailService.sendCreationEmail`
(`MailService.java:121-125`).

### 3. `email.reset.greeting` — dead key, present in `en`/root only (latent)

Defined in `messages.properties:20` and `messages_en.properties:20`, absent from
`messages_pl.properties`. It is the only key with a presence gap across the three bundles. It is
also referenced by **no** template and no Java code — every mail template greets with
`#{email.greeting}`, including `passwordResetEmail.html`. So the gap is currently harmless; it
becomes a `NoSuchMessageException` risk only if someone wires the key up later.

### 4. `email.greeting` punctuation drift (cosmetic)

`en`/root: `Hello {0},` — `pl`: `Cześć {0}` (no trailing comma). Both render, both read fine; it is
inconsistency rather than breakage.

### 5. Ruled out: `reports.vehicle.main.certificate` (NOT a defect)

Both `messages.properties:47` and `messages_en.properties:47` read

```
reports.vehicle.main.certificate Registration certificate
```

with a space rather than `=` (Polish uses `=`). This *looks* like a broken entry, and the key is
live — `VehicleReport` reads it. It is nevertheless correct: `java.util.Properties` (and
`PropertyResourceBundle` on top of it) accepts unescaped whitespace as a key/value separator
alongside `=` and `:`, so the key resolves to `Registration certificate` as intended. Worth
normalizing to `=` for consistency during the sweep, but it is not a bug and carries no behavioral
risk either way. **Do not report it as a fix.**

### 6. Why nothing caught this — the coverage gap

`MailServiceIT` (`src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java`) is stock
JHipster scaffolding: ten tests covering `sendEmail` variants, `sendEmailFromTemplate`, activation,
creation, password-reset, and a per-language loop — **all against `mail/testEmail`, a two-token
stub** (`src/test/resources/templates/mail/testEmail.html`). The three typed reminder methods
(`sendInsuranceReminderEmail`, `sendInspectionReminderEmail`, `sendRoutineServiceReminderEmail`,
`MailService.java:85-113`) have zero coverage, and the four assertions that do touch real templates
assert only `isNotEmpty()`.

The golden reminder capture from `golden-baseline-capture` does not close the gap either. Both
`src/test/resources/golden/reminders/typed-seam.json` and `full-path.json` capture the *selection*
decision only — `eventType`, `ownerLogin`, `ownerLangKey`, `vehicleHandle`, `eventHandle`,
`dueDate`, `diff` — six entries each, with **no rendered message text** (grepped: zero hits for the
message body in either file). Per `context/changes/golden-baseline-capture/reference.md:202-211`,
the capture runner intercepted a synchronous `MailService` subclass *at the three typed send
methods*, i.e. deliberately above template rendering and bundle lookup. The bug lives below that
line. Note also that neither golden file is currently read by any test — `grep -rn "golden/reminders"
src/test` returns nothing; they are captured references awaiting a parity slice.

**`MailServiceIT` is nevertheless the right home for the new test.** Its `setup()` swaps in a
`ResourceBundleMessageSource` with basenames `("i18n/test-messages", "i18n/messages")`
(`MailServiceIT.java:68-72`). `test-messages_en.properties` defines only `email.test.title`, so
`email.service.text1` falls through to the second basename and resolves from the **real**
`messages_en.properties` — a render assertion there exercises the actual production string. The
test also constructs `new MailService(...)` directly rather than getting the bean, which bypasses
the `@Async` proxy and makes the typed reminder methods run synchronously.

### 7. Fixture shape for the new test

Domain objects are Lombok `@Builder` with private all-args constructors:
- `Vehicle` — `make`, `model`, `licensePlate` are direct fields (`Vehicle.java:45-59`), *not*
  inside the embedded `VehicleDetails`, so the templates' `${vehicle.make}` works off a minimal
  builder.
- `RoutineService` — `nextByDate` and `details` are direct fields (`RoutineService.java:75-89`).
- `Insurance` / `Inspection` — templates read `validThru` on each.
- `diff` is passed as a plain `int` by `ReminderServiceImpl` (`ReminderServiceImpl.java:58,69,80`).

A caution for whoever writes the assertion: `{3}` is an `int`, so `MessageFormat` applies number
formatting — a day count of e.g. `1000` renders as `1,000` under `en`. Keep fixture values under
four digits, or assert with the formatted form.

## Code References

- `src/main/resources/i18n/messages_en.properties:35` — **the bug**; wrong placeholder indices
- `src/main/resources/i18n/messages.properties:35` — correct ordering; the fix target value
- `src/main/resources/i18n/messages_pl.properties:34` — correct Polish ordering (do not "align")
- `src/main/resources/i18n/messages_pl.properties:17` — `poniÅ¼szy` mojibake → `poniższy`
- `src/main/resources/i18n/messages_pl.properties:9` — `email.greeting` missing trailing comma
- `src/main/resources/i18n/messages.properties:20`, `messages_en.properties:20` — dead
  `email.reset.greeting`, absent in `pl`
- `src/main/resources/templates/mail/serviceReminderEmail.html:10` — defines the argument order
- `src/main/resources/templates/mail/insuranceReminderEmail.html:10`,
  `inspectionReminderEmail.html:10` — same pattern, already correct
- `src/main/java/com/kasztelanic/carcare/service/MailService.java:85-113` — the three untested
  typed reminder methods
- `src/main/java/com/kasztelanic/carcare/service/impl/ReminderServiceImpl.java:41-85` — daily
  `@Scheduled(cron = "0 0 8 * * *")` dispatch; `diff` computed via `ChronoUnit.DAYS.between`
- `src/main/java/com/kasztelanic/carcare/config/Constants.java:17` — `DEFAULT_LANGUAGE = "en"`
- `src/main/java/com/kasztelanic/carcare/service/UserService.java:139` — assigns it on registration
- `src/main/resources/config/application.yml:77-78` — `spring.messages.basename: i18n/messages`
- `src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java:68-72` — message-source swap
  that lets a new test still hit the real bundle
- `src/test/resources/golden/reminders/typed-seam.json`, `full-path.json` — selection-only capture
- `src/main/java/com/kasztelanic/carcare/web/rest/ReminderResource.java:22-26` —
  `GET /api/reminder/send`, `ROLE_ADMIN`, manual dispatch trigger useful for a live check

## Architecture Insights

- **Two English bundles, one of them shadowed.** `messages.properties` and
  `messages_en.properties` are byte-identical except for the broken line. The root file is
  effectively dead weight for English users — `messages_en` always wins — yet it is the file that
  holds the *correct* string. Any future English edit made to only one of the pair silently
  diverges again. Collapsing the duplication (or making the pair's equality an asserted invariant)
  is the structural fix behind the one-line one; worth raising at plan time, though it is a scope
  decision rather than a finding.
- **The reminder pipeline has no observable seam below `MailService`.** Selection is golden-captured;
  rendering is not tested at all. The layer boundary that `golden-baseline-capture` chose for good
  reasons (avoiding the `@Async` proxy) happens to be exactly where this bug hides.
- **Placeholder ordering is legitimately language-dependent**, so cross-locale index comparison is
  not a sound invariant here. Placeholder *set* equality is sound and already holds. This is the
  concrete reason the render assertion is the right test.
- The i18n bundles are UTF-8 and read as UTF-8 (Boot 3 default `spring.messages.encoding=UTF-8`);
  the Polish corruption is baked into the stored bytes, not an encoding-configuration problem, so
  it will not be fixed by any config change.

## Historical Context (from prior changes)

- `context/changes/golden-baseline-capture/reference.md:202-211` — Phase 4 reminder capture
  methodology; states explicitly that the observation point is the three typed `MailService`
  methods with a synchronous subclass, which is why message rendering is outside the golden
  reference.
- `context/changes/golden-baseline-capture/reference.md:30-68` — golden fixture inventory; the
  `*-reminder-plus-three` / `*-reminder-plus-seven` / `reminder-minus-one` handles and the
  `2026-04-15` reference date, reusable if the new test wants realistic reminder data.
- `context/archive/2026-08-25-test-context-restored/` — F-04 test-context restoration; established
  that current coverage is "JHipster scaffolding only — no vehicle, event, report, statistics, or
  reminder business behavior is tested", which this change partially addresses.
- `AGENTS.md` "Test context restoration (F-04)" — records that `MailServiceIT` runs in the shared
  cached context and that its `@AfterEach` restores `templateEngineMessageSource` to `null`; a new
  test added to that class inherits both behaviors and must not disturb them.

## Related Research

- `context/changes/golden-baseline-capture/research.md` — reminder selection behavior at the
  known-good baseline `6e19b96`.
- `context/changes/report-parity/research.md` — the other consumer of `MessageSource` (`CostReport`,
  `VehicleReport`), which reads the `reports.*` keys touched by finding 5.

## Decisions (settled 2026-08-28, before planning)

1. **Duplicate English bundles — keep both, fix the one line.** `messages_en.properties:35` is
   corrected to match `messages.properties:35`; neither file is deleted and no config changes. The
   duplication survives by choice; the new render test is what guards against re-divergence.
   *Rejected alternative and why it mattered:* deleting `messages_en.properties` so the base bundle
   serves English is stock JHipster convention and would kill the drift class permanently, but it
   is **unsafe as a standalone edit** — see the fallback finding below.
2. **`email.reset.greeting` — delete** from `messages.properties:20` and
   `messages_en.properties:20`. Dead in all three bundles: no template and no Java references it,
   and `passwordResetEmail.html` greets with `email.greeting` like every other mail.
3. **Render assertions cover both `en` and `pl`** for all three reminder templates. Covering `pl`
   is what locks in the mojibake fix as well as the placeholder fix.
4. **`email.greeting`** — add the trailing comma to `messages_pl.properties:9`, matching `en`/root.
5. **`reports.vehicle.main.certificate`** — normalize the whitespace separator to `=` in root and
   `en`. Cosmetic only; it already resolves correctly (finding 5).
6. **Out of scope:** `sendCreationEmail` passes `"email.activation.title"` as its subject key
   (`MailService.java:124`), so account-creation mails are subject-lined "CarCare account
   activation". Pre-existing and unrelated to bundle content — recorded here only so it is not
   rediscovered later as a regression from this change.

### Fallback finding that constrained decision 1

`spring.messages.fallback-to-system-locale` is set **nowhere** in
`src/main/resources/config/` or `src/test/resources/config/`, so Boot's default `true` applies and
Spring's `MessageSourceControl` leaves `getFallbackLocale` active. The `ResourceBundle` candidate
chain is therefore `requested locale → JVM default locale → base`.

This is inert today because both `en` and `pl` have bundles. But deleting
`messages_en.properties` would make an explicitly-requested `en` resolve
`messages_en` (absent) → **`messages_pl`** → base, so on any JVM whose default locale is Polish —
the dev machine, and any container not pinned to `C`/`en` — **English users would receive Polish
mail**. `pom.xml` pins `-Duser.timezone=UTC` for tests but nothing pins `user.language`.

Deletion is clean from a path-reference standpoint (`grep` over `src/` and `pom.xml` finds only
`basename: i18n/messages` at `application.yml:78` and the unrelated `test-messages_<locale>` load
at `MailServiceIT.java:222`) — the constraint is purely the fallback semantics. If the duplication
is ever revisited, delete `messages_en.properties` and set
`spring.messages.fallback-to-system-locale: false` **in the same change**, never separately.

## Open Questions

1. **Is the corrupted Polish word the only defect in `messages_pl.properties`?** The sweep found
   exactly one mojibake artifact, but the detector only recognizes the Latin-1-through-UTF-8
   signature — it cannot see ordinary typos or awkward phrasing across the 82 keys (mostly report
   column headers). A native-Polish read is a stronger check than any script and is worth doing
   before the sweep is called complete. Raised with the user; not blocking the plan.

## Appendix — sweep reproduction

The three-bundle comparison is scripted at
`/private/tmp/claude-501/-Users-kacper-Dev-carcare-server/b6d365fc-4d8a-4922-be25-350fedff4146/scratchpad/i18n_sweep.py`
(run from the repo root). Output at `8fcea80`:

```
root  messages.properties          keys= 83 utf8_clean=True
en    messages_en.properties       keys= 83 utf8_clean=True
pl    messages_pl.properties       keys= 82 utf8_clean=True

=== key presence gaps ===
  email.reset.greeting: root=Y, en=Y, pl=N

=== placeholder-SET mismatches ===
  (none)

=== placeholder ORDER differences (same set, different sequence) ===
  email.service.text1  root order=['3','4','0','1','2','5']  line 35
  email.service.text1  en   order=['0','1','2','3','4','5']  line 35
  email.service.text1  pl   order=['0','1','2','3','4','5']  line 34

=== likely mojibake ===
  pl line 17  email.creation.text1 = … kliknij poniÅ¼szy link …

=== entries using whitespace instead of '=' as separator ===
  root line 47: reports.vehicle.main.certificate Registration certificate
  en   line 47: reports.vehicle.main.certificate Registration certificate

=== values differing between root and en ===
  email.service.text1   (the only one of 83 keys)
```
