---
change_id: vehicle-archiving
title: Vehicle archiving
status: impl_reviewed
created: 2026-08-28
updated: 2026-08-29
archived_at: null
---

## Notes

Roadmap S-05. PRD refs US-02, FR-009, FR-012, FR-015. Prerequisites S-01, S-03, S-04 all done.

Owner decisions taken 2026-08-28 during research (full rationale in `research.md`):

- **D1** Archive = soft-delete. The owner never sees the vehicle in normal list, detail, edit, or
  event workflows again; direct access to an owned archived resource returns `410 Gone`. Admins
  can restore it through a discoverable admin API.
- **D2** Cost report and cost statistics union in the caller's archived vehicles, scoped to those
  with at least one event inside the requested period. Sold-in-2025 shows up in a 2025 report,
  not a 2026 one.
- **D3** Column is `archived_at TIMESTAMP NULL` on `vehicles`; `NULL` means active.
- **D4** `DELETE /api/vehicle/{id}` becomes the archive verb, keeping its 200, its body, and the
  `carcareApp.vehicle.deleted` alert header.
- **D5** The client's "delete this vehicle and all the associated information?" copy stays — it is
  accurate from the user's point of view under D1.
- **D6** Read-only mileage/consumption statistics and single-vehicle reports remain historical for
  an owned archived vehicle. Cost reports and cost statistics append matching archived vehicles
  after the requested active segment, ordered deterministically by vehicle id.
- **D7** `archived_at` is set from the injected application `Clock`; reminder selection and normal
  event collections remain active-only, while the composite event collection returns `200` without
  archived rows.
- **D8** The admin surface exposes a paginated archived-vehicle list and an idempotent restore
  operation under `/api/admin/vehicles`; no client UI change is part of this change.
