# Claude of Duty

Alerting platform in the spirit of PagerDuty: it ingests events from many
sources and formats, turns them into alerts with rules that can be changed at
runtime, and notifies the right people over SMS, voice, email and a live web feed.

**Status:** work in progress. Deployed: the mock data sources and the
application infrastructure. Implemented: webhook ingest, the processor
(parsing, rules, deduplication, alert storage, live feed publishing), the REST
API and notifications (SMS, email, in-app, simulated voice) with throttling and
dead-letter retries, the connector worker for HTTP and Kafka sources, and the
load test. Next: the rule preview and AI assistant, and the web application. See [TASKS.md](TASKS.md) for the plan and the current state.

## Architecture

```
mock sources: JSON API, application log, access log, Kafka, webhook
  -> connector worker (pull) / API Gateway writing straight to the stream (push)
  -> Kinesis Data Streams
  -> processor Lambda: parse -> normalize -> rules -> deduplicate -> throttle
  -> DynamoDB (alerts, timeline) + AppSync Events -> browser (live feed)
  -> SQS queue per channel (+ dead-letter queue) -> notifier Lambda: SMS, voice, email, UI
```

Sources, rules, recipient groups and runbooks are DynamoDB items. Adding or
changing one takes effect within seconds, without a deployment or restart.

## Repository layout

| Path | Contents |
|---|---|
| `Docs/contracts/` | Data shapes, DynamoDB tables, REST API and real-time channels. Start here. |
| `Docs/frontend-integration/` | Guide for the web application: sign-in, API calls, live feed client, TypeScript types. |
| `backend/core/` | Domain model and logic. No AWS dependencies, tested locally. |
| `backend/aws/` | Adapters from the core's ports to AWS services, shared by the Lambdas and the worker. |
| `backend/lambdas/` | Lambda handlers. Builds the one jar all functions run from. |
| `backend/worker/` | Connector worker: pulls from HTTP and Kafka sources and writes to the stream. |
| `infra/app/` | Terraform for the application: Kinesis, DynamoDB, Lambda, API Gateway, Cognito, AppSync Events, SQS. |
| `demo/` | Demo data and ready-made API requests. |
| `infra/datasource/` | Terraform for the mock data source instance. |
| `loadtest/` | Load test script, how it measures, and the measured results. |
| `mocks/datasource/` | The mock data sources. See its [README](mocks/datasource/README.md) for endpoints and formats. |

## Prerequisites

- JDK 21 or newer. Maven is not needed: the wrapper in `backend/` downloads it.
- Terraform 1.5 or newer and AWS credentials for the target account.
- Docker, only for running the mock data sources locally.

## Build and test

```bash
cd backend
./mvnw package
```

This runs the tests and produces `backend/lambdas/target/lambdas.jar` and `backend/worker/target/worker.jar`.

## Deploy

Build first, because Terraform uploads both jars.

```bash
cd backend && ./mvnw package && cd ..
cd infra/app
terraform init
terraform apply
```

Settings are in `infra/app/terraform.tfvars`. After `apply`:

- `terraform output frontend_env` prints the variables for `frontend/.env`.
- `terraform output ingest_url` prints the webhook URL pattern.

To redeploy after a code change, run the same two commands again: Terraform
notices the new jars, updates the functions and replaces the worker instance.

### Pausing to save cost

The Kinesis stream is the only application resource billed by the hour.
Everything else costs nothing while idle. To delete just the stream and keep
tables, users and settings:

```bash
terraform apply -var stream_enabled=false   # pause
terraform apply                             # resume
```

While paused, the ingest endpoint answers with an error.

### Demo data

Load a demo organization (invite code `DEMO-2026`) with five sources (the
webhook and the four pull and Kafka sources of the mock), their rules and a
runbook library straight into DynamoDB:

```bash
java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.Seed demo/seed
```

Then send an event and look at the resulting alert:

```bash
API=$(terraform -chdir=infra/app output -raw api_url)
INGEST=$(terraform -chdir=infra/app output -raw ingest_base_url)
curl -X POST "$INGEST/ingest/demo/mock-webhook?token=demo-token" \
  -H 'content-type: application/json' \
  -d '{"status":"firing","alerts":[{"labels":{"alertname":"DB_TIMEOUT","severity":"error","service":"payments","instance":"pay-1"},"annotations":{"summary":"Database connection timeout"},"startsAt":"2026-11-20T21:00:00Z"}]}'
aws dynamodb scan --table-name cod-alerts
```

### Calling the API

Create a user and get a token (the password needs 8 characters with a digit):

```bash
POOL=$(terraform -chdir=infra/app output -raw user_pool_id)
CLIENT=$(terraform -chdir=infra/app output -raw user_pool_client_id)
aws cognito-idp sign-up --client-id $CLIENT --username you@example.com --password 'demo-pass-1'
aws cognito-idp admin-confirm-sign-up --user-pool-id $POOL --username you@example.com
TOKEN=$(aws cognito-idp initiate-auth --client-id $CLIENT --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=you@example.com,PASSWORD='demo-pass-1' \
  --query AuthenticationResult.IdToken --output text)
```

Join the demo organization and read its alerts:

```bash
curl -X POST "$API/orgs/join" -H "Authorization: Bearer $TOKEN" -d '{"inviteCode":"DEMO-2026"}'
curl "$API/alerts" -H "Authorization: Bearer $TOKEN"
```

The first user to join an organization without members becomes its admin. All
endpoints are listed in [Docs/contracts/api.md](Docs/contracts/api.md).

### Pull and Kafka sources

The connector worker runs on one EC2 instance created by `infra/app`. Every 15
seconds it reads the sources from DynamoDB and keeps one connector running per
enabled `http-pull` or `kafka` source, so a source added through the API starts
flowing without a deployment. Webhook sources do not need the worker.

```bash
$(terraform -chdir=infra/app output -raw worker_logs_command)   # follow its log
```

Set `worker_enabled = false` in `infra/app/terraform.tfvars` to stop paying for
the instance. To run the worker on your own machine instead:

```bash
STREAM_NAME=cod-events TABLE_PREFIX=cod- AWS_REGION=eu-central-1 java -jar backend/worker/target/worker.jar
```

### Notifications

A rule's `routing` lists who is notified over which channel when the rule
creates an alert. Channels: `sms` (Amazon SNS), `email` (Amazon SES), `ui`
(pop-up in the web application) and `voice` (simulated for now).

- **Email** needs a sender: set `notification_sender_email` in
  `infra/app/terraform.tfvars`, apply, and click the confirmation link SES
  sends to that address. In the SES sandbox the recipients must be verified too.
- **SMS** in the SNS sandbox reaches verified phone numbers only.

Every delivery try is written to the alert's timeline, with the measured
ingest-to-delivery latency on success. A failing job is retried three times,
then goes through the dead-letter queue for two more rounds before it is given up on.

The mock data sources are deployed separately from `infra/datasource`, as
described in [mocks/datasource/README.md](mocks/datasource/README.md).
