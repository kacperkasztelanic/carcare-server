# External Signing Key Implementation Plan

## Overview

The production JWT signing key is committed to this repository at
`src/main/resources/config/application-prod.yml:105` and the live deployment does not override
it, so the running application signs every token with a value anyone with read access can copy.
This plan closes that in a strictly ordered rollout: the deployment host supplies a freshly
generated key **while the committed default is still in place**, and only then does the
repository remove the default, refuse to boot without a key, and stop advertising a deployment
path that no longer exists.

Roadmap item **S-01** (`context/foundation/roadmap.md:109-151`), the north star of the
`security-baseline` change. PRD refs FR-001, FR-002 (narrowed — see below), FR-003, US-01, US-02.

## Current State Analysis

Established empirically in `context/changes/external-signing-key/research.md`; not re-derived here.

- **The key resolves through seven hops, two of which can silently drop a value.** Hop 5 (Spring
  property resolution, `application-prod.yml:101,105`) and hop 6 (binding, where
  `ApplicationProperties.java:13` sets `ignoreUnknownFields = true`) both fail silently, and hop 5
  always has a working default to fall back to. Every misconfiguration in this area currently
  fails *safely and invisibly* — which is exactly the property this change removes.
- **Two independent override mechanisms sit on the same YAML line.** The `${…}` placeholder is a
  literal-name lookup that *resolves* the line; relaxed binding of the canonical property
  *bypasses* it. Four environment-variable spellings work on `HEAD`.
- **Production runs image tag `1.3.10`, which binds the key under a different property prefix.**
  In `1.3.10` the block's root key is `jhipster:`, `base64-secret:` is a bare literal with **no
  placeholder at all**, and `TokenProvider` reads `tech.jhipster.config.JHipsterProperties`.
  `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` — the name this repository's own comment
  at `application-prod.yml:104` recommends — would be **silently ignored** by the container running
  production today.
- **`TokenProvider.afterPropertiesSet()` (`:46-63`) is the only reader of either key field** in
  `src/main` or `src/test`, and it is constructor parameter 0 of `SecurityConfiguration`, so every
  `@SpringBootTest` context requires it.
- **The two unit tests that touch it bypass it.** `TokenProviderTest:33-40` and
  `JwtFilterTest:33-39` construct a bare `TokenProvider` and inject `key` via
  `ReflectionTestUtils` without ever calling `afterPropertiesSet()`.
- **dev and prod share one committed literal; test carries a different one.**
  `application-prod.yml:105` and `application-dev.yml:88` hash identically (SHA-256, 172 base64
  chars each); `src/test/resources/config/application-test.yml:97` differs.
- **The live deployment is not this repository's.** Compose project `services`, config file
  `/home/kacper/services/carcare.yml`, working dir `/home/kacper/services` — a separate private
  git repository whose `carcare.yml` already resolves `${CARCARE_MYSQL_USER}`,
  `${CARCARE_MYSQL_PASSWORD}` and `${CARCARE_MAIL_PASSWORD}` from a gitignored `~/services/.env`
  with a committed `.env.gpg` counterpart. The mechanism this change needs already exists and
  demonstrably works.
- **`src/main/docker/{app.yml,env-template,deploy.sh}` have zero automated consumers.** No pom
  plugin, no CI job, no script in the tree reads them. CI's tag-only `app` job copies exactly
  `Dockerfile` and `entrypoint.sh`; Jib (`pom.xml:497-500`) has no version and no executions so it
  never runs.
- **Baseline:** `./mvnw verify` green at 38 unit + 249 integration tests.

## Desired End State

Production signs tokens with a 512-bit key that exists only in `~/services/.env` and its
encrypted counterpart. The repository contains no key that has ever signed a production token.
The application refuses to boot when neither key field is configured, naming the property and the
accepted environment-variable spellings. The superseded deployment files carry headers that make
their status unmistakable.

**Verify by:** an old token 401s while a fresh login succeeds (Phase 1); `./mvnw verify` green at
38/249 through Phase 2 and 42/249 from Phase 3 on (the four new guard tests); a booted context with
both key fields empty fails naming the property
(Phase 3); a client-1.2.5 session completes end to end against production (Phase 5).

### Key Discoveries:

- Exactly one spelling binds on **both** `1.3.10` and `HEAD`:
  `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` (`research.md` §1).
- `ApplicationProperties`'s nested config objects are eagerly-initialised `final` fields
  (`ApplicationProperties.java:26,68,73`), so `new ApplicationProperties()` yields a non-null chain
  with both jwt fields `null`. The new guard is unit-testable without a Spring context.
- Nothing in the boot log distinguishes a supplied key from the committed default — boot-time
  secret-provenance reporting was the requirement dropped from the PRD during the challenge round
  (`prd.md:300-306`). Step 1 must be verified by token invalidation, not by inspection.
- A bad key already aborts the context today (`Keys.hmacShaKeyFor` throws `WeakKeyException` under
  256 bits, surfacing as a `BeanCreationException` on `tokenProvider`). The new check joins an
  existing failure shape rather than introducing one.
- The dev/prod literal has been in git since `6d17c37` (2018-10-29). Deleting the line prevents
  *future* exposure; only rotation ends the *current* one.

