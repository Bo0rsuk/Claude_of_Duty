import assert from "node:assert/strict";
import test from "node:test";
import { arrange, reposition } from "#features/canvas/domain/widget-grid.ts";

test("legacy tiles acquire stable positions and groups keep separate grids", () => {
  const tiles = arrange([
    { id: "a", kind: "feed", group: "" },
    { id: "b", kind: "details", group: "" },
    { id: "c", kind: "feed", group: "other" },
  ]);
  assert.deepEqual(tiles.map((tile) => tile.rect.x), [0, 6, 0]);
  assert.deepEqual(arrange(tiles), tiles);
  const moved = reposition(tiles, "a", tiles[1].rect);
  assert.deepEqual(moved.map((tile) => tile.rect.x), [6, 0, 0]);
  assert.deepEqual(tiles[0].rect, { x: 0, y: 0, w: 6, h: 18 });
});
