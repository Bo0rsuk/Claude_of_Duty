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

- **About 10 concurrent Lambda executions** for the whole account until the quota is raised. Ingest no longer uses them, the processor and notifier still do.
- **A few hundred new alerts per second per organization**, set by the processor's concurrency and by the single partition of the alerts table. Identical events are not affected: thousands per second collapse into one alert.
- **Counts can be too high after a retried batch.** Processing is at-least-once: when a batch fails halfway, the retry counts its events again.

Raw files: `results/optimized/`, `results/optimized-batched/`.

### Final (2026-11-21)

Changes since the previous run:

- **Direct ingest:** API Gateway writes webhook payloads straight to Kinesis, with no Lambda. Ingest no longer competes for the account's 10 concurrent executions. The sender's token is checked by the processor.
- **Atomic alert write:** creating an alert and adding events to it is one conditional-free update, which removed a race between two processor instances (see below).
- **Smaller alerts:** an event keeps only its own part of the request as raw text.
- **DynamoDB retries:** up to 8 attempts, so a brief throttle is waited out.
- **SnapStart priming:** each function makes its first service calls during initialization, so they are in the snapshot.

**Direct ingest, one event per request** (`--ingest-route direct`), scenario `unique`:

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 50/s | 50/s | 0 of 1500 | 0 | 179.1 ms | 292.4 ms | 71 ms | 133 ms | 160 ms | 268 ms |
| 100/s | 99.9/s | 0 of 3000 | 0 | 177.2 ms | 302.3 ms | 72 ms | 131 ms | 207 ms | 604 ms |
| 200/s | 199.7/s | 0 of 6000 | 0 | 192.7 ms | 338.4 ms | 74 ms | 167 ms | 2102 ms | 9782 ms |
| 400/s | 398.7/s | 0 of 12000 | 0 | 1181.6 ms | 2267.6 ms | 989 ms | 2063 ms | - ms | - ms |

**Direct ingest, burst** of identical events at 1000 events/s (5 per request):

| Target rate | Events accepted | Alerts created | Final count | First event to alert | Count complete after last event |
|---|---|---|---|---|---|
| 1000/s | 25000 | 1 | 25000 | 346.9 ms | 141.6 ms |

**Ingest through the Lambda** (`--ingest-route lambda`), for comparison. Scenario `unique`, one event per request:

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 25/s | 24.9/s | 0 of 750 | 0 | 186.1 ms | 290 ms | 71 ms | 131 ms | 140 ms | 247 ms |
| 50/s | 49.7/s | 7 of 1500 | 0 | 186.6 ms | 429.5 ms | 75 ms | 313 ms | 300 ms | 3205 ms |

Several events per request (5, 10 and 20):

| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |
|---|---|---|---|---|---|---|---|---|---|
| 100/s | 100/s | 0 of 3000 | 0 | 201.6 ms | 390.5 ms | 73 ms | 179 ms | 423 ms | 6663 ms |
| 200/s | 200/s | 0 of 6000 | 0 | 262 ms | 628.8 ms | 93 ms | 430 ms | 1368 ms | 4820 ms |
| 400/s | 400/s | 0 of 12000 | 0 | 1117.2 ms | 3840.6 ms | 926 ms | 3651 ms | 16455 ms | 26111 ms |


Burst at 200 events/s, three runs:

| Target rate | Events accepted | Alerts created | Final count | First event to alert | Count complete after last event |
|---|---|---|---|---|---|
| 200/s | 4810 | 1 | 4810 | 1744.4 ms | 145.4 ms |
| 200/s | 5000 | 1 | 5000 | 286.1 ms | 211.8 ms |
| 200/s | 5000 | 1 | 5000 | 260.8 ms | 161.4 ms |


In the first burst run 190 events were refused with `503` right after a deployment; all 4810 accepted ones were counted.

**Cold start:** the first event after about 15 minutes of idle took 1.2 s end to end (9.2 s before priming); the following ones were back to normal. Raw files: `results/final-cold/`.

Findings:

- **With direct ingest, 200 single-event requests per second are delivered with no rejection and no loss**, end to end in 193 ms at p50 and 338 ms at p99. Through the Lambda the same account rejected requests from 50 per second.
- **400 distinct alerts per second is where the processor saturates** on 4 concurrent invocations: everything is delivered, but latency grows to 1.2 s at p50 and the backlog clears 2 s after the last send.
- **Deduplication holds at 1000 events per second:** 25,000 identical events became one alert with a count of exactly 25,000, complete 142 ms after the last event.
- **A measurement found a bug.** A burst once produced two alerts and a count of 4990 instead of 5000. Two processor instances raced: one had claimed the alert but not stored it yet, the other stored it, and the first then overwrote it. Storing became a single atomic update. Three burst runs afterwards, and 40 concurrent writers against the real table, gave exact counts.
- **Notification latency rises under load** (p50 2.1 s at 200/s) because the notifier is capped at 2 concurrent invocations per queue. That cap exists only because of the account limit.

Raw files: `results/final/`, `results/final-batched/`, `results/direct/`.
