import assert from "node:assert/strict";
import test from "node:test";
import { summarizeActivity } from "#features/alerts/domain/activity.ts";

test("activity counts group volume within the period and excludes resolved alerts from active totals", () => {
  const hour = 3_600_000;
  const alerts = [
    { last: hour, count: 2, status: "Open" },
    { last: 0, count: 3, status: "Resolved" },
    { last: -1, count: 10, status: "Open" },
  ];
  const summary = summarizeActivity(alerts, 1, hour);
  assert.deepEqual(summary.active, [alerts[0]]);
  assert.equal(summary.total, 5);
  const baseline = summarizeActivity([], 1, hour).samples;
  assert.equal(summary.samples[0], baseline[0] + 3);
  assert.equal(summary.samples.at(-1), baseline.at(-1) + 2);
});

test("an alert's events are spread over the time it has been firing", () => {
  const hour = 3_600_000;
  const none = Array(72).fill(0);
  const { samples } = summarizeActivity([{ first: 0, last: hour, count: 2400, status: "Open" }], 1, hour, none);
  assert.equal(samples.length, 24);
  assert.ok(samples.every((count) => count === 100));
});
