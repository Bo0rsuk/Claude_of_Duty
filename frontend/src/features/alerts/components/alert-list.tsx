import { Empty } from "#components/empty.tsx";
import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { OlderAlerts } from "#features/alerts/components/older-alerts.tsx";
import { buildAlertFeed } from "#features/alerts/domain/alert-feed.ts";
import { addAction } from "#features/alerts/utils/add-action.ts";
import {
  type Status,
  fixtures,
  severities,
  statuses,
  useWorkspaceContext,
} from "#features/workspace";
import { ago } from "#lib/time.ts";
import { useNow } from "#lib/use-now.ts";
import { Filter, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/** Who is working on the alert. Demo records keep that in target. */
const ownerOf = (alert: { owner?: string; target: string }) => alert.owner ?? alert.target;

const feedTime = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
});

export function AlertList() {
  const {
    data,
    alertId,
    selectAlert,
    commit,
    paused,
    mode,
    severity,
    setSeverity,
    status,
    setStatus,
    account,
  } = useWorkspaceContext();
  const [query, setQuery] = useState(""),
    [filters, setFilters] = useState(false),
    [sort, setSort] = useState("newest"),
    [openGroup, setOpenGroup] = useState(""),
    [checked, setChecked] = useState<string[]>([]),
    [pending, setPending] = useState(0);
  const list = useRef<HTMLDivElement>(null);
  const now = useNow(5000);
  useEffect(() => {
    // Real alerts arrive by themselves; only the demo has to make some up.
    if (account || paused || mode !== "Normal") return;
    const t = setInterval(() => setPending((n) => Math.min(n + 1, 5)), 60000);
    return () => clearInterval(t);
  }, [paused, mode]);
  const { rows, groups, entries, groupCount } = buildAlertFeed(data, { query, severity, status, sort });
  const inspectedGroup = groups.get(openGroup);
  const selected = rows.filter((a) => checked.includes(a.id));
  const allSelected = rows.length > 0 && selected.length === rows.length;
  function updateSelected(status: "Acknowledged" | "Resolved") {
    const ids = new Set(
      selected
        .filter((a) =>
          status === "Acknowledged"
            ? a.status === "Open"
            : a.status !== "Resolved",
        )
        .map((a) => a.id),
    );
    if (!ids.size) return;
    commit(
      {
        ...data,
        alerts: data.alerts.map((a) =>
          ids.has(a.id)
            ? { ...addAction(a, status, "Updated from the alert feed"), status }
            : a,
        ),
      },
      ids.size +
        (ids.size === 1 ? " alert " : " alerts ") +
        status.toLowerCase() +
        ".",
    );
    setChecked([]);
  }
  function receive() {
    const template =
      data.alerts.find((a) => a.severity === "Warning") ??
      data.alerts[0] ??
      fixtures().alerts[0];
    const now = Date.now(),
      start = Math.max(2048, ...data.alerts.map((a) => Number(a.id.slice(4))));
    const incoming = Array.from({ length: pending }, (_, i) => ({
      ...template,
      id: "ALT-" + (start + i + 1),
      first: now + i,
      last: now + i,
      count: 1,
      status: "Open" as Status,
      checks: [],
      timeline: [
        {
          time: now + i,
          title: "Alert opened",
          body: "Simulated incoming event",
        },
      ],
    }));
    commit(
      { ...data, alerts: [...incoming, ...data.alerts] },
      "New demo alerts added.",
    );
    setPending(0);
    setSort("newest");
    setChecked([]);
    setSeverity("");
    setStatus("");
    setQuery("");
    list.current?.scrollTo({ top: 0 });
  }
  return (
    <section className="surface feed">
      <header className="feed-head">
        <div className="feed-heading">
          <h2>Alert feed</h2>
          <span className="feed-count">
            {rows.length} {rows.length === 1 ? "alert" : "alerts"}
            {groupCount > 0 && " · " + groupCount + (groupCount === 1 ? " group" : " groups")}
          </span>
        </div>
        <div className="feed-controls">
          <label className="search">
            <Search />
            <span className="sr">Search alerts</span>
            <input
              type="search"
              placeholder="Search alerts..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <button onClick={() => setFilters(true)}>
            Filters{(severity || status) && <span className="dot" />}
          </button>
          <select
            aria-label="Sort alerts"
            value={sort}
            onChange={(e) => setSort(e.target.value)}
          >
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
            <option value="severity">By severity</option>
          </select>
        </div>
      </header>
      {selected.length > 0 && (
        <div className="feed-tools">
          <span className="selection-count" aria-live="polite">
            {selected.length} selected
          </span>
          <button
            disabled={!selected.some((a) => a.status === "Open")}
            onClick={() => updateSelected("Acknowledged")}
          >
            Acknowledge
          </button>
          <button
            disabled={!selected.some((a) => a.status !== "Resolved")}
            onClick={() => updateSelected("Resolved")}
          >
            Resolve
          </button>
        </div>
      )}
      {(severity || status) && (
        <div className="chips">
          {severity && (
            <button onClick={() => setSeverity("")}>
              {severity}
              <X />
            </button>
          )}
          {status && (
            <button onClick={() => setStatus("")}>
              {status}
              <X />
            </button>
          )}
        </div>
      )}
      {pending > 0 && (
        <div className="new-alerts">
          <span role="status">
            {pending} new demo {pending === 1 ? "alert" : "alerts"}
          </span>
          <button onClick={receive}>Show latest</button>
        </div>
      )}
      <div className="feed-rows" ref={list}>
        {rows.length > 0 && (
          <table className="alert-table" aria-label="Alert feed">
            <thead>
              <tr>
                <th scope="col">
                  <input
                    type="checkbox"
                    aria-label="Select all visible alerts"
                    checked={allSelected}
                    ref={(input) => {
                      if (input)
                        input.indeterminate =
                          selected.length > 0 && !allSelected;
                    }}
                    disabled={!rows.length}
                    onChange={() =>
                      setChecked(allSelected ? [] : rows.map((a) => a.id))
                    }
                  />
                </th>
                <th scope="col">Severity</th>
                <th scope="col">Alert</th>
                <th scope="col">Status</th>
                <th scope="col">Owner</th>
                <th scope="col" className="number-cell">
                  Events
                </th>
                <th scope="col">Notified</th>
                <th scope="col" className="number-cell">
                  Last seen
                </th>
              </tr>
            </thead>
            <tbody>
              {entries.map(([key, members]) => {
                const a = members[0];
                const ids = members.map((member) => member.id);
                const allChecked = ids.every((id) => checked.includes(id));
                const someChecked = ids.some((id) => checked.includes(id));
                return (
                  <tr
                    key={key}
                    data-severity={a.severity}
                    className={someChecked ? "checked" : undefined}
                    // The whole row opens the alert; its own controls keep their meaning.
                    onClick={(e) => {
                      if (!(e.target as HTMLElement).closest("input, button")) selectAlert(a.id);
                    }}
                  >
                    <td>
                      <input
                        type="checkbox"
                        aria-label={"Select " + a.title}
                        checked={allChecked}
                        ref={(el) => {
                          if (el) el.indeterminate = someChecked && !allChecked;
                        }}
                        onChange={(e) =>
                          setChecked(
                            e.target.checked
                              ? [...new Set([...checked, ...ids])]
                              : checked.filter((id) => !ids.includes(id)),
                          )
                        }
                      />
                    </td>
                    <td>
                      <span
                        className={
                          "feed-severity " +
                          a.severity.toLowerCase() +
                          (a.status === "Resolved" ? " resolved" : "")
                        }
                      >
                        {a.severity}
                      </span>
                    </td>
                    <td>
                      <button
                        className="alert-link"
                        onClick={() => selectAlert(a.id)}
                        aria-pressed={ids.includes(alertId)}
                      >
                        {a.title}
                      </button>
                      {/* On a narrow screen the columns are dropped and their content moves here. */}
                      <span className="alert-narrow">
                        {a.status} · {ownerOf(a) || "nobody yet"} · {members.reduce((sum, member) => sum + member.count, 0).toLocaleString("en-US")} events
                      </span>
                      <span className="alert-source">
                        {a.source}
                        {members.length > 1 && (
                          <>
                            {" "}
                            ·{" "}
                            <button
                              className="group-link"
                              onClick={() => setOpenGroup(key)}
                            >
                              {members.length} alerts grouped (since{" "}
                              {feedTime.format(
                                Math.min(
                                  ...members.map((member) => member.first),
                                ),
                              )}
                              )
                            </button>
                          </>
                        )}
                      </span>
                    </td>
                    <td>
                      <span className={"feed-status " + a.status.toLowerCase()}>
                        {a.status}
                      </span>
                    </td>
                    <td>{ownerOf(a) || <span className="nobody">Nobody yet</span>}</td>
                    <td className="number-cell">
                      {members
                        .reduce((sum, member) => sum + member.count, 0)
                        .toLocaleString("en-US")}
                    </td>
                    <td>
                      <div className="feed-channels">
                        {a.notified?.length ? <em>{a.notified.join(", ")}</em> : null}
                        {a.channels.map((channel) => (
                          <span key={channel}>{channel}</span>
                        ))}
                      </div>
                    </td>
                    <td className="number-cell">
                      <time
                        dateTime={new Date(a.last).toISOString()}
                        title={new Date(a.last).toLocaleString()}
                      >
                        {ago(a.last, now)}
                      </time>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {!data.alerts.length && (
          <Empty title="No alerts yet">
            {account
              ? "An alert appears here as soon as an event matches one of your rules."
              : "New demo alerts will appear here when the live feed is running."}
          </Empty>
        )}
        {data.alerts.length > 0 && !rows.length && (
          <Empty
            title="No matching alerts"
            action={
              <button
                onClick={() => {
                  setQuery("");
                  setSeverity("");
                  setStatus("");
                }}
              >
                Clear filters
              </button>
            }
          >
            Try another search or clear the filters.
          </Empty>
        )}
      </div>
      <OlderAlerts />
      <footer className="feed-foot">
        <span className={"tiny-dot " + ((account ? !account.connected : paused) ? "paused" : "")} />
        {account
          ? account.connected
            ? "Live · new alerts appear as they happen"
            : "Reconnecting to the live feed…"
          : paused
            ? "Live paused"
            : "Listening for demo events"}
      </footer>
      {inspectedGroup && (
        <Modal title="Grouped alerts" onClose={() => setOpenGroup("")}>
          <div className="menu-list">
            {inspectedGroup.map((member) => (
              <button
                key={member.id}
                onClick={() => {
                  setOpenGroup("");
                  selectAlert(member.id);
                }}
              >
                {member.id} · {member.title} · {feedTime.format(member.last)}
              </button>
            ))}
          </div>
        </Modal>
      )}
      {filters && (
        <Modal title="Filter alerts" onClose={() => setFilters(false)}>
          <Field label="Severity">
            <select
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
            >
              <option value="">All severities</option>
              {severities.map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </Field>
          <Field label="Status">
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {statuses.map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </Field>
          <button className="primary" onClick={() => setFilters(false)}>
            <Filter />
            Show {rows.length} alerts
          </button>
        </Modal>
      )}
    </section>
  );
}
