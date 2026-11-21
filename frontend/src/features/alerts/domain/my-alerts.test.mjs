import assert from "node:assert/strict";
import test from "node:test";
import { alertsOf } from "#features/alerts/domain/my-alerts.ts";

const data = {
  members: [
    { id: "u1", name: "Anna Nowak", email: "anna@example.pl", phone: "+48600100200" },
    { id: "u2", name: "Bob", email: "bob@example.pl", phone: "" },
  ],
  groups: [
    { id: "g1", name: "Payments on-call", members: ["u1"] },
    { id: "g2", name: "Platform", members: ["u2"] },
  ],
  alerts: [
    { id: "owned", owner: "Anna Nowak", target: "Anna Nowak", notified: [] },
    { id: "direct", owner: "", target: "Anna Nowak", notified: ["Anna Nowak"] },
    { id: "via-group", owner: "", target: "Payments on-call", notified: ["Payments on-call"] },
    { id: "by-address", owner: "", target: "x", notified: ["anna@example.pl"] },
    { id: "someone-else", owner: "Bob", target: "Bob", notified: ["Platform"] },
    { id: "nobody", owner: "", target: "Unassigned", notified: [] },
  ],
};

test("a person's alerts are the ones they own or are notified about, directly or through a group", () => {
  assert.deepEqual(alertsOf("u1", data).map((alert) => alert.id), ["owned", "direct", "via-group", "by-address"]);
  assert.deepEqual(alertsOf("u2", data).map((alert) => alert.id), ["someone-else"]);
  assert.deepEqual(alertsOf("unknown", data), []);
});

test("an empty phone or name never makes unassigned alerts somebody's", () => {
  const blank = { ...data, alerts: [{ id: "a", owner: "", target: "", notified: [""] }] };
  assert.deepEqual(alertsOf("u2", blank), []);
});
