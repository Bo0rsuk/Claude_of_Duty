import assert from "node:assert/strict";
import test from "node:test";
import { formatConditions, parseConditions } from "#services/backend/conditions.ts";

test("condition text becomes the backend's list of conditions", () => {
  assert.deepEqual(parseConditions("service = payments-api AND http.status >= 500 AND level in ERROR, FATAL"), {
    mode: "all",
    conditions: [
      { field: "service", op: "eq", value: "payments-api" },
      { field: "http.status", op: "gte", value: 500 },
      { field: "level", op: "in", value: ["ERROR", "FATAL"] },
    ],
  });
  assert.deepEqual(parseConditions("code != REQUEST_OK OR message contains timeout OR traceId missing"), {
    mode: "any",
    conditions: [
      { field: "code", op: "neq", value: "REQUEST_OK" },
      { field: "message", op: "contains", value: "timeout" },
      { field: "traceId", op: "exists", value: false },
    ],
  });
  assert.deepEqual(parseConditions("service starts_with pricing-engine.eu").conditions, [
    { field: "service", op: "regex", value: "^pricing-engine\\.eu" },
  ]);
  assert.deepEqual(parseConditions("  "), { mode: "all", conditions: [] });
});

test("text that does not follow the format is refused", () => {
  for (const text of ["service", "service ~ x", "a = 1 AND b = 2 OR c = 3", "latency > fast", "host exists yes", "x in"]) {
    assert.equal(parseConditions(text), null, text);
  }
});

test("conditions read back as the text they were written as", () => {
  for (const text of [
    "service = payments-api AND http.status >= 500",
    "level in ERROR, FATAL",
    "service starts_with pricing-engine- AND delay > 10",
    "message matches after \\d+ ms$ OR host missing OR code exists",
    "body contains out of memory",
  ]) {
    assert.equal(formatConditions(parseConditions(text)), text);
  }
  assert.equal(formatConditions(null), "");
});
