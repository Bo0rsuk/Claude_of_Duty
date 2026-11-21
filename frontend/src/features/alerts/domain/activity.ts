import { inboundBaseline } from "#features/alerts/domain/inbound-baseline.ts";
import type { Alert } from "#features/workspace";

/** @param baseline background traffic per 20 minutes of a day; pass zeros when only real events should count */
export function summarizeActivity(alerts: Alert[], hours: number, until: number, baseline = inboundBaseline) {
  const recent = alerts.filter((a) => a.last >= until - hours * 3600000);
  const active = recent.filter((a) => a.status !== "Resolved");
  const total = recent.reduce((n, a) => n + a.count, 0);
  const buckets = hours === 24 ? 72 : hours === 6 ? 36 : 24;
  const since = until - hours * 3600000,
    bucketMs = (until - since) / buckets;
  // The baseline represents unmatched traffic. Grouped alert events arrive in their recorded time bucket.
  const samples = Array.from({ length: buckets }, (_, i) => {
    const baselineIndex = Math.min(
      71,
      Math.floor(72 * (1 - hours / 24) + (((72 * hours) / 24) * i) / buckets),
    );
    return Math.round(
      (baseline[baselineIndex] * 9 * bucketMs) / (20 * 60000),
    );
  });
  const bucketOf = (time: number) => Math.min(buckets - 1, Math.max(0, Math.floor((time - since) / bucketMs)));
  recent.forEach((a) => {
    // Only the first and last event of an alert are known, so its events are spread evenly between them.
    const from = bucketOf(a.first ?? a.last),
      to = bucketOf(a.last);
    for (let bucket = from; bucket <= to; bucket++) samples[bucket] += a.count / (to - from + 1);
  });
  const rounded = samples.map(Math.round);
  return { active, total, samples: rounded, max: Math.max(...rounded, 1) };
}
