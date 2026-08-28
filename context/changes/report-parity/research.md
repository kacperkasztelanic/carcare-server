---
date: 2026-08-28T12:31:59+02:00
researcher: Kacper Kasztelanic
git_commit: 2e6da14a38f7ad64c144811ce6f5a3c76bbdf2c1
branch: refactor
repository: carcare/server
topic: "S-03 report-parity: do HEAD's reports and statistics match the F-02 golden baseline?"
tags: [research, codebase, reports, statistics, golden-baseline, i18n, poi]
status: complete
last_updated: 2026-08-28
last_updated_note: "Resolved all four open questions (D1-D4)"
last_updated_by: Kacper Kasztelanic
---

# Research: S-03 report-parity

**Date**: 2026-08-28T12:31:59+02:00
**Researcher**: Kacper Kasztelanic
**Git Commit**: `2e6da14`
**Branch**: `refactor`
**Repository**: `carcare/server`

## Research Question

Roadmap slice **S-03** (`context/foundation/roadmap.md`): *an owner can request consumption,
mileage, and cost statistics and both XLSX reports, and receive output matching the F-02 reference
at value level — cell values, computed figures, and content type.* What stands between HEAD and
that outcome?

## Summary

**S-03 is far closer to done than the roadmap assumes, and the remaining work is almost entirely in
the test harness rather than in production code.**

A throwaway probe (`GoldenParityProbeIT`, written, run, and deleted during this research) seeded
`SessionFixtures.seedGoldenDataset()` into the H2 test context and compared all eleven committed
golden references against live MockMvc responses at `2e6da14`:

| Result | Count | Files |
| --- | --- | --- |
| Match at HEAD | 9 | everything except the two below |
| Match once the i18n shadowing defect is removed | 1 | `reports/vehicle-pl.json` |
| Genuine divergence | 1 | `stats/consumption-period-zero.json` |

The single genuine divergence is the **intentional** `4ad88bd` zero-mileage guard already recorded in
`context/changes/golden-baseline-capture/reference.md`. **No migration regression was found in any
report or statistics value.** Costs, consumption, mileage, workbook cell values and data formats,
the `Infinity` divide-by-zero error cell, the `application/vnd.ms-excel` content type, the
`Content-Disposition` filename, `Cache-Control`, `X-Total-Count`, and both 404 paths all reproduce
exactly.

Two defects block S-03 from *asserting* that, and both are new findings — neither is in F-02's
reference document, and neither is exercised by any committed test:

1. **`GoldenReference` rejects the handle map `SessionFixtures.seedGoldenDataset()` returns.** The
   two halves of F-02 were never wired together; every golden assertion throws before comparing.
2. **`src/test/resources/i18n/` shadows the production message bundles for the whole test JVM.**
   Polish reports render in English; the English report passes only by accident.

So the shape of S-03 is: fix two harness defects, decide one policy question about the
zero-consumption divergence, then convert the probe into permanent tests. There is no production
parity bug to hunt.

## Detailed Findings

### Finding 1 — `GoldenReference.validateHandleMap` rejects F-02's own handle map (blocker)

`GoldenReference` requires the handle map to be injective over ids
(`src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java:201-217`):

```java
String previous = seenIds.put(entry.getValue(), entry.getKey());
if (previous != null && !previous.equals(entry.getKey())) {
    throw new IllegalArgumentException("Golden handle map resolves id " + entry.getValue()
        + " to both " + previous + " and " + entry.getKey());
}
```

But `SessionFixtures.seedGoldenDataset()` returns 28 handles spanning **ten different tables**
(`src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:273-357`), and H2 assigns
identity values **per table**. The actual map observed in the probe:

```
fuel-type:diesel=2, insurance-type:oc=2, reminder-advance:three-days=1, vehicle:en-primary=1,
refuel:en-first=1, repair:same-date-low-mileage=1, inspection:same-date-high-mileage=1, …
```

The first `assertWorkbookMatches` call therefore fails with:

```
IllegalArgument Golden handle map resolves id 2 to both fuel-type:diesel and insurance-type:oc
```

This is not an H2 quirk to work around — the baseline capture only avoided it because
`golden-dataset.sql` hand-assigned globally distinct ids in the `900000+` range. Any generated-id
mirror of that fixture will collide.

