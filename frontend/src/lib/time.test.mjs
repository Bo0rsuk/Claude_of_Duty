import assert from "node:assert/strict";
import test from "node:test";
import { ago, stamp } from "#lib/time.ts";

test("fresh times are counted in seconds, older ones in coarser units", () => {
  const now = 10_000_000_000;
  assert.equal(ago(now - 2_000, now), "now");
  assert.equal(ago(now - 42_000, now), "42 s ago");
  assert.equal(ago(now - 5 * 60_000 - 1, now), "5 min ago");
  assert.equal(ago(now - 3 * 3_600_000, now), "3 h ago");
  assert.match(ago(now - 3 * 86_400_000, now), /\d{2}:\d{2}/);
  assert.equal(ago(now + 500, now), "now");
});

test("a moment from another day carries its date", () => {
  const noon = new Date(2026, 10, 21, 12, 0, 0).getTime();
  assert.equal(stamp(noon - 3_600_000, noon), "11:00:00");
  assert.equal(stamp(noon - 24 * 3_600_000, noon), "20 Nov, 12:00:00");
});
