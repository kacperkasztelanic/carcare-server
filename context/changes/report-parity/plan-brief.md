# Report Parity (S-03) — Plan Brief

> Full plan: `context/changes/report-parity/plan.md`
> Research: `context/changes/report-parity/research.md`

## What & Why

Roadmap slice S-03 requires that an owner can request consumption, mileage, and cost statistics and
both XLSX reports, and receive output matching the F-02 golden baseline at value level. Research
measured this directly with a throwaway probe: ten of eleven goldens already match at HEAD, and the
eleventh is the documented intentional divergence. The work is therefore to make that fact
*assertable and permanent*, not to hunt a migration regression.

## Starting Point

Eleven golden references sit in `src/test/resources/golden/`, and **none is consumed by any
integration test** — the producer (`SessionFixtures.seedGoldenDataset`) and consumer
(`GoldenReference`) halves of F-02 were never wired together. Two defects make wiring them
impossible: `GoldenReference` rejects the very handle map the fixture returns, because H2 assigns
identity values per table and ids collide across the ten seeded tables; and two stub property files
in `src/test/resources/i18n/` shadow the production message bundles for the whole test JVM, so
Polish reports render in English. The report and statistics endpoints currently have owner-isolation
coverage only — no test asserts a single computed value.

## Desired End State

`ReportParityIT` asserts all eleven goldens: ten as exact value-level matches across both XLSX
reports and all four statistics endpoints in both locales, and the zero-consumption case as an
explicitly named divergence recording the captured 500 against HEAD's deliberate 200. The i18n stubs
are gone, a guard test prevents a third instance of that defect class, and the report sort path is
deterministic under a total comparator rather than relying on the fixture happening to use distinct
dates.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Zero-consumption golden | Keep as captured, assert the divergence | Re-baselining makes the file assert HEAD against HEAD and stops it detecting a regression back to the 500 | Research D1 |
| `averageConsumption: 0.0` contract | Surface, do not resolve | Returning null or omitting the field is client-visible and belongs to S-07 | Research D1 |
| i18n fix ownership | Lands in S-03 | S-03 is blocked by it now, S-04 is not; the fix is a two-file deletion | Research D2 |
| Ordering hazard 1 (`ORDER BY`) | Not pinned, documented as accepted risk | Production change inside a parity slice; S-05 touches that query's neighbourhood | Research D3 |
| Ordering hazard 2 (`HashSet` + date sort) | Made genuinely deterministic, `id` tiebreaker | Removes latent flakiness rather than documenting a fixture constraint nobody reads | Research D3 |
| Polish cost golden | Not added | Baseline worktree and clock containers are torn down; PL path adds no coverage beyond a bundle lookup | Research D4 |
| Handle-map collision fix | Scope validation and rewriting to the `vehicle:` namespace | Only `vehicleId` is ever rewritten, so this matches what the code consults and lets callers pass the fixture map unchanged | Plan |
| Test structure | One `ReportParityIT`, explicit test per golden | Mirrors `OwnerIsolationIT`; each failure names its golden; request shapes vary too much to parameterize readably | Plan |
| `email.test.title` | New `i18n/test-messages` basename layered over `i18n/messages` | Keeps a test-only key out of shipped resources; follows the `templates/mail/testEmail.html` precedent | Plan |
| Sequencing | Harness, then tests, then determinism | The parity suite must exist before the production change so it can prove that change was inert | Plan |
| langKey cache hazard | Evict inside `seedGoldenDataset` | Fixes it at the source for every current and future consumer; mirrors `UserService.clearUserCaches` | Plan |
| Extra hardening | Resource-collision guard test only | F-04 fixed this defect class once, S-03 fixes it again; the guard stops a third instance | Plan |

## Scope

**In scope:** `GoldenReference` handle-map scoping; fixture cache eviction; deleting the two i18n
stubs and repairing `MailServiceIT`; a main/test resource-collision guard; `ReportParityIT` with
eleven assertions; ordered collections and total sort comparators in the report path.

**Out of scope:** `order by vehicle.id`; a Polish cost golden; `WorkbookValues`' English `"Costs"`
key; the `averageConsumption` wire contract; the two reminder goldens (S-04); the English mail text
and `messages_pl`'s missing `email.reset.greeting` (S-04); `AGENTS.md` and `reference.md` updates.

## Architecture / Approach

Reports and statistics share one data path — `VehicleRichMapper` loads for both, and `CostCalculator`
is invoked identically by `ReportServiceImpl` and `StatisticServiceImpl` — so `costs-en.json` and
`cost-en.json` are two views of one computation. Locale is per-owner, not per-request: it comes from
the persisted `User.langKey`, which is why the PL report is only reachable through the PL owner and
why the user cache matters. The new suite extends the existing `AbstractSessionIT` harness
(`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`), so no additional Spring context is
started.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Harness repair | `GoldenReference` accepts the fixture's real map; fixture evicts the user cache; the untested seam gets coverage | Narrowing to `vehicle:` is invisible at the call site if a future golden references another id type |
| 2. i18n de-shadowing | Production bundles resolve in tests; `MailServiceIT` repaired; collision guard added | Stale `target/test-classes` produces a convincing false negative unless `clean` runs |
| 3. Golden parity suite | `ReportParityIT` — ten matches plus the named divergence | The PL test fails confusingly if Phase 1's eviction is wrong |
| 4. Ordering determinism | Total comparators in the report path; `/api/events` ordering pinned; accepted risk documented | A production change landing last, verified by Phase 3's goldens plus one added before/after check |

**Prerequisites:** F-02 (`golden-baseline-capture`) and F-04 (`test-context-restored`), both
delivered. Java 17 exactly — `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`. Green baseline
at `2e6da14`: 33 unit (1 skipped), 177 integration (1 skipped).

**Estimated effort:** ~2-3 sessions across 4 phases; Phase 3 is the bulk.

## Open Risks & Assumptions

- `cost-en.json`'s array is compared index-exactly against an unordered query. It matches by
  insertion order on H2 and MariaDB, which is not a contract — a deliberately accepted risk (D3),
  most likely to surface under a different database or a parallel-execution change.
- The probe's 10/11 result is assumed to hold when reconstructed as permanent tests. The probe ran
  in one transaction per assertion; `ReportParityIT` runs one per test.
- With the i18n fix landed, a Polish cost report would be named `Koszty` and silently skip
  `WorkbookValues`' sort. Inert while no PL cost golden exists; a prerequisite for anyone adding one.
- Phase 4's `VehicleReport` tiebreaker is asserted to be inert because no golden sheet currently has
  a same-date tie. Verified by inspection now, and by Phase 3's suite at execution time.
- Phase 4's `VehicleRichMapper` change also reaches `/api/events`, which Phase 3's goldens do not
  cover: `ForthcomingEvent.compareTo` ties on equal `dateThru` when a `mileageThru` is zero, so
  ordering there is reachable. Pinned by a before/after assertion in Phase 4 §4 rather than assumed.
- `ReportParityIT` caches the seeded langKeys through `@Cacheable findOneWithAuthoritiesByLogin`,
  and those entries survive the transactional rollback into a JVM-shared `CacheManager`. Handled by
  evicting in both directions (Phase 1 §2 + an `@AfterTransaction` hook).

## Success Criteria (Summary)

- `./mvnw -o -B verify` is green with integration tests rising from 177 to 188 or higher
- A value regression in any report or statistics path fails the build naming the golden file and the
  exact JSON path
- No golden reference file is edited by this slice — `git diff --stat src/test/resources/golden/` stays empty
