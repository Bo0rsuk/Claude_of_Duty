import type { Rule } from "#features/workspace/types/records.ts";
import { unique } from "#lib/id.ts";

export function newRule(): Rule {
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
