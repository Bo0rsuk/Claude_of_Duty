"""Random incident records and their rendering in each mock source format.

Every source draws a `Record` from the same scenario catalog and renders it
with its own formatter, so the formats differ while the content stays realistic.
"""

import json
import random
import uuid
from dataclasses import dataclass, replace
from datetime import datetime, timezone


@dataclass(frozen=True)
class Scenario:
    level: str
    code: str
    message: str
    status: int
    weight: float


SCENARIOS = [
    Scenario("INFO", "REQUEST_OK", "Request completed", 200, 70),
    Scenario("INFO", "CACHE_REFRESH", "Cache refreshed in {latency_ms} ms", 200, 8),
    Scenario("WARN", "SLOW_QUERY", "Query took {latency_ms} ms, threshold is 500 ms", 200, 10),
    Scenario("WARN", "RETRY", "Retrying call to inventory-service, attempt 2 of 3", 200, 5),
    Scenario("ERROR", "DB_TIMEOUT", "Database connection timeout after 3000 ms", 503, 4),
    Scenario("ERROR", "UPSTREAM_5XX", "Upstream payment-gateway returned 502", 502, 2.5),
    Scenario("FATAL", "OUT_OF_MEMORY", "OutOfMemoryError: Java heap space", 500, 0.5),
]
BURST_SCENARIO = SCENARIOS[4]

ENDPOINTS = [
    ("GET", "/v1/orders"),
    ("POST", "/v1/orders"),
    ("POST", "/v1/payments"),
    ("GET", "/v1/inventory/items"),
    ("GET", "/v1/users/me"),
]

SLOW_LEVELS = {"WARN", "ERROR", "FATAL"}


@dataclass(frozen=True)
class Record:
    timestamp: datetime
    service: str
    host: str
    level: str
    code: str
    message: str
    method: str
    path: str
    status: int
    latency_ms: int
    client_ip: str
    trace_id: str


def make_record(services: list[str], scenario: Scenario | None = None) -> Record:
    """Build one random record; pass `scenario` to force a specific incident."""
    scenario = scenario or random.choices(SCENARIOS, weights=[s.weight for s in SCENARIOS])[0]
    service = random.choice(services)
    method, path = random.choice(ENDPOINTS)
    slow = scenario.level in SLOW_LEVELS
    latency_ms = random.randint(600, 3500) if slow else random.randint(5, 250)
    return Record(
        timestamp=datetime.now(timezone.utc),
        service=service,
        host=f"{service}-{random.randint(1, 3)}",
        level=scenario.level,
        code=scenario.code,
        message=scenario.message.format(latency_ms=latency_ms),
        method=method,
        path=path,
        status=scenario.status,
        latency_ms=latency_ms,
        client_ip=f"10.0.{random.randint(0, 9)}.{random.randint(2, 254)}",
        trace_id=uuid.uuid4().hex[:16],
    )


def make_burst(services: list[str], count: int) -> list[Record]:
    """`count` copies of one incident: same service, host and error, new trace ids."""
    template = make_record(services, BURST_SCENARIO)
    return [
        replace(template, timestamp=datetime.now(timezone.utc), trace_id=uuid.uuid4().hex[:16])
        for _ in range(count)
    ]


def as_json_event(record: Record) -> dict:
    """Structured application event with a nested `http` object."""
    return {
        "id": str(uuid.uuid4()),
        "timestamp": record.timestamp.isoformat(timespec="milliseconds").replace("+00:00", "Z"),
        "service": record.service,
        "host": record.host,
        "level": record.level,
        "code": record.code,
        "message": record.message,
        "http": {
            "method": record.method,
            "path": record.path,
            "status": record.status,
            "latency_ms": record.latency_ms,
        },
        "trace_id": record.trace_id,
    }


def as_app_log(record: Record) -> str:
    """Log4j-style line: `2026-11-20 21:40:01,123 ERROR [service] [host] [trace=..] CODE - message`."""
    timestamp = record.timestamp.strftime("%Y-%m-%d %H:%M:%S") + f",{record.timestamp.microsecond // 1000:03d}"
    return (
        f"{timestamp} {record.level:<5} [{record.service}] [{record.host}] "
        f"[trace={record.trace_id}] {record.code} - {record.message}"
    )


def as_access_log(record: Record) -> str:
    """Nginx combined access log line with the request time in seconds appended."""
    timestamp = record.timestamp.strftime("%d/%b/%Y:%H:%M:%S +0000")
    return (
        f'{record.client_ip} - - [{timestamp}] "{record.method} {record.path} HTTP/1.1" '
        f'{record.status} {random.randint(120, 4096)} "-" "okhttp/4.12.0" {record.latency_ms / 1000:.3f}'
    )


def as_kafka_message(record: Record) -> str:
    """Infrastructure event as a JSON string: epoch millis, lowercase severity, labels map."""
    return json.dumps(
        {
            "ts": int(record.timestamp.timestamp() * 1000),
            "source": "k8s/prod-eu",
            "kind": record.code,
            "severity": record.level.lower(),
            "summary": record.message,
            "labels": {"service": record.service, "pod": record.host, "namespace": "prod"},
            "value": record.latency_ms,
        }
    )


def as_webhook(record: Record) -> dict:
    """Alertmanager-style webhook payload carrying a single alert."""
    return {
        "version": "4",
        "status": "firing",
        "receiver": "claude-of-duty",
        "alerts": [
            {
                "status": "firing",
                "labels": {
                    "alertname": record.code,
                    "severity": record.level.lower(),
                    "service": record.service,
                    "instance": record.host,
                },
                "annotations": {"summary": record.message},
                "startsAt": record.timestamp.isoformat(timespec="seconds").replace("+00:00", "Z"),
            }
        ],
    }
