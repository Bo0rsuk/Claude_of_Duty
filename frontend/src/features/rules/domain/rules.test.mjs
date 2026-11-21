import assert from "node:assert/strict";
import test from "node:test";
import { testConditions } from "#features/rules/domain/test-conditions.ts";
import { ruleValidationError } from "#features/rules/domain/rule-validation.ts";

test("all conditions must match the same event", () => {
  const events = [
    { service: "pricing-eu", level: "warning", delay: "12" },
    { service: "pricing-us", level: "warning", delay: "2" },
    { service: "payments", level: "warning", delay: "23" },
  ];
  const proposal = "service starts_with pricing- AND delay > 10 AND level = warning";
  assert.match(testConditions(proposal, events), /1 of the last 3 events matched/);
  assert.match(testConditions("level != warning", events), /Edit the proposal/);
});

test("saving a draft requires a name, conditions, delivery settings and positive limits", () => {
  const rule = { name: "Delay", conditions: "delay > 10", targets: ["On-call"], channels: ["SMS"], interval: "1", limit: "2" };
  assert.equal(ruleValidationError(rule), "");
  for (const change of [{ name: " " }, { conditions: "" }, { targets: [] }, { channels: [] }, { interval: "0" }, { limit: "-1" }]) {
    assert.ok(ruleValidationError({ ...rule, ...change }));
  }
});

test("the limit per recipient and the delivery window are complete or empty", () => {
  const rule = { name: "Delay", conditions: "delay > 10", targets: ["On-call"], channels: ["SMS"], interval: "1", limit: "2" };
  assert.equal(ruleValidationError({ ...rule, recipientLimit: "", recipientInterval: "", window: "" }), "");
  assert.equal(ruleValidationError({ ...rule, recipientLimit: "2", recipientInterval: "10", window: "08:00-20:00" }), "");
  assert.equal(ruleValidationError({ ...rule, window: "22:00\u201306:00" }), "");
  assert.match(ruleValidationError({ ...rule, recipientLimit: "2", recipientInterval: "" }), /limit per recipient/);
  assert.match(ruleValidationError({ ...rule, recipientLimit: "0", recipientInterval: "5" }), /limit per recipient/);
  for (const window of ["office hours", "8-20", "25:00-26:00", "08:00-08:00"]) {
    assert.match(ruleValidationError({ ...rule, window }), /delivery window/);
  }
});

test("on the backend a rule may notify nobody, but half-filled delivery settings are refused", () => {
  const rule = { name: "Delay", conditions: "delay > 10", targets: [], channels: [], interval: "1", limit: "2" };
  assert.equal(ruleValidationError(rule, true), "");
  assert.match(ruleValidationError({ ...rule, channels: ["SMS"] }, true), /Add a recipient/);
  assert.match(ruleValidationError({ ...rule, targets: ["Ada"] }, true), /delivery channel/);
});
