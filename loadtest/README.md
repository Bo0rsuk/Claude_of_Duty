# Load test

Measures throughput and end-to-end latency of the deployed system. Nothing
here is estimated: the script sends real webhook events and listens on the live
feed's WebSocket channel like a browser, matching every alert that arrives to
the moment its event was sent.

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

## Scenarios

- **`unique`:** every event becomes its own alert. This is the most expensive
  case per event: a DynamoDB write, a live feed message and a notification
  each. The rule routes to the simulated voice channel, so the notifier is
  exercised without sending real messages.
- **`burst`:** all events are identical and must collapse into one alert. This
  checks deduplication under load: one alert, with a count equal to the number
  of events sent.

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

The script reads the endpoints from `terraform output`, so it needs no further configuration.

## Run

```bash
.venv/bin/python loadtest.py --password '<password>' --scenario unique --rate 25 --duration 30
.venv/bin/python loadtest.py --password '<password>' --scenario burst --rate 200 --batch 10 --duration 20
```

| Option | Meaning | Default |
|---|---|---|
| `--rate` | Events per second | 20 |
| `--duration` | Seconds of measured traffic | 30 |
| `--warmup` | Seconds of traffic before the measurement, not counted | 10 |
| `--batch` | Events per HTTP request | 1 |
| `--drain-timeout` | Seconds to wait for the live feed after sending | 60 |
| `--notification-sample` | Alerts whose timeline is read for the notification latency | 100 |
| `--out` | Directory for the result files | `results/` |

Requests are sent on a fixed schedule whether or not earlier ones were
answered, so a slow system cannot hide by slowing the test down. Each run
writes a JSON summary and, for `unique`, a CSV with one row per event.

`python summarize.py results/<directory>` prints the tables below from those files.

## Results

Test machine in Poland, stack in `eu-central-1`. Functions: Java 21, 1024 MB.

### Baseline (2026-11-21)

Configuration: no SnapStart, alerts of a batch stored one after another,
processor up to 8 concurrent invocations, account limit of 10 concurrent
Lambda executions.

Scenario `unique`:

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 25/s | 25/s | 0 of 750 | 0 | 210 ms | 447.3 ms | 79 ms | 161 ms | 159 ms | 491 ms |
| 50/s | 44.3/s | 162 of 1500 | 0 | 256.2 ms | 942.2 ms | 103 ms | 770 ms | 310 ms | 4233 ms |

Scenario `burst`:

| Target rate | Events accepted | Alerts created | Final count | First event to alert | Count complete after last event |
|---|---|---|---|---|---|
| 200/s | 5000 | 1 | 5000 | 289.5 ms | 197.5 ms |

Findings:

- **No accepted event was lost** in any run, and the burst produced exactly one alert with an exact count.
- **The limit at 50 events/s is the account, not the design.** The 162 rejected requests are `503` responses from API Gateway: the account allows 10 concurrent Lambda executions, and the processor and notifier used so many of them that ingest was throttled.
- **Cold starts dominate the tail.** A function instance that starts during the run takes several seconds for its first batch, which shows in the p99 of `ingest_to_alert` and `notification` at 50 events/s.

Raw files: `results/baseline/`.

### After the first optimizations (2026-11-21)

Changes: SnapStart on all functions, alerts of a batch stored concurrently (16
threads), processor limited to 4 concurrent invocations and the notifier to 2
per queue so that ingest keeps its share of the account's 10.

Scenario `unique`, one event per request:

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 25/s | 25/s | 0 of 750 | 0 | 192.5 ms | 328.3 ms | 72 ms | 123 ms | 157 ms | 246 ms |
| 50/s | 48.7/s | 37 of 1500 | 0 | 195.8 ms | 466.4 ms | 71 ms | 158 ms | 299 ms | 1311 ms |

Scenario `unique`, several events per request (5, 10 and 20), which takes
ingest out of the picture and loads the processor:

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 100/s | 100/s | 0 of 3000 | 0 | 199.6 ms | 317 ms | 75 ms | 154 ms | 218 ms | 542 ms |
| 200/s | 200/s | 0 of 6000 | 0 | 254 ms | 574 ms | 91 ms | 293 ms | 914 ms | 4236 ms |
| 400/s | 389.5/s | 300 of 12000 | 6831 | 35761.6 ms | 106091 ms | 35112 ms | 105748 ms | 35142 ms | 105116 ms |

Scenario `burst`:

| Target rate | Events accepted | Alerts created | Final count | First event to alert | Count complete after last event |
|---|---|---|---|---|---|
| 200/s | 5000 | 1 | 5000 | 328.6 ms | 215.7 ms |

"Lost" in the 400/s row means "not on the live feed within the 90 s drain
timeout": the events were still queued behind retries when the test stopped waiting.

Findings:

- **At the same rates everything got faster.** At 50 events/s the end-to-end p99 fell from 942 ms to 466 ms and rejections from 162 to 37.
- **With batched requests the system delivers 200 distinct alerts per second** with an end-to-end p99 of 574 ms and no loss, still inside the account's limit of 10 concurrent executions.
- **400 distinct alerts per second overloaded it.** DynamoDB throttled the writes: all alerts of one organization share a partition of the alerts table, which takes about 1000 write units per second, and each alert stored the whole 20-event request it arrived in. Throttled batches failed and were retried, which built a backlog.
- **The notifier lags at 200/s** because it is capped at 2 concurrent invocations to protect ingest. This cap goes away with a higher account limit.
- **Cold starts are still slow.** The first event after idle took 9 s end to end even with SnapStart, because the first request to each AWS service was still made after the snapshot.

Fixes made after this run, not measured yet:

- An event keeps only its own part of the request as raw text, which makes a stored alert several times smaller.
- DynamoDB calls retry up to 8 times, so a short throttle is waited out instead of failing the batch.
- Each function makes its first service calls during initialization, so they are part of the SnapStart snapshot.

Known limits:

- **About 10 concurrent Lambda executions** for the whole account until the quota is raised.
- **A few hundred new alerts per second per organization**, set by the single partition of the alerts table. Identical events are not affected: thousands per second collapse into one alert.
- **Counts can be too high after a retried batch.** Processing is at-least-once: when a batch fails halfway, the retry counts its events again.

Raw files: `results/optimized/`, `results/optimized-batched/`.
