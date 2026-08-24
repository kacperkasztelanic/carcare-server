# Migration Surface — F-01 Handoff to F-03

> Refreshed at the end of Phase 3 (jjwt migration), reflecting the tree after all three phases
> of `resolvable-build`. Commands recorded so the capture is reproducible.

## Note on raw vs. unique diagnostic counts

`./mvnw compile 2>&1 | grep -c '^\[ERROR\] /'` — the exact command this plan's success
criteria specify — counts each diagnostic **twice**: maven-compiler-plugin logs every error
inline during compilation, then reprints the identical list in its final
`Compilation failure:` summary. Maven's own `[INFO] N errors` line gives the true unique count
(confirmed: raw count / 2 == `[INFO] N errors`, exactly, at every measurement below). The
success criteria are defined against the raw (doubled) count, so that is what is reported
where a criterion asks for it; the unique count is reported alongside for clarity, since a
reader of this document should not conclude there are 796 distinct problems when there are
398.

## javac diagnostic count, end of Phase 3

Command: `./mvnw compile 2>&1 | grep -c '^\[ERROR\] /'`

| Point | Raw count | Unique count (`[INFO] N errors`) |
| --- | --- | --- |
| Phase 1 end (before Phase 2/3 changes) | 882 | 441 |
| Phase 3 end (this refresh) | 796 | 398 |

Lower on both measures, satisfying "diagnostic count is lower than Phase 1's" (criterion 3.5).
The drop (86 raw / 43 unique) is entirely Phase 2's Zalando removal and Phase 3's jjwt
migration — neither phase converts any `javax.*` import, so the Jakarta surface itself is
unchanged in size; the reduction is Zalando/jjwt-attributable diagnostics disappearing.

## Category attribution (criterion 3.4)

Every one of the 398 unique `[ERROR]` source lines was walked file-by-file
(`./mvnw compile` output, deduplicated by taking only the lines before Maven's final summary
reprint) and assigned to a cause. The plan anticipated three categories; measurement surfaced
a **fourth** that the plan's prose did not mention. Both corrections are called out below.

| Category | Files | Unique error lines | Note |
| --- | --- | --- | --- |
| (a) Unconverted Jakarta namespace | 15 domain entities, `UserDto`, `LoginVm`, `ManagedUserVm`, `AccountResource`, `UserResource`, `UserJwtController`, `JwtFilter`, `WebConfigurer`, `MailService` (1 line: `javax.mail.internet`) | 393 | F-03's core worklist — matches the 152 static `javax.*` import count from Phase 1, modulo cascading "cannot find symbol" fan-out per file |
| (b) Spring Security 6 API removal | `SecurityConfiguration.java` (`WebSecurityConfigurerAdapter`, lines 15 & 28) | 2 | F-03 / FR-004 |
| (c) `tech.jhipster` symbol | — | 0 | `jhipster-framework` is pinned and resolves cleanly; no `tech.jhipster.*` symbol is currently unresolved anywhere in the tree |
| **(d) NEW — third-party artifact rename tied to Spring version** | `MailService.java` (lines 16, 30, 39) | 3 | **Not one of the plan's three categories.** `org.thymeleaf.spring5.SpringTemplateEngine` does not exist for Spring 6; Thymeleaf's Spring-integration module is `org.thymeleaf.spring6` for this Boot version. This is a one-line import fix (`org.thymeleaf.spring5` → `org.thymeleaf.spring6`), analogous to the springdoc artifact rename Phase 1 already handled in `pom.xml`, except this one is a *source* import, not a POM coordinate — `spring-boot-starter-thymeleaf` already pulls the correct `thymeleaf-spring6` jar transitively. Flagged here as a discovered gap in the plan's three-category framing; low effort for F-03 to close. |

393 + 2 + 0 + 3 = 398. ✓

## Correction: `antMatchers` is a real, verified problem but does not currently produce a diagnostic

`plan.md`'s Success Criteria for Phase 3 states: *"`SecurityConfiguration.java:45-51,86-96`
calls `antMatchers(...)`, removed in Spring Security 6 (`AbstractRequestMatcherRegistry`
exposes only `requestMatchers`), and cascading `cannot find symbol` errors name only the
missing symbol."*

