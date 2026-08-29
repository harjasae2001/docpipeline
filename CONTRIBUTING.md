# Contributing to DocPipeline

Thanks for helping improve DocPipeline.

1. Open an issue for substantial changes.
2. Create a focused branch such as `feat/document-tags` or `fix/upload-confirmation`.
3. Keep secrets out of commits; store local values in the ignored `.env` file.
4. Add or update tests with behavior changes.
5. Run the checks below before opening a pull request.

```bash
mvn verify
cd frontend
npm ci
npm run lint
npm run build
```

For Terraform changes, also run:

```bash
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
```

Pull requests should explain the problem, solution, verification, configuration or migration changes, and operational risk. Keep unrelated refactors separate. Public API changes should keep generated OpenAPI documentation accurate.

Do not report exploitable vulnerabilities or credentials in public issues. Contact the repository owner privately with sanitized reproduction details and impact.

Contributions are licensed under the MIT License.
