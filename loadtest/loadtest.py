#!/usr/bin/env python3
"""Load test for Claude of Duty: measured throughput and end-to-end latency.

Sends webhook events at a fixed rate and, at the same time, listens on the
live feed's WebSocket channel like a browser does. Every event carries a unique
id, so each alert that arrives on the WebSocket is matched to the moment its
event was sent.

Scenarios:
  unique  every event becomes its own alert. Measures the full path per event:
          ingest -> stream -> processor -> DynamoDB -> live feed, plus the
          notifier (the rule routes to the simulated voice channel).
  burst   all events are identical and must collapse into one alert. Measures
          deduplication under load and how fast the event count catches up.

Reported latencies:
  end_to_end        HTTP request sent -> alert arrives on the WebSocket.
                    Measured with this machine's clock only.
  http_round_trip   HTTP request sent -> 202 response from the ingest endpoint.
  ingest_to_alert   receivedAt -> processedAt, both stamped by the backend.
  alert_to_publish  processedAt -> publishedAt, both stamped by the backend.
  notification      receivedAt -> the notifier delivered the notification
                    (latencyMs in the alert timeline, stamped by the backend).

Usage: see README.md in this directory.
"""

import argparse
import asyncio
import base64
import csv
import json
import ssl
import statistics
import subprocess
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import aiohttp
import certifi
import websockets

ORG_ID = "loadtest"
INVITE_CODE = "LOAD-TEST"
INGEST_PATH = "/ingest/loadtest/loadtest-webhook?token=loadtest-token"
HERE = Path(__file__).resolve().parent
# Not every Python installation ships root certificates, so they come from certifi.
TLS = ssl.create_default_context(cafile=certifi.where())


# ---------------------------------------------------------------- configuration


def terraform_outputs(terraform_dir: Path) -> dict:
    """Endpoints and ids of the deployed stack, read from Terraform."""
    raw = subprocess.run(
        ["terraform", f"-chdir={terraform_dir}", "output", "-json"],
        check=True, capture_output=True, text=True,
    ).stdout
    return {name: output["value"] for name, output in json.loads(raw).items()}


def cognito_id_token(region: str, client_id: str, username: str, password: str) -> str:
    request = urllib.request.Request(
        f"https://cognito-idp.{region}.amazonaws.com/",
        data=json.dumps({
            "AuthFlow": "USER_PASSWORD_AUTH",
            "ClientId": client_id,
            "AuthParameters": {"USERNAME": username, "PASSWORD": password},
        }).encode(),
        headers={
            "Content-Type": "application/x-amz-json-1.1",
            "X-Amz-Target": "AWSCognitoIdentityProviderService.InitiateAuth",
        },
    )
    with urllib.request.urlopen(request, timeout=15, context=TLS) as response:
        return json.load(response)["AuthenticationResult"]["IdToken"]


async def ensure_membership(session: aiohttp.ClientSession, api_url: str, token: str) -> bool:
    """The load test user has to belong to the load test organization to read its alerts.

    Returns True when a new token is needed: the live feed admits a subscriber only
    to the organization named in the token, and this token was issued before that was recorded.
    """
    headers = {"Authorization": f"Bearer {token}"}
    async with session.get(f"{api_url}/me", headers=headers) as response:
        response.raise_for_status()
        profile = await response.json()
    if profile.get("org") is None:
        async with session.post(f"{api_url}/orgs/join", headers=headers, json={"inviteCode": INVITE_CODE}) as response:
            response.raise_for_status()
        return True
    if profile["org"]["orgId"] != ORG_ID:
        sys.exit(f"The user belongs to organization {profile['org']['orgId']}, not to {ORG_ID}. Use a dedicated user.")
    return bool(profile.get("tokenRefreshNeeded"))


# ------------------------------------------------------------- live feed client


