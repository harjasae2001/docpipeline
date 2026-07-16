output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = module.ecs.alb_dns_name
}

output "ecr_repository_url" {
  description = "URL of the ECR repository"
  value       = module.ecs.ecr_repository_url
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.rds.address
  sensitive   = true
}

output "s3_bucket_name" {
  description = "Name of the S3 uploads bucket"
  value       = module.s3.bucket_name
}

output "kms_key_arn" {
  description = "ARN of the KMS encryption key"
  value       = module.kms.key_arn
}

output "sqs_queue_url" {
  description = "URL of the SQS processing queue"
  value       = module.eventbridge.queue_url
}
