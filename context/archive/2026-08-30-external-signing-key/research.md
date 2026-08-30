---
date: 2026-08-30T15:06:26+0200
researcher: Claude (Opus 5) with repo owner
git_commit: 779098e2ea04de4d53bf0d4d8c8675cf809ed7e2
branch: refactor
repository: kkasztel_carcare/server
topic: "How the JWT signing key reaches the running application — repository, host, container"
tags: [research, codebase, security, jwt, configuration, spring-relaxed-binding, deployment, S-01]
status: complete
last_updated: 2026-08-30
last_updated_by: Claude (Opus 5)
---

# Research: How the JWT signing key reaches the running application

**Date**: 2026-08-30T15:06:26+0200
**Researcher**: Claude (Opus 5), with the repo owner
**Git Commit**: `779098e2ea04de4d53bf0d4d8c8675cf809ed7e2`
**Branch**: `refactor`
**Repository**: `kkasztel_carcare/server`

## Research Question

Every path by which the JWT signing key reaches the running application, across the
repository, the deployment host, and the running container — preparation for roadmap item
**S-01 `external-signing-key`**. Five sub-questions: the complete resolution chain and the
operative environment-variable name; the interaction of the two key fields; what reads the
key at boot and what a fail-fast check would break; whether the repository's docker files are
still consumed by anything; and prior art for a rollout step the repository cannot complete.

## Summary

**The blocking Unknown is resolved, empirically, at both the binding level and end to end.**
Three environment-variable spellings populate the key on this branch. Two work by Spring's
relaxed binding of the canonical property (`APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`
and `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64SECRET`); one works by literal placeholder
name (`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`). The `JHIPSTER_*` placeholder in
`application-prod.yml:105` is therefore **not** a dead leftover — it is a deliberate,
functioning compatibility alias, designed as such during the Jakarta migration.

**A finding the roadmap did not anticipate changes the rollout.** Production runs image tag
**1.3.10**, which predates the `jhipster.*` → `application.*` property rename. In 1.3.10 the
key binds under `jhipster.security.authentication.jwt.base64-secret` via `tech.jhipster.config.JHipsterProperties`,
and the YAML value is a **bare literal with no placeholder at all**. Consequently
`APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` — the name the PRD's own comment
recommends — would be **silently ignored by the container running in production today**.
Exactly one spelling works on both 1.3.10 and this branch:
`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`. Step 1 of the mandatory two-step rollout
("confirm the running container actually picks the value up") can only succeed with that name,
or after the 1.3.11 image is deployed.

**A fail-fast check is cheap to add and, scoped correctly, costs no tests.** `TokenProvider`
is the only reader of the key in the entire tree. It is a required dependency of
`SecurityConfiguration`, so every full application context needs it — but the two unit tests
that touch it bypass `afterPropertiesSet()` entirely, and the `test` profile carries its own
base64 literal that is **byte-different** from the shared dev/prod one. A check that rejects
the committed dev/prod value leaves all 287 tests green untouched; a check that demands an
externally-supplied key breaks all 249 integration tests unless the test profile is exempted.

**The repository-side half of S-01 is smaller than it looks, and partly documentation.**
`src/main/docker/app.yml`, `env-template` and `deploy.sh` have **zero automated consumers** —
no pom plugin, no CI job, no script in the tree reads them. The only repository artifacts that
reach production are `application-prod.yml` (inside the WAR), `src/main/docker/Dockerfile`, and
`src/main/docker/entrypoint.sh`, which the tag-only `app` CI job copies.

**Finally, an unavoidable consequence for FR-001.** The dev/prod literal has been in git since
the initial commit, `6d17c37` (2018-10-29). Deleting it from `HEAD` does not remove it from
history. "No usable signing key remains anywhere in version control" is satisfiable only in the
sense that the committed key stops being *usable* — i.e. **rotation is mandatory, not optional**,
and it is rotation, not deletion, that closes the exposure.

## Detailed Findings

### 1. The complete key-resolution chain

Seven hops. Only two of them can silently drop a value.

