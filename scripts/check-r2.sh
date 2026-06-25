#!/usr/bin/env bash
# Quick R2 connectivity check. Reads credentials from ../.env and uses the
# Dockerized AWS CLI (no local install needed) to list the bucket.
#
#   Usage:  bash scripts/check-r2.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."

# Load .env without leaking it to the shell history
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "Testing R2 bucket '${R2_BUCKET}' at ${R2_ENDPOINT} ..."
docker run --rm \
  -e AWS_ACCESS_KEY_ID="${R2_ACCESS_KEY_ID}" \
  -e AWS_SECRET_ACCESS_KEY="${R2_SECRET_ACCESS_KEY}" \
  -e AWS_DEFAULT_REGION=auto \
  amazon/aws-cli \
  --endpoint-url "${R2_ENDPOINT}" s3 ls "s3://${R2_BUCKET}" \
  && echo "R2 OK: credentials valid and bucket reachable (an empty listing is fine)."
