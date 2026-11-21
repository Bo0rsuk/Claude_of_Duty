import type { Role } from "#features/workspace";

/** What each role may do, in the words shown wherever a role is chosen. */
export const roleDescriptions: Record<Role, string> = {
  User: "Respond to alerts and personalize views.",
  "Team lead": "Manage team members, routing rules, sources and runbooks.",
  Admin: "Manage teams, accounts and join codes across the company.",
};
