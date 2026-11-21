// All group boards share a twelve-column coordinate system. Positions are zero-based.
export const columns = 12;
export const rowHeight = 32;
export const gutter = 6;
export const minWidth = 3;
export const minHeight = 2;
export const maxHeight = 48;

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}
export interface StoredTile {
  id: string;
  kind: string;
  group: string;
  wide?: boolean;
  rect?: Rect;
}
export interface Tile extends StoredTile {
  rect: Rect;
}

const heights: Record<string, number> = {
  activity: 5,
  feed: 18,
  details: 18,
  rules: 18,
  rule: 18,
  sources: 12,
  source: 12,
  groups: 8,
  members: 8,
  books: 10,
  book: 10,
  users: 12,
  user: 12,
  legend: 8,
  assign: 12,
  teams: 9,
  team: 12,
  accounts: 12,
  code: 7,
  account: 12,
  manage: 4,
};
export const clamp = (n: number, min: number, max: number) =>
  Math.min(max, Math.max(min, Math.round(n)));
function bounded(r: Rect): Rect {
  const w = clamp(r.w, minWidth, columns);
  return {
    x: clamp(r.x, 0, columns - w),
    y: Math.max(0, Math.round(r.y)),
    w,
    h: clamp(r.h, minHeight, maxHeight),
  };
}
function overlaps(a: Rect, b: Rect) {
  return (
    a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
  );
}
function firstSpace(size: Rect, occupied: Rect[]): Rect {
  const end = occupied.reduce((n, r) => Math.max(n, r.y + r.h), 0);
  for (let y = 0; y <= end; y++)
    for (let x = 0; x <= columns - size.w; x++) {
      const r = { ...size, x, y };
      if (!occupied.some((o) => overlaps(o, r))) return r;
    }
  return { ...size, x: 0, y: end };
}
export function arrange(items: StoredTile[]): Tile[] {
  const boards = new Map<string, Rect[]>();
  return items.map((item) => {
    const occupied = boards.get(item.group) ?? [];
    const valid =
      item.rect &&
      ["x", "y", "w", "h"].every((k) =>
        Number.isFinite(item.rect![k as keyof Rect]),
      );
    const initial = valid
      ? bounded(item.rect!)
      : { x: 0, y: 0, w: item.wide ? columns : 6, h: heights[item.kind] ?? 8 };
    const rect =
      valid && !occupied.some((o) => overlaps(o, initial))
        ? initial
        : firstSpace(initial, occupied);
    occupied.push(rect);
    boards.set(item.group, occupied);
    return { id: item.id, kind: item.kind, group: item.group, rect };
  });
}
export function reposition(items: Tile[], id: string, requested: Rect): Tile[] {
  const current = items.find((t) => t.id === id);
  if (!current) return items;
  const next = bounded(requested);
  const peers = items.filter((t) => t.group === current.group && t !== current);
  const displaced = peers.filter((t) => overlaps(t.rect, next));
  const occupied = [
    next,
    ...peers.filter((t) => !displaced.includes(t)).map((t) => t.rect),
  ];
  const changes = new Map([[id, next]]);
  for (const tile of displaced) {
    const exchange = bounded({
      ...tile.rect,
      x: current.rect.x,
      y: current.rect.y,
    });
    const rect = occupied.some((o) => overlaps(o, exchange))
      ? firstSpace(tile.rect, occupied)
      : exchange;
    changes.set(tile.id, rect);
    occupied.push(rect);
  }
  return items.map((t) =>
    changes.has(t.id) ? { ...t, rect: changes.get(t.id)! } : t,
  );
}
export const spatialOrder = (a: Tile, b: Tile) =>
  a.rect.y - b.rect.y || a.rect.x - b.rect.x;
