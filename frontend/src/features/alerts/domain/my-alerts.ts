import type { Alert, Data } from "#features/workspace";

/**
 * The alerts that concern one person: those they own, and those whose rule
 * notifies them directly, through a group they belong to, or at their own
 * email address or phone number.
 */
export function alertsOf(memberId: string, data: Pick<Data, "alerts" | "members" | "groups">): Alert[] {
  const person = data.members.find((member) => member.id === memberId);
  if (!person) return [];
  const names = new Set(
    [
      person.name,
      person.email,
      person.phone,
      ...data.groups.filter((group) => group.members.includes(memberId)).map((group) => group.name),
    ].filter(Boolean),
  );
  return data.alerts.filter(
    (alert) =>
      // Demo records keep the owner in target and carry no list of notified people.
      names.has(alert.owner ?? alert.target) || (alert.notified ?? []).some((name) => names.has(name)),
  );
}
