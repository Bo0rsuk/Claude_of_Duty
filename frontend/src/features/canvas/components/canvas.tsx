import { Box } from "#components/box.tsx";
import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import {
  ContextPicker,
  controllingLists,
} from "#features/canvas/components/context-picker.tsx";
import { TileMenu } from "#features/canvas/components/tile-menu.tsx";
import { WidgetBoard } from "#features/canvas/components/widget-board.tsx";
import { type StoredTile, type Tile, arrange } from "#features/canvas/domain/widget-grid.ts";
import { defaultLayout, originalAlerts, registry } from "#features/canvas/widget-catalog.ts";
import { allowed, useWorkspaceContext } from "#features/workspace";
import { useLocal } from "#hooks/use-local.ts";
import { unique } from "#lib/id.ts";
import {
  ArrowLeft,
  Check,
  LayoutGrid,
  Monitor,
  Plus,
  RotateCcw,
  Smartphone,
  Tablet,
  Undo2,
} from "lucide-react";
import { useLayoutEffect, useRef, useState } from "react";

export function Canvas({
  editing,
  setEditing,
}: {
  editing: boolean;
  setEditing: (v: boolean) => void;
}) {
  const {
    panel,
    role,
    notify,
    mode,
    setMode,
    compactDetail,
    setCompactDetail,
    rule,
    account,
  } = useWorkspaceContext();
  const [saved, setSaved] = useLocal<StoredTile[]>(
    "fans:layout:" + role + ":" + panel,
    () => defaultLayout(panel),
  );
  const catalog = Object.keys(registry).filter(
    (k) =>
      allowed(registry[k].panel, role) &&
      !(account && registry[k].demoOnly) &&
      (panel === "Custom" || registry[k].panel === panel),
  );
  const clean = (arr: StoredTile[]) => {
    const kept = arr.filter((t) => catalog.includes(t.kind));
    // When a stored tile is no longer offered, the rest is laid out again so that no hole is left where it was.
    const layout = arrange(
      kept.length === arr.length ? kept : kept.map(({ id, kind, wide, group }) => ({ id, kind, wide, group })),
    );
    // Upgrade the former half-width default while preserving individually arranged boards.
    return panel === "Alerts" &&
      JSON.stringify(layout) === JSON.stringify(originalAlerts)
      ? defaultLayout(panel)
      : layout;
  };
  const [draft, setDraft] = useState(() => clean(saved)),
    [history, setHistory] = useState<Tile[][]>([]),
    [pick, setPick] = useState(catalog[0]),
    [menu, setMenu] = useState(""),
    [width, setWidth] = useState("Desktop");
  const tiles = editing ? draft : clean(saved),
    dirty = JSON.stringify(draft) !== JSON.stringify(clean(saved)),
    cur = tiles.find((t) => t.id === menu);
  const change = (next: Tile[]) => {
    if (JSON.stringify(next) === JSON.stringify(draft)) return;
    setHistory((h) => [...h, draft]);
    setDraft(next);
  };
  const stop = () => {
    setEditing(false);
    setWidth("Desktop");
    setHistory([]);
    setMenu("");
  };
  const previousDetail = useRef(false);
  const returnButton = useRef<HTMLButtonElement>(null);
  useLayoutEffect(() => {
    if (compactDetail && returnButton.current?.offsetParent)
      returnButton.current.focus();
    else if (!compactDetail && previousDetail.current) {
      document
        .querySelector<HTMLElement>(
          '.alert-link[aria-pressed="true"], .record-list button[aria-pressed="true"]',
        )
        ?.focus();
    }
    previousDetail.current = compactDetail;
  }, [compactDetail]);
  const back = () => setCompactDetail(false);
  // The alert workspace with its usual widgets opens details beside the feed, however a stored
  // layout sized them. Only a view built from other widgets keeps its own grid.
  const kinds = (layout: { kind: string }[]) => layout.map((t) => t.kind).sort().join();
  const standardAlerts = panel === "Alerts" && kinds(tiles) === kinds(defaultLayout("Alerts"));
  const hasList = tiles.some((t) => registry[t.kind].placement === "list"),
    hasDetail = tiles.some((t) => registry[t.kind].placement === "detail");
  function renderWidget(t: Tile) {
    const w = registry[t.kind],
      View = w.view;
    return w.boxed ? (
      <Box title={w.name} icon={LayoutGrid}>
        <View key={t.kind === "assistant" ? rule.id : undefined} />
      </Box>
    ) : (
      <View />
    );
  }
  function body() {
    if (!editing && mode !== "Normal" && mode !== "Partial data")
      return (
        <section className="surface">
          {mode === "Loading" ? (
            <div aria-live="polite" className="loading">
              <span>Loading workspace…</span>
              <i />
              <i />
              <i />
            </div>
          ) : (
            <Empty
              title={
                mode === "Permission denied"
                  ? "Access restricted"
                  : mode === "Error"
                    ? "This view couldn’t load"
                    : "Your workspace is empty"
              }
              action={
                <button onClick={() => setMode("Normal")}>
                  {mode === "Error"
                    ? "Retry loading"
                    : mode === "Permission denied"
                      ? "Return to permitted view"
                      : "Load demo fixtures"}
                </button>
              }
            >
              {mode === "Permission denied"
                ? "Your demo role has limited access."
                : mode === "Error"
                  ? "The workspace could not be loaded. Check your connection and try again."
                  : "Add sample records to explore this view."}
            </Empty>
          )}
        </section>
      );
    if (!tiles.length && !editing)
      return (
        <section className="surface">
          <Empty
            title="Build your custom view"
            action={
              <button
                className="primary"
                onClick={() => {
                  setDraft(clean(saved));
                  setEditing(true);
                }}
              >
                <Plus />
                Add widgets
              </button>
            }
          >
            Choose widgets from the catalog to arrange your workspace.
          </Empty>
        </section>
      );
    const groups = tiles.length
      ? [...new Set(tiles.map((t) => t.group))]
      : [""];
    const lists = new Set(tiles.map((t) => t.kind));
    const missingLists = [
      ...new Set(
        tiles
          .map((t) => controllingLists[t.kind])
          .filter((k) => k && !lists.has(k) && !(account && registry[k]?.demoOnly)),
      ),
    ];
    return (
      <>
        {missingLists.map((list) => (
          <ContextPicker key={list} list={list} />
        ))}
        {groups.map((group) => {
          const board = (
            <WidgetBoard
              items={tiles}
              group={group}
              editing={editing}
              className={
                (hasList && hasDetail && panel !== "Custom"
                  ? "split-mobile "
                  : "") + (compactDetail ? "show-detail" : "show-list")
              }
              describe={(t) => registry[t.kind]}
              render={renderWidget}
              onChange={change}
              menu={setMenu}
            />
          );
          return group ? (
            <section className="widget-group" key={group}>
              <h3>{group}</h3>
              {board}
            </section>
          ) : (
            <div key={group}>{board}</div>
          );
        })}
      </>
    );
  }
  return (
    <>
      {editing && (
        <section className="layout-tools surface">
          <div>
            <h2>
              <LayoutGrid />
              Customize view
            </h2>
            <span className="draft">
              {dirty ? "Unsaved changes" : "Editing layout"}
            </span>
          </div>
          <p className="grid-hint">
            Drag a header to move a panel. Drag its corner to resize. Both
            handles also support arrow keys.
          </p>
          <div className="buttons">
            <Field label="Widget catalog">
              <select value={pick} onChange={(e) => setPick(e.target.value)}>
                {catalog.map((k) => (
                  <option value={k} key={k}>
                    {panel === "Custom" ? registry[k].panel + " · " : ""}
                    {registry[k].name}
                  </option>
                ))}
              </select>
            </Field>
            <button
              onClick={() =>
                change(
                  arrange([
                    ...draft,
                    {
                      id: unique(),
                      kind: pick,
                      wide: registry[pick].placement === "full",
                      group: "",
                    },
                  ]),
                )
              }
            >
              <Plus />
              Add widget
            </button>
          </div>
          <div className="buttons">
            <div className="segmented">
              {(["Desktop", "Tablet", "Mobile"] as const).map((v, i) => {
                const Icon = [Monitor, Tablet, Smartphone][i];
                return (
                  <button
                    title={v + " preview"}
                    aria-label={v + " preview"}
                    aria-pressed={width === v}
                    key={v}
                    onClick={() => setWidth(v)}
                  >
                    <Icon />
                  </button>
                );
              })}
            </div>
            <button
              disabled={!history.length}
              onClick={() => {
                setDraft(history.at(-1)!);
                setHistory(history.slice(0, -1));
              }}
            >
              <Undo2 />
              Undo
            </button>
            <button onClick={() => change(defaultLayout(panel))}>
              <RotateCcw />
              Restore default
            </button>
            <button
              onClick={() => {
                setDraft(clean(saved));
                stop();
              }}
            >
              Cancel
            </button>
            <button
              className="primary"
              onClick={() => {
                setSaved(draft);
                stop();
                notify("Layout saved locally for " + role + ".");
              }}
            >
              <Check />
              Save layout
            </button>
          </div>
        </section>
      )}
      {mode === "Partial data" && (
        <div className="notice-bar">
          Some event fields are unavailable in this simulated state.{" "}
          <button onClick={() => setMode("Normal")}>Retry</button>
        </div>
      )}
      <div
        className={
          "canvas-wrap " +
          (standardAlerts ? "alerts-canvas " : "") +
          (editing ? "editing " : "") +
          width.toLowerCase()
        }
      >
        {compactDetail &&
          !editing &&
          hasList &&
          hasDetail &&
          panel !== "Custom" && (
            <button ref={returnButton} className="back-list" onClick={back}>
              <ArrowLeft />
              Back to list
            </button>
          )}
        {body()}
      </div>
      {cur && (
        <TileMenu key={cur.id} tile={cur} draft={draft} change={change} onClose={() => setMenu("")} />
      )}
    </>
  );
}
