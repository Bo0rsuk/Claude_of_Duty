import { Box } from "#components/box.tsx";
import { roleDescriptions } from "#features/members/domain/role-descriptions.ts";
import { roles } from "#features/workspace";
import { Shield } from "lucide-react";
import { Fragment } from "react";

export function RolesLegend() {
  return (
    <Box title="Role permissions" icon={Shield}>
      <dl className="plain-dl">
        {roles.map((role) => (
          <Fragment key={role}>
            <dt>{role}</dt>
            <dd>{roleDescriptions[role]}</dd>
          </Fragment>
        ))}
      </dl>
    </Box>
  );
}
