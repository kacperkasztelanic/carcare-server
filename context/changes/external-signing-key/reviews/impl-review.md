<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: External Signing Key

- **Plan**: `context/changes/external-signing-key/plan.md`
- **Scope**: Phases 1–4 of 4 delivered (Phase 5 extracted to roadmap S-07)
- **Date**: 2026-08-30
- **Verdict**: NEEDS ATTENTION
- **Findings**: 1 critical, 2 warnings, 1 observation

Commits reviewed: `5ce929d` (p1 tracking), `47bab4d` (p2), `bad035c` (p3), `59325b6` (p4),
`b9b5043` / `590f047` / `612325b` (tracking).

Success criteria re-run at review time: `./mvnw verify` green — **42 unit tests** (1 pre-existing
`@Disabled` in `WebConfigurerTest`) + **249 integration tests**, 0 failures, 0 errors. Matches
criteria 3.1, 3.2 and 4.1 exactly.

**Note on the verdict**: the rubric maps a critical Safety FAIL to REJECTED. Not applied here —
Phase 1 already rotated the production key, so F1 carries no live exposure. It is a stated-end-state
miss with a one-line fix, not an open credential leak.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — The previously-committed production key is still in the tree

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `.yo-rc.json:25`
- **Detail**: `"jwtSecretKey"` holds a 172-character literal verified byte-identical to the value
  Phase 2 removed from `application-prod.yml` — the 2018 key that signed production tokens until
  Phase 1. The file is git-tracked. The plan's Desired End State is "The repository contains no key
  that has ever signed a production token" (`plan.md:57-58`), but Phase 2's automated criterion 2.3
  was written as "No file under `src/` contains the previously-committed literal", so the check
  passed while the stated goal did not. `research.md` never surfaced this site either — the whole
  key-resolution analysis is scoped to `src/`. Mitigating: the key is dead — Phase 1 rotated
  production, no JHipster generator runs in this build, and nothing in the tree reads
  `.yo-rc.json`. The harm is that a reader or a secret scanner cannot tell any of that from the file.
- **Fix**: Replace the value with an empty string (or delete the `jwtSecretKey` entry), and widen
  criterion 2.3 from `src/` to the whole tree so the check matches the goal it stands for.
  - Strength: Removes the last copy of the compromised literal and closes the gap between the
    criterion and the end state it was meant to prove.
  - Tradeoff: Minor — one JSON value plus one criterion reword.
  - Confidence: HIGH — byte-identity confirmed against `47bab4d^:application-prod.yml`; no consumer
    of the file exists in the tree.
  - Blind spot: None significant.
- **Decision**: FIXED — `.yo-rc.json` `jwtSecretKey` blanked; criterion 2.3 and its Progress row widened from `src/` to the whole tree. Verified: the literal no longer appears anywhere in the working tree and `.yo-rc.json` still parses.

