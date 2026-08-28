<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Admin Surface Parity

- **Plan**: `context/changes/admin-surface-parity/plan.md`
- **Scope**: Phases 1–3 of 3 (full plan)
- **Date**: 2026-08-28
- **Verdict**: REJECTED → all findings triaged and resolved 2026-08-28 (see Decisions)
- **Findings**: 1 critical, 2 warnings, 2 observations

## Post-triage verification

`./mvnw verify` after all fixes → **BUILD SUCCESS**, 217 tests (was 216), 0 failures, 1 skipped.
`git diff --check` clean.

## Verification performed (original review)

- `./mvnw verify` (JDK 17 + Byte Buddy agent, `-Duser.timezone=UTC`) → **BUILD SUCCESS**,
  216 tests, 0 failures, 0 errors, 1 skipped (pre-existing `@Disabled` in `WebConfigurerTest`).
- `git diff --check` → clean.
- All four authorized route corrections present and asserted by enabled tests
  (`/api/fuel-type/{type}`, `/api/insurance-type/{type}`, `/api/reminder-advance/{days}` Location,
  and the `@DeleteMapping("/{days}")` binding repair).
- Both reminder golden fixtures consumed, each asserting exactly six typed mail invocations.
- F1 confirmed empirically with a throwaway IT (since removed) — see the finding.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | FAIL |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Blank langKey now persists NULL and 500s downstream

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/dto/UserDto.java:63`
- **Detail**: The hand-written setter coerces blank → null so `@Size(min = 2)` no longer rejects
  `langKey: ""`. On the *create* path this is benign — `UserService.createUser` defaults null to
  `"en"` (`UserService.java:138`). But `UserService.updateUser(UserDto)` (`:204`, the admin
  `PUT /api/users` path) and `updateUser(..., langKey, ...)` (`:178`, the `POST /api/account` path)
  both assign the value unconditionally, and `jhi_user.lang_key` is nullable. Confirmed empirically:
  `PUT /api/users {"langKey":""}` → **200**, persisted `lang_key = null`; a subsequent
  `GET /api/fuel-type` as that user → **500**. The 500 originates in
  `Locale.forLanguageTag(user.getLangKey())`, which NPEs on null — the same call appears in
  `InsuranceTypeResource:75`, `MailService` (5 sites), `ReportServiceImpl` (2 sites),
  `VehicleMapper`, `InsuranceMapper`, and `VehicleRichMapper`. One blank-langKey update therefore
  breaks lookups, reminder mail, and report generation for that user. Before this change the
  request was a clean 400.
- **Fix A ⭐ Recommended**: Make the two update paths fall back to `Constants.DEFAULT_LANGUAGE` when
  the incoming langKey is null (`UserService.java:178` and `:204`), matching the guard already at
  `:138`.
  - Strength: Keeps the intended frozen-client leniency while closing every NULL-persistence route;
    reuses the DEFAULT_LANGUAGE guard already in this class.
  - Tradeoff: Touches two more production lines in a parity change.
  - Confidence: HIGH — the create path already proves this shape works.
  - Blind spot: Users whose `lang_key` is already NULL from an earlier manual edit stay broken; no
    data backfill included.
- **Fix B**: Revert the `UserDto` setter to `@Setter` and drop
  `createUserWithEmptyLanguageKeyUsesDefaultLanguage`.
  - Strength: Restores exact baseline behavior (400), which is what "all other parity behavior
    remains unchanged" asked for.
  - Tradeoff: If the frozen client really does send `langKey: ""`, admin user creation breaks in the
    UI. The commit message asserts it does, but nothing in the diff or research cites the client code
    that proves it.
  - Confidence: MEDIUM — depends on a client claim not verifiable from this repo.
  - Blind spot: `../client` was not grepped for a blank-langKey emitter.
- **Decision**: FIXED via Fix A — `Constants.DEFAULT_LANGUAGE` fallback added at `UserService.java:178`
  (`POST /api/account`) and `:204` (`PUT /api/users`). Review also found a third unguarded path missed in
  the original finding — `registerUser` at `:107` (public `POST /api/register`) — and closed it the same way.
  `UserDto.setLangKey` left as-is. Verified: blank langKey on `PUT /api/users` now returns `"en"` and the
  follow-up `GET /api/fuel-type` returns 200.

### F2 — Two production behavior changes outside the authorized four

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/kasztelanic/carcare/service/dto/UserDto.java:63`,
  `src/main/java/com/kasztelanic/carcare/service/impl/RandomDataServiceImpl.java:49`
