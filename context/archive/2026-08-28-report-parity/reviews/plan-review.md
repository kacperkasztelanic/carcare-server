<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Report Parity (S-03)

- **Plan**: `context/changes/report-parity/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-28
- **Verdict**: REVISE → SOUND after triage (all 5 findings fixed in plan, 2026-08-28)
- **Findings**: 0 critical, 4 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

11/11 paths ✓, 9/9 symbols ✓ (`validateHandleMap:201-217`, `normalizeJsonById:244-255`,
`normalizeRawHandles:284-292`, `VehicleReport` sorts at 126/165/198/235/265,
`VehicleRichMapper:64-83`, `USERS_BY_LOGIN_CACHE`, `MailServiceIT:210-216`, all five report DTOs
carry `Long id`), main∩test resources = exactly the two i18n stubs ✓, Progress↔Phase mechanical
contract ✓, brief↔plan ✓.

Independently confirmed as sound: the golden fixture gives every vehicle distinct dates within each
sheet (so Phase 4's tiebreaker is inert for the report path); `MileageServiceImpl`'s
`(v1, v2) -> v2` merge is order-independent because the pre-sort key (mileage 10800 vs 10900) is
distinct; `testSendActivationEmail` does not assert a subject, so unshadowing
`email.activation.title` breaks nothing; `ReminderService` does not consume `VehicleRichMapper`, so
the two S-04 reminder goldens are genuinely untouched.

## Findings

### F1 — usersByLogin cache pollution survives the test rollback

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 §2, Phase 3 §1
- **Detail**: Phase 1 fixes one direction only — a stale entry read *before* seeding. The other
  direction is unhandled: `ReportResource` calls `userService.getUserWithAuthoritiesOrFail()` →
  `findOneWithAuthoritiesByLogin` (`@Cacheable`, `UserRepository.java:39`), so each parity request
  *repopulates* `usersByLogin` with the seeded `admin=en` / `user=pl`. `AbstractSessionIT` is
  `@Transactional`, so the DB rolls back and the cache entry does not. AGENTS.md records that
  JSR-107 hands the same `CacheManager` to every Spring context in the JVM, so the stale `User`
  leaks past the class boundary. `ReportParityIT` is the first test that both mutates `langKey` and
  authenticates — `GoldenDatasetMirrorIT` seeds but issues no request.
- **Fix A ⭐ Recommended**: Expose `SessionFixtures.evictGoldenOwnerCaches()` and call it from both
  `seedGoldenDataset()` and an `@AfterTransaction` hook on `ReportParityIT`.
  - Strength: Symmetric with `UserService.clearUserCaches`, and the after-hook runs outside the
    rolled-back transaction so the next reader repopulates from real DB state.
  - Tradeoff: Every future consumer must remember the after-hook; the seed method alone is no longer
    sufficient.
  - Confidence: HIGH — the mechanism is directly verified in the code.
  - Blind spot: Whether any current IT actually asserts admin/user `langKey` was not enumerated; the
    leak may be latent today.
- **Fix B**: Put the eviction on `AbstractSessionIT` as an `@AfterTransaction` that clears
  `usersByLogin` unconditionally.
  - Strength: No consumer can forget it; protects S-04's suite too.
  - Tradeoff: Touches the shared harness for one slice's problem, and costs a cache miss on every
    session IT.
  - Confidence: MEDIUM — broader blast radius across 177 existing ITs.
  - Blind spot: Interaction with the F-04 idempotent-`createCache` fix under the second
    (`@MockBean MailService`) context.
- **Decision**: FIXED (Fix A)

### F3 — Phase 4's inertness proof does not cover /api/events

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 4 §1, Key Discoveries
- **Detail**: The plan justifies Phase 4 with "`VehicleRichDto` is never serialized over REST … so
  Phase 4 has no wire-visible effect." The DTO isn't serialized, but its iteration order propagates:
  `EventServiceImpl.java:40-45` maps through `VehicleRichMapper` and feeds `EventResource`
  `/api/events`, which is serialized. `ForthcomingEvent.compareTo` (`:31-38`) returns 0 for equal
  `dateThru` whenever either `mileageThru` is 0 — and `findForthcomingInsurances` always builds
  `.mileageThru(0)` — so ties are broken by the stable sort, i.e. by exactly the collection order
  Phase 4 changes. Phase 3's suite asserts reports and statistics only, so it cannot be the
  inertness proof the plan claims for this consumer.
- **Fix A ⭐ Recommended**: Add an `/api/events` ordering assertion to Phase 4's Automated
  Verification, seeded from the golden fixture.
  - Strength: Turns the claim into a check; the fixture already contains three 2026-04-18 events on
    `vehicle:en-primary`, which is the exact tie shape at risk.
  - Tradeoff: One more test to write in a phase framed as verification-free.
  - Confidence: HIGH — the tie condition is visible in the comparator.
  - Blind spot: Whether the three same-date events land in one type bucket (they don't today)
    determines if the tie is reachable now.
- **Fix B**: Keep Phase 4 as-is, correct the Key Discovery, and record the `/api/events` ordering
  shift as an accepted risk alongside the `VehicleRepository` note.
  - Strength: Zero added scope; consistent with D3's "document hazard 1".
  - Tradeoff: Ships an ordering change to a live endpoint with no assertion behind it.
  - Confidence: MEDIUM — low practical risk, but unverified.
  - Blind spot: Client 1.2.5's tolerance for `/api/events` reordering.
- **Decision**: FIXED (Fix A)

### F4 — Narrowing to `vehicle:` does not make normalizeRawHandles safe

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Key Discoveries, Phase 1 §1
- **Detail**: The plan states narrowing the map is "a correctness fix on both paths". On the raw
  path it is not. `normalizeRawHandles` (`GoldenReference.java:284-292`) does
  `rawBody.replace(Long.toString(id), handle)` with no delimiter. The capture was safe because
  `golden-dataset.sql` used `900000+` ids. The mirror fixture uses H2 identity ids — research
  observed `vehicle:en-primary=1`. Replacing `"1"` across a raw body corrupts `"2026-03-01"`,
  `"error.http.500"`, and the problem-document `path`. Narrowing removes a small hazard and leaves a
  much larger one. It is inert today only because Phase 3's divergence test asserts fields directly
  instead of routing `consumption-period-zero.json` through `compareJson` — a fact the plan never
  states.
- **Fix A ⭐ Recommended**: In Phase 1, make the raw replace delimiter-aware (regex with non-digit
  boundaries) and correct the Key Discovery.
  - Strength: Fixes the class of defect while the file is already open; the next golden with a
    textual body cannot silently corrupt.
  - Tradeoff: Slightly widens Phase 1 beyond validation scoping.
  - Confidence: HIGH — the failure is reproducible by inspection.
  - Blind spot: Whether the captured `900000+` substitutions in the committed golden still
    round-trip under a boundary regex.
- **Fix B**: Leave the method alone; correct the Key Discovery and state explicitly that the raw
  path is unusable with generated ids.
  - Strength: Minimal edit; matches the slice's "don't fix what isn't asserted" character.
  - Tradeoff: Leaves a loaded gun for whoever adds the next textual golden — the exact seam this
    slice exists to close.
  - Confidence: HIGH — documentation-only, no behavior risk.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F2 — The divergence test cannot read the golden's status

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §2
- **Detail**: Phase 3 §2 requires the test to assert "the loaded golden's status is 500".
  `GoldenReference` exposes only `resourceName()` plus the comparison methods; `expected` is private
  with no accessor (`GoldenReference.java:41-66`). Phase 1's contract explicitly freezes the public
  signatures and adds no getter, so as written the phase is not implementable without an unplanned
  edit.
- **Fix**: Add a `status()` accessor to `GoldenReference` in Phase 1 (it belongs with the other
  harness repair), and say so in Phase 1 §1's contract.
- **Decision**: FIXED (Fix in plan)

### F5 — Phase 3 misattributes what fixes cost-en.json's array order

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1 (request-matrix note)
- **Detail**: Phase 3 says the request's `en-primary, pl-primary, zero-consumption` order "is
  load-bearing … and must not be reordered." It isn't. `StatisticServiceImpl.calculate` (`:66-73`)
  passes the ids straight into `findAllByIdAndOwnerIsCurrentUser`
  (`… where vehicle.id in :id`) and iterates the *query result*; the request list order never
  reaches the output. Phase 4 §3 states this correctly, so the plan contradicts itself and points
  the implementer at the wrong fragility. The golden holds two entries — `en-primary`,
  `zero-consumption` — since pl is filtered as unowned.
- **Fix**: Reword Phase 3 to say the golden's array is `[en-primary, zero-consumption]`, fixed by DB
  insertion order per Phase 4 §3, not by the request list.
- **Decision**: FIXED (Fix in plan)