## What We're NOT Doing

- **No blocklist of the previously-committed value.** See "Deviation from FR-002" below.
- **No retirement of the `JHIPSTER_*` environment-variable aliases.** 32 of them span the profile
  YAMLs, plus `JHIPSTER_SLEEP` in `Dockerfile:4` and `entrypoint.sh:3`. Retiring them is a
  deployment-coordination change already deferred once at
  `context/archive/jakarta-platform-migration/plan.md:45`. Phase 1 deliberately makes that future
  slice **repository-only** by having the host set both spellings now.
- **No deletion of `src/main/docker/{app.yml,env-template,deploy.sh}`.** Marked historical, not
  removed.
- **No client release.** Client 1.2.5 is frozen; paths, payloads and status codes do not move.
- **No schema change, no data migration, no dual-key grace window.** Rotation costs exactly one
  forced re-login, spent in Phase 1.
- **No token-revocation or token-validity change** (PRD Non-Goals).
- **No edits to `context/foundation/**` beyond the FR-002 narrowing** — FR-002, US-01's fourth
  acceptance criterion and S-01's Outcome were reconciled on 2026-08-30 (see below); nothing else in
  `context/foundation/**` is touched.
- **No writes on the VPS by this session.** Phase 1 is a runbook the operator executes.

### Deviation from FR-002 — owner-approved, reconciled into the PRD

FR-002 used to read: *"The application refuses to start when the signing key is absent, **or when
it equals the value previously committed to this repository**."* This plan implements the first
clause and **not** the second, by owner decision on 2026-08-30: the value blocklist was judged not
to earn its cost for a family-and-friends deployment. A **length** check took its place (Phase 3),
which the blocklist never provided.

Two things make the narrowing defensible rather than a quiet drop. First, the clause that survives
does the load-bearing work — a forgotten environment line fails loudly instead of silently signing
with a fallback, which is the failure this change exists to eliminate. Second, once Phase 2 removes
the literal from the YAML and Phase 1 has rotated the key, nothing would re-supply the old value
except someone deliberately pasting it back. The residual risk is exactly that.

**Reconciled 2026-08-30.** FR-002's wording, US-01's fourth acceptance criterion, and the roadmap's
S-01 Outcome have all been updated to the narrowed requirement, each carrying a dated note pointing
back here. FR-002's Socrates block retains the original resolution and records the narrowing beneath
it, so the challenge history is not overwritten.

## Implementation Approach

Five phases in a fixed order. The ordering is the substance of this plan, not a convenience:

**Phase 1 is where the live exposure closes, and it is the declared closure point for this
change.** Once the host supplies a key, production stops signing with a repository value —
before a single line of Java changes. Because the committed default is still present and still
functions as a fallback, Phase 1 cannot take production down. That is precisely why it goes
first; reversing it converts a forgotten environment line into a boot outage, which the PRD
refuses (`prd.md:436-441`, Open Question 3) and which the roadmap restructured itself to avoid
(`roadmap.md:128-142`).

Phases 2–4 are repository work and ship as **one merge**; they are split for verification
granularity, not into separate branches. Phase 5 is the release and the FR-003 verification.

The `1.3.10` divergence drives the single most consequential detail. The host sets **both**
variable spellings from one `.env` key, so the key binds against the container running today
(via `JHIPSTER_*` relaxed binding on `jhipster.*`) and against the new image after Phase 5 (via
either mechanism), with no host edit in between — and the future alias-retirement slice inherits
a host that is already correct.

## Critical Implementation Details

**Ordering and the single re-login.** The key changes at Phase 1, on the `1.3.10` container. Phases
2–5 change *no key* — the same value keeps binding, just through a different mechanism once the new
image runs. US-02's "exactly one forced re-login" is therefore spent in Phase 1 and must not be
spent again; if a second re-login is observed at Phase 5, the key did not survive the deploy and
the rollout must be halted.

**Verification of Phase 1 has one honest signal and several false ones.** Because provenance
logging was dropped from the PRD, a successful boot proves nothing — the application starts
identically whether it read the environment or fell through to the default. Container-environment
inspection proves only that Compose substitution worked (hop 2 of 7), which is exactly the
false-confidence trap the `1.3.10` divergence creates. Only token invalidation proves the signing
key actually changed.

**Never print a secret.** Every command in Phase 1 is written to avoid echoing a value: generation
appends directly to the file, verification reports lengths and counts, and environment inspection
pipes through a redacting `sed`. Bearer tokens are credentials too — the verification curls use
`-o /dev/null -w '%{http_code}'` and never render a token.

## Phase 1: Deliver the key from the host

### Overview

The operator generates a fresh 512-bit key on the VPS, adds it to `~/services/.env`, maps it to
both accepted variable spellings in `~/services/carcare.yml`, regenerates `.env.gpg`, and proves
the running container picked it up. The committed default stays untouched throughout, so this
phase cannot take production down. **This is the closure point for the change.**

Nothing in this repository performs or verifies this phase. It is a runbook, executed by the
operator on the host, in a separate private git repository.

### Changes Required:

#### 1. Generate the key and add it to the environment file

