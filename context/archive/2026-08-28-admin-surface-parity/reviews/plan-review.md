<!-- PLAN-REVIEW-REPORT -->

# Plan Review: Admin Surface Parity

**Mode:** Deep
**Date:** 2026-08-28
**Plan:** `context/changes/admin-surface-parity/plan.md`
**Verdict:** SOUND

═══════════════════════════════════════════════════════════
  PLAN REVIEW: Admin Surface Parity
  Mode: Deep  |  Date: 2026-08-28
  Findings: 0 critical  3 warnings  0 observations
═══════════════════════════════════════════════════════════

## Dimension verdicts

| Dimension | Verdict | Rationale |
|---|---|---|
| End-State Alignment | PASS ✅ | F1 was fixed by making the four authorized API corrections explicit in the plan, roadmap, and research; the remaining S-02 behavior stays unchanged. |
| Lean Execution | PASS ✅ | Scope is narrow, phases are vertical, existing fixtures and full-context MockMvc patterns are reused, and production refactoring is excluded. |
| Architectural Fitness | PASS ✅ | The proposed `Clock` seam, test-only golden coverage, and controller/service boundaries fit the existing Spring architecture. |
| Blind Spots | PASS ✅ | Security, headers, pagination, generated data, mail dispatch, external scripts, and rollback implications are called out. |
| Plan Completeness | PASS ✅ | F2 and F3 were fixed by prescribing the approved Maven invocation and explicitly including planned-new files in whitespace validation. |

**Grounding:** 9/9 sampled existing paths ✓, 4/4 planned-new paths identified ✓, 22/22 handler mappings confirmed ✓, brief↔plan ✓, Progress↔Phase ✓ (11/11 criteria mapped). `context/foundation/lessons.md` and `docs/reference/contract-surfaces.md` are absent, so those checks were skipped. The research↔plan check is represented by F1 below because the artifacts disagree.

## Findings

### F1 — Production fixes contradict the S-02 source-of-truth artifacts

**Severity:** WARNING ⚠️
**Impact:** 🔬 HIGH — architectural stakes; think carefully before deciding
**Dimension:** End-State Alignment
**Location:** `plan.md` Overview, Phase 2, Migration Notes; `context/foundation/roadmap.md` S-02; `research.md` lookup findings

**Detail:** The plan's stated end state intentionally corrects three malformed `Location` headers and the reminder-advance DELETE binding. The roadmap says the admin surface should work “exactly as before” and warns to resist fixing known defects. The research likewise says to characterize these defects rather than fix them. `plan-brief.md` records the correction decision, but does not formally supersede or reconcile the roadmap and research, leaving the intended compatibility boundary ambiguous.

**Fix A — Retain the four fixes and make the exception authoritative** ⭐

- **Strength:** Preserves the explicitly requested usable API behavior and makes the migration/compatibility impact visible to future implementers.
- **Tradeoff:** Requires updating the roadmap and research artifacts, plus documenting authorization and external-client compatibility; this expands the planning change beyond the code slice.
- **Confidence:** High.
- **Blind spot:** Unknown external consumers may still depend on the malformed routes or unusable DELETE behavior.

**Fix B — Preserve the S-02 baseline and characterize only**

- **Strength:** Keeps the implementation aligned with the current roadmap and research scope.
- **Tradeoff:** Leaves four known API defects in place and weakens the “admin surface parity” usability outcome.
- **Confidence:** High.
- **Blind spot:** Disabled characterization tests could become permanent debt unless a later fix slice is explicitly scheduled.

**Decision:** FIXED — Fix A. The plan now authorizes the four API corrections as an explicit S-02
exception, and the roadmap/research artifacts describe the same compatibility boundary.

### F2 — Maven verification criteria omit the required Byte Buddy agent

**Severity:** WARNING ⚠️
**Impact:** 🏃 LOW — quick decision; fix is obvious and narrowly scoped
**Dimension:** Plan Completeness
**Location:** `plan.md` Phase 1–3 automated verification; `pom.xml` test JVM configuration; repository build instructions

**Detail:** Every automated command in the plan is a bare `./mvnw verify ...`. The repository instructions require Java 17 and the approved test invocation includes the Byte Buddy `-javaagent` together with the JVM flags. The current `pom.xml` `argLine` contains the other flags but no agent. The plan therefore does not prescribe the known-good invocation for the integration tests it uses as its acceptance gate.

**Fix:** Standardize every targeted and full-suite Maven command on the repository-approved Java 17 environment and explicit `-DargLine` containing the Byte Buddy agent, `-Djava.security.egd=file:/dev/./urandom`, `-Xmx256m`, and `-Duser.timezone=UTC`; define the command once in the verification section and reference it from each phase.

**Decision:** FIXED — Fix in plan. All targeted and full-suite Maven gates now specify Java 17 and
the required Byte Buddy agent with the approved JVM flags.

### F3 — `git diff --check` skips new untracked source files

**Severity:** WARNING ⚠️
**Impact:** 🏃 LOW — quick decision; fix is obvious and narrowly scoped
**Dimension:** Plan Completeness
**Location:** `plan.md` Phase 2 and Phase 3 verification criteria; current worktree status

**Detail:** Phase 2 and Phase 3 add four new files, but the current change folder is untracked. `git diff --check` does not inspect untracked files, so the criterion can pass while whitespace errors remain in the new integration tests or `ClockConfiguration`.

**Fix:** Add an explicit whitespace check covering the planned-new paths (or stage the new files before running `git diff --check`) and retain `git diff --check` for tracked edits.

**Decision:** FIXED — Fix in plan. The Phase 2 and Phase 3 whitespace gates now mark planned-new
files as intent-to-add before running `git diff --check`.

═══════════════════════════════════════════════════════════
  ► Overall: SOUND
═══════════════════════════════════════════════════════════

═══════════════════════════════════════════════════════════
  TRIAGE COMPLETE
═══════════════════════════════════════════════════════════

  Fixed: F1 (Fix A), F2, F3   (3)
  Skipped: —                 (0)
  Accepted: —                (0)
  Dismissed: —               (0)

  ► Verdict after fixes: SOUND
═══════════════════════════════════════════════════════════
