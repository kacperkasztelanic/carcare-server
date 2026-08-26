---
change_id: session-parity
title: Session parity
status: preparing
created: 2026-08-26
updated: 2026-08-26
archived_at: null
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
