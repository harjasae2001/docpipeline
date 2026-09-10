# Migration runbook

The target Supabase project is `ianwqptxwzbvwvehdmyd` in `ap-south-1`.

Use [TEST_MATRIX.md](TEST_MATRIX.md) to capture AWS parity evidence and [CUTOVER_CHECKLIST.md](CUTOVER_CHECKLIST.md) for the maintenance window and rollback gates.

1. Put the AWS deployment into maintenance mode and export `users`, `documents`, and `processing_results` from RDS.
2. Convert the users export to a JSON array with `id`, `email`, `password_hash`, `full_name`, and `role`, then run `node scripts/import-supabase-users.mjs` with `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, and `USER_EXPORT_PATH` set.
3. Load application rows into `app.documents` and `app.processing_results`, mapping `s3_key` to `storage_key` and setting `storage_bucket` to `docpipeline-private`.
4. Generate an S3 object manifest containing key, size, content type, and checksum. Copy objects through the Supabase S3 endpoint using server-side credentials.
5. Compare row counts, user/document ownership, object counts, byte totals, and checksums before enabling writes.
6. Keep the AWS deployment and snapshots for seven days. Do not destroy the legacy `infra/` stack until target acceptance tests pass.

Never commit database passwords, Supabase secret keys, Storage S3 credentials, or AWS credentials.
