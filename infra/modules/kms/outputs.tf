output "key_id" {
  description = "ID of the KMS key"
  value       = aws_kms_key.s3_key.key_id
}

output "key_arn" {
  description = "ARN of the KMS key"
  value       = aws_kms_key.s3_key.arn
}

output "alias_name" {
  description = "Alias name of the KMS key"
  value       = aws_kms_alias.s3_key_alias.name
}
