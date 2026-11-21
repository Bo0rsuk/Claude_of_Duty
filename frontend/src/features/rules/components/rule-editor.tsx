import { Box } from "#components/box.tsx";
import { Field } from "#components/field.tsx";
import { Assistant } from "#features/rules/components/assistant.tsx";
import { ConditionBuilder } from "#features/rules/components/condition-builder.tsx";
import { ChannelMessages } from "#features/rules/components/channel-messages.tsx";
import {
  Grouping,
  SeveritySelector,
  Targets,
} from "#features/rules/components/delivery-settings.tsx";
import { MessagePreview } from "#features/rules/components/message-preview.tsx";
import { RecipientLimit } from "#features/rules/components/recipient-limit.tsx";
import { RuleActions } from "#features/rules/components/rule-actions.tsx";
import { RuleRunbook } from "#features/rules/components/rule-runbook.tsx";
import { type Rule, useWorkspaceContext } from "#features/workspace";
import { BookOpen, ChevronRight, ListFilter, SlidersHorizontal } from "lucide-react";

export function RuleEditor() {
  const { rule, data, editRule } = useWorkspaceContext(),
    saved = data.rules.find((r) => r.id === rule.id);
  const change = (key: keyof Rule, v: string) => editRule({ [key]: v });
  return (
    <Box
      icon={SlidersHorizontal}
      title={rule.name || "Draft rule"}
      extra={
        JSON.stringify(saved) !== JSON.stringify(rule) && (
          <span className="draft">Unsaved changes</span>
        )
      }
    >
      <Field label="Rule name">
        <input
          value={rule.name}
          onChange={(e) => change("name", e.target.value)}
        />
      </Field>
      <label className="check-line">
        <input
          type="checkbox"
          checked={rule.enabled !== false}
          onChange={(e) => editRule({ enabled: e.target.checked })}
        />
        Rule is on
        {rule.enabled === false && <span className="hint"> · new events are not matched until it is turned on</span>}
      </label>
      <Field label="Event source">
        <select
          value={rule.source}
          onChange={(e) => change("source", e.target.value)}
        >
          {data.sources.map((s) => (
            <option key={s.id}>{s.id}</option>
          ))}
        </select>
      </Field>
      <ConditionBuilder key={rule.id} />
      <details>
        <summary>
          <ListFilter />
          Generate conditions
          <ChevronRight />
        </summary>
        <Assistant key={rule.id} />
      </details>
      <SeveritySelector />
      <Targets />
      <details>
        <summary>Grouping & limits</summary>
        <Grouping />
        <RecipientLimit />
      </details>
      <details>
        <summary>
          <BookOpen />
          Runbook
        </summary>
        <RuleRunbook />
      </details>
      <details>
        <summary>Message template & preview</summary>
        <MessagePreview />
        <ChannelMessages />
      </details>
      <RuleActions />
    </Box>
  );
}
