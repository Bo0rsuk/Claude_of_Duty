// The rule editor shows conditions as one line of text, e.g.
//   service = payments-api AND http.status >= 500
// The backend stores them as a list of { field, op, value }. This module
// converts between the two.

import type { Condition, FieldValue, Match, Operator } from "#services/backend/api-types.ts";

const SYMBOLS: [string, Operator][] = [
  [">=", "gte"],
  ["<=", "lte"],
  ["!=", "neq"],
  ["=", "eq"],
  [">", "gt"],
  ["<", "lt"],
];
const WORDS = ["contains", "starts_with", "matches", "in", "exists", "missing"];
const NUMERIC: Operator[] = ["gt", "gte", "lt", "lte"];
export const CONDITION_HELP =
  "Write conditions as: field = value. Operators: = != > >= < <= contains starts_with matches in exists missing. " +
  "Join them with AND, or all of them with OR.";

const escapeRegex = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const unquote = (text: string) => text.trim().replace(/^(["'])(.*)\1$/, "$2");
const scalar = (text: string): FieldValue => {
  const value = unquote(text);
  return value !== "" && !Number.isNaN(Number(value)) ? Number(value) : value;
};

function parseClause(clause: string): Condition | null {
  const word = clause.match(/^([\w.-]+)\s+(\w+)(?:\s+(.*))?$/);
  if (word && WORDS.includes(word[2].toLowerCase())) {
    const [, field, rawOperator, rest = ""] = word;
    const operator = rawOperator.toLowerCase();
    if (operator === "exists" || operator === "missing") {
      return rest.trim() ? null : { field, op: "exists", value: operator === "exists" };
    }
    if (!rest.trim()) return null;
    if (operator === "contains") return { field, op: "contains", value: unquote(rest) };
    if (operator === "matches") return { field, op: "regex", value: unquote(rest) };
    if (operator === "starts_with") return { field, op: "regex", value: "^" + escapeRegex(unquote(rest)) };
    const values = rest.trim().replace(/^[([]|[)\]]$/g, "").split(",").map(scalar);
    return { field, op: "in", value: values };
  }
  for (const [symbol, op] of SYMBOLS) {
    const parts = clause.match(new RegExp("^([\\w.-]+)\\s*" + escapeRegex(symbol) + "\\s*(.+)$"));
    if (!parts) continue;
    const value = scalar(parts[2]);
    if (NUMERIC.includes(op) && typeof value !== "number") return null;
    return { field: parts[1], op, value: NUMERIC.includes(op) ? value : unquote(parts[2]) };
  }
  return null;
}

/** @returns the conditions, or null when the text does not follow the format */
export function parseConditions(text: string): Match | null {
  const trimmed = text.trim();
  if (!trimmed) return { mode: "all", conditions: [] };
  const hasAnd = /\s+AND\s+/i.test(trimmed);
  const hasOr = /\s+OR\s+/i.test(trimmed);
  // One mode applies to all conditions, so the two cannot be mixed.
  if (hasAnd && hasOr) return null;
  const conditions = trimmed.split(hasOr ? /\s+OR\s+/i : /\s+AND\s+/i).map((clause) => parseClause(clause.trim()));
  if (conditions.some((condition) => !condition)) return null;
  return { mode: hasOr ? "any" : "all", conditions: conditions as Condition[] };
}

function formatClause({ field, op, value }: Condition): string {
  if (op === "exists") return field + (value === false ? " missing" : " exists");
  if (op === "in") return field + " in " + (Array.isArray(value) ? value : [value]).join(", ");
  if (op === "contains") return field + " contains " + value;
  if (op === "regex") {
    const pattern = String(value);
    const literal = pattern.startsWith("^") ? pattern.slice(1).replace(/\\(.)/g, "$1") : null;
    // A pattern this module produced for "starts_with" is shown that way again.
    return literal !== null && "^" + escapeRegex(literal) === pattern
      ? field + " starts_with " + literal
      : field + " matches " + pattern;
  }
  const symbol = SYMBOLS.find(([, operator]) => operator === op)?.[0] ?? "=";
  return field + " " + symbol + " " + value;
}

export function formatConditions(match: Match | null | undefined): string {
  if (!match) return "";
  return match.conditions.map(formatClause).join(match.mode === "any" ? " OR " : " AND ");
}
