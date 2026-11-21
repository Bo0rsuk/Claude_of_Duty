// Ready-made source types the server knows: a connector and a parser that fit
// a common kind of sender. Demo data has none.

import { live } from "#services/backend/config.ts";
import { loadSourcePresets } from "#services/backend/gateway.ts";
import { connectorAddress, mapping, sourceFormat, sourceType } from "#services/backend/to-workspace.ts";

export type Preset = {
  name: string;
  /** Shown like the type, format and field mapping of a saved source. */
  type: string;
  format: string;
  mapping: string;
  /** The address the preset suggests, for the user to replace with their own. Empty for a webhook. */
  connection: string;
  setup: { connector: object; parser: object | null };
};

export async function loadPresets(): Promise<Preset[]> {
  if (!live) return [];
  return (await loadSourcePresets()).map((preset) => ({
    name: preset.name,
    type: sourceType(preset.connector),
    format: sourceFormat(preset.parser),
    mapping: mapping(preset.parser),
    connection: connectorAddress(preset.connector),
    setup: { connector: preset.connector, parser: preset.parser },
  }));
}
