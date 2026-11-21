import { Modal } from "#components/modal.tsx";
import { Canvas } from "#features/canvas";
import {
  AccountMenu,
  type Panel,
  type Role,
  TeamAccess,
  allowed,
  panelNames,
  useWorkspaceContext,
} from "#features/workspace";
import { useLocal } from "#hooks/use-local.ts";
import { Sidebar } from "#layouts/sidebar.tsx";
import {
  ChevronRight,
  CircleCheck,
  Menu,
  PanelLeft,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

const titles: Record<Panel, string> = {
  Alerts: "Alert workspace",
  Custom: "Custom view",
  Rules: "Routing rules",
  Sources: "Event sources",
  "Groups & runbooks": "Groups & runbooks",
  Users: "Team members",
  Admin: "Administration",
};
export function App() {
  const {
    data,
    panel,
    navigate,
    role,
    setRole,
    notice,
    setNotice,
    notify,
    prototype,
    setPrototype,
    mode,
    setMode,
    setMemberId,
    setCompactDetail,
    rule,
  } = useWorkspaceContext();
  const [collapsed, setCollapsed] = useLocal("fans:collapsed", () => false),
    [drawer, setDrawer] = useState(false),
    [editing, setEditing] = useState(false),
    [account, setAccount] = useState(false),
    [access, setAccess] = useState("Assigned"),
    [leave, setLeave] = useState<null | (() => void)>(null);
  const main = useRef<HTMLElement>(null);
  const user =
    access === "Assigned"
      ? role === "Admin"
        ? "Alex Morgan"
        : role === "Team lead"
          ? "Anna Kowalski"
          : "Noah Chen"
      : "Riley Park";
  function guard(fn: () => void) {
    const savedRule = data.rules.find((r) => r.id === rule.id);
    if (
      editing ||
      (panel === "Rules" && JSON.stringify(savedRule) !== JSON.stringify(rule))
    )
      setLeave(() => fn);
    else fn();
  }
  function go(p: Panel) {
    if (!allowed(p, role)) p = "Alerts";
    navigate(p);
    setEditing(false);
    setDrawer(false);
    history.replaceState(null, "", "#" + encodeURIComponent(p));
    requestAnimationFrame(() => main.current?.focus({ preventScroll: true }));
    main.current?.scrollTo({ top: 0 });
  }
  useEffect(() => {
    const read = () => {
      let p: Panel | undefined;
      try {
        p = panelNames.find(
          (v) => v === decodeURIComponent(location.hash.slice(1)),
        );
      } catch {
        /* Invalid fragment opens Alerts. */
      }
      navigate(p && allowed(p, role) ? p : "Alerts");
      setEditing(false);
    };
    read();
    window.addEventListener("hashchange", read);
    return () => window.removeEventListener("hashchange", read);
  }, [role]);
  useEffect(() => {
    document.title = titles[panel] + " · FANS";
  }, [panel]);
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 8000);
    return () => clearTimeout(t);
  }, [notice, setNotice]);
  useEffect(() => {
    if (mode !== "Loading") return;
    const t = setTimeout(() => setMode("Normal"), 4000);
    return () => clearTimeout(t);
  }, [mode, setMode]);
  function switchRole(r: Role) {
    setRole(r);
    setAccess("Assigned");
    setMemberId("anna");
    setCompactDetail(false);
    setEditing(false);
    if (!allowed(panel, r)) go("Alerts");
    setAccount(false);
    setPrototype(false);
    notify("Demo role changed to " + r + ".");
  }
  const sidebar = (
    <Sidebar
      access={access}
      user={user}
      onNavigate={(p) => guard(() => go(p))}
      onOpenAccount={() => {
        setDrawer(false);
        setAccount(true);
      }}
    />
  );
  function changeAccess(v: string) {
    setAccess(v);
    setEditing(false);
    setPrototype(false);
    setDrawer(false);
  }
  const content = access === "Assigned" ? (
    <Canvas key={role + panel} editing={editing} setEditing={setEditing} />
  ) : (
    <TeamAccess key={access} access={access} changeAccess={changeAccess} />
  );
  const alertWorkspace = access === "Assigned" && panel === "Alerts";
  const customizeButton = access === "Assigned" && !editing && (
    <button className="customize" onClick={() => setEditing(true)}>
      <SlidersHorizontal />
      Customize view
    </button>
  );
  return (
    <div className={"app " + (collapsed ? "is-collapsed" : "")}>
      <a
        className="skip"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          main.current?.focus();
        }}
      >
        Skip to workspace
      </a>
      <aside className="sidebar" id="workspace-sidebar">
        {sidebar}
      </aside>
      <div className="app-main">
        <header className="utility">
          <button
            className="mobile-menu icon-btn"
            aria-label="Open navigation"
            onClick={() => setDrawer(true)}
          >
            <Menu />
          </button>
          <div className="crumb">
            <button
              className="sidebar-toggle icon-btn"
              aria-label={
                collapsed ? "Expand navigation" : "Compact navigation"
              }
              aria-controls="workspace-sidebar"
              aria-expanded={!collapsed}
              title={collapsed ? "Expand navigation" : "Compact navigation"}
              onClick={() => setCollapsed(!collapsed)}
            >
              <PanelLeft aria-hidden="true" />
            </button>
            <span className="crumb-workspace">Workspace</span>
            <span className="compact-wordmark">FANS</span>
            <ChevronRight />
            <strong>{access === "Assigned" ? panel : "Join team"}</strong>
          </div>
          {customizeButton && (
            <div className="view-toolbar" role="group" aria-label="View controls">
              {customizeButton}
            </div>
          )}
        </header>
        <main id="main" tabIndex={-1} ref={main} aria-label={alertWorkspace ? "Alerts" : undefined}>
          <h1 className="sr">
            {alertWorkspace
              ? "Alerts"
              : access === "Assigned"
                ? titles[panel]
                : "Team access"}
          </h1>
          {content}
        </main>
      </div>
      {drawer && (
        <Modal drawer title="Navigation" onClose={() => setDrawer(false)}>
          {sidebar}
        </Modal>
      )}
      {(prototype || account) && (
        <AccountMenu
          user={user}
          access={access}
          changeAccess={changeAccess}
          switchRole={switchRole}
          closeAccount={() => setAccount(false)}
        />
      )}
      {leave && (
        <Modal title="Leave this view?" onClose={() => setLeave(null)}>
          <p>You have unsaved edits. Save the current view to keep them.</p>
          <div className="buttons">
            <button onClick={() => setLeave(null)}>Keep editing</button>
            <button
              onClick={() => {
                leave();
                setLeave(null);
              }}
            >
              Leave view
            </button>
          </div>
        </Modal>
      )}
      <div className="toast-region" aria-live="polite">
        {notice && (
          <div className="toast">
            <CircleCheck />
            {notice.text}
            {notice.undo && (
              <button
                onClick={() => {
                  notice.undo?.();
                  notify("Change undone.");
                }}
              >
                Undo
              </button>
            )}
            <button
              className="icon-btn"
              aria-label="Dismiss notification"
              onClick={() => setNotice(null)}
            >
              <X />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
