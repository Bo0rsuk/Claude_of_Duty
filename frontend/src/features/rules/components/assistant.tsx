import { Field } from "#components/field.tsx";
import { latestEvent, suggestConditions, tryConditions } from "#features/rules/services/rule-assistant.ts";
import { useWorkspaceContext } from "#features/workspace";
import { Check, Sparkles, Undo2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export function Assistant() {
  const { rule, editRule, account, sourceIdOf } = useWorkspaceContext(),
    sourceId = sourceIdOf(rule.source),
    [example, setExample] = useState<Record<string, unknown> | null>(null),
    [goal, setGoal] = useState(""),
    [proposal, setProposal] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    [old, setOld] = useState<string | null>(null),
    [test, setTest] = useState("");
  // Ignore an answer that arrives after the editor moved on.
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    latestEvent(sourceId).then((event) => active.current && setExample(event), () => undefined);
    return () => {
      active.current = false;
    };
  }, [sourceId]);
  function generate() {
    setBusy(true);
    setTest("");
    suggestConditions(goal, rule.source, sourceId)
      .then((suggestion) => {
        if (!active.current) return;
        setProposal(suggestion.conditions);
        setTest(suggestion.note);
      })
      .catch((failure: Error) => active.current && setTest(failure.message))
      .finally(() => active.current && setBusy(false));
  }
  return (
    <section className="assistant">
      <h3>
        <Sparkles />
        Filter assistant {!account && <span className="demo-label">Simulation</span>}
      </h3>
      <Field label="What do you want to catch?">
        <textarea
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          placeholder="For example, failed production workflows…"
        />
      </Field>
      <details>
        <summary>Example event</summary>
        <pre>
          {JSON.stringify(
            example ?? {
              service: rule.source,
              environment: "production",
              outcome: "failed",
            },
            null,
            2,
          )}
        </pre>
      </details>
      <button disabled={!goal.trim() || busy} onClick={generate}>
        <Sparkles />
        {busy ? "Generating conditions…" : "Generate conditions"}
      </button>
      <div aria-live="polite">
        {proposal !== null && (
          <>
            <Field label="Proposed conditions · review before applying">
              <textarea
                value={proposal}
                onChange={(e) => {
                  setProposal(e.target.value);
                  setTest("");
                }}
              />
            </Field>
            <div className="buttons">
              <button
                onClick={() =>
                  tryConditions(proposal, sourceId).then(setTest, (failure: Error) => setTest(failure.message))
                }
              >
                Test proposal
              </button>
              <button
                disabled={!proposal.trim()}
                onClick={() => {
                  setOld(rule.conditions);
                  editRule({ conditions: proposal });
                  setProposal(null);
                  setTest("Proposal applied to the draft.");
                }}
              >
                <Check />
                Accept
              </button>
              <button
                onClick={() => {
                  setProposal(null);
                  setTest("Proposal discarded.");
                }}
              >
                <X />
                Reject
              </button>
            </div>
          </>
        )}
        {test && <p>{test}</p>}
        {old !== null && (
          <button
            onClick={() => {
              editRule({ conditions: old });
              setOld(null);
              setTest("Previous conditions restored.");
            }}
          >
            <Undo2 />
            Restore conditions
          </button>
        )}
      </div>
    </section>
  );
}
