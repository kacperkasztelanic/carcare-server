# Golden Baseline Capture — Plan Brief

> Full plan: `context/changes/golden-baseline-capture/plan.md`
> Research: `context/changes/golden-baseline-capture/research.md`

## What & Why

Capture the report values, statistics figures, and reminder selections that commit `6e19b96` —
the newest commit that builds and runs — produces from a fixed dataset, and commit them as a
reference. Without it, `report-parity` (S-03) and `english-reminder-fix` (S-04) have nothing to
assert correctness against: they can prove the migrated code runs, but not that it still computes
the same answers. F-02 is the last foundation item gating Stream B → Stream C.

## Starting Point

No reference exists — `src/test/resources/` has no golden or fixture layer at all, and no
snapshot library is on the classpath. `6e19b96` is verified to build offline with Temurin 17.0.20,
but its context boot is only asserted, never exercised. There is no deterministic event-data
generator at that commit: the random-vehicle endpoint is unseeded and creates zero events. The
test layer S-03/S-04 will extend already exists (`SessionFixtures`, `AbstractSessionIT`), but its
event builders are fixed-value and cannot express a curated dataset as they stand.

## Desired End State

A committed golden dataset, a set of machine-readable reference files covering every report,
statistics, and reminder-selection output `6e19b96` produces from it, and a harness at HEAD that
reads those files and compares them against live output at value level. `reference.md` records
the exact command, dataset, commit, profile, clock, timezone, and locale behind every captured
number. S-03 can then adjudicate the two computed-value decisions `session-parity` deferred to it
against real evidence instead of judgement.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Dataset route | Curated deterministic fixture | The only route reproducible on both sides — MariaDB at `6e19b96` for capture and H2 at HEAD for S-03/S-04. | Research → Plan |
| Scope fence | Reference + reusable harness only | Keeps F-02 finishable and immune to HEAD's state; parity assertions belong to S-03/S-04. | Plan |
| Artefact location | Test resources + change doc | JSON under `src/test/resources/golden/` is loadable by tests; `reference.md` carries the provenance that JSON cannot. | Plan |
| Fixture coverage | Full edge-case matrix | Edge cases are exactly where Hibernate 6 and Jackson changes hide, and where `session-parity` deferred its decisions. | Plan |
| Reminder clock | Typed `(dates, now)` seam, plus one full-path run | Fully deterministic without touching `src/main`, while still covering the advance-set derivation. | Research → Plan |
| Owners | Seeded `admin` + `user`, `langKey` `en`/`pl` | Both commits seed them identically, and `SessionFixtures` already resolves owners by login with no test creating users. | Plan |
| Capture mechanism | Boot the WAR, drive over HTTP | Captures the true wire surface including headers and status codes, not just computed values. | Plan |
| Number storage | Fixed-precision decimal strings | Immune to double-formatting differences between JVM and Jackson versions — precisely a migration risk. | Plan |
| Boot spike | Phase 1, standalone | Settles the one ASSUMED fact before fixture authoring is sunk into it. | Plan |

## Scope

**In scope:**
- Boot `6e19b96` against a disposable MariaDB and verify authentication
- A curated golden dataset as SQL, with an auditable branch-coverage inventory
- Captured references for both XLSX reports (both locales), all four statistics endpoints, the two 404 paths, and reminder selection from two capture paths
- An XLSX→value extractor, reference loader, and comparison function at HEAD
- `SessionFixtures` explicit-value overloads plus a test proving the dataset mirrors faithfully under H2
- `reference.md` recording full provenance

**Out of scope:**
- Any `src/main` edit — including the `vnd.ms-excel` content type, the missing `ORDER BY` clauses, the broken reminder-advance delete path, and `messages_en.properties:35`
- Parity assertions against HEAD (S-03, S-04)
- Capturing at HEAD, which cannot boot a `dev` context
- The restored-production-data parallel run, which reuses this harness later

## Architecture / Approach

Two runtimes, one dataset. A throwaway `git worktree` at `6e19b96` builds the WAR and boots it
against an isolated MariaDB container; the golden SQL is loaded, and a driver script captures
every endpoint over real HTTP while a worktree-local runner captures reminder selection through
the typed `ReminderService` seam. Output lands as JSON under `src/test/resources/golden/`, with
XLSX reduced to sorted cell-value maps and every number stored as a fixed-precision decimal
string. Back at HEAD, a small harness reads those files, and `SessionFixtures` gains the overloads
needed to recreate the same rows under H2 — with one integration test proving the mirror is
faithful, so a later value difference means a migration difference and not a dataset difference.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Boot spike | `6e19b96` verified to boot and authenticate | Context boot fails and the whole capture route needs rethinking |
| 2. Golden fixture | `golden-dataset.sql` + branch inventory | A branch is missed and its regression ships uncaught |
| 3. Reports & statistics capture | Golden files for six endpoints, both locales | Reduction rules leave residual non-determinism between runs |
| 4. Reminder capture | Selection reference from two paths | `@Async` `MailService` swallows invocations; the two paths disagree |
| 5. HEAD harness | Extractor, loader, comparison, fixture mirroring | Golden seeding leaks into existing ITs and perturbs them |

**Prerequisites:** Temurin 17.0.20 via SDKMAN, Docker running with a MariaDB image, `~/.m2`
populated (all verified). No roadmap prerequisites — F-02 has none.
**Estimated effort:** ~3-4 sessions across 5 phases; Phase 2 is the authoring bottleneck.

## Open Risks & Assumptions

- `6e19b96` context boot is asserted by `AGENTS.md` but never exercised — Phase 1 exists to settle it, and a failure there invalidates the approach rather than a detail of it.
- Capture-side code is throwaway and lives only in a temporary worktree, so it cannot be re-run later without re-following `reference.md`; the plan makes that document's sufficiency an explicit manual gate.
- The MariaDB image available locally is 10.11.6 while `6e19b96` pinned 10.6.7; the schema is Liquibase-managed so the delta is low risk, but it is a difference from the original environment.
- The fixture must not perturb existing ITs. Golden seeding is deliberately not wired into `SessionFixtures.run(...)`, and phase 5 gates on that being confirmed.

## Success Criteria (Summary)

- Every report cell, statistics figure, and reminder selection `6e19b96` produces from the golden dataset is committed as a reference, and re-running the capture reproduces it byte-identically.
- S-03 can load the reference and compare live HEAD output at value level without transcribing a single number by hand.
- S-04 can prove reminder selection is unchanged, so its bundle fix is demonstrably rendering-only.
