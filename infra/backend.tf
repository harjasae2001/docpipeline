# Prerequisites:
#   1. Create S3 bucket:  aws s3api create-bucket --bucket docpipeline-terraform-state --region ap-south-1 --create-bucket-configuration LocationConstraint=ap-south-1
#   2. Enable versioning: aws s3api put-bucket-versioning --bucket docpipeline-terraform-state --versioning-configuration Status=Enabled
#   3. Create DynamoDB:   aws dynamodb create-table --table-name docpipeline-terraform-locks --attribute-definitions AttributeName=LockID,AttributeType=S --key-schema AttributeName=LockID,KeyType=HASH --billing-mode PAY_PER_REQUEST --region ap-south-1

terraform {
  backend "s3" {
    bucket         = "docpipeline-terraform-state-248094863849"
    key            = "docpipeline/terraform.tfstate"
    region         = "ap-south-1"
    encrypt        = true
    use_lockfile = "docpipeline-terraform-locks"
  }
}
