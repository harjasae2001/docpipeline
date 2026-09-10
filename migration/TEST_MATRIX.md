# Migration test matrix

Record each run with environment, release SHA, timestamp, tester, result, evidence link, latency, and defect ID. A target scenario passes only when it matches an AWS baseline success or an explicitly approved changed behavior.

## AWS baseline

| Area | Scenarios and evidence |
| --- | --- |
| Build/infrastructure | Backend test/package, frontend lint/build, Terraform validate/plan, startup, Flyway, UI, health, Swagger, RDS, S3, SQS, Textract; retain logs and timings. |
| Auth | Register, duplicate email, valid/invalid login, missing/malformed/expired token, protected route, logout expectation, two-user isolation; retain request/response pairs without secrets. |
| Upload | PDF/JPEG/PNG/CSV, empty, exact 50 MB, over 50 MB, unsupported MIME, unsafe name, expired URL, wrong content type, CORS, confirm before PUT, duplicate confirm, abandoned upload. |
| Queue/events | Both accepted S3 event shapes, malformed/unknown event, duplicate delivery, retry, DLQ, visibility expiry, concurrent consumers. |
| Processing | Single/multipage PDF, forms, tables, JPEG/PNG, pagination, throttling, timeout, failed/stuck job recovery, duplicate polling, and the known AWS CSV behavior. |
| Documents | Pagination, sorting, detail, ownership, signed GET and expiry, missing object, archived behavior. |
| Reports | Reject before completion, generate/repeat, download/content, ownership, missing report, URL expiry. |
| Deletion | Object deletion, archived row, repeated delete, report cleanup, post-archive access, partial failure. |
| Observability | Upload/success/failure counters, structured correlation logs, queue/DLQ depth, alarms, CPU/memory, degraded health. |

For data parity also capture table counts grouped by status/owner, primary and foreign keys, JSON/timestamps, an immutable object manifest (`key,size,content-type,checksum,owner`), queue depth, processing outputs, and p50/p95 latency.

## Supabase component checks

- JDBC: session pooler DNS/TLS/auth, pool exhaustion, reconnect, migration connection, fail-fast invalid credentials.
- Auth: signup/login/refresh/logout, imported BCrypt password, JWKS rotation, wrong issuer/audience, expiry, UUID ownership.
- Storage: signed PUT/GET, head/delete, allowlisted MIME and 50 MB limit, expiry, private anonymous denial, owner prefix, browser-secret scan.
- Queue: transactional send, read/delete/archive, visibility timeout, competing workers, delayed retry, poison message, DLQ, restart recovery.
- Network: Render API/worker to Supabase; worker to staging S3/Textract; Vercel to API/Auth with production and preview CORS/callback restrictions.
- AWS retained: SSE-KMS staging object, least-privilege denial cases, immediate cleanup, one-day lifecycle safety net, CloudWatch alarms.

## End-to-end acceptance

Run the following independently for PDF, JPEG, PNG, and CSV with two users:

1. Register or sign in, request an upload URL, PUT directly, and confirm.
2. Observe `PENDING_UPLOAD -> UPLOADED -> PROCESSING -> COMPLETED` and validate text/metadata.
3. List/open/download the original, generate/download the report, then archive/delete.
4. Verify the other user cannot access the row, original, report, or queue data.

Release gates: all AWS baseline successes pass, CSV succeeds, reconciliation is 100%, DTO/error compatibility is preserved, secrets are absent from bundles/logs/responses, and no unexplained queue jobs or staging files remain.
