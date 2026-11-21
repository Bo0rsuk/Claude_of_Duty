import type { Data, Rule } from "#features/workspace/types/records.ts";
import { unique } from "#lib/id.ts";

/**
 * A blank rule. Given the workspace of a signed-in team, the draft starts from
 * what that team really has instead of the demo's source, recipient and runbook.
 */
export function newRule(workspace?: Data): Rule {
  const draft = demoDraft();
  if (!workspace) return draft;
  return {
    ...draft,
    source: workspace.sources[0]?.id ?? "",
    targets: [],
    runbook: "",
    group: "",
    template: "{{message}}",
    fields: "",
  };
}

function demoDraft(): Rule {
  return {
    id: unique(),
    name: "",
    source: "workflow-engine",
    severity: "Warning",
    conditions: "",
    targets: ["On-call engineer"],
    channels: ["Email", "In-app"],
    window: "",
    fallback: "Email",
    group: "service",
    interval: "5",
    limit: "10",
    runbook: "settlement",
    template: "{{service}} event",
    fields: "service, environment",
  };
}
