import assert from "node:assert/strict";
import test from "node:test";
import { toRule, toWorkspace } from "#services/backend/to-workspace.ts";

const rule = { ruleId: "r1", name: "Errors", sourceId: "s1", severity: "WARNING", match: { mode: "all", conditions: [] }, routing: [] };
const alert = {
  alertId: "a1", ruleId: "r1", ruleName: "Errors", sourceId: "s1", severity: "WARNING", status: "OPEN", title: "T",
  fields: {}, count: 1, firstSeenAt: "2026-11-20T21:00:00.000Z", lastSeenAt: "2026-11-20T21:00:00.000Z",
};
const snapshot = {
  profile: {}, organization: null, rules: [rule], groups: [], runbooks: [], members: [], alerts: [alert],
  sources: [{ sourceId: "s1", orgId: "o", name: "payments", enabled: false, connector: { type: "webhook" }, parser: null, updatedAt: "2026-11-20T21:00:00.000Z" }],
};
const local = { checks: { a1: [1] }, windows: { r1: { window: "09:00-17:00", fallback: "SMS" } } };

test("a server from before delivery windows and stored runbook steps leaves them to this browser", () => {
  const shown = toRule(snapshot, rule, local);
  assert.deepEqual([shown.window, shown.fallback], ["09:00-17:00", "SMS"]);
  assert.deepEqual(toWorkspace(snapshot, local, {}).alerts[0].checks, [1]);
});

test("what the server holds wins over what this browser kept", () => {
  const delivery = { window: { from: "08:00", to: "20:00", timezone: "Europe/Warsaw" }, outsideWindow: null };
  const shown = toRule(snapshot, { ...rule, delivery }, local);
  assert.deepEqual([shown.window, shown.fallback], ["08:00-20:00", "No notifications"]);
  assert.equal(toRule(snapshot, { ...rule, delivery: null }, local).window, "");

  const stored = { ...snapshot, alerts: [{ ...alert, checkedSteps: [0, 2] }, { ...alert, alertId: "a2", checkedSteps: null }] };
  assert.deepEqual(toWorkspace(stored, { ...local, checks: { a1: [1], a2: [5] } }, {}).alerts.map((shown) => shown.checks), [[0, 2], []]);
});

test("the editor gets the switch, the texts per channel, the limit per recipient and the group's outside contacts", () => {
  const full = {
    ...rule,
    enabled: false,
    presentation: { title: "T", body: "B", fields: [], channels: { email: { subject: "S" }, sms: { body: "short" } } },
    throttle: { perRule: { max: 5, windowSeconds: 300 }, perRecipient: { max: 2, windowSeconds: 600 } },
  };
  const shown = toRule(snapshot, full, local);
  assert.equal(shown.enabled, false);
  assert.equal(shown.body, "B");
  assert.deepEqual(shown.texts, { Email: { subject: "S", body: "" }, SMS: { subject: "", body: "short" } });
  assert.deepEqual([shown.recipientLimit, shown.recipientInterval], ["2", "10"]);
  assert.deepEqual([toRule(snapshot, rule, local).recipientLimit, toRule(snapshot, rule, local).texts], ["", {}]);

  const groups = [{ groupId: "g1", name: "Ops", members: [{ type: "user", userId: "u1" }, { type: "contact", name: "Desk", phone: "+48600100200" }] }];
  const data = toWorkspace({ ...snapshot, groups }, local, {});
  assert.deepEqual(data.groups[0], { id: "g1", name: "Ops", members: ["u1"], contacts: [{ name: "Desk", email: "", phone: "+48600100200" }] });
  assert.deepEqual([data.sources[0].enabled, data.sources[0].ref], [false, "s1"]);
});

test("the first event of an opened alert is shown with the alert", () => {
  const sampleEvent = { eventTime: "2026-11-20T21:00:00.000Z", level: "ERROR", message: "boom", fields: { code: 503 }, raw: "{}" };
  const [shown] = toWorkspace(snapshot, local, {}, { a1: { ...alert, sampleEvent, runbook: null } }).alerts;
  assert.deepEqual(shown.sample, { time: Date.parse(sampleEvent.eventTime), level: "ERROR", message: "boom", fields: { code: "503" }, raw: "{}" });
  assert.equal(toWorkspace(snapshot, local, {}).alerts[0].sample, undefined);
});
