import assert from "node:assert/strict";
import test from "node:test";
import { Unsupported, groupBody, ruleBody, sourceBody, sourceUpdateBody } from "#services/backend/to-backend.ts";

const stored = {
  ruleId: "r1",
  enabled: true,
  presentation: { title: "T", body: "{{message}} on {{host}}", fields: [], channels: { sms: { body: "old sms" } } },
  throttle: { perRule: { max: 5, windowSeconds: 300 }, perRecipient: { max: 2, windowSeconds: 600 } },
};
const snapshot = {
  rules: [stored],
  sources: [{ sourceId: "s1", name: "payments" }],
  groups: [{ groupId: "g1", members: [{ type: "user", userId: "u1" }, { type: "contact", name: "Vendor", email: "v@example.com" }] }],
};
const data = { groups: [], members: [] };
const rule = {
  id: "r1", name: "Errors", source: "payments", severity: "Critical", conditions: "level = ERROR", targets: [], channels: [],
  group: "", interval: "5", limit: "5", runbook: "", template: "{{message}}", fields: "",
};

test("a rule draft that never showed the newer settings keeps what the rule had", () => {
  const body = ruleBody(rule, data, snapshot);
  assert.equal(body.enabled, true);
  assert.equal(body.presentation.body, "{{message}} on {{host}}");
  assert.deepEqual(body.presentation.channels, { sms: { body: "old sms" } });
  assert.deepEqual(body.throttle.perRecipient, { max: 2, windowSeconds: 600 });
});

test("the rule editor sets the switch, the body, the texts per channel and the limit per recipient", () => {
  const body = ruleBody(
    {
      ...rule,
      enabled: false,
      body: "{{message}}",
      texts: { Email: { subject: "[{{severity}}] {{ruleName}}", body: " " }, SMS: { subject: "", body: "" }, Push: { body: "x{{count}}" } },
      recipientLimit: "3",
      recipientInterval: "10",
    },
    data,
    snapshot,
  );
  assert.equal(body.enabled, false);
  assert.equal(body.presentation.body, "{{message}}");
  assert.deepEqual(body.presentation.channels, { email: { subject: "[{{severity}}] {{ruleName}}" }, push: { body: "x{{count}}" } });
  assert.deepEqual(body.throttle.perRecipient, { max: 3, windowSeconds: 600 });

  const cleared = ruleBody({ ...rule, texts: {}, recipientLimit: "", recipientInterval: "" }, data, snapshot);
  assert.equal(cleared.presentation.channels, null);
  assert.equal(cleared.throttle.perRecipient, null);
  assert.throws(() => ruleBody({ ...rule, recipientLimit: "3", recipientInterval: "" }, data, snapshot), Unsupported);
});

test("outside contacts of a group are saved from the screen, or carried over when the screen did not list them", () => {
  assert.deepEqual(groupBody({ id: "g1", name: "Ops", members: ["u1"] }, snapshot).members, [
    { type: "user", userId: "u1" },
    { type: "contact", name: "Vendor", email: "v@example.com" },
  ]);
  const edited = { id: "g1", name: "Ops", members: [], contacts: [{ name: " Desk ", email: "", phone: "+48600100200" }] };
  assert.deepEqual(groupBody(edited, snapshot).members, [{ type: "contact", name: "Desk", email: null, phone: "+48600100200" }]);
});

test("a source keeps its parser and connector details when its name, address or switch is edited", () => {
  const existing = {
    sourceId: "s1", name: "payments", enabled: true,
    connector: { type: "http-pull", url: "https://old.example/events", intervalSeconds: 5, itemsPath: "items" },
    parser: { format: "regex", pattern: "x" },
  };
  assert.deepEqual(sourceUpdateBody({ id: " billing ", connection: "https://new.example/events", enabled: false }, existing), {
    name: "billing",
    enabled: false,
    connector: { type: "http-pull", url: "https://new.example/events", intervalSeconds: 5, itemsPath: "items" },
    parser: { format: "regex", pattern: "x" },
  });
  assert.throws(() => sourceUpdateBody({ id: "billing", connection: "ftp://x" }, existing), Unsupported);
  // A webhook's address is assigned by the server and is not sent.
  const webhook = { ...existing, connector: { type: "webhook" } };
  assert.deepEqual(sourceUpdateBody({ id: "hook", connection: "https://ingest/..." }, webhook).connector, { type: "webhook" });
});

test("a source made from a preset sends the preset's connector and parser with the address typed in", () => {
  const preset = { connector: { type: "kafka", bootstrapServers: "a:9092", topic: "t", groupId: "g" }, parser: { format: "json", level: "severity" } };
  assert.deepEqual(sourceBody({ id: "infra", connection: "broker:9092/infra.events", preset }), {
    name: "infra",
    connector: { type: "kafka", bootstrapServers: "broker:9092", topic: "infra.events", groupId: "g" },
    parser: { format: "json", level: "severity" },
  });
});

test("the delivery window is sent as two times of day in the time zone it was written in", () => {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  assert.equal(ruleBody({ ...rule, window: "", fallback: "Email" }, data, snapshot).delivery, null);
  assert.deepEqual(ruleBody({ ...rule, window: " 8:00 - 20:00 ", fallback: "Email" }, data, snapshot).delivery, {
    window: { from: "08:00", to: "20:00", timezone: zone },
    outsideWindow: "email",
  });
  // A window edited later keeps the zone of whoever set it up, and may send nothing outside its hours.
  const lisbon = { ...snapshot, rules: [{ ...stored, delivery: { window: { from: "08:00", to: "20:00", timezone: "Europe/Lisbon" }, outsideWindow: "email" } }] };
  assert.deepEqual(ruleBody({ ...rule, window: "22:00-06:00", fallback: "No notifications" }, data, lisbon).delivery, {
    window: { from: "22:00", to: "06:00", timezone: "Europe/Lisbon" },
    outsideWindow: null,
  });
  assert.throws(() => ruleBody({ ...rule, window: "nights", fallback: "Email" }, data, snapshot), Unsupported);
});

test("the pause between channels is sent in seconds, kept when a screen does not edit it, and checked", () => {
  assert.equal(ruleBody(rule, data, snapshot).escalation, null);
  assert.deepEqual(ruleBody({ ...rule, escalation: "30" }, data, snapshot).escalation, { stepSeconds: 30 });
  assert.equal(ruleBody({ ...rule, escalation: " " }, data, snapshot).escalation, null);
  const escalating = { ...snapshot, rules: [{ ...stored, escalation: { stepSeconds: 45 } }] };
  assert.deepEqual(ruleBody(rule, data, escalating).escalation, { stepSeconds: 45 });
  assert.throws(() => ruleBody({ ...rule, escalation: "2" }, data, snapshot), Unsupported);
  assert.throws(() => ruleBody({ ...rule, escalation: "soon" }, data, snapshot), Unsupported);
});
