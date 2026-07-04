output "queue_url" {
  description = "URL of the SQS processing queue"
  value       = aws_sqs_queue.processing.url
}

output "queue_arn" {
  description = "ARN of the SQS processing queue"
  value       = aws_sqs_queue.processing.arn
}

output "queue_name" {
  description = "Name of the SQS processing queue"
  value       = aws_sqs_queue.processing.name
}

output "dlq_url" {
  description = "URL of the SQS dead letter queue"
  value       = aws_sqs_queue.dlq.url
}

output "dlq_arn" {
  description = "ARN of the SQS dead letter queue"
  value       = aws_sqs_queue.dlq.arn
}

output "event_rule_arn" {
  description = "ARN of the EventBridge rule"
  value       = aws_cloudwatch_event_rule.s3_object_created.arn
}
