import { Box } from "#components/box.tsx";
import { Shield } from "lucide-react";

export function RolesLegend() {
  return (
    <Box title="Role permissions" icon={Shield}>
      <dl className="plain-dl">
        <dt>User</dt>
        <dd>Respond to alerts and personalize views.</dd>
        <dt>Team lead</dt>
        <dd>Manage team members, routing rules, sources and runbooks.</dd>
        <dt>Admin</dt>
        <dd>Manage teams, accounts and join codes across the company.</dd>
      </dl>
    </Box>
  );
}
