import { alertsOf } from "#features/alerts/domain/my-alerts.ts";
import { markAlertViewed } from "#features/workspace/domain/viewed-alert.ts";
import { homeTeam } from "#features/workspace/domain/workspace-defaults.ts";
import { fixtures } from "#features/workspace/services/demo-workspace.ts";
import { type LiveWorkspace, useLiveWorkspace } from "#features/workspace/services/live-workspace.ts";
import { newRule } from "#features/workspace/services/new-rule.ts";
import type { Data, Panel, Role, Rule } from "#features/workspace/types/records.ts";
import { useLocal } from "#hooks/use-local.ts";
import { live } from "#services/backend/config.ts";
import { signOut } from "#services/backend/session.ts";
import { ROLE_LABELS } from "#services/backend/to-workspace.ts";
import { type ReactNode, createContext, useContext, useEffect, useRef, useState } from "react";

// With a backend configured the workspace comes from the API; otherwise from demo data in this browser.
const useBackend: () => LiveWorkspace | null = live ? useLiveWorkspace : () => null;

function useWorkspace() {
  const store = useBackend();
  const [demoData, setData] = useLocal<Data>("fans:data:1", fixtures);
  const [demoRole, setRole] = useLocal<Role>("fans:role", () => "Admin");
  const data = store ? store.data : demoData;
  const role = store ? ROLE_LABELS[store.profile?.org?.role ?? "user"] : demoRole;
  const [panel, setPanel] = useState<Panel>("Alerts");
  const [alertId, setAlertId] = useState(data.alerts[0]?.id || "");
  const [sourceId, setSourceId] = useState(data.sources[0]?.id || "");
  const [alertScope, setAlertScope] = useLocal<"mine" | "all">("fans:alert-scope", () => "mine");
  const [rule, setRule] = useState<Rule>(() => data.rules[0] || newRule());
  const [bookId, setBookId] = useState(data.books[0]?.id || "");
  const [groupId, setGroupId] = useState(data.groups[0]?.id || "");
  const [memberId, setMemberId] = useState("anna");
  const [teamId, setTeamId] = useState(homeTeam);
  const [severity, setSeverity] = useState("");
  const [status, setStatus] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [compactDetail, setCompactDetail] = useState(false);
  const [demoMode, setDemoMode] = useState("Normal");
  const mode = store?.status === "loading" ? "Loading" : store?.status === "error" ? "Error" : demoMode;
  const setMode = (next: string) => {
    // "Retry loading" on the error screen.
    if (store?.status === "error" && next === "Normal") store.retry();
    setDemoMode(next);
  };
  const [paused, setPaused] = useState(false);
  const [notice, setNotice] = useState<{
    text: string;
    undo?: () => void;
  } | null>(null);
  const [alertTab, setAlertTab] = useState("Overview");
  const [prototype, setPrototype] = useState(false);
  const notify = (text: string, undo?: () => void) => setNotice({ text, undo });
  const commit = (next: Data, message: string) => {
    if (!store) {
      const prev = data;
      setData(next);
      notify(message, () => setData(prev));
      return;
    }
    store
      .commit(next)
      .then(({ data: saved, changes, created, notes }) => {
        // Records created here got their ids from the server; follow them.
        const savedRules = new Set(changes.flatMap((change) => (change.kind === "rule.save" ? [change.rule.id] : [])));
        setRule((current) => {
          if (savedRules.has(current.id)) {
            const id = created.rules[current.id] ?? current.id;
            return saved.rules.find((candidate) => candidate.id === id) ?? current;
          }
          return created.books[current.runbook] ? { ...current, runbook: created.books[current.runbook] } : current;
        });
        setBookId((current) => created.books[current] ?? current);
        setGroupId((current) => created.groups[current] ?? current);
        const done = message.replace(" locally", "").replace("resend simulated", "sent again");
        notify([done, ...notes].join(" "));
      })
      .catch((error: Error) => notify("Not saved. " + error.message));
  };
  const navigate = (next: Panel) => {
    setPanel(next);
    setCompactDetail(false);
    setMode("Normal");
  };
  const selectAlert = (id: string) => {
    setAlertId(id);
    setCompactDetail(true);
    if (store) {
      store.openAlert(id);
      return;
    }
    const time = Date.now();
    setData((current) => markAlertViewed(current, id, time));
  };
  const alert = data.alerts.find((a) => a.id === alertId);

  // Once the workspace has loaded: select the first records, or the alert a link points at.
  const ready = store?.status === "ready";
  const initialized = useRef(false);
  useEffect(() => {
    if (!store || !ready || initialized.current) return;
    initialized.current = true;
    const linked = location.pathname.match(/^\/alerts\/([^/]+)/)?.[1];
    if (linked) {
      history.replaceState(null, "", "/#Alerts");
      setPanel("Alerts");
      setAlertId(linked);
      setCompactDetail(true);
      store.openAlert(linked);
    } else {
      const first = store.data.alerts[0]?.id || "";
      setAlertId(first);
      if (first) store.openAlert(first);
    }
    setSourceId(store.data.sources[0]?.id || "");
    setRule(store.data.rules[0] || newRule(store.data));
    setBookId(store.data.books[0]?.id || "");
    setGroupId(store.data.groups[0]?.id || "");
    setMemberId(store.profile?.userId ?? "");
  }, [store, ready]);

  // A notification addressed to this user is shown over the page until the person reacts to it.
  const incoming = store?.popup ?? null;
  const dismissIncoming = () => store?.setPopup(null);
  const openIncoming = () => {
    if (!store || !incoming) return;
    setPanel("Alerts");
    setAlertId(incoming.alertId);
    setCompactDetail(true);
    store.openAlert(incoming.alertId);
    store.setPopup(null);
  };

  /** The signed-in person, or null when running on demo data. */
  const account = store && {
    name: store.profile?.name || store.profile?.email || "",
    team: store.profile?.org?.name ?? "",
    hasTeam: store.status !== "no-team",
    connected: store.connected,
    join: store.join,
    create: store.create,
    signOut,
    /** True while the server holds alerts older than the ones loaded. */
    hasOlderAlerts: store.hasOlderAlerts,
    loadOlderAlerts: store.loadOlder,
  };
  // On the alert page a signed-in person sees their own alerts unless they ask for everyone's.
  const mine = store?.profile?.userId ? alertsOf(store.profile.userId, data) : data.alerts;
  const scopedAlerts = store && alertScope === "mine" ? mine : data.alerts;
  const editRule = (changes: Partial<Rule>) =>
    setRule((r) => ({ ...r, ...changes }));
  return {
    data,
    setData,
    role,
    setRole,
    panel,
    navigate,
    alert,
    alertId,
    selectAlert,
    sourceId,
    setSourceId,
    rule,
    setRule,
    editRule,
    bookId,
    setBookId,
    groupId,
    setGroupId,
    memberId,
    setMemberId,
    teamId,
    setTeamId,
    severity,
    setSeverity,
    status,
    setStatus,
    sourceFilter,
    setSourceFilter,
    compactDetail,
    setCompactDetail,
    mode,
    setMode,
    paused,
    setPaused,
    notice,
    setNotice,
    notify,
    commit,
    alertTab,
    setAlertTab,
    prototype,
    setPrototype,
    account,
    /** "mine": only the alerts that concern the signed-in person; "all": the whole team's. */
    alertScope,
    setAlertScope,
    /** The alerts the alert page shows and counts under the current scope. */
    scopedAlerts,
    /** An in-app notification waiting for the person's reaction, or null. */
    incoming,
    openIncoming,
    dismissIncoming,
    /** The backend id of a source shown by name, or undefined on demo data. */
    sourceIdOf: (name: string) => store?.snapshot?.sources.find((source) => source.name === name)?.sourceId,
  };
}
const Context = createContext<ReturnType<typeof useWorkspace> | null>(null);
export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const value = useWorkspace();
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function useWorkspaceContext() {
  const value = useContext(Context);
  if (!value) throw Error("Workspace provider missing");
  return value;
}