| # | Hop | Location | Can it silently drop the value? |
|---|-----|----------|--------------------------------|
| 1 | `~/services/.env` (gitignored, host) | deployment host, separate repo | Not verified this session — established ground truth |
| 2 | Compose substitution `${CARCARE_…}` → service `environment:` | `/home/kacper/services/carcare.yml` (host) | **Yes, partially** — an unset variable substitutes to the empty string with a warning only, not an error |
| 3 | Container `ENV` baked into the image | `src/main/docker/Dockerfile:3-5` | No — sets only `SPRING_OUTPUT_ANSI_ENABLED`, `JHIPSTER_SLEEP`, `JAVA_OPTS`; no JWT variable, so nothing to shadow |
| 4 | Process launch | `src/main/docker/entrypoint.sh:4` — `exec java ${JAVA_OPTS} … -jar "${HOME}/app.war" "$@"` | No — `exec` inherits the full environment; no filtering, no allowlist |
| 5 | Spring property resolution (two independent mechanisms — see below) | `src/main/resources/config/application-prod.yml:101,105` | **Yes** — a misspelled variable resolves to nothing and the YAML default silently applies |
| 6 | Binding to `@ConfigurationProperties` | `config/ApplicationProperties.java:13,75-92` | **Yes** — `ignoreUnknownFields = true` (line 13) means a misspelled *property* is discarded without error |
| 7 | Key construction | `security/jwt/TokenProvider.java:46-63` | No — but see the field-precedence trap in §2 |

#### Hop 5 in detail — two mechanisms, both live

`application-prod.yml:105` reads (literal redacted):

```yaml
base64-secret: ${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:<committed default>}
```

Two entirely separate things can override this, and they are frequently confused:

- **(a) Placeholder resolution.** The `${…}` names a specific key looked up in the
  `Environment`. An environment variable spelled *exactly*
  `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` is found by the `systemEnvironment`
  property source and replaces the default. Only this exact spelling works, because it is a
  literal lookup, not a relaxed one — there is no `@ConfigurationProperties(prefix = "jhipster")`
  on this branch for relaxed binding to attach to.
- **(b) Relaxed binding of the canonical property.** `application.security.authentication.jwt.base64-secret`
  is itself bindable from the environment, and `systemEnvironment` outranks config-data files
  in Boot's property-source order. When it hits, the whole YAML line — placeholder *and* default —
  is bypassed rather than resolved.

**Empirical results.** A probe built on the project's own classpath (Spring Boot 3.1.5's
`Binder` + `ConfigurationPropertySources` over a real `StandardEnvironment`, with a
lowest-precedence stand-in for the YAML line):

| Environment variable set | `bind(base64-secret)` | `resolvePlaceholders(the YAML line)` |
|---|---|---|
| *(none)* | `COMMITTED_DEFAULT` | `COMMITTED_DEFAULT` |
| `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | **`FROM_ENV`** | `COMMITTED_DEFAULT` |
| `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64SECRET` | **`FROM_ENV`** | `COMMITTED_DEFAULT` |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | **`FROM_ENV`** | **`FROM_ENV`** |

The two `APPLICATION_*` rows are mechanism (b) — note the placeholder column still shows the
default, which is exactly the signature of the YAML line being *bypassed* rather than resolved.
The `JHIPSTER_*` row is mechanism (a).

**End-to-end confirmation against the real application context**, not just the binder: running
`./mvnw verify -Dit.test=TestConfigurationIT -Dtest=TokenProviderTest` with
`APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` set to a deliberately weak 8-byte
base64 value fails the context with

```
Error creating bean with name 'tokenProvider' …
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 64 bits …
```

The environment variable therefore beats the profile YAML in the real config-data ordering, and
`TokenProvider` is reached through `securityConfiguration` (constructor parameter 0). The five
`TokenProviderTest` unit tests passed in the same run — see §3.

#### The 1.3.10 divergence — this changes step 1 of the rollout

Roadmap Unknown #2 on S-01 ("does the deployed 1.3.10 image differ in any way that affects the
boot path?") is answered: **yes, decisively.**

`git diff --stat 1.3.10..HEAD` over the boot path shows `ApplicationProperties.java` +123 lines,
`TokenProvider.java` rewritten, and all four profile YAMLs restructured. At tag `1.3.10`:

- `src/main/resources/config/application-prod.yml:90` — the block's root key is `jhipster:`, not
  `application:`.
- `:106` — `base64-secret:` is a **bare literal**. There is no `${…}` placeholder, so mechanism
  (a) does not exist in that image at all.
- `:101-105` — the comment even recommends `JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET`, i.e.
  the *plain* field, not the base64 one.
- There is **no `secret:` line at all** in 1.3.10's prod YAML.
- `security/jwt/TokenProvider.java:23,40,49,56` at 1.3.10 reads from
  `tech.jhipster.config.JHipsterProperties`, i.e. prefix `jhipster`.
- `config/ApplicationProperties.java:13` at 1.3.10 is `ignoreUnknownFields = false` (it is `true`
  on this branch).

Probed against the same binder, prefix `jhipster`:

| Environment variable set | `bind(jhipster.…base64-secret)` |
|---|---|
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | **binds** |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET` | **binds** |

