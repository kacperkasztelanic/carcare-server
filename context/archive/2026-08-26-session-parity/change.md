---
change_id: session-parity
title: Session parity
status: archived
created: 2026-08-26
updated: 2026-08-28
archived_at: 2026-08-28T14:05:00Z
---

## Notes

Roadmap entry **S-01** (`context/foundation/roadmap.md`), the north star. Prerequisite F-04
(`test-context-restored`) delivered and archived 2026-08-26. Parallel with S-02, S-03, S-04.

Research (2026-08-26, `research.md`) was carried out with a live probe against a booted test
context, plus a diff against the last known-good pre-migration commit `6e19b96`. Five findings
shape the plan:

1. The migration never touched this surface — all nine in-scope controllers and the entire
   `repository/` package are byte-identical to `6e19b96`. Parity risk is behavioural, not textual.
2. Owner isolation is structurally sound; no reachable hole found.
3. **Blocker:** `hibernate.auto_quote_keyword: true` (from F-04) makes every query against the
   five event tables fail. Validated two-line test-profile fix, proven green against all 115
   existing ITs. This is Phase 1.
4. **Confirmed regression:** the `X-carcareApp-*` → `X-carcare-*` rename breaks client 1.2.5's
   alert toasts on exactly the create/update/delete paths S-01 owns.
5. `DELETE /api/vehicle/{id}` is broken for any vehicle with history; S-01 must decide what
   "delete parity" means for it.

## Decided inputs

Both open questions resolved by the owner, 2026-08-26.

**1. Header rename — fix server-side.** The client stays frozen at 1.2.5, per FR-008 and the
PRD's "client breakage is the pager event" framing (`prd.md:182-184`). The observable contract
S-01 must restore is the header **name**:

- `X-carcareApp-alert`, `X-carcareApp-params`, `X-carcareApp-error` — as emitted by `6e19b96`.
- The header **value** namespace is already correct and must not change:
  `carcareApp.<entityName>.<created|updated|deleted>` (`HeaderUtil.java:32`,
  `TRANSLATION_KEY_NAMESPACE`).

Mechanism is the plan's call, but note the trap: `spring.application.name` must stay `carcare`
(it feeds logging and metrics — `application.yml:44,48`), so the fix cannot be a property
change. The natural shape mirrors what F-03 already did for the translation namespace — give
the header name its own constant independent of the application name, which in turn makes
`config/HeaderUtilInitializer.java` dead code to remove. This reverses F-03 impl-review finding
F2 (`archive/2026-08-25-jakarta-platform-migration/reviews/impl-review.md`), which accepted the
rename on reasoning that conflated the header name with the i18n key namespace; that reversal
should be stated in the epilogue so the record is not left contradictory.

Regression coverage: assert the exact header names on create/update/delete for the vehicle and
all five event types, since that is the surface the client actually loses toasts on.

**2. Delete parity — cover the working case, placeholder the broken one.** `DELETE /api/vehicle/{id}`
gets positive coverage for an event-free vehicle (200 + `X-carcareApp-alert:
carcareApp.vehicle.deleted` + row gone) and for a non-owner (404, row intact). The
with-history case — which fails against non-cascading FKs and surfaces as a 500 — is recorded
as a known pre-existing defect with a `@Disabled` placeholder test naming S-05
`vehicle-archiving` as the owner of the real fix. It is deliberately **not** fixed here: doing so
would pre-empt S-05's archiving design, and locking in the 500 as a characterization test would
make S-05 a test-breaking change for the wrong reason.

Harness note carried into the plan: that placeholder test must not be `@Transactional`, or the
FK violation flushes at test rollback rather than inside the request and the assertion never
sees a response.

## Session-parity epilogue

S-01 reverses F-03 implementation-review finding F2. The review correctly noticed two separate
uses of `carcareApp`, but accepted a header rename on reasoning that conflated the HTTP header-name
prefix with the i18n message-key root. Business-resource alerts now restore the baseline
`X-carcareApp-*` names while their values remain `carcareApp.<entity>.<action>`; those are separate
contracts.

Four previously reachable 500 responses now have their intended behavior: missing or unknown fuel
and insurance lookups return a ProblemDetail 400, duplicate vehicle IDs in an events request keep
the first period and return 200, and zero-mileage consumption returns `0.0`. The latter deliberately
conflates unknown consumption with a real zero; S-03 owns deciding that value-level behavior against
its golden baseline.

The delete-with-history case remains an `@Disabled` test because non-cascading foreign keys make it
fail; S-05 `vehicle-archiving` owns the compatible replacement. The test profile's identifier setup
now deliberately differs from MariaDB in a third way (`NON_KEYWORDS=VALUE` plus
case-insensitive identifiers), alongside the two F-04 divergences. Research open question 3 is
resolved: seeded `user`/`user` and `admin`/`admin` credentials work.

S-01 also found 18 unguarded request-side mapper `.trim()` calls. A null string still produces a
500, but client 1.2.5 normally avoids that path. The parked Bean Validation on business request
bodies item in `roadmap.md` owns the real decision: adding validation can reject payloads the frozen
client legitimately sends. `ClientWireContractIT` invariant (a) covers the response direction only,
so this suite would not detect a request-side regression there.

Manual client smoke (2026-08-27, client 1.2.5) ran against a clean MariaDB-backed WAR through
Playwright. Login, list/open vehicle, valid vehicle creation, and repair creation succeeded; the
vehicle creation displayed “Vehicle added”. A normal unauthenticated client load also produced an
`/api/account` 401 and a client-side `applicationProfile(...includes)` console error. Full event
CRUD, console hygiene, and any compatibility fixes are deliberately handed to
`client-server-contract-trial`.

### Recorded during implementation review (2026-08-27)

`InsuranceTypeDto` gained a `@JsonCreator(mode = DELEGATING)` taking a raw `JsonNode`, so the
request contract now accepts both the object form (`{"type":…,"translation":…}`) and client 1.2.5's
bare-string form (`"OC"`). This is a permanent widening of a production request shape that the plan's
Migration Notes did not anticipate — that section names only `HeaderUtil`, the deleted
`HeaderUtilInitializer`, and the four Phase 6 fixes. It is kept: Phase 6 §4 required a clean 200 for
the bare-string PUT, and both forms are covered by green integration tests. `FuelTypeDto` is
deliberately **not** given the same creator — the client never sends a bare-string fuel type, so the
asymmetry is the narrower contract, not an oversight.

The Phase 7 roadmap edit also went beyond its stated contract of "S-02's Risk paragraph only". The
same commit added slice S-07 `client-server-contract-trial`, flipped S-01's table status to
`implemented`, appended to S-03's Risk, and re-sequenced Stream C from four parallel slices to
"S-01 → S-07 (next); S-02 / S-03 / S-04 follow". The expansion is kept as-is, but the resequencing
was S-01's decision rather than the roadmap's: nothing in the manual smoke shows S-02 / S-03 / S-04
actually depend on S-07, so treat their gating as provisional.
