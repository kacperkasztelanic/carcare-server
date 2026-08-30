---
change_id: external-signing-key
title: Supply the signing key from the host, then fail fast without it
status: implementing
created: 2026-08-30
updated: 2026-08-30
roadmap_id: S-01
prd_refs: [FR-001, FR-002, FR-003, US-01, US-02]
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

Roadmap slice **S-01** in `context/foundation/roadmap.md` (the north star of the
`security-baseline` change set). Requirements live in `context/foundation/prd.md`; the
host-measurement session that preceded this change is
`context/changes/security-baseline/oq-resolution.md`.

`research.md` in this folder answers the roadmap's blocking Unknown — which environment
variable name Spring actually binds — empirically, and records a second finding the roadmap
did not anticipate: the deployed 1.3.10 image binds the key under a *different property
prefix* than this branch, so the two-step rollout has a variable-naming constraint of its own.

### Implementation status — 2026-08-30

- **Phase 1 (host key delivery)**: DONE. `CARCARE_JWT_BASE64_SECRET` added to `~/services/.env`,
  mapped to both spellings in `carcare.yml`, `.env.gpg` regenerated (`services` repo `81c9da7`,
  pushed to all three remotes). `carcare` container recreated from image `1.3.10`. Token-invalidation
  proof passed: pre-restart old token `200`, post-restart `401`, fresh login `200`. **The live
  exposure is closed as of this phase.** Round-trip `cmp` of `.env.gpg` waived by owner (no gpg
  private key on the host; regenerated locally via `genc`, recipient `3BB27BD7A0BCAB8D`).
- **Phases 2–4 (repository)**: DONE on branch `refactor`, unpushed — `47bab4d` (rotate committed
  literals), `bad035c` (fail-fast guard + 4 tests), `59325b6` (mark superseded deploy files),
  `5ce929d` / `b9b5043` (tracking). `./mvnw verify` green at 42 unit / 249 integration.
- **Phase 5 (release + production deploy)**: DEFERRED to the owner's cadence. Not started. `master`
  is ~130 commits behind `refactor`; tag/branch strategy and deploy timing are owner decisions.
  `change.md` stays `implementing` and the plan epilogue is not run until Phase 5 lands.

### Out-of-repository step — DONE (see Phase 1 above)

- **What**: add `CARCARE_JWT_BASE64_SECRET` to the gitignored `~/services/.env`
  (`openssl rand -base64 64`, generated on the host), map it from
  `/home/kacper/services/carcare.yml` to **both** `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`
  and `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`, regenerate `.env.gpg`.
- **Why both names**: only the `JHIPSTER_*` spelling binds on the deployed 1.3.10 image; the
  `APPLICATION_*` spelling makes the future alias-retirement slice repository-only.
- **Where it lives**: deployment host `vps`, compose project `services` — a separate private
  git repository. Nothing in this repository can perform or verify it.
- **Status**: not started. Must precede removal of the committed default (roadmap S-01 Risk,
  step 1 before step 2). It is Phase 1 of `plan.md`, and the declared closure point for this
  change — the live exposure ends there.
- **Verification**: token invalidation. A token captured before the restart must return 401
  afterwards; a healthy boot proves nothing, because provenance logging was dropped from the PRD.
- **Decision owner**: repo owner.

### FR-002 narrowed — reconciled 2026-08-30

Owner decision, 2026-08-30: the plan implements only the **absent/empty** half of FR-002. The
"or when it equals the value previously committed to this repository" blocklist is **dropped** —
judged not to earn its cost for a family-and-friends deployment — and a **key-length** check takes
its place, which the blocklist never provided. Rationale and residual risk are recorded in
`plan.md` § "Deviation from FR-002".

All three committed statements have been updated to the narrowed requirement: FR-002, US-01's
fourth acceptance criterion, and roadmap S-01's Outcome. FR-002's Socrates block keeps the original
resolution and records the narrowing beneath it rather than overwriting the challenge history.

### Deferred to a separate slice

Retiring the `JHIPSTER_*` environment-variable aliases — 32 across the profile YAMLs plus
`JHIPSTER_SLEEP` in `Dockerfile:4` / `entrypoint.sh:3`. Already deferred once at
`context/archive/jakarta-platform-migration/plan.md:45` as "a separate deployment-coordination
change". Phase 1 of this plan deliberately leaves the host carrying both spellings so that slice
becomes repository-only.

Precedent for recording an out-of-repo step this way:
`context/archive/2026-08-28-merge-request-ci/change.md` § Merge gate.
