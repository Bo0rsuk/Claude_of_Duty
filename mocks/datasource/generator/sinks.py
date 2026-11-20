"""Destinations for generated payloads. Every sink exposes `publish(payload)`.

- Feed: in-memory ring buffer read by the pull endpoints (cursor = sequence number)
- KafkaSink: produces to a Kafka topic
- WebhookSink: pushes HTTP POST requests to an external URL
"""

import asyncio
import logging
from collections import deque
from itertools import islice

import httpx
from confluent_kafka import KafkaError, KafkaException, Producer
from confluent_kafka.admin import AdminClient, NewTopic

log = logging.getLogger("sinks")


class Feed:
    """Ring buffer of the most recent payloads, each with a growing sequence number."""

    def __init__(self, size: int):
        self._items: deque[tuple[int, object]] = deque(maxlen=size)
        self._last_seq = 0

    def publish(self, payload) -> None:
        self._last_seq += 1
        self._items.append((self._last_seq, payload))

    def read(self, after: int | None, limit: int) -> tuple[list[tuple[int, object]], int]:
        """Return `(items, next_after)`.

        With `after` set, returns up to `limit` items newer than that sequence number.
        Without it, returns the latest `limit` items, which is how a new consumer starts.
        """
        if not self._items:
            return [], self._last_seq
        first_seq = self._items[0][0]
        if after is None:
            start = max(0, len(self._items) - limit)
        else:
            start = max(0, after + 1 - first_seq)
        items = list(islice(self._items, start, start + limit))
        next_after = items[-1][0] if items else max(after or 0, first_seq - 1)
        return items, next_after


class KafkaSink:
    def __init__(self, bootstrap: str, topic: str):
        self._topic = topic
        self._producer = Producer({"bootstrap.servers": bootstrap, "linger.ms": 50})
        self._ensure_topic(bootstrap)

    def _ensure_topic(self, bootstrap: str) -> None:
        admin = AdminClient({"bootstrap.servers": bootstrap})
        future = admin.create_topics([NewTopic(self._topic, num_partitions=1, replication_factor=1)])[self._topic]
        try:
            future.result(timeout=30)
        except KafkaException as error:
            if error.args[0].code() != KafkaError.TOPIC_ALREADY_EXISTS:
                raise

    def publish(self, payload: str) -> None:
        try:
            self._producer.produce(self._topic, value=payload.encode())
        except BufferError:
            log.warning("Kafka producer queue is full, dropping message")
        self._producer.poll(0)

    def close(self) -> None:
        self._producer.flush(5)


class WebhookSink:
    """Queues payloads and posts them from a background task, so slow targets never block generation."""

    def __init__(self, url: str, queue_size: int = 5000):
        self._url = url
        self._queue: asyncio.Queue[dict] = asyncio.Queue(maxsize=queue_size)

    def publish(self, payload: dict) -> None:
        try:
            self._queue.put_nowait(payload)
        except asyncio.QueueFull:
            log.warning("Webhook queue is full, dropping payload")

    async def run(self) -> None:
        async with httpx.AsyncClient(timeout=5) as client:
            while True:
                payload = await self._queue.get()
                try:
                    response = await client.post(self._url, json=payload)
                    response.raise_for_status()
                except httpx.HTTPError as error:
                    log.warning("Webhook delivery to %s failed: %s", self._url, error)
