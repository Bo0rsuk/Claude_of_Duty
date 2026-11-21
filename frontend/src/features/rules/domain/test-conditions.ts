export function testConditions(proposal: string, events: Record<string, string>[]) {
  const clauses = proposal
    .trim()
    .split(/\s+AND\s+/i)
    .map((clause) => clause.match(/^([\w.-]+)\s+(starts_with|=|>)\s+(.+)$/i));
  if (clauses.some((c) => !c))
    return "Edit the proposal to use =, > or starts_with. Join multiple conditions with AND.";
  const matches = events.filter((event) =>
    clauses.every((clause) => {
      if (!clause) return false;
      const [, key, operator, value] = clause;
      if (event[key] === undefined) return false;
      if (operator === ">") return Number(event[key]) > Number(value);
      if (operator.toLowerCase() === "starts_with")
        return event[key].startsWith(value);
      return event[key] === value;
    }),
  );
  return "Demo test · " + matches.length + " of the last " + events.length + " events matched.";
}
