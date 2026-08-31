<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Vehicle Image Write Path (S-02 / S-04 / S-05)

- **Plan**: `context/changes/image-path-containment/plan.md`
- **Mode**: Deep
- **Date**: 2026-08-31
- **Verdict**: REVISE → **SOUND** after triage (all 6 findings fixed 2026-08-31)
- **Findings**: 0 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

11/11 paths ✓, 9/9 symbols & line refs ✓, brief↔plan ✓.
`Tika#detect(byte[])` confirmed present in tika-core 2.7.0 (`javap`). Production volume
measurement re-checked against `context/changes/security-baseline/oq-resolution.md:52-55,133` —
all nine files are PNG/JPEG by content, the four `.bin` are PNG. Every code claim spot-checked
(`prepareImagePath:68-71`, `updateVehicle:76`, `editVehicle:56-61`,
`AdminVehicleServiceImpl:122-141` including the "it *does* check the boolean" correction,
`ExceptionTranslator:124-129`, `SessionFixtures.imageFor:159`, `AdminVehiclePurgeIT:145-148`,
`VehicleDetailsMapper:34-35,51-52`) is accurate. `Progress` section parses cleanly: one `## Progress`
heading, every phase matched, every success-criteria bullet numbered, no stray checkboxes in phase
bodies.

Findings below are in the seams, not the substance.

## Findings

### F1 — Temp-dir design multiplies Spring contexts, not adds one

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 §1 "Per-class temp data directory"; Performance Considerations
- **Detail**: Phase 1 specifies "a static `@TempDir` field plus a `@DynamicPropertySource` method
  registering `application.data-directory.location` as its absolute path". A static `@TempDir` is
  resolved per *concrete* test class, so each subclass registers a different property value — a
  different `MergedContextConfiguration` cache key, hence a separate context. By Phase 3 there are
  three subclasses (`VehicleImageIT`, `VehicleImageRollbackIT`, and `AdminVehiclePurgeIT` once
  re-pointed), and `VehicleImageRollbackIT`'s `@SpyBean` forks the cache key again anyway. The
  Performance section claims "one context startup"; the design as written buys three, on a suite
  the plan itself measures at ~45s.
- **Fix A ⭐ Recommended**: One JVM-wide scratch root, resolved once
  - Strength: A single static root (`Files.createTempDirectory` in a static initializer,
    per-class subdirectories underneath) keeps the registered property value identical across all
    subclasses, so they share one context — while still satisfying the roadmap's "scratch
    directory, never the production volume" note by construction.
  - Tradeoff: JUnit no longer auto-deletes it; needs a shutdown hook or `deleteOnExit`, which
    weakens manual criterion 1.5.
  - Confidence: HIGH — the context cache keys on resolved property values; differing values cannot
    share a context.
  - Blind spot: `VehicleImageRollbackIT`'s `@SpyBean` still forks one context regardless; this
    caps the total at two, not one.
- **Fix B**: Keep per-class `@TempDir`, correct the Performance section
  - Strength: Strongest possible isolation; no cleanup story to invent.
  - Tradeoff: Pays three context startups; the stated cost in the plan has to be rewritten and
    re-accepted by the owner.
  - Confidence: HIGH — behaviour is well understood either way.
  - Blind spot: Actual per-context startup cost here is unmeasured.
- **Decision**: FIXED — via Fix A (shared JVM-wide scratch root; Performance section corrected)

### F2 — Phase 1's `data/` containment check cannot fail

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 Success Criteria / Progress 1.4
- **Detail**: Criterion 1.4 is "`git status --porcelain data/` is empty and `ls data/` is
  unchanged". `.gitignore:5` holds `/data/**`, so `git status --porcelain data/` is empty no matter
  what the suite writes there. The one criterion guarding the whole point of Phase 1 — that no test
  touches the shared directory — is half inert, and the surviving half ("`ls data/` is unchanged")
  has no recorded baseline. `data/` is empty right now.
- **Fix**: Replace with an assertion that actually observes the tree — capture
  `find data -type f | wc -l` before and after `./mvnw verify` and require both to be 0. Note in
  the criterion that `data/` is gitignored so git-based checks do not apply.
- **Decision**: FIXED — criterion 1.4 now reads the filesystem (`find data -type f`), with the gitignore caveat recorded

### F3 — Phase 4's rollback story names a callback that is never registered

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Phase 4 §3 "Rejection surfaces as a 400"; Desired End State bullet 1
- **Detail**: Phase 4 §3 states "the throw rolls the transaction back before any row is written —
  and the Phase 2 callback then removes the new file." In `editVehicle:58` the mapper — and
  therefore `save()` — is evaluated *before* `updateVehicle` runs, and `updateVehicle` is the only
  place Phase 2 registers the synchronization. On a Phase 4 rejection the callback is never
  registered. No orphan actually results (every Phase 4 rejection precedes
  `writeByteArrayToFile`), but the stated mechanism is wrong and an implementer may try to write a
  test for it.
  The same seam exposes a real gap: `addVehicle` registers no callback at all, so a rollback
  *after* a successful image write on create — a constraint violation at flush, say — still orphans
  the new file. Phase 2's scope argument ("addVehicle has no old file, so editVehicle is the entire
  scope") is correct for FR-004, but the plan voluntarily added rollback-orphan cleanup and then
  applied it to only one of the two writing paths.