**Why it was never caught:** `GoldenReferenceTest` builds its own small maps
(`src/test/java/com/kasztelanic/carcare/golden/GoldenReferenceTest.java:21-64`), and
`GoldenDatasetMirrorIT` calls `seedGoldenDataset()` but never calls `GoldenReference`
(`src/test/java/com/kasztelanic/carcare/golden/GoldenDatasetMirrorIT.java:67-90`). The seam between
F-02's producer and its consumer has no coverage — which `reference.md` inadvertently confirms by
describing them in separate paragraphs.

**Note the uniqueness check is still worth keeping** for the id-space it actually protects: only
`vehicleId` fields are rewritten by `normalizeJsonById` (`GoldenReference.java:244-255`), and the
raw-string path `normalizeRawHandles` (`GoldenReference.java:284-292`) does a blind textual replace
over the whole body, where a colliding id would corrupt the comparison. The likely fix is to scope
both the validation and the rewrite to the handles that are actually consulted (the `vehicle:*`
namespace), not to relax the invariant. The probe used exactly that narrowing and every remaining
comparison behaved correctly.

### Finding 2 — test i18n bundles shadow the production bundles JVM-wide (blocker)

`src/test/resources/i18n/` contains two files:

- `messages_en.properties` — 2 keys, with the comment *"as this file is loaded instead of real file"*
- `messages_pl.properties` — 1 key (`email.test.title`)

`target/test-classes` precedes `target/classes` on the test classpath, so
`ResourceBundle.getBundle("i18n/messages", …)` resolves these stubs for **every** test in the JVM,
not just `MailServiceIT`. Proven directly in the probe:

```
PROBE-MS class=org.springframework.context.support.ResourceBundleMessageSource
PROBE-MS pl reports.vehicle.main=General          ← expected "Dane ogólne"
PROBE-MS PL bundle direct=General                  ← raw java.util.ResourceBundle, same result
```

The failure mode is silent because the JDK falls through the stub to the **base** bundle
`messages.properties` in `target/classes`, which holds the English values. So:

- **Polish output is silently English.** `reports/vehicle-pl.json` fails on the very first cell:
  `$.body.sheets[0].name: expected "Dane ogólne" but was "General"`.
- **English output passes for the wrong reason.** `Locale("en")` resolves the 2-key stub, misses
  every `reports.*` key, and falls back to `messages.properties`. Verified that
  `messages.properties` and `messages_en.properties` are byte-equal across all 60 `reports.*` keys,
  so the value is right — but `messages_en.properties` is currently dead weight in every test.

**Proof of the fix.** Removing `src/test/resources/i18n/` *and* the stale `target/test-classes/i18n/`
copies, then re-running the probe:

```
PROBE-MS pl reports.vehicle.main=Dane ogólne
PROBE-OK   golden/reports/vehicle-pl.json  … matches
```

All ten non-divergent references then match. (The first attempt at this experiment failed
misleadingly because Maven does not delete removed resources from `target/` without a `clean` — the
stale copies kept shadowing. Worth knowing before re-running it.)

**Cost of the fix: exactly two tests**, both depending on `email.test.title`, a key that exists in
*no* production bundle:

```
MailServiceIT.testSendEmailFromTemplate:138 » NoSuchMessage 'email.test.title' for locale 'en'
MailServiceIT.testSendLocalizedEmailForAllSupportedLanguages:206 » NoSuchMessage …
```

`testSendLocalizedEmailForAllSupportedLanguages` additionally reads
`i18n/messages_<locale>.properties` off the classpath itself
(`src/test/java/com/kasztelanic/carcare/service/MailServiceIT.java:210-216`) and compares it against
what the mail rendered — so the stub is currently what makes that test self-consistent. Giving
`MailServiceIT` its own message source over a **distinct basename** is the clean shape; adding
`email.test.title` to the production bundles would work but puts a test-only key in shipped
resources.

**This is pre-existing, not migration damage** — `git log` dates both files to `ed7a383`
("Upgrade to JHipster 6.3"). It is the same class of defect F-04 fixed for `application.yml`:
a test resource sharing a classpath location with a main resource shadows it instead of layering
on it.

**Hand-off to S-04.** The stub also overrides `email.activation.title`, so
`MailServiceIT.testSendActivationEmail` currently asserts against the stub rather than the real
English bundle. `english-reminder-fix` will hit this before it hits anything else, and TODO.md's
"fix EN emails" issue may be partly or wholly this. Separately, `messages_pl.properties` is missing
`email.reset.greeting`, which the other two bundles have — also S-04's.

### Finding 3 — the only genuine divergence is the intentional one

