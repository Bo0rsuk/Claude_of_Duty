export const PHONE_HELP = "Enter the number in the international form, for example +48 600 100 200.";

/**
 * A phone number as SMS and calls need it: "+" and 8 to 15 digits. Spaces,
 * dashes and brackets that people type for readability are dropped.
 *
 * @returns the number, "" for an empty field, or null when it is not a phone number
 */
export function normalizePhone(text: string): string | null {
  const compact = text.replace(/[\s().-]/g, "");
  if (!compact) return "";
  return /^\+\d{8,15}$/.test(compact) ? compact : null;
}
