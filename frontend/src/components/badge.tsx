import type { Severity } from "#features/workspace";

export function Badge({
  severity,
  resolved = false,
}: {
  severity: Severity;
  resolved?: boolean;
}) {
  return (
    <span
      className={
        "severity-badge " +
        severity.toLowerCase() +
        (resolved ? " resolved" : "")
      }
    >
      {severity}
    </span>
  );
}
