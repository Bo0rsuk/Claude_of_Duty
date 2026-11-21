import { Logo } from "#components/logo.tsx";
import { type Panel, allowed, useWorkspaceContext } from "#features/workspace";
import { SidebarNavigation } from "#layouts/sidebar-navigation.tsx";
import {
  Bell,
  BookOpen,
  ChevronsUpDown,
  Database,
  GitBranch,
  Layers,
  LayoutDashboard,
  Shield,
  Users,
} from "lucide-react";

const navIcons = {
  Alerts: Bell,
  Custom: LayoutDashboard,
  Rules: GitBranch,
  Sources: Database,
  Groups: Layers,
  Runbooks: BookOpen,
  Users,
  Admin: Shield,
};

interface SidebarProps {
  access: string;
  user: string;
  onNavigate: (panel: Panel) => void;
  onOpenAccount: () => void;
}
export function Sidebar({ access, user, onNavigate, onOpenAccount }: SidebarProps) {
  const { panel, role, data } = useWorkspaceContext();
  function navSection(label: string, items: Panel[]) {
    const list = items.filter((p) => allowed(p, role));
    if (!list.length) return null;
    return (
      <section className="nav-section">
        <h2>{label}</h2>
        {list.map((p) => {
          const Icon = navIcons[p];
          return (
            <a
              key={p}
              href={"#" + encodeURIComponent(p)}
              title={p}
              aria-current={p === panel ? "page" : undefined}
              onClick={(e) => {
                if (e.metaKey || e.ctrlKey) return;
                e.preventDefault();
                onNavigate(p);
              }}
            >
              <Icon />
              <span>{p === "Custom" ? "Custom view" : p}</span>
              {p === "Alerts" && (
                <small>
                  {data.alerts.filter((a) => a.status !== "Resolved").length}
                </small>
              )}
            </a>
          );
        })}
      </section>
    );
  }
  return (
    <>
      <div className="brand">
        <Logo />
        <span>Squawk</span>
      </div>
      <SidebarNavigation panel={access === "Assigned" ? panel : null}>
        {access === "Assigned" && (
          <>
            {navSection("Workspace", ["Alerts", "Custom"])}
            {navSection("Management", [
              "Rules",
              "Sources",
              "Runbooks",
              "Users",
              "Groups",
            ])}
            {navSection("Administration", ["Admin"])}
          </>
        )}
      </SidebarNavigation>
      <div className="sidebar-bottom">
        <button
          className="profile"
          aria-label={"User menu · " + user + " · " + role}
          title="User menu"
          onClick={onOpenAccount}
        >
          <span className="avatar">
            {user
              .split(" ")
              .map((v) => v[0])
              .join("")}
          </span>
          <span>
            <strong>{user}</strong>
            <small>{role}</small>
          </span>
          <ChevronsUpDown />
        </button>
      </div>
    </>
  );
}
