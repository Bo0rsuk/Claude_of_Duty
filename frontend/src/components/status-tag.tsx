type Tone = "neutral" | "info" | "positive";

const alertTones: Record<string, Tone> = {
  Acknowledged: "info",
  Resolved: "positive",
};

export function StatusTag({
  value,
  tone = alertTones[value] ?? "neutral",
}: {
  value: string;
  tone?: Tone;
}) {
  return <span className={"status-badge " + tone}>{value}</span>;
}