`stats/consumption-period-zero.json` is the sole real mismatch:

| | Baseline (`6e19b96`) | HEAD (`2e6da14`) |
| --- | --- | --- |
| Status | 500 | 200 |
| Body | partial JSON + undelimited problem document | `{"periodVehicle":{…},"volume":0.0,"mileage":0,"averageConsumption":0.0}` |

This is the `4ad88bd` zero-mileage guard in
`src/main/java/com/kasztelanic/carcare/service/dto/AverageConsumptionResult.java:19-26`, listed as
expected divergence #1 in `context/changes/golden-baseline-capture/reference.md`. The baseline
produced `NaN` from `volume * 100.0 / mileage` and Jackson failed mid-serialization.

**S-03 owns this decision** — `reference.md` says so explicitly, and the session-parity epilogue
hands it over in the same words. Two shapes:

- **Re-baseline the golden file** to the HEAD 200 response, annotated with why it diverges from
  capture. Keeps every golden file an assertion; loses the record of what the baseline did.
- **Keep the golden file and assert the divergence explicitly** — a named test that documents the
  captured 500 and asserts the deliberate 200. Preserves both facts.

The roadmap's own S-03 Risk paragraph is unusually pointed here: *"this conflates unknown
consumption with a real zero and must be judged against F-02's golden baseline at value level."*
That is a product question, not a test question: `averageConsumption: 0.0` is indistinguishable from
a genuine 0.0 L/100 km reading. A third option — a `null` average, or omitting the field — would be
a client-visible contract change and belongs to the frozen-client discussion, not to S-03 quietly.

### Finding 4 — value-level parity confirmed across every captured surface

Probe output at `2e6da14`, narrowed handle map, i18n shadowing removed:

```
PROBE-OK   golden/reports/vehicle-en.json            [200]
PROBE-OK   golden/reports/vehicle-pl.json            [200]
PROBE-OK   golden/reports/costs-en.json              [200]
PROBE-OK   golden/reports/vehicle-unowned.json       [404]
PROBE-OK   golden/stats/consumption-period-en.json   [200]
PROBE-FAIL golden/stats/consumption-period-zero.json [200]  ← intentional divergence
PROBE-OK   golden/stats/consumption-refuel-en.json   [200]
PROBE-OK   golden/stats/consumption-refuel-zero.json [200]
PROBE-OK   golden/stats/mileage-en.json              [200]
PROBE-OK   golden/stats/mileage-unowned.json         [404]
PROBE-OK   golden/stats/cost-en.json                 [200]
```

Specific behaviours that survived the Jakarta / Hibernate 6 / Spring Boot 3 migration intact:

- **POI 5.2.5 workbook output.** Cell types, values, and `dataFormat` strings all reproduce,
  including the `#DIV/0!` `ERROR` cell carrying the `Infinity` sentinel at `0.00` format for
  `refuel:zero-volume` (`reports/vehicle-en.json`, Refuel sheet, last row).
- **Hibernate 6 fetch and type mapping.** `mileage-en.json`'s nine-entry `SortedMap` reproduces
  exactly, including the same-date merge at `2026-03-25` resolving to `10900` (the inspection, not
  the repair) via `MileageServiceImpl`'s mileage-ordered `TreeMap` collector
  (`src/main/java/com/kasztelanic/carcare/service/impl/MileageServiceImpl.java:30-35`).
- **Inclusive period bounds and `skip(1)`.** `consumption-period-en.json` reproduces
  `volume 87.0 / mileage 1000 / average 8.7`, which depends on `refuel:en-boundary` (2026-03-31)
  being included and `repair:range-before` (2026-02-28) excluded
  (`AverageConsumptionCalculatorImpl.java:21-30`).
- **Controller-owned headers.** `application/vnd.ms-excel` despite XLSX bytes,
  `form-data; name="EN_1001.xlsx"; filename="EN_1001.xlsx"` from Spring 6's
  `setContentDispositionFormData`, and the report's own
  `Cache-Control: must-revalidate, post-check=0, pre-check=0` winning over Spring Security's
  default writer (`ReportResource.java:53-61`). `X-Total-Count` still emitted by
  `ResponseUtil.createListOkResponse` (`web/rest/util/ResponseUtil.java:16-20`).

### Finding 5 — three ordering hazards the golden files do not currently guard

None of these fail today, but all three are unpinned and would surface as confusing S-03 or S-05
failures later.

