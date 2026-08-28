<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Golden Baseline Capture

- **Plan**: `context/changes/golden-baseline-capture/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-27
- **Verdict**: RETHINK at review time → **SOUND** after triage (all 8 findings fixed 2026-08-27)
- **Findings**: 3 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | FAIL |
| Plan Completeness | WARNING |

## Grounding

5/5 paths ✓, 6/6 symbols ✓, 5/5 doc references ✓, brief↔plan ✓, Progress↔Phase ✓ (34/34 criteria mapped).
`docs/reference/contract-surfaces.md` does not exist — contract-surface check skipped.
`context/foundation/lessons.md` does not exist — no accepted-rule priors applied.

## Findings

### F1 — Golden references embed capture-side ids HEAD can never reproduce

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Phase 3 §2, Phase 4 §2, Phase 5 §2
- **Detail**: Every statistics response embeds `periodVehicle.vehicleId` (`PeriodVehicle.java:12`, reached via `CostResult`, `MileageResult`, `AverageConsumptionResult`). Phase 4's reminder reference stores `vehicle id, event id` and *sorts by them*. Requests are keyed by vehicleId too. Capture-side ids come from `golden-dataset.sql`'s explicit high-range primary keys; HEAD-side rows come from `SessionFixtures` builders doing ordinary `repository.save` with JPA-generated ids off a shared H2 sequence, whose values depend on how many rows earlier ITs in the same context created. `GoldenReference` is specified as "exact on the fixed-precision string form", with no id normalisation anywhere in the plan. The desired end state — "S-03 can load the reference and compare live HEAD output at value level without transcribing a single number by hand" — is therefore not reachable: every stats body mismatches on vehicleId, and the reminder sort order is itself capture-id-dependent.
- **Fix A ⭐ Recommended**: Symbolic handles instead of raw ids — golden files store `vehicle:en-primary`, `refuel:zero-volume` etc.; capture writes the id→handle map, and the golden seeding method returns a handle→id map that `GoldenReference` applies before comparing. Sort keys use handles.
  - Strength: Ordering stable across both runtimes; makes `reference.md` readable by hand, which Phase 2's inventory already wants.
  - Tradeoff: Adds a mapping concept to the file format and the comparison API; Phase 3 and 4 capture scripts must emit it.
  - Confidence: HIGH — the id dependence is verified in the DTOs, and every surface carrying an id has a natural fixture name.
  - Blind spot: Whether any XLSX cell carries a raw id (the report name is the license plate, not the id) — check during Phase 3.
- **Fix B**: Force HEAD-side ids to equal capture-side ids by inserting with explicit ids under H2.
  - Strength: Golden files stay literal; comparison stays plain equality.
  - Tradeoff: Requires bypassing `@GeneratedValue` (native SQL or a sequence reset) and collides with rows earlier ITs already allocated in the shared context; `SessionFixtures` stops being a builder for this path.
  - Confidence: MEDIUM — workable, but the shared-context constraint the plan itself calls "hard" makes id pinning fragile.
  - Blind spot: Interaction with `TestUserIdentitySequenceFixup` and H2 2.x identity behaviour is unverified.
- **Decision**: FIXED via Fix A — symbolic handles adopted end to end: Phase 2 §1 declares a handle per row and §2's inventory carries the handle→id map (criterion 2.7); Phase 3 §2 normalises entity ids to handles before storage (criterion 3.5); Phase 4 §1 sorts and §2 stores by handle; Phase 5 §2 makes `GoldenReference` handle-resolving, §3 has golden seeding return the runtime handle→id map, §4 asserts the map is complete (criterion 5.6); Desired End State restated in handle terms

### F2 — Full-path reminder capture is clock-bound; criteria 4.5 and 4.6 cannot both hold

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 4 §1, criteria 4.5 / 4.6 / 4.9
- **Detail**: `ReminderServiceImpl.sendReminders()` derives its window from `LocalDate.now()` (`ReminderServiceImpl.java:45`) with no injectable Clock — the plan's own Key Discoveries say so. The fixture's dates must be absolute, because the same rows feed the XLSX report cells captured in Phase 3. Therefore the full-path run agrees with the typed-seam run only on the single calendar day where the reference date equals the capture date. Criterion 4.5 ("agree entry-for-entry"), 4.6 ("re-running produces byte-identical files") and 4.9 ("reference.md alone reproduces every golden file") are all false from the next day onward. The plan states neither the constraint nor a clock-faking mechanism — Phase 2 picks the reference date on purely aesthetic grounds.
- **Fix A ⭐ Recommended**: Fake the clock for the full-path run (libfaketime on the WAR JVM, or the container's date) pinned to the reference date, recorded verbatim in `reference.md`.
  - Strength: Keeps 4.5/4.6/4.9 honest and keeps the advance-derivation step genuinely covered, which is the only reason the full-path run exists.
  - Tradeoff: One more environment dependency in the capture procedure, on the one artefact that must be reproducible from prose.
  - Confidence: HIGH — the fixture is already committed to absolute dates, so the clock is the only remaining variable.
  - Blind spot: Whether a faked date upsets Liquibase or JWT issuance during the same boot — cheap to check in Phase 1.
- **Fix B**: Demote the full-path run to a non-committed structural check; commit only the typed-seam file and record the full-path result as prose.
  - Strength: No clock machinery; the committed file is fully deterministic.
  - Tradeoff: Loses byte-stable evidence for the advance-derivation step — the exact thing the second capture path was added to cover; 4.5 becomes a one-time observation.
  - Confidence: HIGH — trivially achievable.
  - Blind spot: S-04 then has no reference for the full path.
- **Decision**: FIXED via Fix A — Phase 4 §1 now requires the JVM date pinned to the reference date (libfaketime or container date), recorded in `reference.md`; Phase 1 §1 establishes the mechanism and confirms it does not disturb Liquibase or JWT issuance; criterion 4.6 and Progress 4.6 now test reproduction on a different calendar day

### F3 — The zero-mileage vehicle poisons the whole consumption capture

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 §1 (dataset contract) + Phase 3 criterion 3.1
- **Detail**: Verified by `git show 6e19b96:...AverageConsumptionResult.java`: at the baseline commit `getAverageConsumption()` has no `mileage == 0` guard — that guard is HEAD-only, added by `4ad88bd`. At the baseline, mileage 0 gives `BigDecimal.valueOf(Infinity)`, which throws `NumberFormatException` during Jackson serialization. Phase 2 mandates "a vehicle with exactly 1 in-range refuel (empty per-refuel list, zero-mileage per-period)" in the same dataset Phase 3 drives `POST /api/stats/consumption/per-period` over. If that vehicle is in the same request's vehicle list, the entire response fails and there is no per-vehicle reference to capture — the good vehicles' figures are lost with it. Compounding it, criterion 3.1 pre-commits to "200, or 404 for the two unowned-id cases", declaring a 5xx impossible in advance for a capture whose job is to record what the baseline actually does.
- **Fix**: Capture the zero-mileage case in its own single-vehicle request, kept out of the main consumption calls, and reword 3.1 to "each call returns the status recorded in `reference.md` — including any 5xx, which is captured behaviour, not a capture failure."
  - Strength: Preserves both the healthy figures and the zero-mileage evidence S-03 needs to adjudicate its deferred decision.
  - Tradeoff: One extra golden file per affected endpoint.
  - Confidence: HIGH — the throw is verified in the baseline source.
  - Blind spot: Whether `per-refuel` fails the same way, or only `per-period` — confirm during Phase 3.
- **Decision**: FIXED — Phase 2 §1 now confines the zero-mileage vehicle to its own single-vehicle request with the `6e19b96` throw explained; Phase 3 §1 excludes it from the main consumption lists; criterion 3.1 and Progress 3.1 now record the observed status, 5xx included

### F4 — "A value difference means a migration difference" is already false

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Current State Analysis; Phase 5 §4 Intent
- **Detail**: Commit `4ad88bd` ("fix(session-parity): fix four pre-existing 500s") changed five `src/main` files after the baseline: `AverageConsumptionResult` (zero-mileage guard), `InsuranceTypeDto` (delegating `@JsonCreator`), `EventServiceImpl` (duplicate-vehicleId merge `(first, ignored) -> first`), and `FuelTypeMapper` / `InsuranceTypeMapper` / `ExceptionTranslator` (`InvalidLookupTypeException` → 400 instead of 500). Phase 5 §4's stated purpose is "so S-03 and S-04 can trust that a value difference means a migration difference and not a dataset difference." That trichotomy is missing a third cause: an intentional post-baseline fix. The plan names only two of the five as deferred judgements, and AGENTS.md's "byte-identical between `3e91ed4` and HEAD" claim is now stale.
- **Fix**: Add an "Expected divergences at HEAD" section to `reference.md` enumerating all five `4ad88bd` changes and the captured surfaces each affects; correct Phase 5 §4's Intent to "a migration difference, a dataset difference, or a listed intentional divergence"; and refresh the AGENTS.md byte-identical claim.
  - Strength: Stops S-03 from chasing deliberate fixes as regressions — the most likely way this baseline wastes a session.
  - Tradeoff: None; a documentation addition inside an artefact the plan already writes.
  - Confidence: HIGH — verified against the commit diff.
  - Blind spot: Whether any commit between `6e19b96` and `4ad88bd` also touched `src/main` — one `git log --oneline 6e19b96..HEAD -- src/main` during Phase 1 settles it.
- **Decision**: FIXED — Current State Analysis gains a bullet naming all five `4ad88bd` files and the stale `AGENTS.md` claim; Phase 5 §4 Intent reworded to three causes; Phase 5 §5 now requires an "Expected divergences at HEAD" section plus the `AGENTS.md` correction, gated by new criterion 5.10

### F5 — Golden-seeding guard contradicts `AbstractSessionIT`'s rollback

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 5 §3 Contract
- **Detail**: `AbstractSessionIT` is `@Transactional` (`AbstractSessionIT.java:23`), so rows a test creates are rolled back. `SessionFixtures` is a singleton shared across the whole context. Phase 5 §3 requires golden seeding to be "guarded so a second invocation in the same shared context does not duplicate rows" — but a boolean guard on the singleton survives the rollback that removed the rows, so the second golden test in the run sees an empty database and a guard reporting "already seeded". The alternative — committing outside the test transaction, as `seedLookupTypes()` does from `run(...)` — is exactly the leak criterion 5.8 forbids.
- **Fix A ⭐ Recommended**: Drop the guard; seed inside each test's transaction and let rollback clean up.
  - Strength: Idempotence is unnecessary when nothing survives the test; removes the contradiction rather than patching it, and matches how every other per-test builder already behaves.
  - Tradeoff: Re-seeds per test — negligible at this dataset size.
  - Confidence: HIGH — follows directly from `@Transactional` on the base class.
  - Blind spot: A future non-transactional golden test would need its own cleanup.
- **Fix B**: Make the guard a database query (`existsById` on a golden handle) rather than a boolean flag.
  - Strength: Survives both cases correctly because it reflects real state.
  - Tradeoff: Keeps a guard whose only purpose is a scenario that cannot arise under the current base class.
  - Confidence: MEDIUM — correct, but complexity without a caller.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — Phase 5 §3 now specifies no idempotence guard, with the `@Transactional` rollback reasoning stated; each test re-seeds inside its own transaction

### F6 — Sorting every sheet's rows discards report row order, which is itself captured behaviour

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 §2 Contract; Phase 5 §1 Contract
- **Detail**: Phase 3 stores XLSX with "rows sorted by a stable composite key before storage, neutralising the missing `ORDER BY`", applied to all sheets. But only the cost report is affected by that missing `ORDER BY`: `generateCostReport` iterates `findAllByIdAndOwnerIsCurrentUser` (`VehicleRepository.java:22-23`, unordered). `generateVehicleReport` uses `findByIdAndOwnerIsCurrentUser` — a single vehicle — and its sheets are a laid-out form: header rows, section labels, then event rows whose order comes from the mapped collections. Sorting those discards the layout and discards any genuine row-order regression, e.g. Hibernate 6 changing collection iteration order for the event lists — precisely a migration risk this baseline exists to catch.
- **Fix**: Sort only where the source query is genuinely unordered — the cost report's per-vehicle rows — and store every other sheet in natural order. Record in `reference.md` which sheets are sorted and why.
  - Strength: Preserves order as a comparable signal where it is deterministic; still neutralises the one real source of run-to-run instability.
  - Tradeoff: The extractor needs a per-sheet policy rather than one global rule.
  - Confidence: HIGH — the two repository methods are verified.
  - Blind spot: Whether `VehicleRichMapper`'s event collections are `List` or `Set` — if `Set`, the vehicle report may be unordered too and would need sorting after all. Confirm before Phase 3.
- **Decision**: FIXED — Phase 3 §2 and Phase 5 §1 now define a per-sheet sort policy: only the cost report's per-vehicle rows are sorted, every other sheet keeps natural order, and `reference.md` records which are sorted and why

### F7 — Phase 1 criterion 1.5 is unsatisfiable as written

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, criterion 1.5
- **Detail**: "`git status --porcelain` shows only the new `reference.md`" — the change folder is untracked, so porcelain reports a single `?? context/changes/golden-baseline-capture/` entry covering plan.md, plan-brief.md, research.md and change.md. Confirmed against the current working tree.
- **Fix**: Reword to "`git status --porcelain` reports no change under `src/` or `pom.xml`, and `git worktree list` shows only the intended capture worktree."
- **Decision**: FIXED — criterion 1.5 rescoped to `src/`/`pom.xml` plus `git worktree list`; Progress item 1.5 reworded to match

### F8 — Header comparison crosses a real-HTTP / MockMvc boundary

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1 Contract; Phase 5 §2 Contract
- **Detail**: Capture is over real HTTP against a booted WAR; consumption at HEAD is through MockMvc (`AbstractSessionIT`). `GoldenReference` compares `Content-Type`, `Content-Disposition`, `Cache-Control`, `X-Total-Count` alongside the body. The four headers `ReportResource.prepareResponse` sets explicitly will match, but container-supplied headers (`Content-Length`, `Transfer-Encoding`, `Date`) exist on one side only.
- **Fix**: Scope metadata comparison to status plus the headers the controller sets explicitly, and state in `reference.md` that container-supplied headers are excluded and why.
- **Decision**: FIXED — Phase 3 §1 and Phase 5 §2 now scope metadata comparison to status plus controller-set headers, with the container-header exclusion and its reason recorded in `reference.md`