**Consequence.** The variable-name matrix across the two images:

| Variable name | 1.3.10 (running in production) | HEAD / 1.3.11 |
|---|---|---|
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | ✅ relaxed binding on `jhipster.*` | ✅ placeholder at `application-prod.yml:105` |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET` | ✅ relaxed binding | ❌ no relaxed binding — no `jhipster` prefix exists |
| `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | ❌ **silently ignored** | ✅ relaxed binding |
| `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64SECRET` | ❌ **silently ignored** | ✅ relaxed binding |

Exactly one spelling spans both: `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`.
Recorded as a finding, not a recommendation — but note the failure mode if the wrong one is
chosen: step 1 of the rollout ("confirm the running container actually picks the value up")
would appear to succeed operationally, because the application boots either way, while the key
in use silently remains the committed default. Nothing in the boot log distinguishes the two —
the secret-provenance logging that would have shown it was the requirement dropped from the PRD
during the challenge round.

### 2. Both key fields, not one

`config/ApplicationProperties.java:75-92` binds four properties under
`application.security.authentication.jwt`: `secret`, `base64Secret`, `tokenValidityInSeconds`,
`tokenValidityInSecondsForRememberMe`.

Which profiles set which:

| Profile | file:line | `secret` | `base64-secret` |
|---|---|---|---|
| `prod` | `application-prod.yml:101,105` | `${JHIPSTER_SECURITY_AUTHENTICATION_JWT_SECRET:}` — empty default | placeholder + committed literal |
| `dev` | `application-dev.yml:86,88` | same, empty default | placeholder + **the same literal as prod** |
| `test` | `src/test/resources/config/application-test.yml:95,97` | same, empty default | placeholder + a **different** literal |
| *(no profile)* | `src/main/resources/config/application.yml` | **absent** | **absent** |

Two facts here matter for the fail-fast design:

- **dev and prod share one literal; test carries a different one.** Verified by hashing the two
  values without printing them: `application-prod.yml:105` and `application-dev.yml:88` produce
  an identical SHA-256 (both 172 base64 characters); `application-test.yml:97` produces a
  different one. So "reject the previously-committed value" is a *two-value-or-one* decision, and
  in either case it does not touch the test profile.
- **The base `application.yml` has no jwt block at all.** There is no key outside the three
  profiles; both fields would bind to `null` and `Decoders.BASE64.decode(null)` would throw. This
  is not currently reachable — every run activates a profile — but it means the property has no
  "global default" a check could lean on.

`security/jwt/TokenProvider.java:46-57` is a strict precedence, not a merge:

```java
String secret = applicationProperties.getSecurity().getAuthentication().getJwt().getSecret();
if (!StringUtils.isEmpty(secret)) {          // plain wins whenever non-empty
    log.warn("Warning: the JWT key used is not Base64-encoded. …");
    keyBytes = secret.getBytes(StandardCharsets.UTF_8);
} else {
    keyBytes = Decoders.BASE64.decode(… .getBase64Secret());
}
```

- **If both are set**, `secret` wins and `base64Secret` is never read. The only signal is a
  `WARN` line that says nothing about provenance.