1. **`findAllByIdAndOwnerIsCurrentUser` has no `ORDER BY`**
   (`src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:22-23`). Its result
   order determines both the `cost-en.json` JSON array order — which `GoldenReference` compares
   **index-exactly** (`GoldenReference.java:326-340`) — and the `Costs` sheet row order. The
   workbook side is defended, because `WorkbookValues` sorts that sheet's middle rows
   (`WorkbookValues.java:96-97, 149-160`); the JSON side is not defended at all. It matched on H2
   and on MariaDB by insertion order, which is luck, not contract.

2. **`VehicleRichMapper` collects into `HashSet`**
   (`src/main/java/com/kasztelanic/carcare/service/mapper/VehicleRichMapper.java:64-83`), and every
   `VehicleReport` sheet sorts by date alone
   (`src/main/java/com/kasztelanic/carcare/service/reports/VehicleReport.java:125-126` and four
   more). Java's sort is stable, so same-date events within one sheet keep `HashSet` iteration
   order — non-deterministic across JVMs. The golden dataset happens to give every vehicle distinct
   dates within each sheet, so this is latent. Adding a same-date pair to any one sheet would make
   the suite flaky, which is a constraint on future fixture growth.

3. **`WorkbookValues.shouldSort` keys off the literal English `"Costs"`**
   (`WorkbookValues.java:30, 105-108`). A Polish cost-report golden — which S-03 may reasonably want,
   since only the vehicle report has both locales today — would be named `Koszty` and would silently
   skip the sort that makes hazard 1 safe. Worth noting that with the Finding 2 shadowing bug
   *present*, a PL cost report is named `"Costs"` and does sort; fixing i18n is what exposes this.

### Finding 6 — current coverage of these endpoints, and the gap

The four `/api/stats/*` and two `/api/reports/*` paths have **owner-isolation coverage only**
(`src/test/java/com/kasztelanic/carcare/web/rest/OwnerIsolationIT.java:186-233`): status codes,
empty results for foreign callers, and one license-plate leak check on the cost report. No test
asserts a single computed value. `GoldenDatasetMirrorIT` checks the fixture *definition*, not any
output, and says so in its own class comment.

Green baseline S-03 builds on, measured this session with `./mvnw -o -B verify` at `2e6da14`:
**BUILD SUCCESS — 33 unit tests (1 skipped), 177 integration tests (1 skipped).**

## Code References

- `src/main/java/com/kasztelanic/carcare/web/rest/ReportResource.java:35-61` — both report
  endpoints and the shared header block
- `src/main/java/com/kasztelanic/carcare/web/rest/StatisticResource.java:30-52` — all four
  statistics endpoints
- `src/main/java/com/kasztelanic/carcare/service/impl/ReportServiceImpl.java:42-75` — locale from
  `user.getLangKey()`, report naming, Vavr `Either` error wrapping
- `src/main/java/com/kasztelanic/carcare/service/impl/StatisticServiceImpl.java:37-74` — the four
  calculation entry points
- `src/main/java/com/kasztelanic/carcare/service/impl/AverageConsumptionCalculatorImpl.java:21-53` —
  inclusive bounds, `skip(1)`, per-refuel pairing
- `src/main/java/com/kasztelanic/carcare/service/impl/CostCalculatorImpl.java:31-38` — cents → double
- `src/main/java/com/kasztelanic/carcare/service/impl/MileageServiceImpl.java:30-35` — same-date
  highest-mileage merge
- `src/main/java/com/kasztelanic/carcare/service/dto/AverageConsumptionResult.java:19-26` — the
  `4ad88bd` zero-mileage guard
- `src/main/java/com/kasztelanic/carcare/service/reports/VehicleReport.java:45-63` — six-sheet
  workbook assembly
- `src/main/java/com/kasztelanic/carcare/service/reports/CostReport.java:51-84` — cost sheet, sum row
- `src/main/java/com/kasztelanic/carcare/service/mapper/VehicleRichMapper.java:54-85` — five
  `Collectors.toSet()` calls
- `src/main/java/com/kasztelanic/carcare/repository/VehicleRepository.java:22-23` — the unordered
  multi-id query
- `src/test/java/com/kasztelanic/carcare/golden/GoldenReference.java:201-217` — the rejecting
  validator
- `src/test/java/com/kasztelanic/carcare/golden/WorkbookValues.java:105-108, 149-160` — the
  English-keyed `Costs` sort
