# Mock data sources

One host that emits events non-stop in five formats, standing in for the
external systems Squawk ingests from.

| Source | How to read it | Format |
|---|---|---|
| JSON API | `GET /api/events` | JSON with a nested `http` object |
| Application log | `GET /logs` | Text, log4j style |
| Access log | `GET /logs/access` | Text, nginx combined |
| Kafka | topic `infra.events` on port `9092`, or REST under `/kafka` | JSON, epoch millis, `labels` map |
| Webhook | pushed with `POST` to `WEBHOOK_URL` | Alertmanager-style JSON, warnings and errors only |

`GET /` lists the sources and shows which ones are enabled.

## Reading the pull sources

`/api/events`, `/logs` and `/logs/access` take the same query parameters:

- `after` - return items newer than this sequence number. Omit it on the first call to get the latest items.
- `limit` - page size, 1-1000, default 100.

The cursor for the next call is `next_after` in the JSON body, or the
`X-Next-After` response header for the text sources. Each source keeps its last
10 000 items.

```bash
curl 'http://localhost:8080/api/events?limit=2'
curl -i 'http://localhost:8080/logs?after=1200'
```

## Sample payloads

JSON API:

```json
{"seq": 142, "id": "12e97979-191c-4f47-8b6d-9a9dcdb05dca", "timestamp": "2026-11-20T20:59:22.373Z",
 "service": "payment-api", "host": "payment-api-1", "level": "ERROR", "code": "DB_TIMEOUT",
 "message": "Database connection timeout after 3000 ms",
 "http": {"method": "POST", "path": "/v1/payments", "status": 503, "latency_ms": 3012},
 "trace_id": "e3c696798b55410f"}
```

Application log:

```
2026-11-20 20:59:22,373 WARN  [inventory-service] [inventory-service-1] [trace=caa27ec2cabe4001] RETRY - Retrying call to inventory-service, attempt 2 of 3
```

Access log:

```
10.0.9.56 - - [20/Nov/2026:20:59:22 +0000] "GET /v1/users/me HTTP/1.1" 200 3173 "-" "okhttp/4.12.0" 0.216
```

Kafka message:

```json
{"ts": 1795208347939, "source": "k8s/prod-eu", "kind": "RETRY", "severity": "warn",
 "summary": "Retrying call to inventory-service, attempt 2 of 3",
 "labels": {"service": "k8s-scheduler", "pod": "k8s-scheduler-1", "namespace": "prod"}, "value": 2177}
```

Webhook:

```json
{"version": "4", "status": "firing", "receiver": "claude-of-duty",
 "alerts": [{"status": "firing",
             "labels": {"alertname": "SLOW_QUERY", "severity": "warn", "service": "monitoring", "instance": "monitoring-1"},
             "annotations": {"summary": "Query took 2984 ms, threshold is 500 ms"},
             "startsAt": "2026-11-20T20:59:34Z"}]}
```

Levels are `INFO`, `WARN`, `ERROR` and `FATAL`; about 78% of events are `INFO`.

## Kafka

Native clients connect to `<host>:9092` without authentication. The same topic
is readable over HTTP through the Redpanda HTTP Proxy:

```bash
curl 'http://localhost:8080/kafka/topics'
curl -H 'Accept: application/vnd.kafka.json.v2+json' \
  'http://localhost:8080/kafka/topics/infra.events/partitions/0/records?offset=0&timeout=1000&max_bytes=100000'
```

## Burst scenario

Emits the same error many times on every enabled source. Use it to demo
deduplication: 500 events should produce one alert.

```bash
curl -X POST 'http://localhost:8080/scenarios/burst?count=500'
```

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

The mocks are then at `http://localhost:8080` and Kafka at `localhost:9092`.

## Deploy to AWS

```bash
cd infra/datasource
terraform init
terraform apply
```

This creates one EC2 instance with an Elastic IP in its own small VPC. The
`endpoints` output lists the URLs. The instance needs about three minutes after
`apply` to install Docker and start the containers.

- The mocks are served at `https://datasource.squawk.com.pl` (`domain_name` in `terraform.tfvars`). With `route53_zone_id` set, Terraform creates the DNS record. Otherwise add the A records from the `dns_records` output at the registrar.
- Region, domain, instance size, event rate and webhook URL are in `infra/datasource/terraform.tfvars`.
- After a change to the mocks, `terraform apply` replaces the instance. The IP and the hostname stay the same.
- Every replacement requests a new TLS certificate, and Let's Encrypt issues at most 5 per week for one hostname.
- There is no SSH. Use the `ssm_session_command` output.

## Adding a source

Add a formatter to `generator/formats.py` and one `Source(...)` entry in
`generator/app.py`. A new destination type is a class with a `publish(payload)`
method in `generator/sinks.py`.