**File**: `~/services/.env` on host `vps` (gitignored, not in this repository)

**Intent**: Add one variable holding a freshly generated signing key, alongside the three
`CARCARE_*` secrets already there. Generation happens on the host so the value never crosses a
network hop and never enters a terminal this session can see.

**Contract**: New key `CARCARE_JWT_BASE64_SECRET`, naming-consistent with the existing
`CARCARE_MYSQL_USER` / `CARCARE_MYSQL_PASSWORD` / `CARCARE_MAIL_PASSWORD`. Value is 64 random
bytes, base64-encoded, no trailing newline inside the value — 88 base64 characters.

64 bytes rather than 32 because `TokenProvider:81` signs with an explicit `Jwts.SIG.HS512`, and
under jjwt 0.12.3 (`pom.xml:56`) `DefaultMacAlgorithm` rejects any key shorter than 512 bits. There
is no silent algorithm downgrade — `Keys.hmacShaKeyFor` accepts a 32-byte key and
`afterPropertiesSet()` completes, so the container boots healthy and then throws `WeakKeyException`
on **every** `/api/authenticate`. A short key therefore fails late and confusingly rather than
weakly, which is why Phase 3's guard checks length as well as presence.

The command must not echo the value:

```bash
# on vps, in ~/services
# ensure the file ends in a newline first — otherwise the append lands on the last existing
# line and silently mangles it (CARCARE_MAIL_PASSWORD would take the damage)
[ -s .env ] && [ -n "$(tail -c1 .env)" ] && echo >> .env
printf 'CARCARE_JWT_BASE64_SECRET=%s\n' "$(openssl rand -base64 64 | tr -d '\n')" >> .env
```

Confirm without printing it:

```bash
grep -c '^CARCARE_JWT_BASE64_SECRET=' .env          # expect exactly 1
awk '/^CARCARE_JWT_BASE64_SECRET=/{sub(/^[^=]*=/,""); print length($0)}' .env   # expect 88
```

Note the `sub()` rather than `-F=`: a 64-byte base64 value always ends in `==` padding, and `-F=`
splits on that too, so `$2` would report 86 and send the operator chasing a non-problem.

#### 2. Reference it from the live compose file — both spellings

**File**: `/home/kacper/services/carcare.yml` on host `vps` (separate private git repository)

**Intent**: Map the one `.env` key to both environment-variable spellings on the CarCare app
service, so the key binds against the `1.3.10` image running today and against the new image
after Phase 5, with no further host edit.

**Contract**: Two new entries in the app service's `environment:` block, matching whatever
style that block already uses for `SPRING_DATASOURCE_PASSWORD` and `MAIL_PASSWORD` (list-form
`- NAME=${VAR}` or map-form `NAME: ${VAR}` — match, do not introduce a second style):

- `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=${CARCARE_JWT_BASE64_SECRET}` — binds on
  `1.3.10` (relaxed binding on prefix `jhipster`) and on `1.3.11+` (placeholder at
  `application-prod.yml:105`).
- `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=${CARCARE_JWT_BASE64_SECRET}` — binds on
  `1.3.11+` only; inert on `1.3.10`. Present so the future alias-retirement slice needs no host
  change.

Add a comment noting both resolve from the same `.env` key and must never be edited
independently. Commit `carcare.yml` in the `services` repository — it holds no literal, only
references, exactly as it does for the three existing secrets.

#### 3. Regenerate the encrypted counterpart

**File**: `~/services/.env.gpg` on host `vps`

**Intent**: Keep the committed encrypted copy in step with the plaintext `.env`, which now has a
fourth secret.

**Contract**: Re-encrypt with the **same scheme the existing `.env.gpg` uses** — determine which
before running anything:

```bash
gpg --list-packets .env.gpg | head -5
```

A `pubkey enc packet` means public-key encryption; re-encrypt to the same recipient key id shown:

```bash
gpg --yes --encrypt --recipient <key-id-from-above> --output .env.gpg .env
```

A `symkey enc packet` means a passphrase; re-encrypt symmetrically with the same passphrase:

```bash
gpg --yes --symmetric --output .env.gpg .env
```

Then confirm the round-trip before committing — decrypt to stdout, compare byte-for-byte, never
render:

```bash
gpg --decrypt --quiet .env.gpg 2>/dev/null | cmp -s - .env && echo MATCH
```

`cmp -s` is silent on difference, so no plaintext can leak either way, and an exact comparison
catches a truncated or stale value that a line count would not. `MATCH` is the only acceptable
output; anything else means re-encrypt and re-check before committing.

Commit `.env.gpg` in the `services` repository. Confirm `.env` itself is still ignored
(`git status --short` must not list it).

#### 4. Recreate the container and verify by token invalidation

**Files**: none — an operational step on host `vps`

**Intent**: Prove the key actually reached `TokenProvider`, not merely that Compose substituted a
variable. A successful boot proves nothing; only a signing-key change is observable.

**Contract**: Capture a valid token **before** the restart, recreate the service, then assert the
old token is rejected and a fresh login works. Redact the two cheap diagnostics; never render a
token or a key.

