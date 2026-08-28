# Follow-ups from implementation review

Source: [reviews/impl-review.md](../reviews/impl-review.md) — reviewed 2026-08-28, verdict APPROVED.

## F1 — Collapse the duplicate English bundle (ACCEPTED, deferred)

**Status**: not scheduled. Recorded so the rationale is not re-derived from scratch.

**Why it matters.** `MailServiceIT` resolves `en` → `messages_en.properties` and `pl` →
`messages_pl.properties`. The root `messages.properties` is only ever reached as a final fallback,
so no test renders it. After this change the root and `en` bundles are **byte-identical**, which
means a future edit to one and not the other re-opens exactly the divergence class that produced
the original scrambled English service reminder — and the new render assertions would not catch it,
because they only ever read `messages_en`.

**What the fix is.** Delete `src/main/resources/i18n/messages_en.properties` **and** set
`spring.messages.fallback-to-system-locale: false` in `application.yml`, in the **same** change.

**Why both edits must land together.** `fallback-to-system-locale` is currently unset, so Boot's
default `true` applies and the `ResourceBundle` candidate chain is
`requested → JVM default → base`. Deleting the file alone would make an explicitly-requested `en`
resolve `messages_en` (absent) → **`messages_pl`** → base, so on any JVM whose default locale is
Polish, English users would receive Polish mail. This is also recorded in the plan's
"What We're NOT Doing" and in `change.md`.

**Payoff.** Once `messages_en` is gone, the existing `en` assertions in the three reminder tests
cover the root bundle for free — closing the coverage gap without writing a new test.

## F2, F3 — Fixed during triage

Both applied to `MailServiceIT` on 2026-08-28; no follow-up needed.
