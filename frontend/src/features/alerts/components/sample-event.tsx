import type { SampleEvent as Sample } from "#features/workspace";
import { clock } from "#lib/time.ts";
import { ChevronDown, FileText } from "lucide-react";

/** The first event grouped into the alert: what the source sent and how the parser read it. */
export function SampleEvent({ sample }: { sample: Sample }) {
  const fields = Object.entries(sample.fields);
  return (
    <section className="fields-section sample-event">
      <h3>
        <FileText />
        First event
      </h3>
      <p className="sample-line">
        <span className="neutral-tag">{sample.level}</span>
        <time dateTime={new Date(sample.time).toISOString()}>{clock(sample.time)}</time>
      </p>
      <p className="sample-message">{sample.message || "The event has no message."}</p>
      {fields.length > 0 && (
        <details>
          <summary>
            <FileText />
            All {fields.length} fields of the event
            <ChevronDown />
          </summary>
          <dl className="event-fields">
            {fields.map(([key, value]) => (
              <div key={key}>
                <dt>{key}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </details>
      )}
      <details>
        <summary>
          <FileText />
          As received
          <ChevronDown />
        </summary>
        <pre>{sample.raw}</pre>
      </details>
    </section>
  );
}
