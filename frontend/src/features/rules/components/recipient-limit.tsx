import { Field } from "#components/field.tsx";
import { useWorkspaceContext } from "#features/workspace";

/** How many notifications from the rule one recipient gets on one channel. Empty means no limit. */
export function RecipientLimit() {
  const { rule, editRule } = useWorkspaceContext();
  return (
    <fieldset>
      <legend>Limit per recipient</legend>
      <Field label="Maximum notifications to one recipient on one channel">
        <input
          type="number"
          min="1"
          placeholder="No limit"
          value={rule.recipientLimit ?? ""}
          onChange={(e) =>
            editRule({ recipientLimit: e.target.value, recipientInterval: rule.recipientInterval ?? "" })
          }
        />
      </Field>
      <Field label="Within (minutes)">
        <input
          type="number"
          min="1"
          placeholder="No limit"
          value={rule.recipientInterval ?? ""}
          onChange={(e) =>
            editRule({ recipientInterval: e.target.value, recipientLimit: rule.recipientLimit ?? "" })
          }
        />
      </Field>
      <p className="hint">Leave both empty for no limit. Notifications held back are recorded in the alert's timeline.</p>
    </fieldset>
  );
}
