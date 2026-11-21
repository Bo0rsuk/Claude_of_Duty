import type { Rule } from "#features/workspace";

export function ruleValidationError(rule: Rule) {
  if (
    !rule.name.trim() ||
    !rule.conditions.trim() ||
    !rule.targets.length ||
    !rule.channels.length ||
    Number(rule.interval) < 1 ||
    Number(rule.limit) < 1
  ) {
    return "Add a name, conditions, recipient and channel. Use positive grouping and limit values.";
  }
  return "";
}
