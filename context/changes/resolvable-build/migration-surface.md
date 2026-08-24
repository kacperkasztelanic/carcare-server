# Migration Surface — F-01 Handoff to F-03

> Generated during `resolvable-build` Phase 1. Refreshed at the end of Phase 3 to reflect
> Zalando and jjwt removal. Commands recorded so the capture is reproducible.

## javac diagnostic count

Command: `./mvnw compile 2>&1 | grep -c '^\[ERROR\] /'`

| Run | `-Xmaxerrs` | Diagnostic count |
| --- | --- | --- |
| Unflagged (arg commented out) | default javac cap | 200 |
| Flagged (this change's config) | `10000` | **882** |

The flagged run is strictly greater than the unflagged run and lands on neither count —
confirms `-Xmaxerrs 10000` is binding, not a no-op.

## Full javac diagnostic list

Command: `./mvnw compile 2>&1` (with `-Xmaxerrs 10000` active, this change's `pom.xml`)

882 `[ERROR]`-prefixed source-location lines. Full raw output captured at
`/tmp/carcare-compile-final.log` during implementation; not inlined here for length. Re-run
the command above to regenerate.

Representative early errors (first 10 source-location lines):

```
web/rest/vm/LoginVm.java:[20,6] cannot find symbol — class Size
config/SecurityConfiguration.java:[28,9] cannot find symbol — class SecurityProblemSupport
web/rest/UserJwtController.java:[38,48] cannot find symbol — class Valid
web/rest/AccountResource.java:[62,34] cannot find symbol — class Valid
web/rest/AccountResource.java:[117,30] cannot find symbol — class Valid
web/rest/vm/ManagedUserVm.java:[19,6] cannot find symbol — class Size
web/rest/UserResource.java:[97,45] cannot find symbol — class Valid
web/rest/UserResource.java:[126,48] cannot find symbol — class Valid
```

`SecurityProblemSupport` is a Phase-2-introduced diagnostic (zalando class still referenced
in `SecurityConfiguration` before Phase 2 runs); the `javax.validation.Size`/`Valid` errors
are the Jakarta namespace surface F-03 will convert.

## `javax.*` import counts (static, `src/main` and `src/test`)

Command: `grep -rhoE '^import (javax\.[a-z]+)' src/main/java | sed -E 's/^import //' | sort | uniq -c | sort -rn`

| Namespace | `src/main` | Note |
| --- | --- | --- |
| `javax.persistence` | 101 | F-03 |
| `javax.validation` | 35 | F-03 |
| `javax.servlet` | 8 | F-03 |
| `javax.transaction` | 4 | F-03 |
| `javax.annotation` | 2 | F-03 |
| `javax.mail` | 1 | F-03 |
| `javax.sql` | 1 | **JDK-owned — do not convert** (`LiquibaseConfiguration.java:18`) |
| **total** | **152** | plus 20 in `src/test` |

**Do-not-convert entry**: `javax.sql.DataSource` at
`src/main/java/com/kasztelanic/carcare/config/LiquibaseConfiguration.java:18` is part of the
JDK, not Jakarta EE. It must never become `jakarta.sql.DataSource`. Excluded from the 152/20
counts above being handed to F-03 as convertible work.

## `tech.jhipster.*` references

Command: `grep -rl 'tech\.jhipster' src/main/java src/test/java`

18 files in `src/main`:

- `aop/logging/LoggingAspect.java`
- `CarcareApp.java`
- `config/ApplicationProperties.java`
- `config/AsyncConfiguration.java`
- `config/CacheConfiguration.java`
- `config/DefaultProfileUtil.java`
- `config/LiquibaseConfiguration.java`
- `config/LocaleConfiguration.java`
- `config/LoggingAspectConfiguration.java`
- `config/LoggingConfiguration.java`
- `config/SecurityConfiguration.java`
- `config/WebConfigurer.java`
- `security/jwt/TokenProvider.java`
- `service/AuditEventService.java`
- `service/MailService.java`
- `web/rest/AuditResource.java`
- `web/rest/errors/ExceptionTranslator.java`
- `web/rest/UserResource.java`

6 files in `src/test`:

- `config/WebConfigurerTest.java`
- `security/jwt/JwtFilterTest.java`
- `security/jwt/TokenProviderTest.java`
- `service/AuditEventServiceIT.java`
- `service/MailServiceIT.java`
- `web/rest/AuditResourceIT.java`

None of these are touched by this change. `jhipster-framework` stays on the classpath
(pinned explicitly at `${jhipster-framework.version}` = `8.0.0` in `pom.xml`) as a stated
temporary bridge; removing it completes FR-002 in F-03.

## Category attribution (for Phase 3's success criterion)

Not yet applicable — Phases 2 and 3 have not run. This section will be filled in when the
inventory is refreshed at the end of Phase 3, attributing every remaining `[ERROR]` line to
one of: (a) an unconverted Jakarta namespace, (b) a Spring Security 6 API removal, (c) a
`tech.jhipster` symbol.