- `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java:273-357` — `seedGoldenDataset`
- `src/test/resources/i18n/messages_en.properties`, `messages_pl.properties` — the shadowing stubs
- `src/main/resources/i18n/messages_en.properties:47` — `reports.vehicle.main.certificate` uses a
  whitespace separator rather than `=`; legal `.properties` syntax, resolves correctly, harmless

## Architecture Insights

- **Reports and statistics share one data path.** `VehicleRichMapper` is the single loader for both,
  and `CostCalculator` is invoked identically by `ReportServiceImpl` and `StatisticServiceImpl`.
  `costs-en.json` and `cost-en.json` are therefore two views of the same computation — a value
  regression cannot appear in one without the other, which makes them a useful cross-check but poor
  independent evidence.
- **Locale is per-owner, not per-request.** There is no `Accept-Language` handling on these paths;
  the report locale comes from the persisted `User.langKey`
  (`ReportServiceImpl.java:43`, `:59`), and the fuel-type translation comes from the vehicle owner's
  langKey inside the mapper (`VehicleRichMapper.java:60-62`). The golden fixture exercises this by
  setting `admin`→`en` and `user`→`pl`. It also means a PL report is only reachable through a PL
  owner — a test cannot simply pass a locale.
- **Money widens to `double` at the service boundary.** `costInCents` is summed as `int` and divided
  by `100.0` once (`CostCalculatorImpl.java:37`), so representation error is bounded and
  reproducible; this is why value-level comparison at two decimal places works at all. The
  `WorkbookValues` / `GoldenReference` split of 2 dp for money and 6 dp for everything else encodes
  that.
- **`@Cacheable` on `findOneWithAuthoritiesByLogin`** (`repository/UserRepository.java:39-40`) is a
  live hazard for any test that mutates a user's `langKey` — `seedGoldenDataset` does exactly that
  and never evicts. It happened not to bite (probe confirmed `langKey=pl` reached the service), but
  only because the fixture writes before the first cached read in that context. A test that
  authenticates before seeding would silently get the wrong locale.

## Historical Context (from prior changes)

- `context/changes/golden-baseline-capture/reference.md` — provenance, fixture inventory, the
  eleven captured calls, the value-normalisation policy, and the five `4ad88bd` expected
  divergences. Its closing line assigns the zero-mileage decision to S-03 explicitly.
- `context/changes/session-parity/change.md` (epilogue) — hands S-03 the zero-mileage decision in
  the same words, and records that S-01's Phase 7 re-sequencing of Stream C was S-01's own call,
  with *"nothing in the manual smoke shows S-02 / S-03 / S-04 actually depend on S-07"*. So S-03 is
  not in fact gated on `client-server-contract-trial`.
- `context/archive/2026-08-25-test-context-restored/` — F-04 fixed the `application.yml` shadowing
  bug. Finding 2 is the same defect in `i18n/`, missed because F-04 scoped to configuration.
- `context/foundation/roadmap.md` §S-03 — outcome, prerequisites, and the Risk paragraph naming
  Hibernate 6 type mapping and fetch behaviour as where a silent regression would hide. Research
  answer: it did not hide there; `mileage-en.json` and `cost-en.json` both reproduce exactly.
- `context/foundation/prd.md:336` (FR-013), `:345` (FR-015), `:351` (FR-016).

## Related Research

- `context/changes/golden-baseline-capture/research.md` — capture-side design
- `context/changes/session-parity/research.md` — live-probe method reused here
- `context/archive/2026-08-27-client-server-contract-trial/research.md` — client 1.2.5 wire contract

## Open Questions

1. **Zero-consumption policy (blocking the plan, not the code).** Re-baseline
   `consumption-period-zero.json` to HEAD's 200, or keep the captured 500 and assert the divergence
   explicitly? See Finding 3. The value `averageConsumption: 0.0` conflating "unknown" with "real
   zero" is a product question that S-03 should surface even if it does not resolve it.
2. **Where does the `MailServiceIT` message-source fix live?** Finding 2's fix breaks two tests that
   belong to S-04's territory. S-03 can fix i18n resolution and repair those two tests, or S-03 can
   depend on S-04 doing it first. The former is smaller and unblocks S-03 immediately; the latter
   avoids two slices touching mail concerns. No dependency exists in the roadmap either way.
