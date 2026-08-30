---
change_id: request-body-limit
title: Request body limit
status: impl_reviewed
created: 2026-08-30
updated: 2026-08-31
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

### Direct HTTP request-body boundary smoke test — 2026-08-31

The application was started directly as embedded Tomcat on `127.0.0.1:10344` with the `test`
profile, test classpath, H2, and the committed throwaway test signing key. No NGINX or other proxy
was in the request path. The process was stopped after the checks.

- Exact limit: an authenticated HTTP/1.1 `POST /api/events` declared and uploaded exactly
  `4,194,304` bytes. The body was one JSON string token spanning the complete payload, generated as
  `{ printf '["'; head -c 4194300 /dev/zero | tr '\000' 'a'; printf '"]'; }`. Curl reported
  `uploaded=4194304`; the response was the downstream MVC/Jackson `400 application/problem+json`
  (`message=error.http.400`), proving the request passed the admission filter and the full body
  reached normal application handling.
- Over limit: an authenticated HTTP/1.1 `POST /api/vehicle` declared `4,194,305` bytes and returned
  `413 application/problem+json;charset=UTF-8`. The response contained `status=413`,
  `message=error.http.413`, `path=/api/vehicle`, and the fixed non-echoing detail. The filter warning
  recorded `declaredLength=4194305`.
- Side effects: `GET /api/vehicle/all` returned `[]` before and after, with the same SHA-256
  (`4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945`). The image-file snapshot
  also remained unchanged (empty-set SHA-256
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`).
