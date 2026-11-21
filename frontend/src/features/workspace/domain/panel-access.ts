import type { Panel, Role } from "#features/workspace/types/records.ts";

export const roles: Role[] = ["User", "Team lead", "Admin"];
export const panelNames: Panel[] = [
  "Alerts",
  "Custom",
  "Rules",
  "Sources",
  "Groups & runbooks",
  "Users",
  "Admin",
];
export function allowed(panel: Panel, role: Role) {
  return (
    panel === "Alerts" ||
    panel === "Custom" ||
    (panel === "Admin" ? role === "Admin" : role !== "User")
  );
}
