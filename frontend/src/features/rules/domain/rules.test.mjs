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