- **Detail**: The plan's Overview names exactly four authorized production corrections (three
  Locations + the reminder DELETE binding) and states "all other parity behavior remains unchanged".
  The diff contains two more, neither in any "Changes Required" section:
  (1) `UserDto.setLangKey` — see F1;
  (2) `RandomDataServiceImpl.generateRandomVehicles`, `.map(...).count()` → `.mapToInt(...).sum()`.
  The second is a genuine latent bug fix — `count()` on a SIZED stream elides the mapper, so the
  endpoint returned `true` without generating anything — but it also tightens the return contract
  from "N elements seen" to "all N `generateOne` calls succeeded". Neither `plan.md` nor `change.md`
  was amended; the only plan diff across the three phase commits is Progress checkbox flips. The
  Migration Notes rollback instruction ("revert those four controller-owned route strings") is now
  incomplete.
- **Fix A ⭐ Recommended**: Add a short addendum to `plan.md` recording both changes, why each was
  necessary, and their rollback; extend Migration Notes to cover them.
  - Strength: Preserves the RandomDataService fix (Phase 3's own count-delta assertion depends on
    it) and makes the plan honest before archival.
  - Tradeoff: Documents a scope expansion after the fact rather than re-deciding it.
  - Confidence: HIGH — this repo already documents accepted divergences this way (see AGENTS.md on
    the `4ad88bd` fix groups).
  - Blind spot: Doesn't by itself resolve F1.
- **Fix B**: Revert both and re-scope into a follow-up change.
  - Strength: Strict adherence to the S-02 "unchanged" wording.
  - Tradeoff: Reverting `RandomDataServiceImpl` re-breaks
    `adminCanGenerateOneRandomVehicleForTheCurrentUser`, so Phase 3 coverage would shrink too.
  - Confidence: MEDIUM — the test dependency is clear, the appetite for losing coverage is not.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — `plan.md` gained an "Addendum: production changes beyond the authorized
  four" section (A1 blank language keys incl. the F1 fix, A2 random vehicle generation), each with rationale
  and rollback; Migration Notes extended to point at it and to note that pre-existing NULL `lang_key` rows
  are not backfilled.

### F3 — New leniency is only tested on the create path

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/UserResourceIT.java:180`
- **Detail**: `createUserWithEmptyLanguageKeyUsesDefaultLanguage` pins the new behavior for
  `POST /api/users` only. The same setter also loosened `PUT /api/users` and `POST /api/account`,
  where the outcome is NULL rather than `"en"` (F1), and no test exercises either. The suite is
  green precisely because the broken paths are uncovered.
- **Fix**: Once F1 is resolved, add a `PUT /api/users` case asserting a blank langKey persists
  `DEFAULT_LANGUAGE` (not null), plus a follow-up `GET /api/fuel-type` as that user asserting 200.
- **Decision**: FIXED — added `UserResourceIT.updateUserWithEmptyLanguageKeyKeepsLookupsUsable`. Confirmed the
  test bites: with the `:204` guard reverted it fails `JSON path "$.langKey" expected:<en> but was:<null>`.

### F4 — Criterion 3.4 names a path that never existed

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `context/changes/admin-surface-parity/plan.md` (criterion 3.4)
- **Detail**: Criterion 3.4's `git add --intent-to-add` lists
  `.../carcare/web/rest/golden/ReminderSelectionParityIT.java`, but Phase 3's own "Changes Required"
  and the actual file both use `.../carcare/golden/ReminderSelectionParityIT.java`. The command as
  written would have failed on a nonexistent pathspec. Whitespace is in fact clean (re-verified with
  `git diff --check`) — the criterion simply didn't check what it claimed.
- **Fix**: Correct the path in criterion 3.4 to match Changes Required.
- **Decision**: FIXED — path corrected to `src/test/java/com/kasztelanic/carcare/golden/ReminderSelectionParityIT.java` in plan.md:331.

### F5 — Clock replaced with a Mockito mock rather than a fixed Clock

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/test/java/com/kasztelanic/carcare/golden/ReminderSelectionParityIT.java:58`
- **Detail**: `@MockBean Clock` plus per-test stubbing of `instant()`/`getZone()` achieves the same
  result as a `@TestConfiguration` supplying the already-constructed `FIXED_CLOCK`, but introduces a
  mock where a real value object exists and stubs two methods the JDK may extend. It also forces an
  extra Spring context, which the plan's Performance Considerations asked to limit. Works correctly
  today.
- **Fix**: Swap `@MockBean Clock` for a `@TestConfiguration` `@Primary` bean returning `FIXED_CLOCK`
  and drop the `reset`/`when` stubs.
- **Decision**: FIXED — `@MockBean Clock` replaced by a nested `FixedClockConfiguration` `@TestConfiguration`
  supplying `FIXED_CLOCK` as a `@Primary` bean, imported via `@Import`; `reset`/`when` clock stubs and the
  `Mockito.when` import removed. Verified: `ReminderSelectionParityIT` 4/4 green.
