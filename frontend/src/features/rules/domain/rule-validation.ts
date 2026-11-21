import type { Rule } from "#features/workspace";
import { deliveryWindowError, escalationError } from "#services/backend/to-backend.ts";

/**
 * Why a rule cannot be saved, or "" when it can. The demo needs a recipient and
 * a channel on every rule. On the backend a rule without them is valid: its
 * alerts appear in the feed and nobody is notified.
 */
export function ruleValidationError(rule: Rule, live = false) {
  if (!rule.name.trim()) return "Give the rule a name.";
  if (!rule.conditions.trim()) return "Add matching conditions.";
  if (Number(rule.interval) < 1 || Number(rule.limit) < 1) return "Use positive grouping and limit values.";
  const each = [rule.recipientLimit ?? "", rule.recipientInterval ?? ""].map((value) => value.trim());
  if (each.some(Boolean) && each.some((value) => !(Number(value) >= 1))) {
    return "Fill in both parts of the limit per recipient with positive numbers, or leave both empty.";
  }
  const window = deliveryWindowError(rule.window ?? "");
  if (window) return window;
  const pause = escalationError(rule.escalation);
  if (pause) return pause;
  const quiet = live && !rule.targets.length && !rule.channels.length;
  if (!rule.targets.length && !quiet) {
    return live
      ? "Add a recipient, or untick every delivery channel to only show alerts in the feed."
      : "Add a recipient.";
  }
  if (!rule.channels.length && !quiet) return "Choose a delivery channel.";
  return "";
}
