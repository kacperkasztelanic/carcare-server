---
change_id: external-signing-key
title: Supply the signing key from the host, then fail fast without it
status: archived
archived_at: 2026-08-30T20:23:18Z
created: 2026-08-30
updated: 2026-08-30
roadmap_id: S-01
prd_refs: [FR-001, FR-002, US-01]
prd_refs_moved: "FR-003 and US-02 verification moved to roadmap S-07 (signing-key-release) 2026-08-30"
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
- **Phase 5 (release + production deploy)**: EXTRACTED 2026-08-30 to its own roadmap slice
  **S-07 (`signing-key-release`)** at the owner's request, to run on a separate cadence. It is no
  longer part of this change. `plan.md` Phase 5 is annotated and its Progress rows marked `[~]`
  (moved), so this change's delivered scope is Phases 1–4, all done and verified. `status` is now
  `implemented`; run `/10x-archive external-signing-key` when ready.
- **S-07 carries**: the merge/tag/deploy mechanics, FR-003's end-to-end client-1.2.5 session
  (which only the new image can serve — 1.3.10 ships client 1.2.4), and
  US-02's no-second-re-login proof. Open decision for S-07: branch/tag strategy (`master` is ~130
  commits behind `refactor`).

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

### Git history retains the old key — accepted, 2026-08-30

The impl review (F1) found the pre-rotation literal surviving at `.yo-rc.json:25`, outside Phase
2's `src/`-scoped check; it was blanked in `d1d66f8`. That clears the working tree but **not git
history** — the value is present in every commit from `6d17c37` (2018-10-29) onward, on all three
remotes including public GitHub.

**Owner decision: accept, do not purge.** The key was rotated out of production in Phase 1 and
signs nothing, so the residual is a dead credential; rewriting 120+ commits across three remotes
would cost more than it buys and invalidate every existing clone. Recorded durably in `AGENTS.md`
§ Security so a future reviewer does not re-raise it. Revisit only if the value turns out to have
been reused outside CarCare.

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
