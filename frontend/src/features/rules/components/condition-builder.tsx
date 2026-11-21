import {
  type Mode,
  OPERATORS,
  type OperatorToken,
  type Row,
  complete,
  standsAlone,
  toRows,
  toText,
} from "#features/rules/domain/condition-rows.ts";
import { latestEvent } from "#features/rules/services/rule-assistant.ts";
import { useWorkspaceContext } from "#features/workspace";
import { Plus, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

const BLANK: Row = { field: "", operator: "=", value: "" };

/**
 * Edits the rule's conditions as rows of field, operator and value. The rule
 * keeps them as one line of text, which stays available for anything the rows
 * cannot express and for people who prefer typing.
 */
export function ConditionBuilder() {
  const { rule, editRule, account, sourceIdOf } = useWorkspaceContext();
  const parsed = toRows(rule.conditions);
  const [rows, setRows] = useState<Row[]>(parsed?.rows ?? []),
    [mode, setMode] = useState<Mode>(parsed?.mode ?? "all"),
    [asText, setAsText] = useState(parsed === null),
    [fields, setFields] = useState<string[]>([]);
  const listId = useId();

  // The text also changes from outside: the assistant's proposal, "Restore conditions", another rule.
  const written = useRef(rule.conditions);
  useEffect(() => {
    if (rule.conditions === written.current) return;
    written.current = rule.conditions;
    const next = toRows(rule.conditions);
    setRows(next?.rows ?? []);
    setMode(next?.mode ?? "all");
    if (!next) setAsText(true);
  }, [rule.conditions]);

  // Field names to offer come from the newest event of the rule's source.
  const sourceId = account ? sourceIdOf(rule.source) : undefined;
  useEffect(() => {
    let current = true;
    setFields([]);
    if (sourceId) {
      latestEvent(sourceId).then(
        (event) => current && event && setFields(Object.keys(event)),
        () => undefined,
      );
    }
    return () => {
      current = false;
    };
  }, [sourceId]);

  function write(nextRows: Row[], nextMode: Mode) {
    setRows(nextRows);
    setMode(nextMode);
    const text = toText(nextMode, nextRows);
    written.current = text;
    editRule({ conditions: text });
  }
  const change = (index: number, patch: Partial<Row>) =>
    write(
      rows.map((row, at) => (at === index ? { ...row, ...patch } : row)),
      mode,
    );

  if (asText) {
    return (
      <div className="conditions">
        <label htmlFor={listId + "text"}>Matching conditions</label>
        <textarea
          id={listId + "text"}
          value={rule.conditions}
          onChange={(e) => {
            written.current = e.target.value;
            editRule({ conditions: e.target.value });
          }}
        />
        {parsed ? (
          <button
            type="button"
            className="link-btn"
            onClick={() => {
              setRows(parsed.rows);
              setMode(parsed.mode);
              setAsText(false);
            }}
          >
            Edit as a list
          </button>
        ) : (
          <p className="hint">
            Write each condition as field, operator, value, joined with AND or with OR. Once the text follows that
            form it can be edited as a list.
          </p>
        )}
      </div>
    );
  }
  return (
    <fieldset className="conditions">
      <legend>Matching conditions</legend>
      {rows.length > 1 && (
        <label className="conditions-mode">
          An event must match
          <select value={mode} onChange={(e) => write(rows, e.target.value as Mode)}>
            <option value="all">all of these</option>
            <option value="any">any of these</option>
          </select>
        </label>
      )}
      {rows.map((row, index) => (
        <div className="condition" key={index}>
          <input
            aria-label={"Field of condition " + (index + 1)}
            list={listId}
            placeholder="Field"
            value={row.field}
            onChange={(e) => change(index, { field: e.target.value })}
          />
          <select
            aria-label={"Operator of condition " + (index + 1)}
            value={row.operator}
            onChange={(e) => change(index, { operator: e.target.value as OperatorToken })}
          >
            {OPERATORS.map(([token, label]) => (
              <option key={token} value={token}>
                {label}
              </option>
            ))}
          </select>
          {!standsAlone(row.operator) && (
            <input
              aria-label={"Value of condition " + (index + 1)}
              placeholder={row.operator === "in" ? "ERROR, FATAL" : "Value"}
              value={row.value}
              onChange={(e) => change(index, { value: e.target.value })}
            />
          )}
          <button
            type="button"
            className="icon-btn"
            aria-label={"Remove condition " + (index + 1)}
            title="Remove condition"
            onClick={() =>
              write(
                rows.filter((_, at) => at !== index),
                mode,
              )
            }
          >
            <X />
          </button>
        </div>
      ))}
      <datalist id={listId}>
        {fields.map((name) => (
          <option key={name} value={name} />
        ))}
      </datalist>
      {!rows.length && <p className="hint">No conditions yet. Add the first one to say which events this rule catches.</p>}
      {rows.some((row) => !complete(row)) && (
        <p className="hint">A condition that is not filled in is left out when the rule is saved.</p>
      )}
      <div className="buttons">
        <button type="button" onClick={() => write([...rows, BLANK], mode)}>
          <Plus />
          Add condition
        </button>
        <button type="button" className="link-btn" onClick={() => setAsText(true)}>
          Edit as text
        </button>
      </div>
    </fieldset>
  );
}
