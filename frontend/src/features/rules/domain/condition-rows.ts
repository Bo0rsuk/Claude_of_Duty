// The condition builder edits the same one-line text the rule stores, e.g.
//   service = payments-api AND http.status >= 500
// as rows of field, operator and value. Text it cannot split into rows
// stays editable as text.

export const OPERATORS = [
  ["=", "is"],
  ["!=", "is not"],
  [">", "is greater than"],
  [">=", "is at least"],
  ["<", "is less than"],
  ["<=", "is at most"],
  ["contains", "contains"],
  ["starts_with", "starts with"],
  ["matches", "matches the pattern"],
  ["in", "is one of"],
  ["exists", "is present"],
  ["missing", "is missing"],
] as const;

export type OperatorToken = (typeof OPERATORS)[number][0];
export type Row = { field: string; operator: OperatorToken; value: string };
export type Mode = "all" | "any";

/** Operators that are complete without a value. */
export const standsAlone = (operator: OperatorToken) => operator === "exists" || operator === "missing";

export const complete = (row: Row) => Boolean(row.field.trim() && (standsAlone(row.operator) || row.value.trim()));

const SYMBOL = /^([\w.-]+)\s*(>=|<=|!=|=|>|<)\s*(.+)$/;
const WORD = /^([\w.-]+)\s+(contains|starts_with|matches|in|exists|missing)(?:\s+(.*))?$/i;

function toRow(clause: string): Row | null {
  const word = clause.match(WORD);
  if (word) {
    const operator = word[2].toLowerCase() as OperatorToken;
    const value = (word[3] ?? "").trim();
    // "exists" takes no value and the others need one.
    return standsAlone(operator) === Boolean(value) ? null : { field: word[1], operator, value };
  }
  const symbol = clause.match(SYMBOL);
  return symbol ? { field: symbol[1], operator: symbol[2] as OperatorToken, value: symbol[3].trim() } : null;
}

/** @returns the rows, or null when the text is not a plain list of conditions joined one way */
export function toRows(text: string): { mode: Mode; rows: Row[] } | null {
  const trimmed = text.trim();
  if (!trimmed) return { mode: "all", rows: [] };
  const hasAnd = /\s+AND\s+/i.test(trimmed),
    hasOr = /\s+OR\s+/i.test(trimmed);
  if (hasAnd && hasOr) return null;
  const rows = trimmed.split(hasOr ? /\s+OR\s+/i : /\s+AND\s+/i).map((clause) => toRow(clause.trim()));
  return rows.every((row) => row !== null) ? { mode: hasOr ? "any" : "all", rows: rows as Row[] } : null;
}

/** Rows that are not filled in yet are left out, so the text is always valid. */
export function toText(mode: Mode, rows: Row[]): string {
  return rows
    .filter(complete)
    .map((row) => row.field.trim() + " " + row.operator + (standsAlone(row.operator) ? "" : " " + row.value.trim()))
    .join(mode === "any" ? " OR " : " AND ");
}