```bash
# 1. BEFORE — obtain a token and confirm it currently works. Never echo $OLD.
OLD=$(curl -s -X POST https://<carcare-host>/api/authenticate \
        -H 'Content-Type: application/json' \
        -d '{"username":"<login>","password":"<password>","rememberMe":false}' \
      | sed -E 's/.*"id_token":"([^"]+)".*/\1/')
curl -s -o /dev/null -w '%{http_code}\n' https://<carcare-host>/api/account \
     -H "Authorization: Bearer $OLD"          # expect 200  (negative control)

# 2. Recreate with the new environment
cd ~/services && docker compose -f carcare.yml up -d

# 3. Cheap diagnostic — variable NAMES only, values redacted
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' <carcare-app-container> \
  | sed -E 's/=.*/=<redacted>/' | grep BASE64_SECRET     # expect both names

# 4. THE PROOF — the old token must now be rejected
curl -s -o /dev/null -w '%{http_code}\n' https://<carcare-host>/api/account \
     -H "Authorization: Bearer $OLD"          # expect 401

# 5. A fresh login must succeed
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<carcare-host>/api/authenticate \
     -H 'Content-Type: application/json' \
     -d '{"username":"<login>","password":"<password>","rememberMe":false}'   # expect 200
```

**If step 4 returns 200, the key did not take effect.** The variable name, the Compose
substitution, or the `.env` entry is wrong. Do not proceed to Phase 2 — Phase 2 removes the
fallback that is currently masking the failure.

**Rollback**: remove the two lines from `carcare.yml` and `docker compose up -d`. The container
returns to the committed default. Cost: one further forced re-login.

### Success Criteria:

#### Automated Verification:

- `grep -c '^CARCARE_JWT_BASE64_SECRET=' ~/services/.env` returns `1`
- `awk '/^CARCARE_JWT_BASE64_SECRET=/{sub(/^[^=]*=/,""); print length($0)}' ~/services/.env` returns `88`
- Decrypted `.env.gpg` is byte-identical to `.env` (`cmp -s` prints `MATCH`)
- `git status --short` in `~/services` does not list `.env`
- Container environment shows both `*_BASE64_SECRET` variable names (values redacted)
- Pre-restart: old token against `/api/account` returns `200`
- Post-restart: **old token against `/api/account` returns `401`**
- Post-restart: fresh `/api/authenticate` returns `200`

#### Manual Verification:

- A browser session against the **1.3.10** container prompts for login exactly once, then works
  normally. Note the client version: 1.3.10 predates this tree's bump to client 1.2.5
  (`pom.xml:13`) and serves **1.2.4**, so this criterion cannot exercise 1.2.5 and does not claim
  to. FR-003's 1.2.5 session is S-07's, against the new image
- No secret value appeared in any terminal, log, or transcript during the phase
- `carcare.yml` and `.env.gpg` are committed in the `services` repository; `.env` is not

**Implementation Note**: This phase is executed by the operator on the host, not by an agent.
Phase 2 must not begin until the `401` in step 4 is observed — it removes the fallback that would
otherwise mask a failed delivery. Pause here for confirmation.

---

## Phase 2: Rotate the committed literals

### Overview

Remove the committed default from the production profile, and replace the dev and test literals
with freshly generated, explicitly-marked non-secret values — so the 2018 key that has signed
production tokens leaves the working tree entirely, while `./mvnw` stays a one-command dev run and
all 249 integration tests keep booting.

### Changes Required:

#### 1. Production profile — remove the default, keep the placeholder

**File**: `src/main/resources/config/application-prod.yml`

**Intent**: Strip the committed literal from `:105` so no production-capable key remains in the
tree, while keeping the placeholder so `JHIPSTER_*` continues to resolve. The empty default is
what Phase 3's guard detects.

**Contract**: `base64-secret:` at `:105` becomes
`${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:}` — placeholder retained, default empty,
mirroring the `secret:` line at `:101` which already has this exact shape. The `secret:` line at
`:101` is unchanged.

Rewrite the comment at `:102-104`, which currently recommends only
`APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`. It must name **both** accepted spellings
and state that the application will not start without one — this comment is the operator-facing
documentation of the requirement.

#### 2. Development profile — fresh non-secret literal

**File**: `src/main/resources/config/application-dev.yml`

**Intent**: Replace the literal at `:88` — currently byte-identical to prod's — with a newly
generated value, so the compromised key is gone from every file in the tree. Keeping a default
here preserves the documented one-command `./mvnw` dev run in `AGENTS.md`.

**Contract**: `:88` keeps the placeholder-with-default shape; the default is a fresh
`openssl rand -base64 64 | tr -d '\n'` value. Generating and committing this is deliberate and
safe — it is a development-only value that has never signed a production token. Add a comment
above it saying exactly that, so no future reader mistakes it for production material or treats
its presence as a regression of this change.

#### 3. Test profile — fresh non-secret literal

**File**: `src/test/resources/config/application-test.yml`

**Intent**: Same treatment at `:97`. Its current literal is already distinct from prod's, but
leaving a committed key that a reader must reason about to dismiss reintroduces the ambiguity this
change removes.

