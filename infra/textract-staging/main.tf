provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "textract_staging" {
  description             = "Encrypt temporary DocPipeline Textract inputs"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "textract_staging" {
  name          = "alias/${var.project_name}-textract-staging-${var.environment}"
  target_key_id = aws_kms_key.textract_staging.key_id
}

resource "aws_s3_bucket" "textract_staging" {
  bucket = "${var.project_name}-textract-staging-${var.environment}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "textract_staging" {
  bucket                  = aws_s3_bucket.textract_staging.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "textract_staging" {
  bucket = aws_s3_bucket.textract_staging.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.textract_staging.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "textract_staging" {
  bucket = aws_s3_bucket.textract_staging.id
  rule {
    id     = "expire-temporary-inputs"
    status = "Enabled"
    filter { prefix = "textract/" }
    expiration { days = 1 }
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
}

data "aws_iam_policy_document" "worker" {
  statement {
    actions = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.textract_staging.arn}/textract/*"]
  }
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.textract_staging.arn]
  }
  statement {
    actions = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.textract_staging.arn]
  }
  statement {
    actions = ["textract:StartDocumentAnalysis", "textract:GetDocumentAnalysis"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "worker" {
  name   = "${var.project_name}-textract-worker-${var.environment}"
  policy = data.aws_iam_policy_document.worker.json
}

resource "aws_cloudwatch_metric_alarm" "textract_server_errors" {
  alarm_name          = "${var.project_name}-textract-server-errors-${var.environment}"
  alarm_description   = "Textract server errors observed by the retained processing stack"
  namespace           = "AWS/Textract"
  metric_name         = "ServerErrorCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "textract_user_errors" {
  alarm_name          = "${var.project_name}-textract-user-errors-${var.environment}"
  alarm_description   = "Textract request, IAM, or input errors observed by the retained processing stack"
  namespace           = "AWS/Textract"
  metric_name         = "UserErrorCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}
