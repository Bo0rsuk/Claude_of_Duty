# Claude of Duty

Alerting platform in the spirit of PagerDuty: it ingests events from many
sources and formats, turns them into alerts with rules that can be changed at
runtime, and notifies the right people over SMS, voice, email and a live web feed.

**Status:** work in progress. Deployed: the mock data sources and the
application infrastructure. Implemented: webhook ingest and the processor
(parsing, rules, deduplication, alert storage, live feed publishing). Next: the
REST API, notifications, pull and Kafka connectors, and the web application.
See [TASKS.md](TASKS.md) for the plan and the current state.

## Architecture

```
mock sources: JSON API, application log, access log, Kafka, webhook
  -> connector worker (pull) / API Gateway + ingest Lambda (push)
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
| `backend/core/` | Domain model and logic. No AWS dependencies, tested locally. |
| `backend/lambdas/` | Lambda handlers and AWS adapters. Builds the one jar all functions run from. |
| `infra/app/` | Terraform for the application: Kinesis, DynamoDB, Lambda, API Gateway, Cognito, AppSync Events, SQS. |
| `infra/datasource/` | Terraform for the mock data source instance. |
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

This runs the tests and produces `backend/lambdas/target/lambdas.jar`.

## Deploy

Build first, because Terraform uploads the jar.

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
notices the new jar and updates the functions.

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

Until the API for sources and rules exists, load a demo webhook source and two
rules straight into DynamoDB:

```bash
java -cp backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.Seed demo/seed
```

Then send an event and look at the resulting alert:

```bash
API=$(terraform -chdir=infra/app output -raw api_url)
curl -X POST "$API/ingest/demo/mock-webhook?token=demo-token" \
  -H 'content-type: application/json' \
  -d '{"status":"firing","alerts":[{"labels":{"alertname":"DB_TIMEOUT","severity":"error","service":"payments","instance":"pay-1"},"annotations":{"summary":"Database connection timeout"},"startsAt":"2026-11-20T21:00:00Z"}]}'
aws dynamodb scan --table-name cod-alerts
```

The mock data sources are deployed separately from `infra/datasource`, as
described in [mocks/datasource/README.md](mocks/datasource/README.md).