The removal itself is confirmed — `javap -p` on `spring-security-config-6.1.5`'s
`AbstractRequestMatcherRegistry` shows only `requestMatchers(...)` overloads, no
`antMatchers`. But **no diagnostic for it currently appears**: `SecurityConfiguration.java`
produces exactly 2 unique errors, both `WebSecurityConfigurerAdapter` (the `import` and the
`extends` clause), and zero errors reference `antMatchers`, `requestMatchers`, or
`AbstractRequestMatcherRegistry` anywhere in the compile output (`grep -i antMatchers` /
`requestMatchers` on the full log: no matches).

**Reason**: once `extends WebSecurityConfigurerAdapter` fails to resolve, javac cannot verify
`configure(WebSecurity)` / `configure(HttpSecurity)` as valid overrides of anything, and its
error-recovery suppresses further member-level type-checking inside that class rather than
cascading. The `antMatchers` problem is real and will surface as soon as F-03 fixes the
`WebSecurityConfigurerAdapter` issue — it just isn't currently *measurable* as a compiler
diagnostic, which is what criterion 3.4 asks this document to record. F-03 should treat
`SecurityConfiguration.java:45-51,86-96` as a known `antMatchers` → `requestMatchers` rename
independent of what today's compile output shows.

## New do-not-convert entry: `javax.crypto.SecretKey`

Phase 3 introduces `import javax.crypto.SecretKey;` in
`security/jwt/TokenProvider.java:24` (replacing `java.security.Key`, per jjwt 0.12's
type-safe `verifyWith(SecretKey)` / `signWith(SecretKey, MacAlgorithm)` API) and in
`TokenProviderTest.java`. Like `javax.sql.DataSource`, `javax.crypto` is a core JDK package
(Java Cryptography Extension) — **not** part of Jakarta EE. It must never be converted to a
`jakarta.crypto` import (no such package exists). Added to the do-not-convert list alongside
`LiquibaseConfiguration.java:18`'s `javax.sql.DataSource`.

## `javax.*` import counts (static, `src/main` and `src/test`) — changed since Phase 1

Command: `grep -rhoE '^import (javax\.[a-z]+)' src/main/java | sed -E 's/^import //' | sort | uniq -c | sort -rn`

| Namespace | `src/main` (Phase 1) | `src/main` (Phase 3, now) | Note |
| --- | --- | --- | --- |
| `javax.persistence` | 101 | 101 | F-03 |
| `javax.validation` | 35 | 35 | F-03 |
| `javax.servlet` | 8 | 7 | F-03 — Phase 2 removed `ExceptionTranslator.java`'s `javax.servlet.http.HttpServletRequest` import (replaced by `WebRequest`), as the plan explicitly intended |
| `javax.transaction` | 4 | 4 | F-03 |
| `javax.annotation` | 2 | 0 | Phase 2 removed both (`Nonnull`/`Nullable` on `ExceptionTranslator.process`, a method that no longer exists) — again explicitly intended by the plan, not a side effect |
| `javax.mail` | 1 | 1 | F-03 |
| `javax.sql` | 1 | 1 | **JDK-owned — do not convert** (`LiquibaseConfiguration.java:18`) |
| `javax.crypto` | 0 | 1 | **JDK-owned — do not convert** (`security/jwt/TokenProvider.java:24`, new in Phase 3) |
| **total** | **152** | **150** | |

`src/test` moved from 20 to 21 (`TokenProviderTest.java` gained the same `javax.crypto`
import). Of the 150 in `src/main`, 2 (`javax.sql.DataSource`, `javax.crypto.SecretKey`) are
JDK-owned and must never be converted, leaving **148** genuinely convertible for F-03 — down
from Phase 1's 151 (152 minus the one pre-existing JDK-owned `javax.sql` entry), because
Phase 2's `ExceptionTranslator` rewrite already eliminated its 3 `javax.*` imports as a
deliberate side effect of routing the request URI through `WebRequest` instead of
`HttpServletRequest`.

## `tech.jhipster.*` references — unchanged since Phase 1

Same 18 files in `src/main`, 6 in `src/test`, listed in the Phase 1 version of this document
(git history: commit `7cd2e41`). Neither Phase 2 nor Phase 3 touched any of them.

## Zalando and jjwt 0.11 API — fully removed

- `grep -rc 'org\.zalando' src/ pom.xml`: 0 everywhere (Phase 2).
- `grep -rc 'SignatureAlgorithm\|parserBuilder\|parseClaimsJws' src/`: 0 everywhere (Phase 3).
