import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import {
  arrange,
  columns,
  maxHeight,
  minHeight,
  minWidth,
  reposition,
  spatialOrder,
  type Tile,
} from "#features/canvas/domain/widget-grid.ts";
import { registry } from "#features/canvas/widget-catalog.ts";
import { unique } from "#lib/id.ts";
import { ArrowLeft, ArrowRight, LayoutGrid } from "lucide-react";
import { useState } from "react";

interface TileMenuProps {
  tile: Tile;
  draft: Tile[];
  change: (layout: Tile[]) => void;
  onClose: () => void;
}

export function TileMenu({ tile, draft, change, onClose }: TileMenuProps) {
  const [geometry, setGeometry] = useState(tile.rect);
  const siblings = draft.filter((t) => t.group === tile.group).sort(spatialOrder);
  function moveBy(delta: number) {
    const from = siblings.indexOf(tile),
      to = from + delta;
    if (to < 0 || to >= siblings.length) return;
    siblings.splice(from, 1);
    siblings.splice(to, 0, tile);
    const packed = arrange(
      siblings.map((t) => ({ ...t, rect: { ...t.rect, x: 0, y: 0 } })),
    );
    change([...draft.filter((t) => t.group !== tile.group), ...packed]);
    onClose();
  }
  return (
    <Modal
      title={"Arrange " + registry[tile.kind].name}
      onClose={onClose}
    >
      <form
        className="grid-form"
        onSubmit={(e) => {
          e.preventDefault();
          change(reposition(draft, tile.id, geometry));
          onClose();
        }}
      >
        {(["x", "y", "w", "h"] as const).map((key, i) => {
          const offset = i < 2 ? 1 : 0;
          return (
            <Field
              key={key}
              label={
                ["Column", "Row", "Width in columns", "Height in rows"][i]
              }
            >
              <input
                required
                type="number"
                min={i < 2 ? 1 : key === "w" ? minWidth : minHeight}
                max={
                  key === "x"
                    ? columns - geometry.w + 1
                    : key === "w"
                      ? columns
                      : key === "h"
                        ? maxHeight
                        : undefined
                }
                value={geometry[key] + offset}
                onChange={(e) =>
                  setGeometry({
                    ...geometry,
                    [key]: Number(e.target.value) - offset,
                  })
                }
              />
            </Field>
          );
        })}
        <button className="primary">Apply position and size</button>
      </form>
      <div className="menu-list">
        <button
          disabled={siblings.indexOf(tile) === 0}
          onClick={() => {
            moveBy(-1);
          }}
        >
          <ArrowLeft />
          Move before
        </button>
        <button
          disabled={siblings.indexOf(tile) === siblings.length - 1}
          onClick={() => {
            moveBy(1);
          }}
        >
          <ArrowRight />
          Move after
        </button>
        <button
          onClick={() => {
            change(
              arrange(
                draft.map((t) =>
                  t.id === tile.id
                    ? { ...t, group: t.group ? "" : "Response group" }
                    : t,
                ),
              ),
            );
            onClose();
          }}
        >
          <LayoutGrid />
          {tile.group ? "Remove from group" : "Move to group"}
        </button>
        <button
          onClick={() => {
            change(arrange([...draft, { ...tile, id: unique() }]));
            onClose();
          }}
        >
          Duplicate
        </button>
        <button
          className="danger-text"
          onClick={() => {
            change(draft.filter((t) => t.id !== tile.id));
            onClose();
          }}
        >
          Remove
        </button>
      </div>
    </Modal>
  );
}