- **If neither is set**, `base64Secret` is `null` → `Decoders.BASE64.decode(null)` → NPE at boot.
- **`secret` is externally settable too**, by all four analogous names. Probed:
  `APPLICATION_SECURITY_AUTHENTICATION_JWT_SECRET=PLAIN` binds `secret` while `base64-secret`
  keeps the committed default; setting both binds both, and `TokenProvider` then uses the plain
  one. This is the concrete shape of the roadmap's warning that "guarding only `base64-secret`
  leaves the other path open" — an operator who sets the *plain* variable (which is what
  1.3.10's own YAML comment at `:105` recommends) bypasses a base64-only check completely.
- **Nothing relies on the plain path today.** No profile sets a non-empty `secret`; no test
  fixture sets it; `grep` finds no other reader.

### 3. What reads the key at boot, and what a fail-fast check would break

**Readers.** `security/jwt/TokenProvider.java:48` and `:55` are the *only* places in
`src/main` or `src/test` that read either field. `ApplicationProperties` is injected in ten main
classes and eight test classes, but none of the others touch `getSecurity().getAuthentication()`.

**Reach.** `TokenProvider` is a `@Component` and a constructor dependency of
`SecurityConfiguration` (parameter 0), which is a `@Configuration` in `src/main` — so every
`@SpringBootTest` context requires it. The e2e run in §1 shows the failure cascade:
`sessionFixtures` → `securityConfiguration` → `tokenProvider`.

**Blast radius of each fail-fast shape:**

| Check | Effect on `./mvnw verify` (38 unit + 249 integration) |
|---|---|
| Reject the **dev/prod committed literal** | **No test breaks.** The `test` profile's literal is a different value (§2). |
| Reject **any key not supplied externally** | **All 249 integration tests fail.** They boot the real context and take the key from `application-test.yml:97`. Requires exempting the `test` profile, or supplying a variable in `pom.xml`'s surefire/failsafe `argLine`/`environmentVariables`. |
| Reject an **absent/empty** key | No test breaks — all three profiles set `base64-secret`. |

**The two unit tests are immune to anything placed in `afterPropertiesSet()`.** Both
`security/jwt/TokenProviderTest.java:33-40` and `security/jwt/JwtFilterTest.java:33-39`
construct `new TokenProvider(…, new ApplicationProperties())` — a bare instance with every field
at its default — and then inject `key` and the validity fields directly via
`ReflectionTestUtils.setField`. Neither calls `afterPropertiesSet()`. This was confirmed
empirically rather than by reading: in the e2e run with a weak key in the environment, the
integration context failed while `TokenProviderTest` reported `Tests run: 5, Failures: 0`.

**No test asserts the committed default.** There is no `@TestPropertySource` or
`@DynamicPropertySource` anywhere in `src/test/java`. `TokenProviderTest:36` hardcodes its own
base64 literal for key construction, unrelated to any config file. `TestConfigurationIT` — the
guard for the F-04 resource-layering fix — contains no reference to `jwt` or `secret`.

**Fail-fast is not a new class of boot failure.** A bad key already aborts the context today:
`Keys.hmacShaKeyFor` throws `WeakKeyException` for anything under 256 bits, surfacing as a
`BeanCreationException` on `tokenProvider`. Whatever S-01 adds joins an existing failure shape
rather than introducing one.

### 4. The repo-vs-host divergence — what is actually consumed

Swept exhaustively: `pom.xml`, `.gitlab/gitlab-ci.yml`, every script, Dockerfile and Makefile.

**Not consumed by anything automated:**

- `src/main/docker/app.yml` — referenced by no pom plugin (`grep "src/main/docker" pom.xml` →
  zero hits) and no CI job. Its `environment:` block (`:6-13`) sets
  `SPRING_PROFILES_ACTIVE=prod,api-docs`, the datasource URL and password, `MAIL_PASSWORD`,
  `MAIL_BASE_URL`, `JHIPSTER_SLEEP` — none of which reach the running container.
- `src/main/docker/env-template` — **zero consumers of any kind.** `deploy.sh` reads a *host*
  file (`$MISC/env`), not this template.
- `src/main/docker/deploy.sh` — a host-run script; nothing in the repository invokes it and
  nothing copies it to the host. The only line in the tree that ever copied these files out is
  `src/main/scripts/legacy/deploy.sh:10`, itself unreferenced.

**Actually consumed, and reaching production:**