**Contract**: Same shape, a second freshly generated value, same explanatory comment. Must differ
from dev's — two distinct throwaway values, not one shared constant.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./mvnw test` — 38 tests
- Integration tests pass: `./mvnw verify` — 249 tests
- No file **anywhere in the tree** contains the previously-committed literal: verify by comparing
  the SHA-256 of each remaining `base64-secret` default against the recorded prod/dev digest,
  without printing any value. Scope is the whole repository, not `src/` — the impl review found the
  literal surviving at `.yo-rc.json:25`, outside the original `src/`-only wording
- `application-prod.yml`'s `base64-secret` default is empty
- `application-dev.yml` and `application-test.yml` defaults differ from each other

#### Manual Verification:

- `./mvnw` still starts the app under the `dev` profile with no environment variable set
- The comment at `application-prod.yml:102-104` names both accepted variable spellings

**Implementation Note**: Set `export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem` before any
Maven command — the SDKMAN default is a newer JDK and the enforcer will fail the build
(`AGENTS.md` § Toolchain).

---

## Phase 3: Fail fast when no key is configured

### Overview

Refuse to start when neither key field is configured, naming the missing property and the accepted
environment variables — instead of the current `NullPointerException` from
`Decoders.BASE64.decode(null)`.

### Changes Required:

#### 1. Guard both key fields at key construction

**File**: `src/main/java/com/kasztelanic/carcare/security/jwt/TokenProvider.java`

**Intent**: Add the check at the top of `afterPropertiesSet()` (`:46`), before any key bytes are
derived. This method is the only place in the tree that reads either field and the only place that
knows their precedence, so the guard cannot drift out of sync with the code it protects — the
duplication hazard that let the plain-`secret` path be missed once already
(`context/archive/jakarta-platform-migration/reviews/impl-review.md:195-231`, finding F5).

**Contract**: Read both `secret` and `base64Secret`. If **both** are null or empty, throw
`IllegalStateException` before the existing `if (!StringUtils.isEmpty(secret))` branch at `:49`.
Then, after the derived `keyBytes` exists and before `Keys.hmacShaKeyFor` at `:57`, require
`keyBytes.length >= 64` and throw `IllegalStateException` otherwise. The existing plain-over-base64
precedence, the deprecation `log.warn` at `:50-51`, and the `Keys.hmacShaKeyFor` call itself are
otherwise unchanged.

The length half matters as much as the presence half. `createToken` at `:81` pins
`Jwts.SIG.HS512`, and jjwt 0.12.3's `DefaultMacAlgorithm` requires 512 bits for it — but
`Keys.hmacShaKeyFor` accepts anything from 256 bits up, so a 32-byte key passes construction, boots
a healthy container, and then throws `WeakKeyException` on every login. Presence-only guarding
would leave exactly the fails-late shape this phase exists to remove. (Catching `WeakKeyException`
around `hmacShaKeyFor` is not an alternative: that call does not throw for a 32-byte key — only
`createToken` does, at request time.)

The message must be actionable without the reader knowing this codebase — it is the operator's only
diagnostic. It names the canonical property
`application.security.authentication.jwt.base64-secret`, both accepted environment-variable
spellings (`APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` and the legacy
`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`), and states that the plain `secret` field is
an accepted alternative. The short-key message additionally states the required minimum (64 decoded
bytes / 512 bits, `openssl rand -base64 64`) and the length actually found — a byte count, never the
value. Do not quote, log, or otherwise render either field's value.

`TokenProvider` is constructor parameter 0 of `SecurityConfiguration`, so the throw aborts context
startup exactly as the existing `WeakKeyException` already does — a `BeanCreationException` on
`tokenProvider`. This is an existing failure shape, not a new one.

#### 2. Cover the guard with unit tests

**File**: `src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java`

**Intent**: Assert the new behaviour directly. The existing tests in this class bypass
`afterPropertiesSet()` entirely (`:33-40` injects `key` via `ReflectionTestUtils`), so nothing
currently exercises it and the guard would otherwise ship untested.

**Contract**: Two new unit tests on a bare `new TokenProvider(securityMetersService, new
ApplicationProperties())` — safe because `ApplicationProperties`'s nested config objects are
eagerly-initialised `final` fields (`ApplicationProperties.java:26,68,73`), so the chain is
non-null with both jwt fields `null`:

- both fields unset → `afterPropertiesSet()` throws `IllegalStateException`, and the message
  contains the canonical property name
- `base64Secret` set to a 32-byte (256-bit) value → `afterPropertiesSet()` throws
  `IllegalStateException`, and the message names the 64-byte minimum. Without the guard this case
  *passes* `afterPropertiesSet()` and only fails later in `createToken`, so the test must assert on
  the construction call, not on token creation.
- `base64Secret` set to a valid **64-byte** value → `afterPropertiesSet()` completes and a token can
  be created and validated. Use 64 bytes, not merely ≥256 bits: `Jwts.SIG.HS512` at `:81` rejects
  anything shorter, so a 32-byte happy-path fixture would fail at `createToken`.

