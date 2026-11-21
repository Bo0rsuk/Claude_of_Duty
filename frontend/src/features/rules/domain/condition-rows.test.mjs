import assert from "node:assert/strict";
import test from "node:test";
import { toRows, toText } from "#features/rules/domain/condition-rows.ts";
import { parseConditions } from "#services/backend/conditions.ts";

test("conditions written as text open as rows and come back as the same text", () => {
  for (const text of [
    "level = ERROR",
    "service = payments-api AND http.status >= 500",
    "level in ERROR, FATAL OR value > 2000",
    "service starts_with pricing- AND trace_id exists AND code missing",
    "message matches ^timeout.*db$ AND message contains pool exhausted",
  ]) {
    const parsed = toRows(text);
    assert.ok(parsed, text);
    assert.equal(toText(parsed.mode, parsed.rows), text);
    // Whatever the builder writes, the backend format accepts.
    assert.ok(parseConditions(toText(parsed.mode, parsed.rows)), text);
  }
  assert.deepEqual(toRows("a = 1 OR b = 2")?.mode, "any");
  assert.deepEqual(toRows("  "), { mode: "all", rows: [] });
});

test("text the builder cannot show as rows is left to the text editor", () => {
  assert.equal(toRows("a = 1 AND b = 2 OR c = 3"), null);
  assert.equal(toRows("level ~ ERROR"), null);
  assert.equal(toRows("code exists now"), null);
});

test("a row that is not filled in yet is not written", () => {
  const rows = [
    { field: "level", operator: "=", value: "ERROR" },
    { field: "", operator: "=", value: "" },
    { field: "status", operator: ">=", value: " " },
    { field: "trace_id", operator: "exists", value: "" },
  ];
  assert.equal(toText("all", rows), "level = ERROR AND trace_id exists");
});