- `.gitlab/gitlab-ci.yml` job `app` (tag-only) copies exactly two files:
  `cp src/main/docker/Dockerfile Dockerfile` and `cp src/main/docker/entrypoint.sh entrypoint.sh`,
  then `docker build`. Job `proxy` copies `src/main/docker/reverseproxy/*`. Job `build` runs
  `./mvnw deploy -Pprod -DskipTests` — **no `jib:` goal anywhere**.
- The Jib plugin at `pom.xml:497-500` has no `<version>` and no `<executions>`, so it never runs
  in a lifecycle phase; its `pluginManagement` config at `:608-635` (including container ENV at
  `:628-631`) is a second, unsynchronised ENV surface that CI never builds.

**Therefore, inside this repository, exactly three artifacts can affect the key at runtime:**

1. `src/main/resources/config/application-prod.yml` — packaged into the WAR. This is where the
   committed default lives and where the placeholder alias lives. **Real delivery.**
2. `src/main/docker/Dockerfile:3-5` — the `ENV` block genuinely ships, and a default declared
   there would be overridden by the live compose file. **Real delivery**, though only for
   defaults.
3. `src/main/docker/entrypoint.sh:4` — passes the environment through untouched. No change needed.

Editing `app.yml` or `env-template` is **documentation of intent only**. This is direct evidence
for roadmap Open Question 6 ("what becomes of the superseded deployment files?"): they are dead
weight with a demonstrated capacity to mislead — they already misled this roadmap's first draft.

### 5. Prior art in this tree

**The alias mechanism was designed deliberately, and this research is its first verification.**
`context/archive/jakarta-platform-migration/plan.md:145-153` specifies it:

> "**Alias mechanism** — do not introduce a second `@ConfigurationProperties(prefix = "jhipster")`
> shim … Instead give each canonical YAML key a placeholder default naming the legacy environment
> variable … `APPLICATION_*` then wins automatically, because environment variables outrank
> `application.yml` in Boot's property-source order, while `JHIPSTER_*` still resolves through the
> placeholder when no canonical variable is set."

That reasoning is now confirmed correct by measurement (§1). It also settles the AGENTS.md
tension the roadmap flagged: AGENTS.md's "the old JHipster `jhipster.*` key no longer exists in
any YAML" is true of *property keys* only — 32 `JHIPSTER_*` **environment-variable names**
survive across the profile YAMLs by design, plus `JHIPSTER_SLEEP` in `Dockerfile:4`,
`entrypoint.sh:3` and `app.yml:13`.

**The plain-`secret` gap has been found once before.**
`context/archive/jakarta-platform-migration/reviews/impl-review.md:195-231` (finding F5, MEDIUM)
caught the alias list omitting `.secret`, with a failure scenario at `:206-208` that reads as a
preview of this entire change:

> "a deployment setting that variable starts cleanly and signs tokens with the publicly committed
> base64 default — anyone reading the repo can mint an admin token. `TokenProvider.afterPropertiesSet`
> warns only when `secret` is present, so the fallback is silent."

It was fixed by adding the alias with an empty default (the `:101` / `:86` / `:95` lines today),
explicitly *not* by retiring the alias window — `plan.md:45` scoped that as "a separate
deployment-coordination change", which is S-01.

**Precedent for a step the repository cannot complete: one, and it is a good template.**
`context/archive/2026-08-28-merge-request-ci` needed a GitLab project setting flipped. The plan
gave it its own numbered work item whose *deliverable was a document* (`plan.md:308-320`), the
record went into `change.md` under `## Notes` as a labelled block — **Setting / Current value /
Where it lives / Status / Roadmap impact / Decision owner** (`change.md:14-28`) — closed by a
dated decision line, and it was verified through a "Manual Verification" bullet in the plan's
Success Criteria (`plan.md:333-336`).

**Gaps, stated plainly:**

- **No archived change has ever edited configuration on the deployment host.** The only host
  contact anywhere in `context/` is the read-only SSH inspection in
  `context/changes/security-baseline/oq-resolution.md`.
- **No convention exists for a "done in repo, pending on host" status.** No such frontmatter key
  or status value appears anywhere. The merge-request-ci precedent resolved its out-of-repo step
  to an explicit *decision* (defer) before archiving, rather than leaving it open.
