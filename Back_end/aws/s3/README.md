# Private S3 configuration

The current web frontend already uses signed S3 PUT/GET URLs. Local file APIs remain only
for compatibility. Cognito authentication is enabled for both local and AWS profiles.

This folder contains infrastructure templates, not automatic application startup steps:

- create-and-verify-bucket.ps1 creates/configures a private bucket and changes its settings.
- cors.json allows GET/HEAD/PUT from the two local port-5500 frontend origins.
- backend-iam-policy.json scopes storage access to documents/* in the example bucket.

Review the AWS account, bucket name and region before running infrastructure scripts. Update
policy resources when using a different bucket. Application IAM role credentials should grant
only required object operations; the bucket setup script needs separate provisioning rights.
Do not put AWS access keys in source. The SDK uses its default credentials provider chain;
prefer an IAM instance role after deployment.

For local S3 tests, use an explicitly chosen AWS profile and set AWS_REGION / S3_BUCKET to the
intended environment. Keep Block Public Access enabled, ACLs disabled, and encryption enabled.
CORS must include the actual frontend origin if it differs from the template. No part of the
project review automatically executes these scripts or changes AWS policies.

Upload: allocate metadata -> signed PUT -> PUT File -> confirm completion. Preview/download
use signed GET after checking the current account's permission. Signed URLs already issued
may remain usable until expiry; deleting an object is separate from any S3 version cleanup.
