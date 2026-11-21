terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "claude-of-duty"
      Component = "app"
      ManagedBy = "terraform"
    }
  }
}

# Kept only so that Terraform can delete the certificate left in us-east-1 by
# the abandoned Route 53 setup. Remove it after the next apply.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
