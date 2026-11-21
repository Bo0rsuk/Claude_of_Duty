import type { Severity, Status } from "#features/workspace/types/records.ts";

export const severities: Severity[] = ["Critical", "Warning", "Info"];
export const statuses: Status[] = ["Open", "Acknowledged", "Resolved"];
export const homeTeam = "platform";
export const channels = ["Phone", "SMS", "Email", "In-app", "Push"];
export const sourceTypes = [
  "HTTP endpoint",
  "Webhook",
  "Kafka topic",
  "RabbitMQ queue",
];
export const bookTypes = ["Checklist", "Markdown", "Wiki link", "Plain text"];