### F2 — mariadb.yml left unmarked while deploy.sh names it as collateral

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/main/docker/mariadb.yml`
- **Detail**: Phase 4 scoped the historical headers to `app.yml`, `env-template` and `deploy.sh`,
  and all three landed correctly. But `deploy.sh`'s own new header states its `sed -i` "rewrite the
  `${..._ENV}` placeholders in app.yml / mariadb.yml IN PLACE" — so `mariadb.yml` is named as
  equally superseded and equally destructible, and is the one file of the four a reader can open
  with no warning attached. This is a gap in the plan's scope, not drift in the execution: the
  implementation did exactly what Phase 4 specified.
- **Fix**: Add the same `HISTORICAL — NOT DEPLOYED` header block to `src/main/docker/mariadb.yml`.
- **Decision**: FIXED — `HISTORICAL — NOT DEPLOYED` header added to `src/main/docker/mariadb.yml`, matching the three Phase 4 headers and naming its `${MARIADB_PASSWORD_ENV}` placeholder as `deploy.sh` collateral. `docker compose config` still validates the file.

### F3 — AGENTS.md § Deployment not updated after Phase 4

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: `AGENTS.md` § Deployment, § Security
- **Detail**: Two staleness points. § Deployment still closes with "Read the section below as
  history until those files are reconciled" — Phase 4 is exactly that reconciliation, and the new
  headers cross-reference `AGENTS.md`, so the two records point at each other with one of them
  saying the work is outstanding. Separately, § Security documents where the JWT key binds but not
  that the application now refuses to boot without one — the single most surprising new behavior in
  this change for anyone running the prod profile, in the file every future agent reads first.
- **Fix A ⭐ Recommended**: Update § Deployment's closing sentence to note the files are now marked
  historical (Phase 4), and add one line to § Security on the fail-fast requirement and the two
  accepted variable spellings.
  - Strength: `AGENTS.md` is the first file every future agent reads; the fail-fast behavior is a
    boot outage waiting for someone who does not know about it.
  - Tradeoff: Two more edits to a file this change did not plan to touch.
  - Confidence: HIGH — both statements are verifiably stale against the landed commits.
  - Blind spot: None significant.
- **Fix B**: Leave it — defer both edits to S-07, which touches deployment anyway and will move
  production off 1.3.10.
  - Strength: Keeps this change's scope exactly as planned; S-07 has to rewrite the version claims
    in that section regardless.
  - Tradeoff: The fail-fast note stays unwritten through however long S-07's separate cadence takes
    — and that is the window in which someone could hit it cold.
  - Confidence: MEDIUM — depends entirely on S-07's timing, which is deliberately unscheduled.
  - Blind spot: Haven't checked whether S-07's plan exists yet.
- **Decision**: FIXED via Fix A — `AGENTS.md` § Deployment's closing sentence now records that the four files carry headers as of Phase 4 and that the image-tag gap is S-07's, and § Security gained a paragraph on the fail-fast guard, both accepted variable spellings, the 64-byte minimum, and why dev/test keep committed defaults.

### F4 — Deployed client is 1.2.4, but every criterion says 1.2.5

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `plan.md:725` (Progress 1.9)
- **Detail**: Progress 1.9 honestly records "deployed client is 1.2.4" while the plan, PRD FR-003
  and the criteria carried into S-07 all say client 1.2.5. This was disclosed rather than
  rubber-stamped, so it is not a verification defect here — but S-07 inherits criteria naming a
  client version that is not the one deployed, on the checks that decide whether its release stands.
- **Fix**: Correct the client version in S-07's carried criteria (and in FR-003 if 1.2.4 is in fact
  the frozen client) before S-07 opens.
- **Decision**: FIXED, with the finding's premise corrected — `pom.xml:13` pins client 1.2.5, so FR-003 and the roadmap were right; production's 1.3.10 image simply predates that bump (tag 1.3.9 still bundles 1.2.3) and serves 1.2.4. The real defect was Phase 1's own criterion asking for a 1.2.5 session against a container that cannot serve one. Corrected `plan.md` Phase 1 criterion and Phase 5 §3, and `change.md`'s S-07 line. FR-003 and the roadmap left untouched.

## Verified clean

- The diff touches exactly the eight planned files; no unplanned changes, no scope creep.
- The Phase 3 guard sits where specified: both-empty check before the precedence branch, length
  check after `keyBytes` is derived and before `Keys.hmacShaKeyFor`.
- All four specified unit tests present (`TokenProviderTest` 9 tests total); existing tests unchanged.
- `application-prod.yml` default is empty; `application-dev.yml` and `application-test.yml` carry
  distinct fresh 64-byte values; no old literal remains anywhere under `src/`.
- Checked and cleared rather than flagged: the guard uses `StringUtils.isEmpty`, so a whitespace-only
  key passes the presence check — but it then decodes short and the length guard catches it, so the
  failure is still loud.
