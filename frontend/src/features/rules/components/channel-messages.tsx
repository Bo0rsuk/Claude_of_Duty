import { Field } from "#components/field.tsx";
import { type ChannelText, useWorkspaceContext } from "#features/workspace";

/** Channels whose notification has a heading of its own: the email subject, the pop-up and push title. */
const WITH_SUBJECT = ["Email", "In-app", "Push"];
const SUBJECT_LABELS: Record<string, string> = { Email: "Email subject", "In-app": "Pop-up title", Push: "Push title" };

/** The alert's body text and, per delivery channel, a text that replaces the title and body on that channel. */
export function ChannelMessages() {
  const { rule, editRule } = useWorkspaceContext();
  const texts = rule.texts ?? {};
  // Channels the rule delivers on, and channels that still hold a text from before.
  const shown = [...new Set([...rule.channels, ...Object.keys(texts).filter((label) => texts[label].subject || texts[label].body)])];
  const change = (label: string, part: ChannelText) =>
    editRule({ texts: { ...texts, [label]: { subject: "", body: "", ...texts[label], ...part } } });
  return (
    <>
      <Field label="Body template">
        <textarea
          value={rule.body ?? ""}
          placeholder="{{message}}"
          onChange={(e) => editRule({ body: e.target.value })}
        />
      </Field>
      <p className="hint">
        Placeholders: any event field, plus {"{{level}}"}, {"{{message}}"}, {"{{severity}}"}, {"{{ruleName}}"},{" "}
        {"{{sourceName}}"}, {"{{count}}"} and {"{{alertUrl}}"}.
      </p>
      <fieldset className="channel-texts">
        <legend>Text per channel</legend>
        {shown.length ? (
          <>
            <p className="hint">
              Leave a box empty to send the alert's title and body on that channel. SMS and email always end with the
              link to the alert.
            </p>
            {shown.map((label) => (
              <div className="channel-text" key={label}>
                <strong>
                  {label}
                  {!rule.channels.includes(label) && <small> · not a delivery channel of this rule</small>}
                </strong>
                {WITH_SUBJECT.includes(label) && (
                  <Field label={SUBJECT_LABELS[label]}>
                    <input
                      value={texts[label]?.subject ?? ""}
                      placeholder="Same as the alert title"
                      onChange={(e) => change(label, { subject: e.target.value })}
                    />
                  </Field>
                )}
                <Field label={label === "Phone" ? "Text read out on the call" : label + " text"}>
                  <textarea
                    value={texts[label]?.body ?? ""}
                    placeholder="Same as the alert body"
                    onChange={(e) => change(label, { body: e.target.value })}
                  />
                </Field>
              </div>
            ))}
          </>
        ) : (
          <p className="hint">Choose a delivery channel to write a text of its own for it.</p>
        )}
      </fieldset>
    </>
  );
}
