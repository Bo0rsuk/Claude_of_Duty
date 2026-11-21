// Suggestions and trial runs for rule conditions. With a backend these come
// from the rule assistant and the source's recent events; the demo makes them up.

import { testConditions } from "#features/rules/domain/test-conditions.ts";
import { recentDemoEvents } from "#features/rules/services/demo-events.ts";
import type * as Api from "#services/backend/api-types.ts";
import { CONDITION_HELP, formatConditions, parseConditions } from "#services/backend/conditions.ts";
import { live } from "#services/backend/config.ts";
import { api } from "#services/backend/http.ts";

export type Suggestion = { conditions: string; note: string };

const result = (preview: Api.RulePreview) =>
  preview.total
    ? `${preview.matched} of the last ${preview.total} events of this source matched.`
    : "No recent events from this source to test against yet.";

/** @param sourceId the backend id of the rule's source */
export async function suggestConditions(goal: string, sourceName: string, sourceId: string | undefined): Promise<Suggestion> {
  if (!live) {
    await new Promise((resolve) => setTimeout(resolve, 650));
    const text = goal.toLowerCase();
    return {
      note: "",
      conditions: text.includes("pricing")
        ? "service starts_with pricing-engine- AND level = warning"
        : text.includes("trade") || text.includes("notional")
          ? "desk = rates AND notional > 10000000"
          : text.includes("payment")
            ? "service = payments-api AND level = critical"
            : "service = " + sourceName + " AND environment = production AND outcome = failed",
    };
  }
  if (!sourceId) throw new Error("Choose an event source first.");
  const suggestion = await api<Api.RuleSuggestion>("POST", "/ai/rule-suggestion", { sourceId, goal });
  return {
    conditions: formatConditions(suggestion.match),
    note: [suggestion.explanation, result(suggestion.preview), ...suggestion.warnings].join(" "),
  };
}

export async function tryConditions(conditions: string, sourceId: string | undefined): Promise<string> {
  if (!live) return testConditions(conditions, recentDemoEvents);
  const match = parseConditions(conditions);
  if (!match) return CONDITION_HELP;
  if (!sourceId) return "Choose an event source first.";
  return result(await api<Api.RulePreview>("POST", "/rules/preview", { sourceId, match }));
}

/** The newest event of the source, to show what its fields look like. */
export async function latestEvent(sourceId: string | undefined): Promise<Record<string, unknown> | null> {
  if (!live || !sourceId) return null;
  const { items } = await api<Api.Page<Api.NormalizedEvent>>("GET", `/sources/${sourceId}/events?limit=1`);
  return items[0] ? { level: items[0].level, message: items[0].message, ...items[0].fields } : null;
}
