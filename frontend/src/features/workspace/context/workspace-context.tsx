import { markAlertViewed } from "#features/workspace/domain/viewed-alert.ts";
import { homeTeam } from "#features/workspace/domain/workspace-defaults.ts";
import { fixtures } from "#features/workspace/services/demo-workspace.ts";
import { newRule } from "#features/workspace/services/new-rule.ts";
import type { Data, Panel, Role, Rule } from "#features/workspace/types/records.ts";
import { useLocal } from "#hooks/use-local.ts";
import { type ReactNode, createContext, useContext, useState } from "react";

function useWorkspace() {
  const [data, setData] = useLocal<Data>("fans:data:1", fixtures);
  const [role, setRole] = useLocal<Role>("fans:role", () => "Admin");
  const [panel, setPanel] = useState<Panel>("Alerts");
  const [alertId, setAlertId] = useState(data.alerts[0]?.id || "");
  const [sourceId, setSourceId] = useState(data.sources[0]?.id || "");
  const [rule, setRule] = useState<Rule>(() => data.rules[0] || newRule());
  const [bookId, setBookId] = useState(data.books[0]?.id || "");
  const [groupId, setGroupId] = useState(data.groups[0]?.id || "");
  const [memberId, setMemberId] = useState("anna");
  const [teamId, setTeamId] = useState(homeTeam);
  const [severity, setSeverity] = useState("");
  const [status, setStatus] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [compactDetail, setCompactDetail] = useState(false);
  const [mode, setMode] = useState("Normal");
  const [paused, setPaused] = useState(false);
  const [notice, setNotice] = useState<{
    text: string;
    undo?: () => void;
  } | null>(null);
  const [alertTab, setAlertTab] = useState("Overview");
  const [prototype, setPrototype] = useState(false);
  const notify = (text: string, undo?: () => void) => setNotice({ text, undo });
  const commit = (next: Data, message: string) => {
    const prev = data;
    setData(next);
    notify(message, () => setData(prev));
  };
  const navigate = (next: Panel) => {
    setPanel(next);
    setCompactDetail(false);
    setMode("Normal");
  };
  const selectAlert = (id: string) => {
    setAlertId(id);
    setCompactDetail(true);
    const time = Date.now();
    setData((current) => markAlertViewed(current, id, time));
  };
  const alert = data.alerts.find((a) => a.id === alertId);
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
