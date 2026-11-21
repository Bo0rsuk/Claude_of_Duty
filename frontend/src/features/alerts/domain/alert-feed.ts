import { type Alert, type Data, severities } from "#features/workspace";

export function buildAlertFeed(
  data: Pick<Data, "alerts" | "rules">,
  filters: { query: string; status: string; severity: string; sort: string },
) {
  const { query, status, severity, sort } = filters;
  const rows = data.alerts
    .filter(
      (a) =>
        (!severity || a.severity === severity) &&
        (!status || a.status === status) &&
        [
          a.title,
          a.source,
          a.id,
          a.target,
          ...a.channels,
          data.rules.find((r) => r.id === a.rule)?.name,
        ]
          .join(" ")
          .toLowerCase()
          .includes(query.toLowerCase()),
    )
    .sort((a, b) => b.last - a.last);
  const groups = new Map<string, Alert[]>();
  for (const alert of rows) {
    const field = data.rules.find((rule) => rule.id === alert.rule)?.group;
    const key = JSON.stringify([
      alert.rule,
      alert.source,
      alert.title,
      alert.severity,
      alert.status,
      alert.target,
      alert.channels,
      field && alert.fields[field],
    ]);
    const group = groups.get(key);
    if (group) group.push(alert);
    else groups.set(key, [alert]);
  }
  const entries = [...groups.entries()].sort(([, [a]], [, [b]]) => {
    if (sort === "oldest") return a.last - b.last;
    if (sort === "severity") {
      const priority =
        severities.indexOf(a.severity) - severities.indexOf(b.severity);
      if (priority) return priority;
    }
    return b.last - a.last;
  });
  const groupCount = entries.filter(([, members]) => members.length > 1).length;
  return { rows, groups, entries, groupCount };
}
