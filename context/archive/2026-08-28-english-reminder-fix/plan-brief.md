# English Reminder Fix — Plan Brief

> Full plan: `context/changes/english-reminder-fix/plan.md`
> Research: `context/changes/english-reminder-fix/research.md`

## What & Why

The English service-reminder e-mail renders its placeholders in the wrong slots — recipients read
"in **Toyota** days i.e. **Corolla** … for your vehicle **3** **2026-04-18** (**WX 12345**)". It has
been broken since the reminder feature shipped, and because `Constants.DEFAULT_LANGUAGE` is `"en"`,
this is the default population rather than an edge case. This change fixes it, sweeps four further
i18n bundle defects found alongside it, and adds the render-level test coverage whose absence let
the bug survive for years.

## Starting Point

`messages_en.properties:35` numbers `email.service.text1`'s placeholders `{0}…{5}` ascending, while
`serviceReminderEmail.html:10` passes `(make, model, licensePlate, diff, nextByDate, details)`. The
root bundle already holds the correct string — the two English files are byte-identical on all 83
keys *except* this one — and Polish is correct too. Nothing tests it: `MailServiceIT` is stock
JHipster scaffolding rendering a two-token stub template, the three typed reminder methods have zero
coverage, and the `golden-baseline-capture` reminder fixtures deliberately intercept *above*
template rendering.

## Desired End State

Reminder e-mails render correctly in both languages, the Polish bundle is free of encoding
corruption, and `MailServiceIT` asserts the rendered body of all three reminder templates in `en`
and `pl` — so a future re-divergence fails the build instead of reaching an inbox.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Duplicate English bundles | Keep both, fix the one line | Deleting `messages_en.properties` is unsafe alone — `fallback-to-system-locale` is unset, so `en` would fall through to **`pl`** on a Polish-default JVM | Research |
| `email.reset.greeting` | Delete from root and `en` | Dead in all three bundles; no template or Java reference | Research |
| Test coverage | Render assertions, three templates × `en` + `pl` | Placeholder *sets* already match across locales and ordering is legitimately language-dependent — only rendering distinguishes right from wrong | Research |
| `messages_pl.properties:34` | Leave untouched | Its ascending order is correct for Polish word order | Research |
| Test shape | Per-template methods, both locales inline | Failure names the template directly; matches the existing loop idiom in the class | Plan |
| Ordering | Test-first (red), then fix (green) | A test that only ever passes proves nothing — the predicted failure set is what shows it detects *this* defect | Plan |
| Drift guard | None | Render assertions cover the strings that reach users; a bundle-parity test would expand scope past the settled decisions | Plan |
| Mojibake guard | Tighten `testCreationEmail` | Corrects research decision 3 — the reminder templates never reference `email.creation.text1`, so `pl` reminder coverage does **not** lock in the mojibake fix | Plan |
| Polish proofread | Manual verification item in Phase 2 | The sweep detector sees only Latin-1-through-UTF-8 corruption, not typos, across the other 82 keys | Plan |

## Scope

**In scope:**
- `messages_en.properties:35` — placeholder reorder (the user-visible bug)
- `messages_pl.properties:17` — `poniÅ¼szy` → `poniższy` mojibake
- `messages_pl.properties:9` — `email.greeting` trailing comma
- Delete `email.reset.greeting` from root and `en` (line 21)
- Normalize `reports.vehicle.main.certificate` separator to `=` (cosmetic, root and `en`, line 47)
- Four test additions in `MailServiceIT`

**Out of scope:**
- Deleting `messages_en.properties` / touching `spring.messages.fallback-to-system-locale`
- A bundle-parity or placeholder-set drift guard
- `sendCreationEmail`'s wrong subject key (`MailService.java:124`, pre-existing)
- The reminder golden-parity slice
- Any production Java, schema, Liquibase, or config change

## Architecture / Approach

Two phases, test-first. Phase 1 adds render assertions to `MailServiceIT` and demonstrates them
failing on the live bugs; Phase 2 applies five properties-file edits and turns them green.

`MailServiceIT` is the right host because its `setup()` installs a message source with basenames
`("i18n/test-messages", "i18n/messages")` — the test bundle defines only `email.test.title`, so
reminder keys fall through to the **real** production bundles — and it constructs `MailService`
directly, bypassing the `@Async` proxy so the typed reminder methods run synchronously.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Red — render coverage | Four test additions; recorded failure set (5 fail, 3 pass) | Ends with a knowingly failing build; the commit message must make the red state explicit |
| 2. Green — bundle edits | Five single-line properties fixes; full suite green | Re-introducing double encoding while fixing the mojibake — invisible in most terminals, so `hexdump` verification is a checklist item |

**Prerequisites:** `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` (Java 17 exactly — the
enforcer fails on anything newer). Working tree is clean at `8fcea80` on branch `refactor`.

**Estimated effort:** ~1 session. Phase 1 is the bulk of it (fixture setup and six expected strings);
Phase 2 is five line edits plus a proofread.

## Open Risks & Assumptions

- **The Polish proofread is a human gate.** Phase 2 cannot be called complete until a native read of
  the remaining 82 keys confirms the one mojibake was the only defect. Any typos found fold into the
  same phase.
- **The duplicate English bundle survives this change.** The render test guards `email.service.text1`
  specifically; a future divergence on a key with no render coverage (a `reports.*` header, say)
  would still slip through silently. Accepted deliberately.
- **Assertion literals couple tests to bundle text.** A legitimate future copy edit to any reminder
  string means editing the test too. Accepted as the cost of asserting real rendering.

## Success Criteria (Summary)

- An English user receiving a service reminder reads the day count where days belong and the vehicle
  where the vehicle belongs.
- A Polish user's account-creation mail reads `kliknij poniższy link`, not `poniÅ¼szy`.
- `./mvnw verify` is green, and re-running the sweep script reports no placeholder-order differences,
  no key-presence gaps, no mojibake, and no whitespace separators across all three bundles.
