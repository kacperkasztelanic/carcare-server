---
change_id: jakarta-platform-migration
title: Jakarta platform migration
status: impl_reviewed
created: 2026-08-25
updated: 2026-08-25
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

**Ownership boundary at close (Phase 3, roadmap.md updated in step):** F-03 (this change)
implements every JHipster utility replacement (`HeaderUtil`, `PaginationUtil`, `ResponseUtil`)
and renames alert/error headers on ten resources from `X-carcareApp-*` to a single
`X-carcare-*` namespace. S-02 verifies admin header/pagination/response parity against that
renamed contract and owns confirming the client does not key on the old `carcareApp` prefix.
F-04 owns the H2 dialect, the five CLOB/`TEXT` schema-validation pairs, remaining test-source
`javax.*` imports, full-context MockMvc conversion, Liquibase-before-Hibernate-validation
startup ordering, and runtime security assertions — none of which this change's green main
compile proves. `hibernate.hbm2ddl.auto: validate` is unchanged.

**Implementation review (2026-08-25, `reviews/impl-review.md`):** ten findings, all fixed. Three
change what a later slice must do:

1. **S-02's client work shrank to one line.** `HeaderUtil` now keeps the i18n key namespace
   (`carcareApp`) separate from the header name (`X-carcare-*`), so the client's `carcare.json`
   bundles need **no** change. What remains is `notification-middleware.ts`, which matches headers by
   suffix: `endsWith('app-alert')` / `'app-params'` / `'app-error'` (`:28`, `:30`, `:60`) must become
   `endsWith('-alert')` / `'-params'` / `'-error'`, then bump `carcare-client.version` here.
2. **F-04 should assert `GET /` → 200.** The Spring Security 6 chain had no terminal rule; SS5
   permitted unmatched requests, SS6 denies them, so every root SPA asset (`/`, `index.html`,
   `service-worker.js`, `manifest.webapp`, `robots.txt`, `favicon.ico`, `precache-manifest.*.js`)
   returned 401. Fixed with `.anyRequest().permitAll()`, but not runtime-confirmed.
3. **Two deliberate divergences.** `/test/**` was dropped from the security bypass although
   `plan.md:93` lists it as preserved; and gate 2.5 (`! rg carcareApp`) no longer holds literally,
   because `carcareApp` legitimately survives as an i18n key namespace. Both are explained in the
   review; do not "restore" either.

Also corrected: `UserResourceIT:96` no longer reads the deleted `jhipster.clientApp.name`, JWT
validity fields carry their pre-migration defaults again (1800 / 2592000), and the legacy
`JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET` alias is back.