- **Fix A ⭐ Recommended**: Correct the rationale; leave the scope asymmetry, stated
  - Strength: Keeps Phase 2 minimal and honest. Replace the wrong sentence with "no file is
    written on a Phase 4 rejection, so no cleanup is needed", and add a line to "What We're NOT
    Doing" recording that create-path rollback orphans are out of scope and why — matching the
    existing "Not cleaning up pre-existing orphaned files" entry.
  - Tradeoff: A known orphan class ships unfixed; discoverable later as a surprise.
  - Confidence: HIGH — verified by reading `editVehicle:56-61` and the `save()` contract;
    rejections all precede the byte write.
  - Blind spot: How often `addVehicle` actually rolls back post-write is unmeasured.
- **Fix B**: Extract the synchronization into a helper and register it in `addVehicle` too
  - Strength: Closes the orphan class symmetrically; the rollback branch is already being written,
    so the marginal cost is small.
  - Tradeoff: Widens Phase 2 beyond FR-004 a second time and adds a second IT; `addVehicle` has no
    old-file branch, so the shared helper needs a null-old-file mode.
  - Confidence: MEDIUM — straightforward, but the plan's own framing calls the rollback cleanup
    "self-contained and droppable", and this makes it less so.
  - Blind spot: Not verified whether any current IT would start failing on the new create-path
    cleanup.
- **Decision**: FIXED — via Fix A (rationale corrected; create-path rollback orphans added to "What We're NOT Doing")

### F4 — Three either/or decisions left to the implementer

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1, §3; Phase 4 §1
- **Detail**:
  (a) Phase 3 §1: the containment exception goes in "`service/impl` or `service/exception`". Every
  existing unchecked service exception lives in `service/exception/` (8 of them).
  (b) Phase 3 §3: `AdminVehiclePurgeIT` gets the shared helper "either by extending it or by moving
  the helper somewhere both can reach". These are not equivalent — only *extending*
  `AbstractImageIT` moves that IT off the shared `data/` directory, which is what F2's criterion is
  trying to prove. Moving the helper alone leaves it writing to `data/`.
  (c) Phase 4 §1 lists what `save()` drops (`forName`, the `MimeTypeException` catch) and what
  stays (the `IOException` catch, the null guard) but is silent on the Phase-3 containment catch
  added one phase earlier.
- **Fix**: Pin all three — `service/exception/` for (a), "extends `AbstractImageIT`" for (b), and
  an explicit "the Phase 3 containment catch stays" for (c).
- **Decision**: FIXED — all three pinned: `service/exception/`, "extends `AbstractImageIT`", containment catch stays

### F5 — Verification-command hygiene in Phases 1, 3 and 4

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Progress 1.3, 3.3, 4.4
- **Detail**:
  - 4.4: "`git diff --stat` shows no change to `VehicleDetailsMapper` lines 34-35" — `--stat`
    reports file-level counts only and cannot express a line range.
  - 3.3: the grep is well-formed and currently returns exactly the two expected hits, but the
    stated expected output ("only the service and the test base class") won't hold —
    `AbstractImageIT`'s helper resolves against the `@TempDir`, not
    `getDataDirectory().getLocation()`, so the correct post-condition is "only the service".
  - 1.3: "none skipped" sits awkwardly against the documented intentionally `@Disabled` test in
    `WebConfigurerTest` (AGENTS.md).
  Both the Phase 3 and Phase 4 greps were run against HEAD: each returns hits today and will return
  the documented result after the change, so the commands themselves are sound.
- **Fix**: Use `git diff -- src/.../VehicleDetailsMapper.java` and eyeball, or drop 4.4 to a manual
  criterion; correct 3.3's expected output; scope 1.3's "none skipped" to the integration count.
- **Decision**: FIXED — criteria 1.3, 3.3 and 4.4 corrected

### F6 — Rollback IT's spy collides with its own fixture setup

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 §2 "Rollback proof"
- **Detail**: The IT stubs `VehicleRepository.save` to throw, but `SessionFixtures`' `vehicleFor`,
  `imageFor` and `archive` all call the same method to build the fixture, and the try/finally
  cleanup runs after. The stub must be installed after setup and reset before cleanup, or the test
  fails in setup rather than at the point under test.
- **Fix**: State in the contract that the `doThrow` stub is installed after fixture setup and
  `Mockito.reset`'d in the finally block, before `purgeRowsFor`.
- **Decision**: FIXED — stub install/reset ordering added to the Phase 2 §2 contract

## Confirmed, not findings

The FR-007 risk I went looking for is not present: `oq-resolution.md:52-55,133` shows all nine
production files are PNG/JPEG *by content*, with the four `.bin` being PNG, so Phase 4's mismatch
rule cannot brick edits on any existing vehicle. The "octet-stream is treated as no claim" branch
is exactly what keeps those four editable.
