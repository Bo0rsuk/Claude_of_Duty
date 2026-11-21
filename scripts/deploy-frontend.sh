#!/usr/bin/env bash
# Builds the web application against the deployed backend and publishes it to
# the S3 bucket behind CloudFront. Run from anywhere; needs AWS credentials,
# Terraform state in infra/app and Node.js 22.12 or newer.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
infra="$root/infra/app"

# The addresses and ids of the deployed stack, as the application reads them at build time.
terraform -chdir="$infra" output -raw frontend_env > "$root/frontend/.env"

cd "$root/frontend"
npm ci
npm run build

bucket="$(terraform -chdir="$infra" output -raw frontend_bucket)"
distribution="$(terraform -chdir="$infra" output -raw frontend_distribution_id)"

# The push test page lives under its own prefix in the same bucket and is left alone.
aws s3 sync dist "s3://$bucket" --delete --exclude "push-test/*"
aws cloudfront create-invalidation --distribution-id "$distribution" --paths '/*' --query 'Invalidation.Status' --output text

echo "Published: $(terraform -chdir="$infra" output -raw frontend_url)"