class LiveFeed:
    """Subscribes to /alerts/{orgId} on AppSync Events, the way the web application does."""

    def __init__(self, realtime_url: str, http_host: str, token: str):
        self._url = realtime_url
        self._authorization = {"Authorization": token, "host": http_host}
        self._socket = None
        self._reader = None
        self.messages: list[tuple[float, dict]] = []

    async def connect(self) -> None:
        header = base64.urlsafe_b64encode(json.dumps(self._authorization).encode()).rstrip(b"=").decode()
        self._socket = await websockets.connect(
            self._url, subprotocols=[f"header-{header}", "aws-appsync-event-ws"], max_size=None, ssl=TLS)
        await self._socket.send(json.dumps({"type": "connection_init"}))
        await self._expect("connection_ack")
        await self._socket.send(json.dumps({
            "type": "subscribe",
            "id": str(uuid.uuid4()),
            "channel": f"/alerts/{ORG_ID}",
            "authorization": self._authorization,
        }))
        await self._expect("subscribe_success")
        self._reader = asyncio.create_task(self._read())

    async def _expect(self, message_type: str) -> None:
        while True:
            message = json.loads(await asyncio.wait_for(self._socket.recv(), timeout=15))
            if message.get("type") == message_type:
                return
            if message.get("type", "").endswith("error"):
                raise RuntimeError(f"AppSync refused the connection or subscription: {message}")

    async def _read(self) -> None:
        async for raw in self._socket:
            arrived = time.time()
            message = json.loads(raw)
            if message.get("type") != "data":
                continue
            events = message["event"] if isinstance(message["event"], list) else [message["event"]]
            for event in events:
                self.messages.append((arrived, json.loads(event)))

    async def close(self) -> None:
        if self._reader:
            self._reader.cancel()
        if self._socket:
            await self._socket.close()


# --------------------------------------------------------------- load generator


def payload(scenario: str, run_id: str, event_ids: list[str]) -> dict:
    """Alertmanager-style webhook body, the same shape the mock data source pushes."""
    starts_at = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    return {
        "status": "firing",
        "alerts": [
            {
                "labels": {
                    "alertname": "LOAD_TEST", "severity": "error", "service": "loadtest",
                    "scenario": scenario, "run_id": run_id, "event_id": event_id,
                },
                "annotations": {"summary": "Synthetic event from the load test"},
                "startsAt": starts_at,
            }
            for event_id in event_ids
        ],
    }


async def send_at_fixed_rate(session, url, scenario, run_id, phase, rate, seconds, batch, sent, accepted, responses):
    """Open loop: requests leave on schedule whether or not earlier ones have been answered."""
    interval = batch / rate
    total_requests = int(rate * seconds / batch)
    started = time.time()
    tasks = []
    for index in range(total_requests):
        delay = started + index * interval - time.time()
        if delay > 0:
            await asyncio.sleep(delay)
        event_ids = [f"{run_id}-{phase}-{index}-{n}" for n in range(batch)]
        tasks.append(asyncio.create_task(
            post(session, url, scenario, run_id, phase, event_ids, sent, accepted, responses)))
    await asyncio.gather(*tasks)
    return time.time() - started


async def post(session, url, scenario, run_id, phase, event_ids, sent, accepted, responses):
    sent_at = time.time()
    for event_id in event_ids:
        sent[event_id] = (sent_at, phase)
    try:
        async with session.post(url, json=payload(scenario, run_id, event_ids)) as response:
            await response.read()
            responses.append((phase, response.status, time.time() - sent_at, len(event_ids)))
            if response.status == 202:
                accepted.update(event_ids)
    except (aiohttp.ClientError, asyncio.TimeoutError) as error:
        responses.append((phase, type(error).__name__, time.time() - sent_at, len(event_ids)))


async def wait_until(done, timeout: float) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if done():
            return True
        await asyncio.sleep(0.25)
    return done()


# --------------------------------------------------------------------- analysis


