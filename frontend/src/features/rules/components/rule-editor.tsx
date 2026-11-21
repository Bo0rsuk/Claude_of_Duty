import { Box } from "#components/box.tsx";
import { Modal } from "#components/modal.tsx";
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
import { BookOpen, ChevronRight, ListFilter, SlidersHorizontal, X } from "lucide-react";
import { useState } from "react";

export function RuleEditor() {
  const { rule, data, editRule, setRule, compactDetail, setCompactDetail, panel } = useWorkspaceContext(),
    saved = data.rules.find((r) => r.id === rule.id),
    [closing, setClosing] = useState(false);
  const edited = saved
    ? JSON.stringify(saved) !== JSON.stringify(rule)
    : Boolean(rule.name.trim() || rule.conditions.trim());
  function close() {
    // Edits that were not saved are dropped, so the list and a reopened rule show what is stored.
    if (saved) setRule(saved);
    setClosing(false);
    setCompactDetail(false);
  }
  const change = (key: keyof Rule, v: string) => editRule({ [key]: v });
  return (
    <Box
      icon={SlidersHorizontal}
      title={rule.name || "Draft rule"}
      extra={
        <span className="inspector-tools">
          {JSON.stringify(saved) !== JSON.stringify(rule) && <span className="draft">Unsaved changes</span>}
          {/* In the rule workspace the editor opens over half of the list; closing it gives the list the page back. */}
          {compactDetail && panel === "Rules" && (
            <button
              className="icon-btn close-detail"
              aria-label="Close rule editor"
              title="Close rule editor"
              onClick={() => (edited ? setClosing(true) : close())}
            >
              <X />
            </button>
          )}
        </span>
      }
    >
      {closing && (
        <Modal title="Discard unsaved changes?" onClose={() => setClosing(false)}>
          <p>“{rule.name || "The draft rule"}” has edits that are not saved. Closing the editor discards them.</p>
          <div className="buttons">
            <button onClick={() => setClosing(false)}>Keep editing</button>
            <button className="danger-btn" onClick={close}>
              Discard changes
            </button>
          </div>
        </Modal>
      )}
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
