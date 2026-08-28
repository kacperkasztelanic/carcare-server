<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-01 `session-parity`

- **Plan**: `context/changes/session-parity/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-26
- **Verdict**: REVISE → **SOUND** after triage (2026-08-26; all 7 findings fixed in the plan)
- **Findings**: 1 critical, 4 warnings, 2 observations

## Verdicts

| Dimension | Verdict (at review) | After triage |
|-----------|---------------------|--------------|
| End-State Alignment | FAIL | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | PASS | PASS |
| Blind Spots | WARNING | PASS |
| Plan Completeness | WARNING | PASS |

## Grounding

12/12 paths ✓, 14/14 line refs ✓, brief↔plan ✓, Progress↔Phase contract ✓ (7 phases, 26 steps, all
matched; no stray checkboxes in phase bodies).

Every `file:line` reference spot-checked was accurate, including the client-side suffix match
(`notification-middleware.ts:27-33`), the two `HeaderUtil` overload families, `VehicleServiceImpl:44/63`,
`VehicleRichMapper:64-80`, `Insurance.java:44`, `TestUtil.java:42`, and `application-test.yml:15,34`.
No `lessons.md` or `docs/reference/contract-surfaces.md` exists in this project — both checks skipped.

Codebase verification was performed inline rather than via a sub-agent (per the repo owner's global
default on subagent use).

## Findings

### F1 — Phase 4 promises an insurance round-trip no phase makes true

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Phase 4 §3 ↔ Phase 6 §1
- **Detail**: Phase 4 §3 states `InsuranceResourceIT` covers the POST-vs-PUT `insuranceType`
  asymmetry and that "if both shapes do not round-trip, the client's edit flow breaks." The asymmetry
  is confirmed at the client: `insurance.reducer.ts:183-190` wraps `insuranceType` into
  `{type, translation}` on create; `:192-198` does **not** on update — it spreads `...insurance` and
  trims only `insurer`/`number`/`details`. Both shapes land on the same server path:
  `InsuranceMapper.java:42` → `InsuranceTypeMapper.java:25-28`, which is the byte-for-byte twin of
  `FuelTypeMapper.java:25-28` — a null DTO NPEs, an unknown type throws `IllegalStateException`, both
  500 today. Phase 6 §1 fixes `FuelTypeMapper` only. So the plan promises an assertion, names the
  exact mapper family whose 500s it is fixing, and then leaves the one that S-01's own insurance tests
  will hit — with no `@Disabled` placeholder recording that it doesn't hold, unlike the vehicle-delete
  case which got exactly that treatment.
- **Fix A ⭐ Recommended**: Extend Phase 6 §1 to `InsuranceTypeMapper`
  - Strength: Same new `service/exception` type and the same `@ExceptionHandler` already being added
    for `fuelType` — ~5 extra lines, no new pattern. Consistent with the owner's stated decision to
    fix pre-existing 500s, and it makes Phase 4's insurance assertion satisfiable.
  - Tradeoff: Phase 6's title and the epilogue's "three 500s" become four; the brief's decision table
    needs the same edit.
  - Confidence: HIGH — both mappers read; they are identical in shape.
  - Blind spot: Whether PUT's bare-string `insuranceType` deserializes to 400 (Jackson mismatch) or
    null (NPE) is unverified — Phase 4 should assert whichever it is, not assume.
- **Fix B**: Demote Phase 4's insurance assertion to characterization
  - Strength: Preserves strict parity; matches the vehicle-delete precedent (`@Disabled` + named owner
    slice) exactly.
  - Tradeoff: Leaves a live 500 on the client's insurance edit flow that Phase 7's manual session will
    hit and fail on.
  - Confidence: MEDIUM — depends on whether the client actually sends a null/unmappable
    `insuranceType`, which could not be settled from `insurance-update.tsx:117` alone.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — Phase 6 §1 now covers `InsuranceTypeMapper` alongside
  `FuelTypeMapper` (one shared `service/exception` type); Phase 4 §3 writes the insurance round-trip
  as a characterization and Phase 6 §4 tightens it to a clean 200, with criterion 6.2 carving out that
  single expected change. "Three 500s" → "four" throughout the plan and brief. The unverified PUT
  deserialization layer is now an explicit *verify-before-writing-the-handler* instruction in Phase 6 §1.

### F2 — 18 unconditional `.trim()` sites are an unrecorded 500 class

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Key Discoveries §6, Phase 4 §4
- **Detail**: Every request-side mapper dereferences string fields without a null check —
  `VehicleMapper.java:37-39`, `VehicleDetailsMapper.java:42-48`, `InsuranceMapper.java:41-44`,
  `RefuelMapper.java:32`, `RepairMapper.java:32-33`, `InspectionMapper.java:33-34`,
  `RoutineServiceMapper.java:34,37`. Eighteen sites, all on S-01's create/update surface, all 500 on
  null. The plan discovered this (Key Discovery 6) but filed it as a test-harness note — "S-01 request
  bodies must not go through `TestUtil`" because `NON_EMPTY` strips empty strings and NPEs the mapper —
  which routes around a production defect rather than recording it. Separately, `ClientWireContractIT`
  invariant (a) asserts these fields are non-null in a GET *response*; the 500 is on the *request*
  side, so the invariant as written cannot catch the thing that motivated it. Mitigating: client 1.2.5
  largely protects itself — `updateVehicle` trims client-side (`vehicle.reducer.ts:169-179`),
  `createVehicle` omits `vehicleDetails` entirely so `VehicleMapper.java:41-45` defaults it, and the
  four non-insurance event types share one `prepareToDispatch` with AvField-backed strings. Latent,
  not live — except via insurance, which is F1.
- **Fix**: Record the class in Phase 7's epilogue with the `file:line` list and a named owner slice,
  the way the vehicle-delete 500 was handled. Do not fix it here — parity says leave it. Optionally
  restate `ClientWireContractIT` invariant (a) to say it pins the response direction only.
  - Strength: Keeps S-01 at parity while making the defect discoverable from the record instead of
    only from a test-harness footnote.
  - Tradeoff: The suite still won't catch a regression here.
  - Confidence: HIGH — all 18 sites enumerated and the client payload builders checked for all six
    resources.
  - Blind spot: None significant.
- **Decision**: FIXED — Key Discoveries now states the 18 sites as a production defect (with the
  client-side mitigations) rather than a `TestUtil` footnote; Phase 4 §4 restates invariant (a) as
  response-direction-only with an explicit pointer to the unfixed request side; Phase 7 §3 records the
  full `file:line` list and names the parked *Bean Validation on business request bodies* item
  (`roadmap.md:505-507`) as owner.

### F3 — Phase 6 §3's guard contradicts its own stated contract

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 6 §3
- **Detail**: The contract says "every non-zero input must produce a bit-identical result to today",
  but the guard is `mileage <= 0`. A negative mileage — an odometer corrected downward — is a non-zero
  input. Today `AverageConsumptionResult.java:20` computes a finite negative value and
  `BigDecimal.valueOf` succeeds; after the fix it returns `0.0`. Only `mileage == 0` produces the
  NaN/Infinity that throws.
- **Fix**: Guard on `mileage == 0` and let negative mileage keep its current behaviour, or widen the
  contract sentence to "every input with positive mileage" and record the negative case as a second
  deliberate change. Criterion 6.4's "bit-identical" unit test should include a negative-mileage case
  either way.
- **Decision**: FIXED — Phase 6 §3's guard narrowed to `mileage == 0` with the negative case called
  out explicitly; §4 and criterion 6.4 now require a negative-mileage case in the bit-identical test.

### F4 — Phase 3 never says what triggers fixture seeding

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1, Phase 3 §2
- **Detail**: `SessionFixtures` is specified as a `@Component` that "seeds the lookups idempotently",
  but nothing says what invokes seeding. The cited analogue,
  `TestUserIdentitySequenceFixup.java:21`, is an `ApplicationRunner` — it self-triggers at context
  start. A plain `@Component` does not. The implementer has to pick between a startup runner and a
  `@BeforeEach` on `AbstractSessionIT`, and the pick decides whether Phase 4's ~40–50 tests see a
  `fuel_types` row at all (`vehicles.fuel_type_id` is `NOT NULL`). The idempotency rationale given
  ("find-or-create, not `deleteAll` — another IT's data must survive") holds under neither candidate:
  Phase 4/5 ITs are class-level `@Transactional` and roll back, so no IT's data survives anyway. The
  one place it genuinely bites is the non-`@Transactional` delete-with-history test.
- **Fix**: Name the trigger explicitly (`@BeforeEach` on `AbstractSessionIT` is the smaller choice — it
  keeps seeding inside the rolled-back transaction), and restate the idempotency reason as the
  `NOT_SUPPORTED` test rather than cross-IT survival.
  - Strength: Removes the one guess that gates the largest phase.
  - Tradeoff: A `@BeforeEach` re-seeds per test; negligible at this size.
  - Confidence: HIGH — both candidate analogues and the `NOT NULL` constraint path were read.
  - Blind spot: None significant.
- **Decision**: FIXED (owner chose the `ApplicationRunner` trigger over the suggested `@BeforeEach`) —
  Phase 3 §1 now specifies `@Component @Profile("test") implements ApplicationRunner`, matching
  `TestUserIdentitySequenceFixup.java:21`, and notes that committing outside any test transaction is
  what makes the lookups visible to both the `@Transactional` ITs and the `NOT_SUPPORTED` delete test.
  The idempotency rationale is restated as multi-context re-seeding against a JVM-wide H2 rather than
  cross-IT survival. §3's smoke test now asserts the runner fired before re-seeding; criteria
  renumbered 3.1–3.5.

### F5 — Wire invariant (d) has unresolved ownership

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4 §4 (d), Phase 5 §3
- **Detail**: Phase 4 §4 lists the `Bearer ` prefix as invariant (d) and then says "see Phase 5, which
  may host this one instead" — an open TBD. Phase 5 §3's contract independently asserts the same
  thing. Criterion 4.1 counts `ClientWireContractIT` without settling whether it holds three
  invariants or four, and the Desired End State promises "the four wire invariants".
- **Fix**: Assign (d) to `JwtSessionIT` (it needs a real token anyway) and drop it from
  `ClientWireContractIT`, leaving three invariants there. Adjust the Desired End State wording to
  match.
- **Decision**: FIXED — invariant (d) assigned to `JwtSessionIT` (Phase 5 §3) and removed from
  `ClientWireContractIT`, which now states it holds three and points at the fourth; Desired End State
  and the brief both name the split.

### F6 — "Exactly one Spring context" is already false, and its rationale is stale

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Critical Implementation Details; criterion 3.3
- **Detail**: `TestConfigurationIT.java:17` is `@SpringBootTest` only; `AuditResourceIT` adds
  `@AutoConfigureMockMvc`. Those are different context cache keys, so the suite has at least two
  contexts today and criterion 3.3 is not checkable as written. Separately, the stated reason — a
  second context "re-enters the JCache territory F-04 fixed" — is stale: F-04 made `createCache()`
  idempotent precisely so multiple contexts are safe. The no-`@MockBean` / no-`@DirtiesContext`
  constraint is still good hygiene; only the justification and the criterion's wording need
  correcting.
- **Fix**: Reword 3.3 to "no new context configuration beyond the two that already exist" and drop the
  JCache rationale.
- **Decision**: FIXED — Critical Implementation Details now states the two-context baseline with its
  cause, replaces the JCache rationale with cost/shared-H2 hygiene, and notes that `AbstractSessionIT`
  fixes one configuration for the suite; the criterion (renumbered 3.4 by F4) reworded to match.

### F7 — Phase 2 restores `X-carcareApp-error` onto dead code

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §1; Phase 7 §2
- **Detail**: `HeaderUtil.java:78-84` — the three-arg `createFailureAlert` whose emitted names Phase 2
  restores to `X-carcareApp-error` — has zero callers (verified by grep across `src/`). The live error
  path is `ExceptionTranslator.java:99,107,121`, which uses the explicit-`applicationName` overload
  and emits `X-carcare-error`. That does not match the client's `endsWith('app-error')` at
  `notification-middleware.ts:57-63`, so no 400 ever produces a header-driven error toast. This is
  baseline behaviour and correctly out of S-01's scope — and harmless, because the client falls back
  to `data.message` (`notification-middleware.ts:80-81`), which is exactly why Phase 6's new
  `fuelType` 400 will still toast.
- **Fix**: One sentence in Phase 7 §2's `AGENTS.md` subsection noting that the error-alert prefix
  diverges from the alert prefix and the client degrades to `data.message` — otherwise the new
  documentation records a half-truth about the header contract.
- **Decision**: FIXED — Phase 7 §2's contract now requires recording that only the alert prefix is
  restored, that `X-carcareApp-error` sits on a zero-caller overload while the live path emits
  `X-carcare-error`, and that the client degrades to `data.message`.
