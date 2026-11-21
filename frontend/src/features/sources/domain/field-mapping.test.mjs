import assert from "node:assert/strict";
import test from "node:test";
import { detectFieldMapping } from "#features/sources/domain/field-mapping.ts";

test("source fields are mapped in order for the supported event formats", () => {
  const expected = "service → service\nlevel → level";
  assert.equal(detectFieldMapping('{"service":"api","level":"error"}', "JSON"), expected);
  assert.equal(detectFieldMapping("service=api level=error", "key=value"), expected);
  assert.equal(detectFieldMapping("service: api level: error", "Custom format"), expected);
  assert.equal(detectFieldMapping("plain text", "Custom format"), "message → message");
  assert.throws(() => detectFieldMapping("{}", "JSON"));
  assert.throws(() => detectFieldMapping("plain text", "key=value"));
});