3. **Should S-03 pin the ordering hazards in Finding 5, or only note them?** Adding
   `order by vehicle.id` to `findAllByIdAndOwnerIsCurrentUser` is a one-line production change that
   makes `cost-en.json` a sound assertion rather than a lucky one — but it is a production change
   inside a parity slice, which cuts against S-03's character.
4. **Is a PL cost-report golden worth adding?** Only the vehicle report has both locales today. If
   yes, Finding 5 hazard 3 must be fixed first, and a new golden would have to be captured from the
   baseline worktree — which `reference.md`'s teardown section has already torn down.

## Decisions (2026-08-28)

All four open questions above are resolved. The plan should treat these as settled inputs.

### D1 — zero consumption: keep the golden, assert the divergence (resolves Q1)

`stats/consumption-period-zero.json` stays exactly as captured (500 + partial body). S-03 adds a
named test that records the baseline 500 and asserts HEAD's deliberate 200, following the
"documented expected divergence" pattern `context/changes/golden-baseline-capture/reference.md`
already establishes for the five `4ad88bd` fix groups.

Rejected: re-baselining the file to HEAD's 200. That makes the file assert HEAD against HEAD and
stops it detecting a regression back to the 500.

The product question — `averageConsumption: 0.0` being indistinguishable from a real zero reading —
is **surfaced, not resolved**. Returning `null` or omitting the field is a client-visible contract
change and belongs to the frozen-client discussion (S-07), not to S-03.

### D2 — the i18n shadowing fix lands in S-03 (resolves Q2)

S-03 removes `src/test/resources/i18n/messages_en.properties` and `messages_pl.properties`, fixes
bundle resolution, and repairs the two `MailServiceIT` tests that depend on `email.test.title`.

Scope boundary: S-03 repairs those assertions to match whatever the **real** production bundle
produces. It does not judge whether that English is correct — removing the stub is precisely what
makes the real EN mail text visible to tests, which is S-04's starting position. The missing
`email.reset.greeting` in `messages_pl.properties` is handed to S-04 untouched.

Rejected: blocking S-03 on S-04. No such roadmap dependency exists, and it stalls a slice that is
otherwise 10/11 done for a mechanical two-file deletion.

### D3 — ordering hazards: pin hazard 2 properly; hazards 1 and 3 are not pinned (resolves Q3)

**Hazard 2 is made genuinely deterministic.** `VehicleRichMapper`'s five `Collectors.toSet()` calls
(`VehicleRichMapper.java:64-83`) move to ordered collections, and the date-only sorts in
`VehicleReport` (`VehicleReport.java:125-126` and four more) gain an explicit secondary key. The
chosen tiebreaker is **`id` ascending**.

This cannot change any captured golden value: the golden dataset gives every vehicle distinct dates
within each sheet, so no sheet currently has a tie for the new key to break. The change removes the
latent flakiness and lifts the constraint on future fixture growth rather than merely documenting
it.

**Hazard 1 is deliberately left unpinned — accepted risk.**
`findAllByIdAndOwnerIsCurrentUser` (`VehicleRepository.java:22-23`) keeps its missing `ORDER BY`.
Consequence to carry forward: `cost-en.json`'s JSON array is still compared index-exactly
(`GoldenReference.java:326-340`) against an unordered query result. It matches on H2 and MariaDB by
insertion order, which is not a contract. If that assertion ever flakes — most likely under a
different database, a schema change, or a parallel-execution change — this is the cause, and the
one-line `order by vehicle.id` is the fix. S-05 (`vehicle-archiving`) touches this query's
neighbourhood and is the natural place to revisit it.

**Hazard 3 is not addressed**, following from D4: `WorkbookValues.shouldSort` keeps keying off the
literal English `"Costs"` (`WorkbookValues.java:30, 105-108`). Note the interaction — with D2's i18n
fix landed, a Polish cost report is named `Koszty` and would silently skip that sort. That is inert
while no PL cost golden exists, and is a prerequisite for anyone who later adds one.

### D4 — no Polish cost-report golden (resolves Q4)

Not added, in either the captured or the synthesised form. The baseline worktree and libfaketime
clock containers are torn down (`reference.md:298-303`), so a true capture means rebuilding
`6e19b96` plus MariaDB plus the clock containers for a single file. The PL cost path exercises no
code beyond the resource-bundle lookup that `reports/vehicle-pl.json` already covers.

If PL cost coverage is wanted later, the cheap route is a locale-parameterised test asserting sheet
names and headers against `messages_pl.properties` — no baseline capture needed — and it requires
hazard 3 from D3 to be fixed first.
