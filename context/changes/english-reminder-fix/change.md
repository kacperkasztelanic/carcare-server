---
change_id: english-reminder-fix
title: English reminder fix
status: implementing
created: 2026-08-28
updated: 2026-08-28
archived_at: null
---

## Notes

Fixes the English service-reminder e-mail, whose placeholder indices in
`messages_en.properties:35` disagree with the argument order the Thymeleaf template passes, plus a
sweep of the other i18n bundle defects. Full evidence and the settled scope decisions are in
[research.md](research.md).

Do not delete `messages_en.properties` without also setting
`spring.messages.fallback-to-system-locale: false` in the same change — see the fallback finding in
research.md.
