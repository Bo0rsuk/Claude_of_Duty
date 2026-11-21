import { Field } from "#components/field.tsx";
import { testConditions } from "#features/rules/domain/test-conditions.ts";
import { recentDemoEvents } from "#features/rules/services/demo-events.ts";
import { useWorkspaceContext } from "#features/workspace";
import { Check, Sparkles, Undo2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export function Assistant() {
  const { rule, editRule } = useWorkspaceContext(),
    [goal, setGoal] = useState(""),
    [proposal, setProposal] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    [old, setOld] = useState<string | null>(null),
    [test, setTest] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  function generate() {
    setBusy(true);
    setTest("");
    timer.current = setTimeout(() => {
      const text = goal.toLowerCase();
      setProposal(
        text.includes("pricing")
          ? "service starts_with pricing-engine- AND level = warning"
          : text.includes("trade") || text.includes("notional")
            ? "desk = rates AND notional > 10000000"
            : text.includes("payment")
              ? "service = payments-api AND level = critical"
              : "service = " +
                rule.source +
                " AND environment = production AND outcome = failed",
      );
      setBusy(false);
    }, 650);
  }
  return (
    <section className="assistant">
      <h3>
        <Sparkles />
        Filter assistant <span className="demo-label">Simulation</span>
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
            {
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
              <button onClick={() => setTest(testConditions(proposal, recentDemoEvents))}>
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
