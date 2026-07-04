# -----------------------------------------------------------------------------
# SQS Dead Letter Queue
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.project_name}-processing-dlq-${var.environment}"
  message_retention_seconds = 1209600 # 14 days

  tags = {
    Name = "${var.project_name}-processing-dlq-${var.environment}"
  }
}

# -----------------------------------------------------------------------------
# SQS Processing Queue
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "processing" {
  name                       = "${var.project_name}-processing-${var.environment}"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 1209600 # 14 days
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name = "${var.project_name}-processing-${var.environment}"
  }
}

# -----------------------------------------------------------------------------
# SQS Queue Policy - Allow EventBridge to send messages
# -----------------------------------------------------------------------------
resource "aws_sqs_queue_policy" "processing" {
  queue_url = aws_sqs_queue.processing.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEventBridgeSendMessage"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.processing.arn
      }
    ]
  })
}

# -----------------------------------------------------------------------------
# EventBridge Rule - S3 Object Created
# -----------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "s3_object_created" {
  name        = "${var.project_name}-s3-upload-${var.environment}"
  description = "Capture S3 object creation events for document processing"

  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [var.s3_bucket_name]
      }
    }
  })

  tags = {
    Name = "${var.project_name}-s3-upload-${var.environment}"
  }
}

# -----------------------------------------------------------------------------
# EventBridge Target - SQS Queue
# -----------------------------------------------------------------------------
resource "aws_cloudwatch_event_target" "sqs" {
  rule = aws_cloudwatch_event_rule.s3_object_created.name
  arn  = aws_sqs_queue.processing.arn
}
