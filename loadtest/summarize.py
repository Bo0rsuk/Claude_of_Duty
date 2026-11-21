#!/usr/bin/env python3
"""Prints the results of load test runs as Markdown tables.

Usage: python summarize.py results/baseline [results/optimized ...]
"""

import json
import sys
from pathlib import Path


def cell(stats: dict | None, key: str) -> str:
    return "-" if not stats else f"{stats[key]:g}"


def unique_table(runs: list[dict]) -> list[str]:
    lines = [
        "| Target rate | Accepted | Rejected | Lost | End to end p50 | p99 | Ingest to alert p50 | p99 | Notification p50 | p99 |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for run in runs:
        ingest, latency = run["ingest"], run["latency"]
        lines.append(
            f"| {run['settings']['target_rate_events_per_s']:g}/s "
            f"| {ingest['accepted_rate_events_per_s']:g}/s "
            f"| {ingest['events_sent'] - ingest['events_accepted']} of {ingest['events_sent']} "
            f"| {run['processing']['events_lost']} "
            f"| {cell(latency.get('end_to_end'), 'p50_ms')} ms | {cell(latency.get('end_to_end'), 'p99_ms')} ms "
            f"| {cell(latency.get('ingest_to_alert'), 'p50_ms')} ms | {cell(latency.get('ingest_to_alert'), 'p99_ms')} ms "
            f"| {cell(latency.get('notification'), 'p50_ms')} ms | {cell(latency.get('notification'), 'p99_ms')} ms |")
    return lines


def burst_table(runs: list[dict]) -> list[str]:
    lines = [
        "| Target rate | Events accepted | Alerts created | Final count | First event to alert | Count complete after last event |",
        "|---|---|---|---|---|---|",
    ]
    for run in runs:
        dedup = run["deduplication"]
        lines.append(
            f"| {run['settings']['target_rate_events_per_s']:g}/s | {dedup['events_accepted']} "
            f"| {dedup['alerts_created']} | {dedup['final_count']} "
            f"| {dedup['first_event_to_alert_ms']} ms | {dedup['last_event_to_final_count_ms']} ms |")
    return lines


def main() -> None:
    for directory in sys.argv[1:]:
        runs = sorted((json.loads(path.read_text()) for path in Path(directory).glob("*.json")),
                      key=lambda run: (run["scenario"], run["settings"]["target_rate_events_per_s"], run["run_id"]))
        print(f"### {Path(directory).name}\n")
        for scenario, table in (("unique", unique_table), ("burst", burst_table)):
            selected = [run for run in runs if run["scenario"] == scenario]
            if selected:
                print(f"Scenario `{scenario}`:\n")
                print("\n".join(table(selected)) + "\n")


if __name__ == "__main__":
    main()
