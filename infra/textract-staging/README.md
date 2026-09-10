# Textract staging infrastructure

This is the only AWS runtime infrastructure required after cutover. Configure the empty `remote` backend during `terraform init` with the approved HCP Terraform organization/workspace; do not migrate state by copying state files manually.

The worker policy is intentionally not attached to an IAM user by Terraform. Attach it to the least-privilege deployment identity used by Render, and store its credentials only in Render secrets.