def parse_time(value: str) -> float:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def summarize(values_ms: list[float]) -> dict | None:
    if not values_ms:
        return None
    ordered = sorted(values_ms)

    def percentile(p: float) -> float:
        # Nearest rank: the smallest value with at least p percent of the samples at or below it.
        rank = max(1, -(-len(ordered) * p // 100))
        return round(ordered[int(rank) - 1], 1)

    return {
        "samples": len(ordered),
        "min_ms": round(ordered[0], 1),
        "p50_ms": percentile(50),
        "p90_ms": percentile(90),
        "p99_ms": percentile(99),
        "max_ms": round(ordered[-1], 1),
        "mean_ms": round(statistics.fmean(ordered), 1),
    }


def analyze_unique(sent: dict, feed: LiveFeed):
    """One row per measured event that arrived on the live feed."""
    rows = []
    seen = set()
    for arrived, message in feed.messages:
        alert = message.get("alert", {})
        event_id = alert.get("fields", {}).get("labels.event_id")
        if message.get("type") != "alert.created" or event_id not in sent or event_id in seen:
            continue
        seen.add(event_id)
        sent_at, phase = sent[event_id]
        if phase != "measured":
            continue
        received = parse_time(alert["timing"]["receivedAt"])
        processed = parse_time(alert["timing"]["processedAt"])
        published = parse_time(message["publishedAt"])
        rows.append({
            "event_id": event_id,
            "alert_id": alert["alertId"],
            "sent_at": round(sent_at, 3),
            "end_to_end_ms": round((arrived - sent_at) * 1000, 1),
            "ingest_to_alert_ms": round((processed - received) * 1000, 1),
            "alert_to_publish_ms": round((published - processed) * 1000, 1),
        })
    return rows


def analyze_burst(sent: dict, accepted: set, feed: LiveFeed, run_id: str) -> dict:
    """How the single alert of a burst run was created and how its count caught up."""
    updates = [
        (arrived, message["type"], message["alert"]["count"])
        for arrived, message in feed.messages
        if message.get("alert", {}).get("fields", {}).get("labels.run_id") == run_id
    ]
    measured = [sent_at for event_id, (sent_at, phase) in sent.items() if phase == "measured" and event_id in accepted]
    first_sent = min(sent_at for sent_at, _ in sent.values())
    created = [arrived for arrived, kind, _ in updates if kind == "alert.created"]
    final_count = max((count for _, _, count in updates), default=0)
    reached_final = next((arrived for arrived, _, count in updates if count == final_count), None)
    return {
        "events_accepted": len(accepted),
        "alerts_created": len(created),
        "final_count": final_count,
        "all_events_counted": final_count == len(accepted),
        "first_event_to_alert_ms": round((created[0] - first_sent) * 1000, 1) if created else None,
        "last_event_to_final_count_ms": (
            round((reached_final - max(measured)) * 1000, 1) if reached_final and measured else None),
        "live_feed_messages": len(updates),
    }


async def notification_latencies(session, api_url, token, alert_ids: list[str]) -> list[float]:
    """Ingest-to-delivery latency of the notifier, read from the alert timelines."""
    headers = {"Authorization": f"Bearer {token}"}
    latencies = []
    for alert_id in alert_ids:
        async with session.get(f"{api_url}/alerts/{alert_id}/timeline", headers=headers) as response:
            if response.status != 200:
                continue
            for entry in (await response.json())["items"]:
                if entry["type"] == "NOTIFICATION_SENT" and "latencyMs" in entry["detail"]:
                    latencies.append(float(entry["detail"]["latencyMs"]))
    return latencies


# -------------------------------------------------------------------------- run


async def run(args) -> dict:
    outputs = terraform_outputs(args.terraform_dir)
    api_url = outputs["api_url"]
    region = urlparse(api_url).hostname.split(".")[2]
    http_host = urlparse(outputs["events_http_endpoint"]).hostname
    token = cognito_id_token(region, outputs["user_pool_client_id"], args.username, args.password)
    run_id = f"{args.scenario}-{int(time.time())}"
    sent: dict[str, tuple[float, str]] = {}
    accepted: set[str] = set()
    responses: list[tuple] = []

    connector = aiohttp.TCPConnector(limit=args.connections, ssl=TLS)
    timeout = aiohttp.ClientTimeout(total=30)
    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        if await ensure_membership(session, api_url, token):
            token = cognito_id_token(region, outputs["user_pool_client_id"], args.username, args.password)
        feed = LiveFeed(outputs["events_realtime_endpoint"], http_host, token)
        await feed.connect()
        url = api_url + INGEST_PATH

        if args.warmup > 0:
            print(f"Warm-up: {args.rate} events/s for {args.warmup} s (not measured)")
            await send_at_fixed_rate(session, url, args.scenario, run_id, "warmup", args.rate, args.warmup,
                                     args.batch, sent, accepted, responses)
        print(f"Measuring: {args.rate} events/s for {args.duration} s, {args.batch} event(s) per request")
        send_seconds = await send_at_fixed_rate(session, url, args.scenario, run_id, "measured", args.rate,
                                                args.duration, args.batch, sent, accepted, responses)

        measured_ids = {event_id for event_id, (_, phase) in sent.items() if phase == "measured"}
        accepted_measured = measured_ids & accepted
        if args.scenario == "unique":
            def all_arrived():
                return len(analyze_unique(sent, feed)) >= len(accepted_measured)
        else:
            def all_arrived():
                return analyze_burst(sent, accepted, feed, run_id)["final_count"] >= len(accepted)
        print(f"Sent. Waiting up to {args.drain_timeout} s for the live feed to catch up")
        drained = await wait_until(all_arrived, args.drain_timeout)
        drain_seconds = time.time() - max(sent_at for sent_at, _ in sent.values())

        measured_responses = [r for r in responses if r[0] == "measured"]
        accepted_events = len(accepted_measured)
        statuses: dict[str, int] = {}
        for _, status, _, _ in measured_responses:
            statuses[str(status)] = statuses.get(str(status), 0) + 1

        result = {
            "run_id": run_id,
            "started_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "scenario": args.scenario,
            "settings": {"target_rate_events_per_s": args.rate, "duration_s": args.duration,
                         "events_per_request": args.batch, "warmup_s": args.warmup},
            "ingest": {
                "events_sent": len(measured_ids),
                "events_accepted": accepted_events,
                "http_statuses": statuses,
                "achieved_rate_events_per_s": round(len(measured_ids) / send_seconds, 1),
                "accepted_rate_events_per_s": round(accepted_events / send_seconds, 1),
            },
            "latency": {
                "http_round_trip": summarize([rtt * 1000 for _, status, rtt, _ in measured_responses if status == 202]),
            },
            "live_feed_caught_up": drained,
        }

        rows = []
        if args.scenario == "unique":
            rows = analyze_unique(sent, feed)
            first_sent = min(sent_at for sent_at, phase in sent.values() if phase == "measured")
            last_arrival = max((row["sent_at"] + row["end_to_end_ms"] / 1000 for row in rows), default=first_sent)
            result["processing"] = {
                "alerts_delivered_to_live_feed": len(rows),
                # Accepted by the ingest endpoint but never seen on the live feed.
                "events_lost": len(accepted_measured) - len(rows),
                # From the first measured send to the last arrival: includes the time needed to drain a backlog.
                "processed_rate_events_per_s": round(len(rows) / (last_arrival - first_sent), 1) if rows else 0,
                "seconds_to_catch_up_after_last_send": round(drain_seconds, 1),
            }
            for stage in ("end_to_end", "ingest_to_alert", "alert_to_publish"):
                result["latency"][stage] = summarize([row[f"{stage}_ms"] for row in rows])
            if args.notification_sample > 0 and rows:
                await asyncio.sleep(5)
                step = max(1, len(rows) // args.notification_sample)
                sample = [row["alert_id"] for row in rows[::step]][: args.notification_sample]
                result["latency"]["notification"] = summarize(
                    await notification_latencies(session, api_url, token, sample))
        else:
            result["deduplication"] = analyze_burst(sent, accepted, feed, run_id)

        await feed.close()

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f"{run_id}.json").write_text(json.dumps(result, indent=2) + "\n")
    if rows:
        with open(args.out / f"{run_id}.csv", "w", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    return result


def print_report(result: dict) -> None:
    ingest = result["ingest"]
    print(f"\nRun {result['run_id']}")
    print(f"  sent      {ingest['events_sent']} events at {ingest['achieved_rate_events_per_s']} events/s")
    print(f"  accepted  {ingest['events_accepted']} events at {ingest['accepted_rate_events_per_s']} events/s,"
          f" rejected {ingest['events_sent'] - ingest['events_accepted']}  (HTTP statuses: {ingest['http_statuses']})")
    if "processing" in result:
        processing = result["processing"]
        print(f"  delivered {processing['alerts_delivered_to_live_feed']} alerts to the live feed at "
              f"{processing['processed_rate_events_per_s']} events/s, lost {processing['events_lost']}, "
              f"caught up {processing['seconds_to_catch_up_after_last_send']} s after the last send")
    if "deduplication" in result:
        for key, value in result["deduplication"].items():
            print(f"  {key:<32} {value}")
    print(f"\n  {'latency (ms)':<18}{'samples':>8}{'p50':>10}{'p90':>10}{'p99':>10}{'max':>10}")
    for stage, stats in result["latency"].items():
        if stats:
            print(f"  {stage:<18}{stats['samples']:>8}{stats['p50_ms']:>10}{stats['p90_ms']:>10}"
                  f"{stats['p99_ms']:>10}{stats['max_ms']:>10}")
    if not result["live_feed_caught_up"]:
        print("\n  The live feed did not catch up before the drain timeout: the system was overloaded at this rate.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scenario", choices=["unique", "burst"], default="unique")
    parser.add_argument("--rate", type=float, default=20, help="events per second")
    parser.add_argument("--duration", type=float, default=30, help="seconds of measured traffic")
    parser.add_argument("--warmup", type=float, default=10, help="seconds of unmeasured traffic before the measurement")
    parser.add_argument("--batch", type=int, default=1, help="events per HTTP request")
    parser.add_argument("--connections", type=int, default=200, help="maximum concurrent HTTP connections")
    parser.add_argument("--drain-timeout", type=float, default=60, help="seconds to wait for the live feed after sending")
    parser.add_argument("--notification-sample", type=int, default=100,
                        help="alerts whose timeline is read for the notification latency (unique scenario)")
    parser.add_argument("--username", default="loadtest@example.com")
    parser.add_argument("--password", required=True)
    parser.add_argument("--terraform-dir", type=Path, default=HERE.parent / "infra" / "app")
    parser.add_argument("--out", type=Path, default=HERE / "results")
    print_report(asyncio.run(run(parser.parse_args())))


if __name__ == "__main__":
    main()
