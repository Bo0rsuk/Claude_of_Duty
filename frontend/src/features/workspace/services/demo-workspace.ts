import { homeTeam, sourceTypes } from "#features/workspace/domain/workspace-defaults.ts";
import { newRule } from "#features/workspace/services/new-rule.ts";
import type {
  Alert,
  Book,
  Data,
  Entry,
  Member,
  Rule,
  Severity,
  Status,
} from "#features/workspace/types/records.ts";

export function fixtures(): Data {
  const now = Date.now();
  const ago = (min: number) => now - min * 60000;
  const members: Member[] = [
    {
      id: "alex",
      name: "Alex Morgan",
      email: "alex@fans.example",
      phone: "+1 202 555 0102",
      role: "Admin",
      team: homeTeam,
      duty: false,
    },
    {
      id: "anna",
      name: "Anna Kowalski",
      email: "anna@fans.example",
      phone: "+1 202 555 0108",
      role: "Team lead",
      team: homeTeam,
      duty: true,
    },
    {
      id: "noah",
      name: "Noah Chen",
      email: "noah@fans.example",
      phone: "+1 202 555 0116",
      role: "User",
      team: homeTeam,
      duty: false,
    },
    {
      id: "ines",
      name: "Inés Silva",
      email: "ines@fans.example",
      phone: "+1 202 555 0132",
      role: "Pending",
      team: homeTeam,
      duty: false,
    },
    {
      id: "sam",
      name: "Sam Brooks",
      email: "sam@fans.example",
      phone: "+1 202 555 0140",
      role: "Team lead",
      team: "markets",
      duty: true,
    },
    {
      id: "guest",
      name: "Riley Park",
      email: "riley@fans.example",
      phone: "",
      role: "Pending",
      team: "",
      duty: false,
    },
  ];
  const definitions: [
    string,
    string,
    string,
    Severity,
    Status,
    number,
    number,
    Record<string, string>,
  ][] = [
    [
      "Order settlement failed",
      "workflow-engine",
      "Production workflow failure",
      "Critical",
      "Open",
      2,
      500,
      {
        workflow: "order-settlement",
        environment: "production",
        step: "finalize_settlement",
        error: "UPSTREAM_TIMEOUT",
      },
    ],
    [
      "Payment gateway timeout",
      "payments-api",
      "Payment availability",
      "Critical",
      "Open",
      5,
      18,
      {
        service: "payments-api",
        environment: "production",
        gateway: "clearing-east",
        error: "GATEWAY_TIMEOUT",
      },
    ],
    [
      "EUR 12.5M trade rejected · Credit limit",
      "trade-gateway",
      "Large trade rejection",
      "Warning",
      "Open",
      12,
      4,
      {
        desk: "rates",
        currency: "EUR",
        notional: "12500000",
        reason: "credit limit",
      },
    ],
    [
      "Pricing feed variance",
      "pricing-engine-eu",
      "Instrument price drift",
      "Info",
      "Open",
      18,
      36,
      {
        service: "pricing-engine-eu",
        instrument: "EUR/USD",
        window: "5 minutes",
        variance: "0.12%",
      },
    ],
    [
      "Queue depth above threshold",
      "settlement-queue",
      "Queue backlog",
      "Warning",
      "Acknowledged",
      24,
      12,
      { region: "eu-west-1", queue: "settlements", depth: "1800" },
    ],
    [
      "Clearing connection lost",
      "clearing-adapter",
      "Clearing availability",
      "Critical",
      "Acknowledged",
      39,
      8,
      {
        venue: "LCH",
        connection: "clearing-primary",
        reason: "heartbeat expired",
      },
    ],
    [
      "Market snapshot delayed",
      "pricing-engine-eu",
      "Market freshness",
      "Warning",
      "Open",
      53,
      3,
      { instrument: "GBP/USD", delay: "12 seconds" },
    ],
    [
      "Reconciliation completed late",
      "workflow-engine",
      "Batch reconciliation",
      "Info",
      "Resolved",
      76,
      2,
      { workflow: "reconcile", delay: "3 minutes" },
    ],
    [
      "Trade acknowledgment delayed",
      "trade-gateway",
      "Trade confirmation lag",
      "Warning",
      "Open",
      93,
      6,
      { desk: "rates", lag: "14 seconds" },
    ],
    [
      "Cache refresh retried",
      "pricing-engine-eu",
      "Cache refresh retry",
      "Info",
      "Resolved",
      115,
      15,
      { instrument: "USD/JPY", attempt: "2" },
    ],
    [
      "Settlement window extended",
      "settlement-queue",
      "Settlement window",
      "Info",
      "Acknowledged",
      141,
      5,
      { queue: "settlements", extension: "5 minutes" },
    ],
    [
      "Payment callback delayed",
      "payments-api",
      "Callback delivery lag",
      "Warning",
      "Open",
      180,
      9,
      { service: "payments-api", delay: "45 seconds" },
    ],
  ];
  const sourceIds = [...new Set(definitions.map((x) => x[1]))];
  const sources = sourceIds.map((id, i) => ({
    id,
    type: sourceTypes[i % 4],
    format: i === 2 ? "key=value" : "JSON",
    connection: "demo://platform/" + id,
    mapping: "service → service\nenvironment → environment",
    last: ago(definitions.find((x) => x[1] === id)![5]),
  }));
  const books: Book[] = [
    {
      id: "settlement",
      name: "Settlement recovery",
      type: "Checklist",
      body: "Inspect the failed workflow step\nCheck the upstream settlement provider\nReplay the settlement in the demo\nVerify the final workflow state",
    },
    {
      id: "payments",
      name: "Payment gateway recovery",
      type: "Markdown",
      body: "## Verify gateway health\nCheck the primary clearing route.\n## Recover delivery\nReview pending payment callbacks.",
    },
    {
      id: "market",
      name: "Market operations guide",
      type: "Wiki link",
      body: "https://wiki.example/market-operations",
    },
    {
      id: "trade",
      name: "Trade rejection review",
      type: "Plain text",
      body: "Review the desk limit, currency and credit decision with the on-call risk analyst.",
    },
  ];
  const rules: Rule[] = definitions.map((x, i) => ({
    ...newRule(),
    id: "rule-" + i,
    name: x[2],
    source: x[1],
    severity: x[3],
    channels:
      i === 0
        ? ["Phone", "SMS"]
        : i === 1
          ? ["SMS"]
          : x[3] === "Info"
            ? ["In-app"]
            : ["Email", "In-app"],
    targets: [
      i === 0
        ? "On-call engineer"
        : i === 1
          ? "Anna Kowalski"
          : "Market operations",
    ],
    conditions:
      [
        "workflow = order-settlement AND environment = production AND outcome = failed",
        "service = payments-api AND level = critical",
        "desk = rates AND notional > 10000000 AND result = rejected",
        "service starts_with pricing-engine- AND level = warning",
      ][i] || "service = " + x[1],
    group: i === 3 ? "instrument" : "service",
    window: i === 1 ? "08:00–20:00" : "",
    runbook: ["settlement", "payments", "trade", "market"][i % 4],
    template:
      i === 2 ? "{{currency}} {{notional}} rejected · {{reason}}" : x[0],
    fields: Object.keys(x[7]).join(", "),
  }));
  const alerts: Alert[] = definitions.map(
    ([title, source, , severity, status, min, count, fields], i) => {
      const first = ago(min + 3),
        last = ago(min),
        rule = rules[i];
      const timeline: Entry[] = [
        { time: first, title: "Alert opened", body: rule.name },
        {
          time: first + 1500,
          title: "Sent",
          body:
            rule.channels.join(" + ") +
            " notification queued for " +
            rule.targets[0],
        },
        {
          time: first + 4000,
          title: "Delivered",
          body: "Demo delivery receipt received",
        },
        {
          time: last,
          title: "Events grouped",
          body: count + " matching events in this alert",
        },
      ];
      if (i === 0)
        timeline.splice(
          2,
          0,
          {
            time: first + 2000,
            title: "Failed",
            body: "Phone route timed out",
          },
          {
            time: first + 3000,
            title: "Retrying",
            body: "Second delivery attempt",
          },
        );
      if (count > 1)
        timeline.push({
          time: last,
          title: "Suppressed",
          body: "Duplicate notifications suppressed",
        });
      if (status !== "Open")
        timeline.push({
          time: last,
          title: status,
          body: "Updated by Anna Kowalski",
        });
      return {
        id: "ALT-" + (2048 - i),
        title,
        severity,
        status,
        source,
        rule: rule.id,
        first,
        last,
        count,
        target: rule.targets[0],
        channels: rule.channels,
        fields,
        runbook: rule.runbook,
        checks: [],
        timeline,
      };
    },
  );
  return {
    alerts,
    sources,
    rules,
    books,
    members,
    teams: [
      {
        id: homeTeam,
        name: "Platform operations",
        lead: "anna",
        code: "PLAT-4826",
      },
      {
        id: "markets",
        name: "Markets engineering",
        lead: "sam",
        code: "MKT-7124",
      },
    ],
    groups: [
      { id: "market", name: "Market operations", members: ["anna", "noah"] },
      {
        id: "response",
        name: "Settlement response",
        members: ["alex", "anna"],
      },
    ],
  };
}
