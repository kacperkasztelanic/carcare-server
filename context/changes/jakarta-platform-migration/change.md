---
change_id: jakarta-platform-migration
title: Jakarta platform migration
status: implemented
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
