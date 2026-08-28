# S-01 `session-parity` — Plan Brief

> Full plan: `context/changes/session-parity/plan.md`
> Research: `context/changes/session-parity/research.md`

## What & Why

S-01 is the roadmap's north star: prove that an existing owner's whole session — log in, list and
open vehicles, create/read/update/delete all five event types — is indistinguishable through the
unmodified React client 1.2.5 after the Jakarta / Spring Boot 3 migration, while reaching no other
user's data on any path. The PRD names client breakage as the pager event, which makes proving the
opposite the thing worth proving first.

## Starting Point

The migration never textually touched this surface: all nine in-scope controllers and the entire
`repository/` package are byte-identical to the last known-good commit `6e19b96`, so every parity
risk here is behavioural rather than diffable. Three things block the proof. The test profile cannot
execute a single query against any of the five event tables (`auto_quote_keyword: true`, added by
F-04). The client-visible `X-carcareApp-*` alert header was renamed to `X-carcare-*` during F-03, and
client 1.2.5 matches it by suffix — so every create, update, and delete silently loses its toast.
And the project has no business-behaviour tests and no fixture layer at all: 25 test classes, none
covering `Vehicle` or any event type, with both lookup tables empty under test.

## Desired End State

`./mvnw verify` runs the existing 115 integration tests plus a new S-01 suite, all green, asserting
baseline status codes and restored header names across every CRUD path, a uniform 404 (never 403) on
every cross-user access, a real JWT minted and replayed successfully, and the four wire invariants
whose violation crashes the client (three in `ClientWireContractIT`, the `Bearer ` prefix in
`JwtSessionIT`). A human has walked one full session in a browser against the real
client and seen the toasts appear.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Header regression | Fix server-side, restore `X-carcareApp-*` | Client is frozen at 1.2.5 per FR-008; `spring.application.name` must stay `carcare` for logging/metrics | Research |
| Vehicle delete parity | Cover the working case, `@Disabled` placeholder for the broken one | Fixing it pre-empts S-05's archiving design; a characterization test would make S-05 test-breaking | Research |
| Fixtures | Java builder seeding via repositories | Typed, refactors with the entities, stays entirely in `src/test` — no test rows in a production changelog | Plan |
| Auth seam | `@WithMockUser` throughout + one real-JWT test | Cheap for 30+ tests while still proving the login path a mock structurally cannot | Plan |
| Suite shape | One IT per resource + one cross-cutting `OwnerIsolationIT` | Matches F-04's convention; concentrates the highest-severity guardrail in one auditable file | Plan |
| Wire assertions | The break-the-client set only | Covers every invariant whose violation is a crash or dead flow, without straying into S-03's value-level mandate | Plan |
| Four pre-existing 500s | Fix them here | Owner decision — widens past strict parity deliberately; sequenced after the parity suite is green. `InsuranceTypeMapper` joins `FuelTypeMapper` because the client's PUT shape hits it and Phase 4 asserts that round-trip | Plan |
| Stats/report reads | Isolation smoke only, values to S-03 | Closes the gap on the guardrail that fails silently, without needing F-02's baseline | Plan |
| Done gate | Green suite + one manual client session | Only a browser session can prove the *client* is satisfied — the exact blind spot the header rename exploited | Plan |

## Scope

**In scope:** the test-profile blocker fix; restoring the alert header name; a fixture layer; CRUD
parity ITs for `/api/vehicle` and the five event types; a cross-cutting owner-isolation IT covering
those six plus the seven shared stats/report/events read paths; a real-JWT session test; fixing four
pre-existing 500s; the manual client gate and record correction.

**Out of scope:** fixing `DELETE /api/vehicle/{id}` for vehicles with history (S-05); asserting any
computed statistic or report value (S-03, needs F-02); consolidating ownership enforcement into a
single boundary (parked); Bean Validation on business bodies (parked); CI wiring (S-06 — note none of
this suite runs in CI until then, since `.gitlab/gitlab-ci.yml:20` runs Surefire only).

## Architecture / Approach

Two blocking changes first, each with its own gate so a failure is attributable: the test-profile
identifier fix (validated green against all 115 existing ITs during research), then the `HeaderUtil`
constant restoration. Then a test-only `SessionFixtures` component seeding through the repositories,
component-scanned from `src/test/java` and triggered by an `ApplicationRunner` exactly like the
existing `TestUserIdentitySequenceFixup`, so the shared Spring context is not forked and the lookups
are committed before any test transaction opens. Then the suite. Behaviour changes land last, so parity is proven
against the tree as it stands before anything is fixed.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Unblock test profile | Event-table queries execute under test | Adds a third test-vs-production identifier divergence |
| 2. Restore alert header | Client 1.2.5 gets its toasts back | Must not touch `UserResource`/`ExceptionTranslator`, which are baseline-correct |
| 3. Fixture layer | Idempotent seeding + two-owner builders + IT base | Unique constraints on both lookup tables; shared JVM-wide H2 |
| 4. CRUD parity ITs | Six `*ResourceIT` classes, ~40–50 tests | The `@Disabled` delete case must escape class-level `@Transactional` |
| 5. Isolation + JWT | The highest-severity guardrail, in one file | Shared read paths are POST-with-body, not GET |
| 6. Fix four 500s | 400 / 400 / merged / `0.0` instead of server errors | Deliberately past parity; the NaN fix touches S-03's surface |
| 7. Manual gate + record | Browser session, `AGENTS.md`, epilogue | Reverses F-03 impl-review finding F2 — must be stated, not silent |

**Prerequisites:** F-04 delivered and archived (done, 2026-08-26). Java 17 exactly
(`export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`). Phase 7 additionally needs a running
MariaDB and the real client WAR.

**Estimated effort:** ~4–6 sessions across 7 phases; Phase 4 is roughly half the work.

## Open Risks & Assumptions

- The zero-mileage fix reports "unknown" as `0.0`, indistinguishable from a real zero. Chosen because
  it preserves the wire shape exactly, but it is a semantic loss on a surface S-03 owns at value
  level — and we are choosing it without F-02's golden baseline to check against.
- "Keep first" on the duplicate-`vehicleId` merge is an arbitrary tiebreak. If a client ever sends
  two genuinely different periods for one vehicle, the second is silently dropped.
- The suite encodes *post*-migration behaviour as correct. F-02's reference output is what would
  catch a bug being locked in, and F-02 is still `ready`, not done.
- `SecurityEvaluationContextExtension` is still never declared in `src/main`; it arrives implicitly
  through Boot autoconfiguration. Phase 3's fixture smoke and Phase 5's isolation tests are the first
  things that would ever notice if that chain broke.
- Nothing in this suite runs in CI until S-06 lands.

## Success Criteria (Summary)

- An owner can run a full vehicle-and-event session through client 1.2.5 with every create, update,
  and delete producing a visible toast and a clean console.
- No user can reach another user's data on any of the ~31 in-scope handlers, and the failure mode is
  uniformly 404 (or an empty list), never 403.
- `./mvnw verify` is green and the suite is the project's first real regression net — S-02 through
  S-06 can now proceed against something that would notice if they broke this.