A fourth test setting only the plain `secret` (to a ≥64-byte UTF-8 value) is worth adding to pin the
precedence branch the guard must not break. Existing tests in the class are unchanged.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./mvnw test` — **42** tests (38 existing plus the four new guard tests)
- Integration tests pass: `./mvnw verify` — 249, unchanged (all three profiles set a value, so the
  guard never fires in the suite)
- The new test asserting the throw fails if the guard is reverted

#### Manual Verification:

- Running the app under `prod` with no signing-key variable set fails at startup, and the message
  names the property and both environment-variable spellings — confirmed by reading the console
  output, not just the exit code
- Running it with a deliberately short key (`openssl rand -base64 32`) also fails at startup, naming
  the 64-byte minimum — not a healthy boot that 500s on the first login
- The error is legible without prior knowledge of this codebase

**Implementation Note**: Pause after this phase for confirmation of the manual startup-failure
check before proceeding.

---

## Phase 4: Mark the superseded deployment files as historical

### Overview

`src/main/docker/{app.yml,env-template,deploy.sh}` describe a deployment that is not running and
that nothing in the tree consumes. They already misled this roadmap's first draft. Roadmap Open
Question 6 asks to resolve this during S-01; the owner's decision is to mark, not delete.

### Changes Required:

#### 1. Header comments naming the live deployment

**Files**: `src/main/docker/app.yml`, `src/main/docker/env-template`,
`src/main/docker/deploy.sh`

**Intent**: Make the status of each file unmistakable at first read, so a future planner does not
mistake them for the operative deployment — which is the specific harm on record.

**Contract**: A header comment block at the top of each file stating that it is **historical and
not deployed**; that the live deployment is compose project `services`, config file
`/home/kacper/services/carcare.yml`, in a separate private repository; and that nothing in this
repository reads the file (verified 2026-08-30). Note in `app.yml` that its `environment:` block
at `:6-13` does **not** reach the running container, and that the signing key is supplied from the
live compose file instead.

`deploy.sh` needs one thing the others do not: an explicit warning that it is **destructive**. Its
`sed -i` substitutions at `:9-12` consume the `app.yml` placeholders permanently — one run makes
the template unusable — and it brings up a `carcare-app` container that is not running.

Cross-reference `AGENTS.md` § Deployment, which already carries the verified topology, so the two
records agree rather than diverging.

### Success Criteria:

#### Automated Verification:

- `./mvnw verify` still green at 42/249 — these files are inert, so this confirms no accidental
  coupling
- All three files carry a header block naming `/home/kacper/services/carcare.yml`

#### Manual Verification:

- `deploy.sh`'s header states plainly that running it destroys `app.yml`'s placeholders
- The wording does not contradict `AGENTS.md` § Deployment

---

## Phase 5: Release and verify against production

> **EXTRACTED — 2026-08-30.** At the owner's request, this phase was split out of this change into
> its own roadmap slice **S-07 (`signing-key-release`)**, to run on a separate cadence. It is kept
> here for context only; its Progress rows below are tracked in S-07, not this plan. Phases 1–4 are
> the delivered scope of `external-signing-key`, and the live exposure is already closed by Phase 1.

### Overview

Merge, tag, deploy the new image, and exercise the FR-003 client-1.2.5 session. The key does not
change here — Phase 1 already rotated it — so this deploy must cost **no** further re-login.

### Changes Required:

#### 1. Merge and tag

**Files**: none — release mechanics

**Intent**: Ship Phases 2–4 as one merge, then trigger the tag-only release path.

**Contract**: Merge `refactor`, then push a tag. `.gitlab/gitlab-ci.yml`'s tag context runs
`test`, `build`, `app`, `proxy`; the `app` job copies `src/main/docker/Dockerfile` and
`entrypoint.sh` and builds the image. Note two standing constraints from `AGENTS.md`: merges are
**not** gated on a green pipeline (`only_allow_merge_if_pipeline_succeeds` is `false` by owner
decision), so a green pipeline is advisory and must be checked deliberately; and the release path
is only ever exercised by a tag, so this is its first execution since the Phase 4 header edits.

#### 2. Deploy and verify

**Files**: none — an operational step on host `vps`

**Intent**: Move production from `1.3.10` to the new image and confirm the key survived the
transition — the point at which the fail-fast check becomes live in production.

**Contract**: Mint a fresh token against the still-running `1.3.10` container **minutes before the
swap** (`/api/authenticate`, treated as a credential — never rendered). Then update the image tag in
`~/services/carcare.yml`, `docker compose up -d`, and check:

- the container is running and healthy — proving the guard did not fire, i.e. the key bound
  through the `APPLICATION_*` or `JHIPSTER_*` path on the new image
- **that pre-deploy token still validates** — `200` from `/api/account`. This is the
  no-second-re-login check; a `401` here means the key did not survive the image change and the
  deploy must be rolled back to `1.3.10`, which still runs on the same host environment.

The token must be minted immediately before the swap, not carried over from Phase 1:
`token-validity-in-seconds` is `86400` (`application-prod.yml:107`) and the plan's own estimate puts
~2 sessions between the two phases, so a Phase-1 token would expire on its own and produce a `401`
that has nothing to do with the signing key — a false rollback trigger on the one check that decides
whether the release stands. A token minted pre-swap was signed by the Phase 1 key, so it proves key
continuity just as well without depending on elapsed time.

**Rollback**: revert the image tag to `1.3.10` and `docker compose up -d`. The `1.3.10` image
still binds `JHIPSTER_*`, so the key set in Phase 1 continues to work and no re-login is forced.

#### 3. FR-003 — client-1.2.5 session end to end

**Files**: none — manual verification

**Intent**: Discharge FR-003 and US-02, which the roadmap names as the verification half of this
slice rather than a separate concern (`roadmap.md:148-150`).

**Contract**: Through the unmodified client 1.2.5 against production: list vehicles, open one,
record an event. This is the first session that can exercise 1.2.5 at all — the new image is what
carries it (`pom.xml:13`); the 1.3.10 container Phase 1 verified against serves 1.2.4. Paths, payloads and status codes unchanged; no client release. Confirm no
additional login prompt beyond the one spent in Phase 1.

### Success Criteria:

#### Automated Verification:

- The tag pipeline is green across `test`, `build`, `app`, `proxy` (advisory, so check it
  explicitly)
- The deployed container reports the new image tag and is running
- A token minted immediately before the deploy returns `200` from `/api/account` after it

#### Manual Verification:

- A client-1.2.5 session lists vehicles, opens one, and records an event successfully
- **No login prompt during this session** — the single forced re-login was spent in Phase 1
- No stored vehicle image became unreachable
- The `~/services/carcare.yml` image-tag change is committed in the `services` repository

---

## Testing Strategy

### Unit Tests:

- `afterPropertiesSet()` with both key fields unset throws `IllegalStateException`, message names
  the canonical property
- `afterPropertiesSet()` with a valid `base64Secret` completes; the resulting key signs and
  validates a token
- `afterPropertiesSet()` with only the plain `secret` set uses it — pinning the precedence branch
  the guard must not disturb

### Integration Tests:

- The existing 249 must stay green and unchanged. They boot the real context and take the key from
  `application-test.yml:97`, which Phase 2 rotates to a fresh value of the same shape. Any failure
  here means the rotation broke the value's form, not that the guard misfired.

### Manual Testing Steps:

1. Phase 1: capture a token, restart, confirm `401` on the old token and `200` on a fresh login.
2. Phase 2: `./mvnw` starts under `dev` with no environment variable set.
3. Phase 3: start under `prod` with no signing-key variable; read the startup failure and confirm
   it names the property and both variable spellings.
4. Phase 5: full client-1.2.5 session against production, with no login prompt.

## Performance Considerations

None. The guard is two null checks plus a byte-length comparison on the startup path, executed once
per context.

## Migration Notes

No schema change and no data migration. The only migration is the signing key itself, and its cost
is one forced re-login, spent in Phase 1 (`prd.md:381-384`: no dual-key grace window, no overlap
period, by decision).

Rollback differs by phase and is cheap throughout. Phase 1 rolls back by removing two lines from
`carcare.yml` — cost, one further re-login. Phases 2–4 roll back by reverting the merge. Phase 5
rolls back by restoring the `1.3.10` image tag, which still binds `JHIPSTER_*` and therefore costs
no re-login at all.

## References

- Research: `context/changes/external-signing-key/research.md` — the key-resolution chain, the
  variable-name matrix across `1.3.10` and `HEAD`, and the fail-fast blast radius
- Roadmap: `context/foundation/roadmap.md:109-151` — S-01 and its mandatory two-step rollout
- Requirements: `context/foundation/prd.md` — FR-001, FR-002, FR-003, US-01, US-02,
  `## Constraints & Compatibility`
