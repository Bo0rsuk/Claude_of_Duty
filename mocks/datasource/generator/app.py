"""Mock data sources: emits events non-stop and exposes them in several formats.

Pull sources are served over HTTP, Kafka messages go to the broker next to this
service, and webhooks are pushed to WEBHOOK_URL when it is set.
"""

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Callable

from fastapi import FastAPI, Query, Response

import formats
from sinks import Feed, KafkaSink, WebhookSink

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("generator")

EVENTS_PER_SECOND = float(os.environ.get("EVENTS_PER_SECOND", "2"))
# Seconds between failures. 0 mixes failures into the traffic at random instead.
INCIDENT_INTERVAL_SECONDS = float(os.environ.get("INCIDENT_INTERVAL_SECONDS", "0"))
FEED_SIZE = int(os.environ.get("FEED_SIZE", "10000"))
KAFKA_BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "")
KAFKA_TOPIC = os.environ.get("KAFKA_TOPIC", "infra.events")
WEBHOOK_URL = os.environ.get("WEBHOOK_URL", "")
MAX_PAGE = 1000
MAX_BURST = 5000


@dataclass
class Source:
    """One mock data source: which services it reports on, its format and where it goes."""

    name: str
    services: list[str]
    render: Callable[[formats.Record], object]
    sink: object
    alerts_only: bool = False

    def emit(self, record: formats.Record) -> None:
        if self.alerts_only and record.level == "INFO":
            return
        self.sink.publish(self.render(record))


json_feed = Feed(FEED_SIZE)
app_log_feed = Feed(FEED_SIZE)
access_log_feed = Feed(FEED_SIZE)

sources = [
    Source("json-api", ["payment-api", "checkout-api"], formats.as_json_event, json_feed),
    Source("app-log", ["order-service", "inventory-service"], formats.as_app_log, app_log_feed),
    Source("access-log", ["edge-nginx"], formats.as_access_log, access_log_feed),
]


async def emit_forever() -> None:
    """Background traffic. With scheduled incidents it holds no failures, so nothing alerts by chance."""
    interval = 1 / EVENTS_PER_SECOND
    scenarios = formats.CALM_SCENARIOS if INCIDENT_INTERVAL_SECONDS > 0 else formats.SCENARIOS
    while True:
        for source in sources:
            source.emit(formats.make_record(source.services, formats.pick(scenarios)))
        await asyncio.sleep(interval)


async def emit_incidents() -> None:
    """One failure on every source each interval: a steady, predictable trickle for demos."""
    while True:
        await asyncio.sleep(INCIDENT_INTERVAL_SECONDS)
        scenario = formats.pick(formats.INCIDENT_SCENARIOS)
        for source in sources:
            source.emit(formats.make_record(source.services, scenario))


@asynccontextmanager
async def lifespan(_: FastAPI):
    tasks = []
    kafka = None
    if KAFKA_BOOTSTRAP:
        kafka = KafkaSink(KAFKA_BOOTSTRAP, KAFKA_TOPIC)
        sources.append(Source("kafka", ["k8s-scheduler", "k8s-ingress"], formats.as_kafka_message, kafka))
    if WEBHOOK_URL:
        webhook = WebhookSink(WEBHOOK_URL)
        sources.append(Source("webhook", ["monitoring"], formats.as_webhook, webhook, alerts_only=True))
        tasks.append(asyncio.create_task(webhook.run()))
    tasks.append(asyncio.create_task(emit_forever()))
    if INCIDENT_INTERVAL_SECONDS > 0:
        tasks.append(asyncio.create_task(emit_incidents()))
    log.info("Emitting %s events/s per source, a failure every %s s: %s",
             EVENTS_PER_SECOND, INCIDENT_INTERVAL_SECONDS or "random", [s.name for s in sources])
    yield
    for task in tasks:
        task.cancel()
    if kafka:
        kafka.close()


app = FastAPI(title="Claude of Duty - mock data sources", lifespan=lifespan)

After = Query(None, ge=0, description="Return items newer than this sequence number")
Limit = Query(100, ge=1, le=MAX_PAGE)


@app.get("/")
def index() -> dict:
    return {
        "sources": {
            "json-api": {"path": "/api/events", "format": "JSON, cursor in next_after"},
            "app-log": {"path": "/logs", "format": "text, log4j style, cursor in X-Next-After header"},
            "access-log": {"path": "/logs/access", "format": "text, nginx combined, cursor in X-Next-After header"},
            "kafka": {"path": "/kafka/topics", "topic": KAFKA_TOPIC, "enabled": bool(KAFKA_BOOTSTRAP)},
            "webhook": {"target": WEBHOOK_URL or None, "enabled": bool(WEBHOOK_URL)},
        },
        "events_per_second_per_source": EVENTS_PER_SECOND,
        "seconds_between_failures": INCIDENT_INTERVAL_SECONDS or None,
    }


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/api/events")
def json_events(after: int | None = After, limit: int = Limit) -> dict:
    items, next_after = json_feed.read(after, limit)
    return {"items": [{"seq": seq, **event} for seq, event in items], "next_after": next_after}


@app.get("/logs")
def app_logs(after: int | None = After, limit: int = Limit) -> Response:
    return _text_page(app_log_feed, after, limit)


@app.get("/logs/access")
def access_logs(after: int | None = After, limit: int = Limit) -> Response:
    return _text_page(access_log_feed, after, limit)


@app.post("/scenarios/burst")
def burst(count: int = Query(500, ge=1, le=MAX_BURST)) -> dict:
    """Emit `count` copies of one error on every source, to exercise deduplication."""
    for source in sources:
        for record in formats.make_burst(source.services, count):
            source.emit(record)
    return {"emitted_per_source": count, "sources": [s.name for s in sources]}


def _text_page(feed: Feed, after: int | None, limit: int) -> Response:
    items, next_after = feed.read(after, limit)
    body = "".join(f"{line}\n" for _, line in items)
    return Response(body, media_type="text/plain", headers={"X-Next-After": str(next_after)})
