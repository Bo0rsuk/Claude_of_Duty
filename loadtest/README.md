# Load test

Measures throughput and end-to-end latency of the deployed system. The script
sends real webhook events and listens on the live feed's WebSocket like a
browser, matching every alert that arrives to the moment its event was sent.

## What is measured

| Metric | From | To | Clock |
|---|---|---|---|
| `end_to_end` | HTTP request sent | Alert arrives on the WebSocket | This machine only |
| `http_round_trip` | HTTP request sent | `202` from the ingest endpoint | This machine only |
| `ingest_to_alert` | `receivedAt` (ingest) | `processedAt` (alert stored) | Backend only |
| `alert_to_publish` | `processedAt` | `publishedAt` (sent to the live feed) | Backend only |
| `notification` | `receivedAt` | Notifier delivered the notification | Backend only |

No metric mixes the two clocks, so clock skew does not distort the results.
`end_to_end` includes the network between the test machine and AWS.

Requests go out on a fixed schedule whether or not earlier ones were answered,
so a slow system cannot hide by slowing the test down.

## Scenarios

- `unique`: every event becomes its own alert, the most expensive case per event: a DynamoDB write, a live feed message and a notification each. The rule routes to the simulated voice channel, so no real messages are sent.
- `burst`: all events are identical and must collapse into one alert whose count equals the number of events sent.

## Setup (once)

```bash
cd loadtest
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

# Organization, webhook source and the two rules used by the scenarios
java -cp ../backend/lambdas/target/lambdas.jar com.claudeofduty.lambda.tools.Seed seed

# A user for the WebSocket subscription
POOL=$(terraform -chdir=../infra/app output -raw user_pool_id)
CLIENT=$(terraform -chdir=../infra/app output -raw user_pool_client_id)
aws cognito-idp sign-up --client-id $CLIENT --username loadtest@example.com --password '<password>'
aws cognito-idp admin-confirm-sign-up --user-pool-id $POOL --username loadtest@example.com
```

The script reads the endpoints from `terraform output`.

## Run

```bash
.venv/bin/python loadtest.py --password '<password>' --scenario unique --rate 200 --duration 30
.venv/bin/python loadtest.py --password '<password>' --scenario burst --rate 1000 --batch 5 --duration 25
```

| Option | Meaning | Default |
|---|---|---|
| `--rate` | Events per second | 20 |
| `--duration` | Seconds of measured traffic | 30 |
| `--warmup` | Seconds of traffic before the measurement, not counted | 10 |
| `--batch` | Events per HTTP request | 1 |
| `--ingest-route` | `direct` (API Gateway to Kinesis) or `lambda` | `direct` |
| `--drain-timeout` | Seconds to wait for the live feed after sending | 60 |
| `--notification-sample` | Alerts whose timeline is read for the notification latency | 100 |
| `--out` | Directory for the result files | `results/` |

Each run writes a JSON summary and, for `unique`, a CSV with one row per event.
`python summarize.py results/<directory>` prints result tables from those files.

## Results

Test machine in Poland, stack in `eu-central-1`. Functions: Java 21, 1024 MB,
SnapStart. Direct ingest. All runs are from 21 November 2026.

### Latest runs

Scenario `unique`, 200 requests per second, one event per request:

| Run | Accepted | Rejected | Lost | End to end p50 | p90 | p99 | Ingest to alert p50 | p99 |
|---|---|---|---|---|---|---|---|---|
| 1, after idle | 199.5/s | 0 of 6000 | 0 | 207.4 ms | 1229.9 ms | 3920.6 ms | 87 ms | 3658 ms |
| 2 | 199.5/s | 0 of 6000 | 0 | 196.4 ms | 271.9 ms | 366.3 ms | 78 ms | 197 ms |
| 3 | 199.7/s | 0 of 6000 | 0 | 194.9 ms | 264.1 ms | 325.3 ms | 76 ms | 156 ms |

Scenario `burst`, 1000 events per second, 5 per request:

| Events accepted | Rejected | Alerts stored | Final count | Count complete after last event |
|---|---|---|---|---|
| 35,000 (10,000 of them warm-up) | 0 | 1 | 35,000 | 191.9 ms |

The script reported `alerts_created 0` for this run because it did not see the
`alert.created` message on the feed. The table counts the alerts stored in
`cod-alerts` for the run.

Raw files: `results/rerun/`.

### Rate sweep

Measured earlier the same day, before escalation and Twilio calls were added.
Scenario `unique`, one event per request:

| Target rate | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|
| 50/s | 0 of 1500 | 0 | 179.1 ms | 292.4 ms | 71 ms | 133 ms | 160 ms | 268 ms |
| 100/s | 0 of 3000 | 0 | 177.2 ms | 302.3 ms | 72 ms | 131 ms | 207 ms | 604 ms |
| 200/s | 0 of 6000 | 0 | 192.7 ms | 338.4 ms | 74 ms | 167 ms | 2102 ms | 9782 ms |
| 400/s | 0 of 12000 | 0 | 1181.6 ms | 2267.6 ms | 989 ms | 2063 ms | - | - |

Through the ingest Lambda (`--ingest-route lambda`) the same account rejected
7 of 1500 requests at 50 per second, because ingest then competes for the
account's concurrent executions.

The first event after about 15 minutes of idle took 1.2 s end to end.

Raw files: `results/final/`, `results/final-batched/`, `results/final-cold/`,
`results/direct/`. Earlier rounds are in `results/baseline/`,
`results/optimized/` and `results/optimized-batched/`.

## Findings

- At 200 single-event requests per second nothing is rejected and nothing is lost, with a warm end-to-end p99 under 370 ms.
- At 400 distinct alerts per second the processor saturates on 4 concurrent invocations. Everything is delivered, but latency grows to 1.2 s at p50.
- Deduplication holds at 1000 events per second.
- A measurement found a bug. A burst once produced two alerts and a count of 4990 instead of 5000: two processor instances raced, one had claimed the alert but not stored it yet, the other stored it, and the first overwrote it. Storing became a single atomic update. Three burst runs afterwards, and 40 concurrent writers against the real table, gave exact counts.
- Notification latency rises under load (p50 2.1 s at 200/s) because the notifier is capped at 2 concurrent invocations per queue.

## Known limits

- The account allows about 10 concurrent Lambda executions. Ingest does not use them, the processor and notifier do, and the notifier's cap exists only because of this quota.
- A few hundred new alerts per second per organization, set by the processor's concurrency and by the single partition of the alerts table. Identical events are not affected.
- Counts can be too high after a retried batch. Processing is at-least-once: when a batch fails halfway, the retry counts its events again.
- A function that starts cold during a run slows the tail, as in run 1 above.
