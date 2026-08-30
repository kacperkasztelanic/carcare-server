<!-- PLAN-REVIEW-REPORT -->
# Plan Review: External Signing Key Implementation Plan

- **Plan**: `context/changes/external-signing-key/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-30
- **Verdict**: REVISE → **SOUND** after triage (all six findings fixed, 2026-08-30)
- **Findings**: 2 critical, 2 warnings, 2 observations — all FIXED

## Verdicts

| Dimension | Verdict | After triage |
|-----------|---------|--------------|
| End-State Alignment | WARNING | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | PASS | PASS |
| Blind Spots | FAIL | PASS |
| Plan Completeness | WARNING | PASS |

## Grounding

9/9 paths ✓, 8/8 symbols & line refs ✓, brief↔plan ✓ — `application-prod.yml:101,105`,
`application-dev.yml:88`, `application-test.yml:97`, `TokenProvider.java:46,49,57`,
`ApplicationProperties.java:26,68,73`, `app.yml:6-13`, `deploy.sh:9-12`. Confirmed:
`TokenProvider` is constructor parameter 0 of `SecurityConfiguration`; it is the only reader of
either key field in `src/main`; the three `base64-secret` sites named by Phase 2 are the complete
set (no fourth, including `application-tls.yml`); the Progress section satisfies the mechanical
`## Progress` contract (one heading, all five phases matched, every criterion enumerated, no
checkboxes in phase bodies).

## Findings

### F1 — Phase 1's key-length check always fails

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 § 1 ("Confirm without printing it"), Progress 1.2
- **Detail**: `awk -F= '/^CARCARE_JWT_BASE64_SECRET=/{print length($2)}' .env` is documented as
  "expect 88". `openssl rand -base64 64` on 64 bytes always ends in `==` padding, and `-F=` splits
  on those too, so `$2` is the value minus its padding. Verified locally: the command prints **86**,
  never 88. This is the first gate of the phase that closes the live exposure; an operator seeing 86
  against a documented 88 will most plausibly re-run the append, duplicating the key line in `.env`.
- **Fix**: Split on the first `=` only —
  `awk '/^CARCARE_JWT_BASE64_SECRET=/{sub(/^[^=]*=/,""); print length($0)}' .env`
  (verified: prints 88). Update Progress 1.2 to match.
- **Decision**: FIXED — corrected at both sites (Phase 1 § 1 and Phase 1 Success Criteria), with a
  note explaining the `sub()` over `-F=`. Progress 1.2 ("Key length check returns 88") needed no edit.

### F2 — Phase 5's no-second-re-login proof expires before it runs

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 5 § 2, Phase 5 Success Criteria, Progress 5.3
- **Detail**: Phase 5 asserts "a token issued **after Phase 1** still validates — `200` from
  `/api/account`", and reads a `401` as "the key did not survive the image change and the deploy
  must be rolled back". `application-prod.yml:107` sets `token-validity-in-seconds: 86400` — 24
  hours. The plan's own estimate is ~2 sessions between Phase 1 and Phase 5, with a merge, a tag,
  and a tag-triggered pipeline in between. A token minted at Phase 1 will almost certainly be
  expired by then, returning `401` for a reason unrelated to the signing key — a false rollback
  trigger on the one check that decides whether the release stands.
- **Fix**: Re-anchor to a token captured *immediately before* the image swap: mint a fresh token
  minutes before `docker compose up -d`, then assert it returns `200` after. That still proves key
  continuity (the pre-deploy token was signed by the Phase 1 key) without depending on elapsed time.
  Reword Progress 5.3 as "a token minted immediately before the deploy returns 200 after it".
- **Decision**: FIXED — Phase 5 § 2 now mints the token minutes before the swap and records why
  (with the `86400` evidence); Phase 5 Success Criteria and Progress 5.3 reworded to match.

### F3 — The guard covers "absent" but not "too short", and the stated reason for 512 bits is wrong

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment / Blind Spots
- **Location**: Phase 1 § 1 (rationale); Phase 3 § 1–2
- **Detail**: Phase 1 justifies 64 bytes with "`Keys.hmacShaKeyFor` selects the HMAC algorithm from
  key length, so a 256-bit key would silently downgrade to HS256". That is not what this codebase
  does. `TokenProvider:81` signs with an explicit `Jwts.SIG.HS512`, and under jjwt 0.12.3
  (`pom.xml:56`) `DefaultMacAlgorithm` rejects a key shorter than 512 bits — there is no downgrade
  path. What actually happens with a 32-byte key: `Keys.hmacShaKeyFor` accepts it,
  `afterPropertiesSet()` completes, the new guard passes, the container boots healthy — and then
  **every** `/api/authenticate` throws `WeakKeyException`. A boot-healthy app that 500s on all
  logins is precisely the fails-late-and-confusingly shape this change exists to eliminate, and
  Phase 3's guard as specified does not catch it. It also lands in Phase 3's test spec:
  "`base64Secret` set to a valid ≥256-bit value → completes and a token can be created and
  validated" — an implementer who takes ≥256 literally writes a test that passes
  `afterPropertiesSet()` and then fails at `createToken`.