- Host measurement: `context/changes/security-baseline/oq-resolution.md` — deployment topology
- Alias mechanism: `context/archive/jakarta-platform-migration/plan.md:145-153`; its
  plain-`secret` gap at `reviews/impl-review.md:195-231`
- Out-of-repository step precedent: `context/archive/2026-08-28-merge-request-ci/change.md:14-28`
  and `plan.md:308-320,333-336`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Deliver the key from the host

#### Automated

- [x] 1.1 `grep -c '^CARCARE_JWT_BASE64_SECRET=' ~/services/.env` returns 1
- [x] 1.2 Key length check returns 88
- [x] 1.3 Decrypted `.env.gpg` is byte-identical to `.env` (`cmp -s` prints MATCH) — round-trip verify waived by owner; `.env.gpg` regenerated via `genc` from the current `.env`, recipient keyid `3BB27BD7A0BCAB8D` (matches prior `.env.gpg`)
- [x] 1.4 `git status --short` in `~/services` does not list `.env`
- [x] 1.5 Container environment shows both `*_BASE64_SECRET` names (values redacted)
- [x] 1.6 Pre-restart: old token returns 200 from `/api/account`
- [x] 1.7 Post-restart: old token returns 401 from `/api/account`
- [x] 1.8 Post-restart: fresh `/api/authenticate` returns 200

#### Manual

