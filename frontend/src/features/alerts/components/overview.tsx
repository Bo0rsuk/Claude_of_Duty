import { SampleEvent } from "#features/alerts/components/sample-event.tsx";
import { type Alert, useWorkspaceContext } from "#features/workspace";
import { stamp } from "#lib/time.ts";
import {
  Bell,
  ChevronDown,
  Clock3,
  Code2,
  Database,
  Layers,
  Mail,
  MessageSquare,
  Phone,
  Radio,
  ShieldAlert,
  SlidersHorizontal,
  UserRound,
  Users,
} from "lucide-react";

export function Overview({ a }: { a: Alert }) {
  const { data, mode } = useWorkspaceContext();
  const matchedRule = data.rules.find((r) => r.id === a.rule);
  const IconForChannel = (ch: string) =>
    ch === "Phone"
      ? Phone
      : ch === "SMS"
        ? MessageSquare
        : ch === "Email"
          ? Mail
          : Bell;
  return (
    <div className="detail-overview">
      <dl className="metadata">
        <dt>
          <Database />
          Source
        </dt>
        <dd>
          <code>{a.source}</code>
        </dd>
        <dt>
          <SlidersHorizontal />
          Matched rule
        </dt>
        <dd>{matchedRule?.name ?? "Archived rule"}</dd>
        <dt>
          <UserRound />
          Owner
        </dt>
        <dd>
          <span className="person-dot">
            <UserRound size={12} />
          </span>
          {(a.owner ?? a.target) || "Nobody yet"}
        </dd>
        {a.notified !== undefined && (
          <>
            <dt>
              <Users />
              Notified
            </dt>
            <dd>{a.notified.length ? a.notified.join(", ") : "Nobody, the alert only appears in the feed"}</dd>
          </>
        )}
        <dt>
          <Radio />
          Channels
        </dt>
        <dd>
          {a.channels.map((ch) => {
            const Icon = IconForChannel(ch);
            return (
              <span className="channel" key={ch}>
                <Icon />
                {ch}
              </span>
            );
          })}
        </dd>
        {matchedRule?.window && (
          <>
            <dt>
              <Clock3 />
              Delivery window
            </dt>
            <dd>
              {matchedRule.window} · {matchedRule.fallback} outside this window
            </dd>
          </>
        )}
        <dt>
          <Clock3 />
          First seen
        </dt>
        <dd>{stamp(a.first)}</dd>
      </dl>
      <div className="group-band">
        <Layers />
        <strong>{a.count} events grouped</strong>
        <span>Last seen {stamp(a.last)}</span>
      </div>
      <section className="fields-section">
        <h3>
          <Code2 />
          Event fields
        </h3>
        {mode === "Partial data" ? (
          <p>
            Field details are temporarily unavailable. The alert and its
            delivery history are available.
          </p>
        ) : (
          <dl className="event-fields">
            {Object.entries(a.fields).map(([k, v]) => (
              <div key={k}>
                <dt>{k}</dt>
                <dd className={["step", "error"].includes(k) ? "risk" : ""}>
                  {["step", "error"].includes(k) && (
                    <ShieldAlert aria-label="Risk field" />
                  )}
                  {v}
                </dd>
              </div>
            ))}
          </dl>
        )}
        <details>
          <summary>
            <Code2 />
            Latest event JSON
            <ChevronDown />
          </summary>
          <pre>
            {JSON.stringify(
              {
                source: a.source,
                received_at: new Date(a.last).toISOString(),
                ...a.fields,
              },
              null,
              2,
            )}
          </pre>
        </details>
      </section>
      {a.sample && <SampleEvent sample={a.sample} />}
    </div>
  );
}
