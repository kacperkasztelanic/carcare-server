---
change_id: client-server-contract-trial
title: Trial and fix client-server contract issues
status: implementing
created: 2026-08-27
updated: 2026-08-27
archived_at: null
---

## Notes

Exercise the frozen React client 1.2.5 against a clean local MariaDB-backed server through
Playwright, record concrete UI-to-server contract failures, and fix only the confirmed issues.
Keep the work separate from `session-parity`, whose Phase 7 manual gate exposed the need for this
focused trial.
