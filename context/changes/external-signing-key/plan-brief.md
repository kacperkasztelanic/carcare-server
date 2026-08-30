# External Signing Key — Plan Brief

> Full plan: `context/changes/external-signing-key/plan.md`
> Research: `context/changes/external-signing-key/research.md`

## What & Why

The production JWT signing key is committed to this repository at
`application-prod.yml:105`, and the live deployment never overrides it — so the running
application signs every token with a value anyone with read access can copy, including tokens for
an administrator. Because authentication is stateless, a forged token cannot be revoked. This is
roadmap **S-01**, the north star of the security-baseline change: *the server refuses to run on a
key that exists in version control.*

## Starting Point

The key resolves through seven hops from the host `.env` to `TokenProvider`, and two of them fail
**silently** — a mistyped variable falls through to a default that always works. Production runs
image tag `1.3.10`, which binds the key under a *different property prefix* (`jhipster.*`, via
`tech.jhipster.config.JHipsterProperties`) with a bare literal and no placeholder at all. The
mechanism the fix needs already exists and demonstrably works: `~/services/carcare.yml` already
resolves three secrets from a gitignored `~/services/.env` with a committed `.env.gpg`. This
change adds a fourth.

## Desired End State

Production signs tokens with a 512-bit key that exists only in `~/services/.env` and its encrypted
counterpart. No key that has ever signed a production token remains in the repository. The
application refuses to boot when neither key field is configured, naming the property and both
accepted environment-variable spellings. Owners notice exactly one thing: a single forced re-login.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Environment variable name | Host sets **both** `JHIPSTER_*` and `APPLICATION_*` from one `.env` key | Only `JHIPSTER_*` binds on the `1.3.10` image running today, so it alone makes step 1 verifiable now; adding `APPLICATION_*` makes the future alias-retirement slice repository-only | Research + Plan |
| Step-1 verification | Token invalidation (old token must 401) | Provenance logging was dropped from the PRD, so a successful boot proves nothing — only a signing-key change is observable | Plan |
| FR-002 scope | **Absent/empty check only — no value blocklist** | Owner decision: the blocklist does not earn its cost for a family-and-friends deployment. Deviation from a must-have; see Risks | Plan |
| Where the check lives | `TokenProvider.afterPropertiesSet()` | The only place that reads both fields and knows their precedence — a second validator could drift, which is how the plain-`secret` path was missed once already | Plan |
| dev / test literals | Fresh throwaway values, explicitly marked non-secret | Removes the 2018 key from the tree entirely while keeping `./mvnw` a one-command dev run and all 249 ITs booting | Plan |
| Key generation | `openssl rand -base64 64` on the host, never echoed | `Jwts.SIG.HS512` is pinned in `TokenProvider`, and jjwt 0.12.3 requires 512 bits for it — a 256-bit key boots healthy and then 500s on every login, which is why Phase 3 guards length too. Generating on the host means the value never crosses a network hop | Plan |
| Superseded docker files | Mark as historical | Owner decision: keep the record, make the status unmistakable | Plan |
| Closure point | End of Phase 1 | The live exposure closes when the host supplies a key — before any code changes | Plan |

## Scope

**In scope:** host key delivery and verification; removing the committed default; rotating the dev
and test literals; a fail-fast guard covering both `secret` and `base64Secret`; historical headers
on the three superseded docker files; release, deploy, and the FR-003 client session.

**Out of scope:** the FR-002 value blocklist (see Risks); retiring the 32 `JHIPSTER_*` aliases (its
own deployment-coordination slice, already deferred once); deleting the superseded docker files; any
client release, schema change, dual-key grace window, or token-revocation work.

## Architecture / Approach

One `.env` key → two `environment:` entries in the live compose file → container env →
Spring (relaxed binding *or* placeholder, depending on image) → `ApplicationProperties.Jwt` →
`TokenProvider.afterPropertiesSet()`.

Setting both spellings is what lets the key bind against `1.3.10` today **and** the new image after
deploy, with no host edit in between. The guard goes at the single point that reads both fields, so
it cannot drift from the precedence it protects.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Deliver the key from the host | Production stops signing with a repository key. **Closure point.** | A wrong variable name makes this *appear* to succeed while the default is still in use — which is why the proof is token invalidation, not a healthy boot |
| 2. Rotate the committed literals | The 2018 key leaves the working tree; prod default emptied | Breaking the dev run or the 249 ITs by malforming a replacement value |
| 3. Fail fast when no key is configured | Boot refuses with an actionable message, both fields covered | Guarding only `base64-secret` leaves the plain path open — the exact gap found once before |
| 4. Mark the superseded docker files | The standing trap is labelled | A header is the mitigation that already failed once; wording must be blunt |
| ~~5. Release and verify~~ | **Extracted 2026-08-30 to roadmap S-07 (`signing-key-release`).** New image live; FR-003 session discharged | A second forced re-login here means the key did not survive the deploy — halt and roll back |

**Prerequisites:** SSH access to `vps`; write access to the private `services` repository; the
`.env.gpg` passphrase or recipient key; `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`
before any Maven command.

**Estimated effort:** ~2 sessions. Phase 1 is a short operator runbook; Phases 2–4 are one small
merge. Phase 5 (the tag-triggered release) is now roadmap slice S-07.

## Open Risks & Assumptions

- **FR-002 was narrowed and the foundation documents are reconciled (2026-08-30).** The value
  blocklist is dropped by owner decision and replaced by a key-length check; FR-002, US-01's fourth
  acceptance criterion and roadmap S-01's Outcome now all state the narrowed requirement.
- **Deleting the literal does not remove it from git history.** It has been in the repository since
  `6d17c37` (2018-10-29). Rotation — not deletion — is what closes the exposure, which is why
  Phase 1 comes first and why "no usable key in version control" means *no longer usable*.
- **`.env.gpg`'s encryption scheme was not verified this session.** Phase 1 determines it with
  `gpg --list-packets` before re-encrypting, rather than assuming.
- **The exact style of `carcare.yml`'s `environment:` block was not read.** The new entries must
  match the existing `SPRING_DATASOURCE_PASSWORD` / `MAIL_PASSWORD` form.
- **The tag release path has not run since the Phase 4 edits**, and merges are not gated on a green
  pipeline, so Phase 5's pipeline must be checked deliberately rather than assumed.

## Success Criteria (Summary)

- An owner logs in once at Phase 1, then completes a normal session — list vehicles, open one,
  record an event — with unchanged paths, payloads and status codes, and is never prompted again.
- Production signs with a key that exists nowhere in this repository, and the application will not
  start without one.
- `./mvnw verify` stays green throughout — 38 unit / 249 integration through Phase 2, then 42 / 249
  once Phase 3's four guard tests land.
