output "staging_bucket" {
  value = aws_s3_bucket.textract_staging.id
}

output "kms_key_arn" {
  value = aws_kms_key.textract_staging.arn
}

output "worker_policy_arn" {
  value = aws_iam_policy.worker.arn
}
