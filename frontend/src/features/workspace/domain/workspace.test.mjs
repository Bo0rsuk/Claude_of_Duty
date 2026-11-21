import assert from "node:assert/strict";
import test from "node:test";
import { allowed, panelNames } from "#features/workspace/domain/panel-access.ts";
import { markAlertViewed } from "#features/workspace/domain/viewed-alert.ts";

test("navigation exposes the expected panels for each demo role", () => {
  const panelsFor = (role) => panelNames.filter((panel) => allowed(panel, role));
  assert.deepEqual(panelsFor("User"), ["Alerts", "Custom"]);
  assert.deepEqual(panelsFor("Team lead"), panelNames.filter((panel) => panel !== "Admin"));
  assert.deepEqual(panelsFor("Admin"), panelNames);
});

test("opening the same alert twice records a single view", () => {
  const data = { alerts: [{ id: "a", timeline: [] }] };
  const viewed = markAlertViewed(data, "a", 1000);
  assert.equal(data.alerts[0].timeline.length, 0);
  assert.deepEqual(viewed.alerts[0].timeline, [
    { title: "Viewed", body: "Opened in the demo workspace", time: 1000 },
  ]);
  assert.equal(markAlertViewed(viewed, "a", 2000), viewed);
  assert.equal(markAlertViewed(viewed, "unknown", 3000), viewed);
});