- **Fix A ⭐ Recommended**: Extend the guard to a length check — after the both-empty check, decode
  and require ≥64 bytes (or catch `WeakKeyException` around `Keys.hmacShaKeyFor`), failing with a
  message naming the required length.
  - Strength: Closes the same class of failure the phase is built for, at the one site that already
    reads both fields — no drift risk. Same abort shape (`BeanCreationException` on `tokenProvider`).
  - Tradeoff: Slightly more than "two null checks"; the Performance Considerations note needs a word.
  - Confidence: HIGH — jjwt 0.12.3's minimum-key-length enforcement for HS512 verified against
    `pom.xml:56` and `TokenProvider:81`.
  - Blind spot: Whether the operator would prefer warn-and-continue for a 384-bit key over a hard stop.
- **Fix B**: Correct the rationale and the test spec only — fix the Phase 1 wording, and pin Phase
  3's happy-path test to a 64-byte value with an explicit "must be ≥64 bytes" note.
  - Strength: Keeps Phase 3 to the two null checks the plan scoped.
  - Tradeoff: A short key still boots clean and breaks every login — the gap stays open, just documented.
  - Confidence: HIGH — purely a documentation edit.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — Phase 3 § 1 now requires `keyBytes.length >= 64` before
  `hmacShaKeyFor` and specifies the short-key message; Phase 3 § 2 gains a short-key test and pins
  the happy path to 64 bytes; Phase 1's rationale, the plan-brief decision row, Phase 3's manual
  criteria, Progress 3.5–3.6, and Performance Considerations all updated.

### F4 — Phase 4 asserts a test count Phase 3 has already changed

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4 Success Criteria, Progress 4.1
- **Detail**: Phase 4's criterion is "`./mvnw verify` still green at **38**/249", but Phase 3 adds
  two-to-three unit tests to `TokenProviderTest`, so the count at Phase 4 is 40 or 41. Phase 3's own
  criterion is the loose "38 existing plus the new guard tests", so nothing in the plan fixes the number.
- **Fix**: Have Phase 3 commit to a count (41 with the precedence test), and restate 4.1 and Phase 5
  against that number.
- **Decision**: FIXED — pinned at **42** (the F3 fix added a fourth guard test). Phase 3 criteria and
  Progress 3.1 now say 42; Phase 4 criteria and Progress 4.1 say 42/249; the Desired End State
  verify-by line and the plan-brief success criteria distinguish 38/249 through Phase 2 from 42/249
  after.

### F5 — `>> .env` corrupts the last line if the file lacks a trailing newline

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 § 1
- **Detail**: `printf 'CARCARE_JWT_BASE64_SECRET=%s\n' … >> .env` concatenates onto the preceding
  line if `.env` has no final newline, silently mangling whichever of the three existing secrets is
  last (`CARCARE_MAIL_PASSWORD` would break reminders). The `grep -c` check does catch it, but the
  damage to the other line is already done and recovering means hand-editing a secrets file.
- **Fix**: Guard the append with `[ -s .env ] && [ -n "$(tail -c1 .env)" ] && echo >> .env` before
  the printf.
- **Decision**: FIXED — the guard now precedes the `printf` in Phase 1 § 1, with a comment naming
  the line that would take the damage.

### F6 — The `.env.gpg` round-trip check compares against a number never captured

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 § 3, Progress 1.3
- **Detail**: `gpg --decrypt … | wc -l   # expect .env's line count` — the runbook never tells the
  operator to record that count first, and a line count would not catch a truncated or stale value
  anyway.
- **Fix**: Replace both checks with one exact, non-leaking comparison:
  `gpg --decrypt --quiet .env.gpg 2>/dev/null | cmp -s - .env && echo MATCH` (`cmp -s` prints
  nothing on difference, so no secret can leak).
- **Decision**: FIXED — Phase 1 § 3 now runs the single `cmp -s` comparison; Phase 1 Success
  Criteria and Progress 1.3 reworded to "byte-identical".

## Verified sound — not findings

The ordering rationale (Phase 1 before the committed default is removed), the token-invalidation
proof for Phase 1, the both-spellings host mapping across `1.3.10` and `1.3.11+`, the guard's
placement at the single reader that knows the precedence, the
`new TokenProvider(securityMetersService, new ApplicationProperties())` construction (eager `final`
nested config confirmed at `ApplicationProperties.java:26,68,73`), the three-file completeness of
the `base64-secret` rotation, the Progress↔Phase mechanical contract, and the FR-002 narrowing
(owner-approved and recorded in `plan.md`, `plan-brief.md` and `change.md`).
