import type { Severity } from "#features/workspace";
import { CircleAlert, Info, type LucideIcon, TriangleAlert } from "lucide-react";

const severityIcons: Record<Severity, LucideIcon> = {
  Critical: CircleAlert,
  Warning: TriangleAlert,
  Info,
};
export function Badge({ severity }: { severity: Severity }) {
  const Icon = severityIcons[severity];
  return (
    <span className={"badge " + severity.toLowerCase()}>
      <Icon size={13} />
      {severity}
    </span>
  );
}
