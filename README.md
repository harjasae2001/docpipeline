# DocPipeline

DocPipeline uploads documents directly to private object storage, processes CSV files in Java or PDFs/images with AWS Textract, and exposes owner-scoped document and report APIs through a React application.

The active target is a hybrid Supabase/Render/Vercel architecture. The legacy AWS application infrastructure remains in `infra/` only for rollback during migration acceptance.

## Architecture

```text
Vercel React SPA
       |-- Supabase Auth
       v
Render Spring Boot API ---- Supabase Postgres
       |                    Supabase Storage
       |                    Supabase Queues (pgmq)
       v
Render background worker
       |-- CSV parser
       `-- temporary AWS S3/KMS --> AWS Textract
```

Supabase is authoritative for identities, application rows, original files, reports, and queue state. AWS remains only for Textract and its encrypted lifecycle-managed staging bucket.

## Implemented migration

- Supabase Auth replaces the custom user table, BCrypt login service, and application-signed JWTs.
- Spring Security validates Supabase JWKS tokens, issuer, audience, expiry, and UUID `sub` ownership.
- Compatibility endpoints remain at `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout`.
- JPA uses the private `app` schema through the Supabase session pooler with TLS.
- Supabase Storage keeps the direct browser PUT and 15-minute signed URL flow through its S3-compatible API.
- Upload confirmation and `pgmq.send` share one database transaction.
- The same Docker image runs as an `api` profile or a non-web `worker` profile.
- CSV is parsed locally. PDF, JPEG, and PNG inputs are copied temporarily to encrypted AWS S3 for asynchronous Textract analysis.
- Worker retries use delayed messages, bounded backoff, compare-and-set claims, a durable ledger, and an application-managed DLQ.
- The original AWS Flyway migrations and Terraform remain available for the rollback window.

The provisioned non-production project is `docpipeline` (`ianwqptxwzbvwvehdmyd`) in `ap-south-1`. Its private schema, bucket, Auth profile trigger, `pgmq` extension, and both queues are installed.

## Technology

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.3, Security resource server, JPA |
| Frontend | React 19, Vite 8, Supabase JS 2.112.4, Axios |
| Supabase | Auth, Postgres, Storage S3 API, Queues/pgmq |
| AWS retained | Textract, temporary S3, KMS, IAM, CloudWatch alarms |
| Hosting | Render API + worker, Vercel SPA |
| Delivery | Docker, GitHub Actions, Terraform |

## Configuration

Copy `.env.example` and `frontend/.env.example`, then supply secrets through your shell or deployment platform. The server needs the Supabase session-pooler JDBC connection, Supabase Storage S3 credentials, and—on the worker—least-privilege AWS staging credentials.

Never expose the Supabase secret key, database password, Storage S3 credentials, or AWS credentials through `VITE_*` variables. The browser receives only the project URL and publishable key.

## Run and verify

Backend API:

```powershell
mvn test
mvn spring-boot:run "-Dspring-boot.run.profiles=api,supabase"
```

Worker, using the same built artifact and environment:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=worker,supabase"
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm run dev
```

The API is available at `http://localhost:8080`, Swagger at `/swagger-ui.html`, and health at `/actuator/health`. A local API/worker run needs either a local Supabase stack or credentials for the provisioned project; plain PostgreSQL alone does not provide Auth, Storage, or `pgmq`.

## Deployment and migration

- [`render.yaml`](render.yaml) defines the Singapore API and worker services from one Dockerfile.
- [`frontend/vercel.json`](frontend/vercel.json) provides Vite SPA routing.
- `.github/workflows/deploy-render.yml` and `deploy-vercel.yml` build, test, and deploy the targets.
- [`infra/textract-staging`](infra/textract-staging) is the minimal retained AWS Terraform stack.
- Follow [`migration/README.md`](migration/README.md) for identity/data/object import, reconciliation, cutover, and rollback.

Legacy AWS workflows are manual rollback-only. Do not destroy that stack until acceptance is complete. Supabase database backups do not include Storage objects, so configure a separate scheduled object export before production cutover.

## Quality gates

```powershell
mvn verify
cd frontend
npm ci
npm run lint
npm run build
```

Acceptance requires AWS baseline-success parity, successful PDF/JPEG/PNG/CSV flows, 100% row/object reconciliation, cross-user isolation, no exposed secrets, and empty unexplained queue/staging backlogs.

## License

Licensed under the [MIT License](LICENSE).
