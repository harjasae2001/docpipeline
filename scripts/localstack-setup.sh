#!/usr/bin/env bash
# =============================================================================
# LocalStack Bootstrap Script
# Runs inside the LocalStack container to provision all required AWS resources.
# =============================================================================

set -e

REGION="ap-south-1"
BUCKET_NAME="docpipeline-documents"
QUEUE_NAME="docpipeline-s3-events"
DLQ_NAME="docpipeline-s3-events-dlq"
KMS_ALIAS="alias/docpipeline-key"
ENDPOINT="http://localhost:4566"

echo "========================================"
echo " DocPipeline LocalStack Bootstrap"
echo "========================================"

# --- KMS Key ---
echo "[1/5] Creating KMS key..."
KEY_ID=$(awslocal kms create-key \
  --description "DocPipeline S3 Encryption Key" \
  --region $REGION \
  --query "KeyMetadata.KeyId" \
  --output text)
awslocal kms create-alias \
  --alias-name $KMS_ALIAS \
  --target-key-id $KEY_ID \
  --region $REGION
echo "  ✓ KMS Key: $KEY_ID (alias: $KMS_ALIAS)"

# --- S3 Bucket ---
echo "[2/5] Creating S3 bucket..."
awslocal s3api create-bucket \
  --bucket $BUCKET_NAME \
  --region $REGION \
  --create-bucket-configuration LocationConstraint=$REGION
awslocal s3api put-bucket-versioning \
  --bucket $BUCKET_NAME \
  --versioning-configuration Status=Enabled
awslocal s3api put-bucket-notification-configuration \
  --bucket $BUCKET_NAME \
  --notification-configuration '{"EventBridgeConfiguration": {}}'
echo "  ✓ S3 Bucket: $BUCKET_NAME"

# --- SQS Queues ---
echo "[3/5] Creating SQS queues..."
DLQ_URL=$(awslocal sqs create-queue \
  --queue-name $DLQ_NAME \
  --region $REGION \
  --query "QueueUrl" \
  --output text)
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url $DLQ_URL \
  --attribute-names QueueArn \
  --query "Attributes.QueueArn" \
  --output text)

QUEUE_URL=$(awslocal sqs create-queue \
  --queue-name $QUEUE_NAME \
  --region $REGION \
  --attributes "{
    \"VisibilityTimeout\": \"300\",
    \"MessageRetentionPeriod\": \"1209600\",
    \"ReceiveMessageWaitTimeSeconds\": \"20\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
  }" \
  --query "QueueUrl" \
  --output text)
echo "  ✓ SQS Queue: $QUEUE_NAME"
echo "  ✓ SQS DLQ:   $DLQ_NAME"

# --- EventBridge Rule ---
echo "[4/5] Creating EventBridge rule..."
QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url $QUEUE_URL \
  --attribute-names QueueArn \
  --query "Attributes.QueueArn" \
  --output text)
awslocal events put-rule \
  --name "docpipeline-s3-object-created" \
  --event-pattern "{\"source\":[\"aws.s3\"],\"detail-type\":[\"Object Created\"],\"detail\":{\"bucket\":{\"name\":[\"$BUCKET_NAME\"]}}}" \
  --region $REGION \
  --state ENABLED
awslocal events put-targets \
  --rule "docpipeline-s3-object-created" \
  --region $REGION \
  --targets "[{\"Id\":\"1\",\"Arn\":\"$QUEUE_ARN\"}]"
echo "  ✓ EventBridge rule → SQS"

# --- Summary ---
echo "[5/5] Done!"
echo ""
echo "========================================"
echo " Resources Summary"
echo "========================================"
echo "  S3 Bucket:     $BUCKET_NAME"
echo "  KMS Key ID:    $KEY_ID"
echo "  KMS Alias:     $KMS_ALIAS"
echo "  SQS Queue:     $QUEUE_URL"
echo "  SQS DLQ:       $DLQ_URL"
echo "========================================"
