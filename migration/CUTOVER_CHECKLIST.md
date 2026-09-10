# Cutover checklist

## Before maintenance

- [ ] AWS baseline is signed off using `TEST_MATRIX.md`.
- [ ] Render/Vercel preview and Supabase component tests pass.
- [ ] Auth import preserves UUID, BCrypt hash, confirmation state, full name, and server-controlled role.
- [ ] Table counts, ownership joins, status/JSON/timestamps, object counts/bytes/checksums reconcile.
- [ ] Supabase security/performance advisors reviewed; private `app` tables remain outside the Data API.
- [ ] Independent Supabase Storage export is scheduled and restore-tested.
- [ ] HCP Terraform state migration and retained AWS staging plan are reviewed.

## Maintenance window

- [ ] Enable read-only/maintenance mode and stop AWS workers.
- [ ] Take final RDS export and immutable S3 manifest; complete the delta object copy.
- [ ] Re-run database/object reconciliation and pending-upload repair.
- [ ] Deploy Render API/worker and Vercel production configuration.
- [ ] Validate CORS/callback allowlists and scan built assets for server secrets.
- [ ] Run health plus PDF/JPEG/PNG/CSV smoke flows before reopening writes.

## Rollback/retirement

- [ ] If acceptance fails before writes reopen, route CloudFront/API back to the frozen AWS deployment.
- [ ] Retain AWS application stack and snapshots for seven days after acceptance.
- [ ] Preserve final RDS dump and S3 manifest before Terraform removal.
- [ ] Destroy only replaced AWS modules; retain Textract, staging S3/KMS/IAM, lifecycle, and alarms.
- [ ] Confirm no unexplained queue messages, failed jobs, or staging objects remain.