- [x] 1.9 Client-1.2.5 session prompts for login exactly once, then works normally — deployed client is 1.2.4; login once → `#/app` loads, vehicle list fetches (empty for this account), no post-login console errors
- [x] 1.10 No secret value appeared in any terminal, log, or transcript — key never rendered (generated host-side into `>>`, all diagnostics redacted); owner's temporary password was visible in `curl`/browser payloads and will be rotated
- [x] 1.11 `carcare.yml` and `.env.gpg` committed in `services`; `.env` is not — commit `81c9da7`, pushed to origin/gitlab/myszu; `.env` gitignored

### Phase 2: Rotate the committed literals

#### Automated

- [x] 2.1 Unit tests pass: `./mvnw test` — 38 tests — 47bab4d
- [x] 2.2 Integration tests pass: `./mvnw verify` — 249 tests — 47bab4d
- [x] 2.3 No file anywhere in the tree contains the previously-committed literal (digest comparison) — `grep` for both old literals across `src/` returns 0; the three `base64-secret` sites now hold the empty prod default plus two fresh distinct values — 47bab4d. Re-verified tree-wide at impl review: one surviving copy at `.yo-rc.json:25` (`jwtSecretKey`, byte-identical to the removed prod literal), blanked as review finding F1
- [x] 2.4 `application-prod.yml`'s `base64-secret` default is empty — 47bab4d
- [x] 2.5 `application-dev.yml` and `application-test.yml` defaults differ from each other — 47bab4d

#### Manual

- [x] 2.6 `./mvnw` starts under `dev` with no environment variable set — `spring-boot:run` with all three JWT env vars unset: "using profiles: dev" → "Started CarcareApp in 4.43 seconds" — 47bab4d
- [x] 2.7 Comment at `application-prod.yml:102-104` names both accepted variable spellings — rewritten to name `APPLICATION_*` (canonical) and `JHIPSTER_*` (legacy alias, only one image 1.3.10 binds), the no-start-without-one requirement, and the 64-byte minimum — 47bab4d

### Phase 3: Fail fast when no key is configured

#### Automated

- [x] 3.1 Unit tests pass: `./mvnw test` — 42 tests (38 existing plus four new guard tests) — `TokenProviderTest` now 9 tests, full suite 42 (1 pre-existing `@Disabled`) — bad035c
- [x] 3.2 Integration tests pass: `./mvnw verify` — 249, unchanged — bad035c
- [x] 3.3 The new test asserting the throw fails if the guard is reverted — stashed `TokenProvider.java`, ran both throw tests: 2 failures; restored and re-verified — bad035c
- [x] 3.4 Startup under `prod` with no variable fails, naming the property and both spellings — real Spring context (`base64-secret` + `secret` forced empty): `BeanCreationException` on `tokenProvider` → `IllegalStateException` naming `application.security.authentication.jwt.base64-secret`, `APPLICATION_*`, `JHIPSTER_*`, and the plain `secret` alternative — bad035c
- [x] 3.5 Startup with a 32-byte key fails, naming the 64-byte minimum — `IllegalStateException`: "too short: 32 bytes decoded, but the HS512 signature algorithm requires at least 64 bytes (512 bits)"; context init cancelled, not a healthy boot — bad035c
- [x] 3.6 The error is legible without prior knowledge of this codebase — both messages name the property, both env-var spellings, and the `openssl rand -base64 64` fix; the short-key message also states found vs required byte count — bad035c

### Phase 4: Mark the superseded deployment files as historical

#### Automated

- [x] 4.1 `./mvnw verify` still green at 42/249 — 42 unit (1 pre-existing `@Disabled`) + 249 integration, BUILD SUCCESS — 59325b6
- [x] 4.2 All three files carry a header naming `/home/kacper/services/carcare.yml` — `app.yml`, `env-template`, `deploy.sh` each open with a `HISTORICAL — NOT DEPLOYED` block naming the live compose path — 59325b6

#### Manual

- [x] 4.3 `deploy.sh`'s header states that running it destroys `app.yml`'s placeholders — "the `sed -i` lines ... rewrite the `${..._ENV}` placeholders in app.yml / mariadb.yml IN PLACE — one run consumes the templates permanently" — 59325b6
- [x] 4.4 Wording does not contradict `AGENTS.md` § Deployment — same topology (compose project `services`, `/home/kacper/services/carcare.yml`, separate private repo, gitignored `~/services/.env`), same destructive-`deploy.sh` and superseded-`carcare-app` notes — 59325b6

### Phase 5: Release and verify against production

> **EXTRACTED to roadmap S-07 (`signing-key-release`) on 2026-08-30.** These rows are not tracked
> by this plan. They are reproduced here only so the split is visible; the live checklist lives in
> S-07's own plan once it is opened.

- [~] 5.1 Tag pipeline green across `test`, `build`, `app`, `proxy` — moved to S-07
- [~] 5.2 Deployed container reports the new image tag and is running — moved to S-07
- [~] 5.3 A token minted immediately before the deploy returns 200 from `/api/account` after it — moved to S-07
- [~] 5.4 Client-1.2.5 session lists vehicles, opens one, records an event — moved to S-07
- [~] 5.5 No login prompt during that session — moved to S-07
- [~] 5.6 No stored vehicle image became unreachable — moved to S-07
- [~] 5.7 The `carcare.yml` image-tag change is committed in `services` — moved to S-07
