const eventExamples: Record<string, string>[] = [
  {
    service: "workflow-engine",
    workflow: "order-settlement",
    environment: "production",
    outcome: "failed",
  },
  {
    service: "workflow-engine",
    workflow: "order-settlement",
    environment: "production",
    outcome: "completed",
  },
  { service: "pricing-engine-eu", level: "warning", instrument: "EUR/USD" },
  { service: "pricing-engine-us", level: "info", instrument: "USD/JPY" },
  {
    service: "trade-gateway",
    desk: "rates",
    notional: "12500000",
    result: "rejected",
  },
  {
    service: "trade-gateway",
    desk: "rates",
    notional: "4500000",
    result: "accepted",
  },
  { service: "payments-api", level: "critical", environment: "production" },
  { service: "payments-api", level: "info", environment: "production" },
  { service: "settlement-queue", depth: "950", environment: "staging" },
];
export const recentDemoEvents = Array.from(
  { length: 100 },
  (_, i) => eventExamples[(i * 7) % eventExamples.length],
);