- **No archived change has removed a committed default or added a fail-fast startup check.**
  Both are new ground.

## Code References

- `src/main/resources/config/application-prod.yml:97-107` — the prod jwt block; `:101` plain-secret alias, `:105` base64 placeholder + committed literal, `:104` the comment naming `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`
- `src/main/resources/config/application-dev.yml:82-90` — same shape; `:88` carries the **same** literal as prod
- `src/test/resources/config/application-test.yml:91-99` — same shape; `:97` carries a **different** literal
- `src/main/resources/config/application.yml` — no jwt block at all
- `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:13` — `ignoreUnknownFields = true`
- `src/main/java/com/kasztelanic/carcare/config/ApplicationProperties.java:75-92` — the `Jwt` class, four bound fields
- `src/main/java/com/kasztelanic/carcare/security/jwt/TokenProvider.java:46-63` — `afterPropertiesSet()`, the sole consumer; `:49` the plain-over-base64 precedence
- `src/test/java/com/kasztelanic/carcare/security/jwt/TokenProviderTest.java:33-40` — bypasses `afterPropertiesSet()` via `ReflectionTestUtils`
- `src/test/java/com/kasztelanic/carcare/security/jwt/JwtFilterTest.java:33-39` — same
- `src/main/docker/Dockerfile:3-5` — the only image `ENV` that ships
- `src/main/docker/entrypoint.sh:4` — `exec java … -jar app.war "$@"`, full environment inherited
- `src/main/docker/app.yml:6-13` — the superseded `environment:` block; no consumers
- `src/main/docker/env-template:1-3` — three keys, no consumers
- `.gitlab/gitlab-ci.yml` `app` job — copies only `Dockerfile` and `entrypoint.sh` out of `src/main/docker/`
- `pom.xml:497-500`, `:608-635` — Jib, declared without a version or executions; never runs in CI
- `git show 1.3.10:src/main/resources/config/application-prod.yml:90,106` — root key `jhipster:`, bare literal, no placeholder
- `git show 1.3.10:src/main/java/com/kasztelanic/carcare/security/jwt/TokenProvider.java:23,40,49,56` — reads `tech.jhipster.config.JHipsterProperties`

## Architecture Insights

- **Two override mechanisms coexist on the same YAML line, and they behave differently.** The
  placeholder is a literal-name lookup; relaxed binding is name-normalising. A planner who tests
  only one will draw a false general conclusion. The distinguishing signature is that relaxed
  binding *bypasses* the YAML line while the placeholder *resolves* it.
- **`ignoreUnknownFields = true` (`ApplicationProperties.java:13`) is the quiet hazard in this
  slice.** It was set during the Jakarta migration to keep the alias window open, and it means a
  mistyped property is discarded with no error. Combined with a `base64-secret` that always has a
  working default, every misconfiguration in this area fails *silently and safely* — which is
  precisely the property FR-002 exists to remove.
- **The key's exposure is a git-history fact, not a working-tree fact.** `git log -S` on the
  dev/prod literal returns two commits: `6d17c37` (2018-10-29, initial commit) and `ed7a383`
  ("Upgrade to JHipster 6.3"). Any plan that treats deleting the line as the mitigation has the
  causality backwards; deletion prevents *future* exposure, rotation ends the *current* one.
- **Nothing in the boot log distinguishes an externally-supplied key from the committed default.**
  The secret-provenance logging that would have made step 1 of the rollout self-verifying is the
  exact requirement dropped from the PRD during the challenge round. Verification of step 1 must
  therefore be done some other way — by observing that existing tokens stop validating, for
  instance, which is the same signal as US-02's forced re-login.
- **`TokenProvider` sits on the critical boot path via `SecurityConfiguration`.** There is no
  lazy-initialisation escape hatch; anything that throws in `afterPropertiesSet()` takes the whole
  application down, which is exactly the FR-002 behaviour and exactly the deploy-outage risk the
  two-step rollout exists to sequence around.

## Historical Context (from prior changes)

- `context/archive/jakarta-platform-migration/plan.md:139,145-155` — the `jhipster.*` → `application.*`
  rename and the placeholder-alias mechanism, including the full list of aliased keys.
