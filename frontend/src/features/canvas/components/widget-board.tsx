import {
  type Tile,
  clamp,
  columns,
  gutter,
  maxHeight,
  minHeight,
  minWidth,
  reposition,
  rowHeight,
  spatialOrder,
} from "#features/canvas/domain/widget-grid.ts";
import { GripVertical, MoreHorizontal, Scaling } from "lucide-react";
import {
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
  useEffect,
  useRef,
  useState,
} from "react";

interface Props {
  items: Tile[];
  group: string;
  editing: boolean;
  className: string;
  describe: (tile: Tile) => { name: string; placement: string };
  render: (tile: Tile) => ReactNode;
  onChange: (next: Tile[]) => void;
  menu: (id: string) => void;
}
type Gesture = {
  tile: Tile;
  mode: "move" | "resize";
  dx: number;
  dy: number;
  stepX: number;
  stepY: number;
  pointerId: number;
  origin: Tile[];
  limit: number;
};
export function WidgetBoard({
  items,
  group,
  editing,
  className,
  describe,
  render,
  onChange,
  menu,
}: Props) {
  const board = useRef<HTMLDivElement>(null);
  const gesture = useRef<Gesture | null>(null);
  const latest = useRef<Tile[] | null>(null);
  const [preview, setPreview] = useState<Tile[] | null>(null);
  const [active, setActive] = useState("");
  const tiles = items.filter((t) => t.group === group).sort(spatialOrder);
  const positions = new Map(
    (editing && preview ? preview : items).map((t) => [t.id, t.rect]),
  );
  function finish(save: boolean) {
    const next = latest.current;
    gesture.current = null;
    latest.current = null;
    setPreview(null);
    setActive("");
    if (save && next) onChange(next);
  }
  useEffect(() => {
    if (!active) return;
    const escape = (e: globalThis.KeyboardEvent) => {
      if (e.key === "Escape") finish(false);
    };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, [active]);
  function start(
    e: PointerEvent<HTMLButtonElement>,
    tile: Tile,
    mode: Gesture["mode"],
  ) {
    if (!e.isPrimary || e.button !== 0 || !board.current) return;
    const el = board.current,
      bounds = el.getBoundingClientRect(),
      css = getComputedStyle(el);
    gesture.current = {
      tile,
      mode,
      dx: e.clientX - bounds.left,
      dy: e.clientY - bounds.top,
      stepX: (el.clientWidth + gutter) / columns,
      stepY: parseFloat(css.gridAutoRows) + gutter,
      origin: items,
      pointerId: e.pointerId,
      limit: Math.max(...tiles.map((t) => t.rect.y + t.rect.h)) + 3,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    setActive(tile.id);
  }
  function update(e: PointerEvent<HTMLButtonElement>) {
    const g = gesture.current;
    if (!g || g.pointerId !== e.pointerId || !board.current) return;
    const bounds = board.current.getBoundingClientRect();
    const dx = Math.round((e.clientX - bounds.left - g.dx) / g.stepX),
      dy = Math.round((e.clientY - bounds.top - g.dy) / g.stepY);
    const r = g.tile.rect;
    const dest =
      g.mode === "move"
        ? {
            ...r,
            x: clamp(r.x + dx, 0, columns - r.w),
            y: clamp(r.y + dy, 0, g.limit),
          }
        : {
            ...r,
            w: clamp(r.w + dx, minWidth, columns - r.x),
            h: clamp(r.h + dy, minHeight, maxHeight),
          };
    const same =
      dest.x === r.x && dest.y === r.y && dest.w === r.w && dest.h === r.h;
    latest.current = same ? null : reposition(g.origin, g.tile.id, dest);
    setPreview(latest.current);
  }
  function arrows(e: KeyboardEvent, tile: Tile, mode: Gesture["mode"]) {
    const delta: Record<string, [number, number]> = {
      ArrowLeft: [-1, 0],
      ArrowRight: [1, 0],
      ArrowUp: [0, -1],
      ArrowDown: [0, 1],
    };
    if (!delta[e.key]) return;
    e.preventDefault();
    const [x, y] = delta[e.key],
      r = tile.rect;
    onChange(
      reposition(
        items,
        tile.id,
        mode === "move"
          ? { ...r, x: r.x + x, y: r.y + y }
          : { ...r, w: clamp(r.w + x, minWidth, columns - r.x), h: r.h + y },
      ),
    );
  }
  function handle(tile: Tile, mode: Gesture["mode"]) {
    return {
      "aria-keyshortcuts": "ArrowLeft ArrowRight ArrowUp ArrowDown",
      onPointerDown: (e: PointerEvent<HTMLButtonElement>) =>
        start(e, tile, mode),
      onPointerMove: update,
      onPointerUp: (e: PointerEvent<HTMLButtonElement>) => {
        if (gesture.current?.pointerId === e.pointerId) {
          update(e);
          finish(true);
        }
      },
      onPointerCancel: () => finish(false),
      onLostPointerCapture: () => {
        if (gesture.current) finish(false);
      },
      onKeyDown: (e: KeyboardEvent) => arrows(e, tile, mode),
    };
  }
  const rows = Math.max(
    0,
    ...tiles.map((t) => {
      const r = positions.get(t.id)!;
      return r.y + r.h;
    }),
  );
  const activeRect = positions.get(active);
  return (
    <div
      className="widget-board"
      style={
        {
          "--board-columns": columns,
          "--board-gap": gutter + "px",
          "--board-row": rowHeight + "px",
        } as CSSProperties
      }
    >
      {editing && (
        <div className="grid-ruler" aria-hidden="true">
          {Array.from({ length: columns }, (_, i) => (
            <span
              key={i}
              className={
                activeRect &&
                i >= activeRect.x &&
                i < activeRect.x + activeRect.w
                  ? "in-range"
                  : undefined
              }
            >
              {i + 1}
            </span>
          ))}
        </div>
      )}
      <div
        className={"canvas-grid " + className + (editing ? " board-edit" : "")}
        ref={board}
        style={
          {
            "--board-rows": tiles.length ? rows + (editing ? 2 : 0) : 6,
          } as CSSProperties
        }
      >
        {!tiles.length && (
          <p className="board-empty">
            Choose a widget above to place the first panel.
          </p>
        )}
        {tiles.map((t) => {
          const r = positions.get(t.id)!,
            info = describe(t);
          return (
            <div
              key={t.id}
              data-widget={t.kind}
              className={
                "tile " +
                info.placement +
                (editing ? " editable" : "") +
                (active === t.id ? " manipulating" : "")
              }
              style={
                {
                  "--col": r.x + 1,
                  "--row": r.y + 1,
                  "--w": r.w,
                  "--h": r.h,
                } as CSSProperties
              }
            >
              {editing && (
                <header className="edit-handle">
                  <button
                    className="drag-grip"
                    aria-label={"Move " + info.name}
                    title="Drag or use arrow keys to move"
                    {...handle(t, "move")}
                  >
                    <GripVertical />
                    <strong>{info.name}</strong>
                  </button>
                  <span className="grid-size">
                    {r.w} × {r.h}
                  </span>
                  <button
                    className="icon-btn"
                    aria-label={"Arrange " + info.name}
                    onClick={() => menu(t.id)}
                  >
                    <MoreHorizontal />
                  </button>
                </header>
              )}
              <div className="tile-content">{render(t)}</div>
              {editing && (
                <button
                  className="resize-grip"
                  aria-label={"Resize " + info.name}
                  title="Drag or use arrow keys to resize"
                  {...handle(t, "resize")}
                >
                  <Scaling />
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
