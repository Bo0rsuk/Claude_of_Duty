import { inboundBaseline } from "#features/alerts/domain/inbound-baseline.ts";
import type { Alert } from "#features/workspace";

export function summarizeActivity(alerts: Alert[], hours: number, until: number) {
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
      (inboundBaseline[baselineIndex] * 9 * bucketMs) / (20 * 60000),
    );
  });
  recent.forEach((a) => {
    const bucket = Math.min(
      buckets - 1,
      Math.max(0, Math.floor((a.last - since) / bucketMs)),
    );
    samples[bucket] += a.count;
  });
  const max = Math.max(...samples, 1);
  return { active, total, samples, max };
}
