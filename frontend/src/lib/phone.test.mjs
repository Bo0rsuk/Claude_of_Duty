import assert from "node:assert/strict";
import test from "node:test";
import { normalizePhone } from "#lib/phone.ts";

test("a phone number is accepted in the international form, however it is spaced", () => {
  assert.equal(normalizePhone("+48600100200"), "+48600100200");
  assert.equal(normalizePhone(" +48 600 100 200 "), "+48600100200");
  assert.equal(normalizePhone("+1 (415) 555-0100"), "+14155550100");
  assert.equal(normalizePhone("   "), "");
});

test("anything else is refused", () => {
  for (const text of ["ssss", "600100200", "0048600100200", "+48", "+48 600 100 20x", "+4860010020012345678"]) {
    assert.equal(normalizePhone(text), null, text);
  }
});