- `context/archive/jakarta-platform-migration/plan.md:44-45` — "Removing temporary legacy
  configuration aliases; their later retirement requires a separate deployment-coordination
  change." S-01 *is* that change.
- `context/archive/jakarta-platform-migration/reviews/plan-review.md:113-116` — where the alias
  mechanism was invented, and why a second `@ConfigurationProperties(prefix="jhipster")` shim was
  rejected.
- `context/archive/jakarta-platform-migration/reviews/impl-review.md:195-231` — finding F5, the
  omitted `.secret` alias; `:199-200` records that the committed literals are byte-identical to
  `bfd3973` and were already in git, i.e. no new secret was introduced by the migration.
- `context/archive/2026-08-28-merge-request-ci/plan.md:308-320,333-336` and `change.md:14-28` —
  the template for recording and discharging an out-of-repository step.
- `context/changes/security-baseline/oq-resolution.md` — the host-measurement session; the
  precedent for keeping host findings in a sibling note with its own frontmatter flags rather
  than in the plan.

## Related Research

- `context/changes/security-baseline/oq-resolution.md` — deployment topology, the production image
  inventory, and the client's rendering of a 413.
- `context/archive/2026-08-28-merge-request-ci/research.md:68-69` — the discovery pattern for
  "the plan cannot fix this from inside the repo".
- `context/foundation/roadmap.md:109-151` — S-01 itself, including the two-step rollout this
  research feeds.

## Open Questions

1. **Which variable name should the host set?** Research records the matrix (§1) but does not
   choose. The choice is constrained: if step 1 of the rollout is to be verified against the
   *currently running* 1.3.10 container, only `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`
   can work. If step 1 is instead performed after deploying 1.3.11, all four spellings are open.
   This is a plan decision.
2. **Should the fail-fast be profile-scoped?** §3 quantifies the two shapes: a value-blocklist
   costs nothing, an "externally supplied" rule costs 249 integration tests unless the `test`
   profile is exempted or the build supplies a variable. Not decided here.
3. **Should the blocklist cover one literal or two?** dev and prod share a value; test differs.
   Blocking the shared dev/prod value alone is sufficient for production and leaves dev working
   off it — which may or may not be intended.
4. **What happens to the `JHIPSTER_*` placeholder aliases after S-01?** Removing the committed
   default is one thing; retiring the alias window is another, and `jakarta-platform-migration/plan.md:45`
   deferred it to "a separate deployment-coordination change". S-01 may be the natural place, or
   may deliberately leave the aliases in.
5. **Is `~/services/.env` the only file the key must land in?** Its encrypted counterpart
   `.env.gpg` is committed to the separate `services` repository and must be regenerated;
   whether anything else on the host reads it was not verified this session.
6. **What becomes of `src/main/docker/{app.yml,env-template,deploy.sh}`?** Roadmap Open Question 6.
   §4 supplies the missing evidence — zero consumers — but the disposition (update / mark
   historical / delete) is the owner's call.

## Method Notes

Two experiments were run; both are reproducible and neither touched the repository or the host.

1. **Binder probe.** `BindProbe.java` / `Probe2.java` compiled against the project's own
   dependency classpath (`./mvnw dependency:build-classpath -DincludeScope=test`, Java 17 from
   `~/.sdkman/candidates/java/17.0.20-tem`), driving Spring Boot 3.1.5's `Binder` over a real
   `StandardEnvironment` with `ConfigurationPropertySources.attach`. Run once per candidate
   environment-variable name. Files are in the session scratchpad, not the repository.
2. **End-to-end context boot.** `./mvnw -o verify -Dit.test=TestConfigurationIT -Dtest=TokenProviderTest -DfailIfNoTests=false`
   with `APPLICATION_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` set to a deliberately weak 8-byte
   base64 value. A `WeakKeyException` on `tokenProvider` proves the variable reached the real
   binding path over the profile YAML; the unit tests passing in the same run proves they bypass
   `afterPropertiesSet()`.

No secret value was printed at any point. The dev/prod and test literals were compared by SHA-256
of the extracted value; the git-history search used `git log -S` with the value passed through a
shell variable.
