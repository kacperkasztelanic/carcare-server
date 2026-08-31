<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Vehicle Image Write Path Implementation Plan

- **Plan**: `context/changes/image-path-containment/plan.md`
- **Scope**: Full plan, phases 1–5
- **Date**: 2026-08-31
- **Verdict**: APPROVED
- **Findings**: 0 critical, 4 warnings, 3 observations (all resolved)

## Verification

The exact plan commands could not run in this sandbox because Mockito/Byte Buddy was unable to
self-attach to the JVM. Re-running with the repository's local Byte Buddy agent succeeded:

- Unit tests: 69 run, 0 failures, 0 errors, 1 intentional skip
- Integration tests: 264 run, 0 failures, 0 errors, 0 skips
- `data/` contained no files after the run
- `git diff --check` passed; the worktree contains the intentional implementation-review fixes

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Write failures can delete a valid previous image

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:50`; `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:108`
- **Detail**: `save()` converted an `IOException` into the `""` sentinel. During an edit, the transaction could commit that empty image value while the completion callback deleted the old valid image. A partially written replacement could also remain on disk.
- **Fix**: `save()` now writes with `CREATE_NEW`, removes the generated path best-effort after an `IOException`, and throws `ImageStorageException` so the enclosing transaction rolls back.
  - Strength: Preserves the old image and avoids silent data loss.
  - Tradeoff: Changes the current sentinel and HTTP error behavior; operational write failures now surface as server errors.
  - Confidence: HIGH — the failure now propagates as an unchecked exception and the focused test covers the failure contract.
  - Blind spot: The test uses a non-directory data-root failure rather than injecting a mid-stream disk failure.
- **Decision**: FIXED — failed writes now roll back instead of committing an empty image sentinel.

### F2 — Containment is lexical and follows symlinks

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java:137`
- **Detail**: `normalize()` plus `startsWith(root)` rejected traversal strings, but file operations could still follow symlinks beneath the data directory. A locally planted symlink could redirect reads, writes, or deletes outside the root.
- **Fix**: `prepareImagePath()` now rejects every symlinked child component; reads also use no-follow checks and writes use `CREATE_NEW`, while the configured root itself remains allowed to be a deployment symlink.
  - Strength: Makes the containment guarantee filesystem-aware without rejecting a symlink used as the configured root.
  - Tradeoff: Adds path-component checks and rejects intentional symlinks inside the data directory.
  - Confidence: MEDIUM-HIGH — the focused test verifies load/delete refusal and protects the outside target.
  - Blind spot: A hostile process racing path validation and file access is outside this fix's scope.
- **Decision**: FIXED — symlinked child paths are now refused.

### F3 — Planned GET round-trip coverage is missing

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleImageIT.java:44`; `context/changes/image-path-containment/plan.md:234`
- **Detail**: The test validated image bytes in the POST response but did not call `GET /api/vehicle/{id}`, although the plan requires GET coverage. The test's `201` expectation is correct for the existing resource; the plan's `200` is stale.
- **Fix**: Added a GET request and asserted the same bytes and content type.
- **Decision**: FIXED — GET round-trip coverage added.

### F4 — FR-007/client compatibility claims lack reproducible evidence

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Success Criteria
- **Location**: `context/changes/image-path-containment/plan.md:518`; `context/changes/image-path-containment/plan.md:692`
- **Detail**: The plan marked production-volume and client 1.2.5 checks complete, but the repository lacked a repeatable test for all nine legacy names. The existing production inventory and client baseline are recorded in `context/changes/security-baseline/oq-resolution.md`.
- **Fix**: Added `VehicleImageCompatibilityIT`, which exercises all nine documented filenames—including the four `.bin` names—through REST with byte-for-byte round-trips and unchanged filename-derived content types, using anonymized fixtures instead of production photos.
  - Strength: Makes the all-nine server-side compatibility claim repeatable without importing personal production images.
  - Tradeoff: A browser-level visual sweep of all nine production bytes is still outside the repository test.
  - Confidence: HIGH — the test covers every documented name and the manual evidence file is already tracked.
  - Blind spot: The fixture uses format-valid stand-ins for the five non-placeholder production images.
- **Decision**: FIXED — repeatable compatibility coverage added and existing manual evidence linked.

### F5 — Context-sharing progress criterion is too strong

- **Severity**: 🔎 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `context/changes/image-path-containment/plan.md:655`
- **Detail**: The progress checklist claimed every image IT shares one Spring context, but the rollback test's `@SpyBean` necessarily forks one. The plan's performance section already acknowledges one additional fork.
- **Fix**: Changed the phase verification text to describe one shared context for non-`@SpyBean` tests plus one expected rollback-test fork.
- **Decision**: FIXED — plan criteria now match the observed context behavior.

### F6 — Unknown transaction status deletes the replacement

- **Severity**: 🔎 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java:108`
- **Detail**: `afterCompletion()` treated every non-committed status, including `STATUS_UNKNOWN`, as rollback and deleted the replacement. An uncertain outcome could leave a committed row pointing at a missing file.
- **Fix**: Added explicit committed and rolled-back branches; unknown statuses are logged and preserve both files.
- **Decision**: FIXED — transaction cleanup now handles unknown outcomes conservatively.

### F7 — Exception constructor shape differs from the plan

- **Severity**: 🔎 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/main/java/com/kasztelanic/carcare/service/exception/UnsupportedImageFormatException.java:11`; `context/changes/image-path-containment/plan.md:468`
- **Detail**: The plan previously specified a single constructor, while the implementation has two overloads for the two rejection messages. Behavior is correct; this was low-risk contract drift.
- **Fix**: Updated the plan to document the two intentional overloads.
- **Decision**: FIXED — plan updated to match the implementation.
