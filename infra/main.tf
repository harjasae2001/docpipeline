# =============================================================================
# Root Module - Cloud-Native Document Processing Pipeline
# =============================================================================

# -----------------------------------------------------------------------------
# KMS - Encryption key for S3, RDS, and other services
# -----------------------------------------------------------------------------
module "kms" {
  source = "./modules/kms"

  project_name = var.project_name
  environment  = var.environment
}

# -----------------------------------------------------------------------------
# S3 - Document upload bucket with KMS encryption
# -----------------------------------------------------------------------------
module "s3" {
  source = "./modules/s3"

  project_name = var.project_name
  environment  = var.environment
  kms_key_arn  = module.kms.key_arn
}

# -----------------------------------------------------------------------------
# VPC - Network infrastructure with public and private subnets
# -----------------------------------------------------------------------------
module "vpc" {
  source = "./modules/vpc"

  project_name = var.project_name
  environment  = var.environment
}

# -----------------------------------------------------------------------------
# RDS - PostgreSQL database for document metadata
# -----------------------------------------------------------------------------
module "rds" {
  source = "./modules/rds"

  project_name      = var.project_name
  environment       = var.environment
  subnet_ids        = module.vpc.private_subnet_ids
  security_group_id = module.vpc.rds_sg_id
  instance_class    = var.db_instance_class
  db_username       = var.db_username
  db_password       = var.db_password
  kms_key_arn       = module.kms.key_arn
}

# -----------------------------------------------------------------------------
# EventBridge + SQS - Event-driven document processing pipeline
# -----------------------------------------------------------------------------
module "eventbridge" {
  source = "./modules/eventbridge"

  project_name   = var.project_name
  environment    = var.environment
  s3_bucket_name = module.s3.bucket_name
}

# -----------------------------------------------------------------------------
# ECS - Fargate-based document processing service
# -----------------------------------------------------------------------------
module "ecs" {
  source = "./modules/ecs"

  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  public_subnet_ids  = module.vpc.public_subnet_ids
  private_subnet_ids = module.vpc.private_subnet_ids
  alb_sg_id          = module.vpc.alb_sg_id
  ecs_sg_id          = module.vpc.ecs_sg_id
  container_image    = var.container_image
  cpu                = var.ecs_cpu
  memory             = var.ecs_memory
  desired_count      = var.ecs_desired_count
  db_host            = module.rds.endpoint
  db_port            = module.rds.port
  db_name            = module.rds.db_name
  db_username        = var.db_username
  db_password        = var.db_password
  aws_region         = var.aws_region
  s3_bucket_name     = module.s3.bucket_name
  kms_key_arn        = module.kms.key_arn
  kms_key_id         = module.kms.key_id
  sqs_queue_name     = module.eventbridge.queue_name
  jwt_secret         = var.jwt_secret
  sqs_queue_arn      = module.eventbridge.queue_arn
  s3_bucket_arn      = module.s3.bucket_arn
}

# -----------------------------------------------------------------------------
# Monitoring - CloudWatch alarms and SNS notifications
# -----------------------------------------------------------------------------
module "monitoring" {
  source = "./modules/monitoring"

  project_name     = var.project_name
  environment      = var.environment
  alert_email      = var.alert_email
  ecs_cluster_name = module.ecs.cluster_name
  ecs_service_name = module.ecs.service_name
  alb_arn_suffix   = module.ecs.alb_arn_suffix
  dlq_name         = split("/", module.eventbridge.dlq_url)[length(split("/", module.eventbridge.dlq_url)) - 1]
}
