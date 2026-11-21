// Finds the fields of a pasted example event. With a backend the example is
// run through the real parser, so the result is what rules will see.

import { detectFieldMapping } from "#features/sources/domain/field-mapping.ts";
import type * as Api from "#services/backend/api-types.ts";
import { live } from "#services/backend/config.ts";
import { api } from "#services/backend/http.ts";
import { parserFor } from "#services/backend/to-backend.ts";

/** Field names that usually carry the three things every event has. */
const WELL_KNOWN: Record<string, string> = {
  level: "level",
  severity: "level",
  message: "message",
  msg: "message",
  summary: "message",
  timestamp: "time",
  time: "time",
  ts: "time",
};

/** @returns one "field → meaning" line per field, for the user to review */
export async function detectFields(sample: string, format: string): Promise<string> {
  const parser = live ? parserFor(format, "") : null;
  if (!parser) {
    await new Promise((resolve) => setTimeout(resolve, 600));
    return detectFieldMapping(sample, format);
  }
  const { events } = await api<{ events: Api.NormalizedEvent[] }>("POST", "/sources/preview", { parser, sample });
  const fields = Object.keys(events[0]?.fields ?? {});
  if (!fields.length) throw new Error("No fields detected");
  return fields.map((field) => field + " → " + (WELL_KNOWN[field.toLowerCase()] ?? field)).join("\n");
}
