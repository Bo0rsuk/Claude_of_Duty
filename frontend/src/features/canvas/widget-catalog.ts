import { AccountList, CreateAccount, ManageTeam, TeamCode, TeamList } from "#features/admin";
import {
  Actions,
  ActivityOverview,
  AlertList,
  Inspector,
  Instructions,
  Timeline,
} from "#features/alerts";
import { arrange } from "#features/canvas/domain/widget-grid.ts";
import { BookEditor, BookList, GroupList, GroupMembers } from "#features/groups-and-runbooks";
import { AssignRules, ManageUser, RolesLegend, UserList } from "#features/members";
import {
  Assistant,
  Grouping,
  MessagePreview,
  RuleActions,
  RuleEditor,
  RuleList,
  RuleRunbook,
  SeveritySelector,
  Targets,
} from "#features/rules";
import { SourceDetails, SourceList, WizardWidget } from "#features/sources";
import type { Panel } from "#features/workspace";
import type { ComponentType } from "react";

type Placement = "list" | "detail" | "full";
interface Widget {
  panel: Panel;
  name: string;
  view: ComponentType;
  placement: Placement;
  boxed?: boolean;
  /** Exists only on the demo's made-up data; left out for a signed-in team. */
  demoOnly?: boolean;
}
const widget = (
  panel: Panel,
  name: string,
  view: ComponentType,
  placement: Placement = "detail",
  boxed = false,
): Widget => ({ panel, name, view, placement, boxed });
export const registry: Record<string, Widget> = {
  activity: widget("Alerts", "Activity overview", ActivityOverview, "full"),
  feed: widget("Alerts", "Alert feed", AlertList, "list"),
  details: widget("Alerts", "Alert details", Inspector),
  manage: widget("Alerts", "Manage alert", Actions, "detail", true),
  timeline: widget("Alerts", "Alert timeline", Timeline, "detail", true),
  instructions: widget("Alerts", "Instructions", Instructions, "detail", true),
  rules: widget("Rules", "Rule list", RuleList, "list"),
  rule: widget("Rules", "Rule editor", RuleEditor),
  assistant: widget("Rules", "Filter assistant", Assistant, "detail", true),
  severity: widget(
    "Rules",
    "Severity selector",
    SeveritySelector,
    "detail",
    true,
  ),
  targets: widget("Rules", "Notification targets", Targets, "detail", true),
  grouping: widget("Rules", "Grouping & limits", Grouping, "detail", true),
  rulebook: widget("Rules", "Rule runbook", RuleRunbook, "detail", true),
  preview: widget("Rules", "Message preview", MessagePreview, "detail", true),
  ruleActions: widget("Rules", "Rule actions", RuleActions, "detail", true),
  sources: widget("Sources", "Source list", SourceList, "list"),
  source: widget("Sources", "Source details", SourceDetails),
  wizard: widget("Sources", "Add source wizard", WizardWidget),
  groups: widget("Groups & runbooks", "Recipient groups", GroupList, "list"),
  members: widget("Groups & runbooks", "Group members", GroupMembers),
  books: widget("Groups & runbooks", "Runbooks", BookList, "list"),
  book: widget("Groups & runbooks", "Runbook editor", BookEditor),
  users: widget("Users", "Member list", UserList, "list"),
  user: widget("Users", "Member details", ManageUser),
  assign: widget("Users", "Assigned rules", AssignRules),
  legend: widget("Users", "Role permissions", RolesLegend, "list"),
  // A company on the backend is one team, so a list of teams has nothing to show.
  teams: { ...widget("Admin", "Teams", TeamList, "list"), demoOnly: true },
  team: widget("Admin", "Team settings", ManageTeam),
  code: widget("Admin", "Team code", TeamCode),
  accounts: widget("Admin", "All accounts", AccountList, "list"),
  account: widget("Admin", "Create account", CreateAccount, "list"),
};
const defaults: Record<Panel, string[]> = {
  Alerts: ["activity", "feed", "details"],
  Custom: [],
  Rules: ["rules", "rule"],
  Sources: ["sources", "source"],
  "Groups & runbooks": ["groups", "members", "books", "book"],
  Users: ["users", "user", "legend", "assign"],
  Admin: ["teams", "team", "accounts", "code", "account"],
};
function originalLayout(panel: Panel) {
  return arrange(
    defaults[panel].map((kind, i) => ({
      id: kind + i,
      kind,
      wide: registry[kind].placement === "full",
      group: "",
    })),
  );
}
export const originalAlerts = originalLayout("Alerts");
export function defaultLayout(panel: Panel) {
  if (panel !== "Alerts") return originalLayout(panel);
  return originalAlerts.map((tile) => {
    if (tile.kind === "feed") return { ...tile, rect: { ...tile.rect, w: 9 } };
    if (tile.kind === "details")
      return { ...tile, rect: { ...tile.rect, x: 9, w: 3 } };
    return tile;
  });
}
