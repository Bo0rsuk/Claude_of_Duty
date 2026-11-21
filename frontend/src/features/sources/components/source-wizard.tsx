import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { detectFieldMapping } from "#features/sources/domain/field-mapping.ts";
import { sourceTypes, useWorkspaceContext } from "#features/workspace";
import { Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export function SourceWizard({ onClose }: { onClose: () => void }) {
  const { data, commit, setSourceId, setCompactDetail } = useWorkspaceContext();
  const [step, setStep] = useState(0),
    [type, setType] = useState(sourceTypes[0]),
    [name, setName] = useState(""),
    [connection, setConnection] = useState(""),
    [format, setFormat] = useState("JSON"),
    [sample, setSample] = useState('{"service":"settlement","level":"error"}'),
    [mapping, setMapping] = useState(""),
    [approved, setApproved] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  function detect() {
    setBusy(true);
    setApproved(false);
    setError("");
    timer.current = setTimeout(() => {
      try {
        setMapping(detectFieldMapping(sample, format));
      } catch {
        setError("No fields detected. Check the event format and try again.");
      }
      setBusy(false);
    }, 600);
  }
  const names = ["Source type", "Connection", "Event format", "Verify fields"];
  return (
    <Modal title="Add source" onClose={onClose}>
      <p className="muted">
        Step {step + 1} of 4 · {names[step]}
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (step === 1 && data.sources.some((s) => s.id === name.trim())) {
            setError("Choose a unique source name.");
            return;
          }
          setError("");
          if (step < 3) {
            setStep(step + 1);
            return;
          }
          if (!approved || !mapping.trim()) {
            setError("Review and confirm the field mapping.");
            return;
          }
          const id = name.trim();
          commit(
            {
              ...data,
              sources: [
                ...data.sources,
                { id, type, format, connection, mapping, last: Date.now() },
              ],
            },
            "Source added locally.",
          );
          setSourceId(id);
          setCompactDetail(true);
          onClose();
        }}
      >
        {step === 0 ? (
          <Field label="Source type">
            <select value={type} onChange={(e) => setType(e.target.value)}>
              {sourceTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
        ) : step === 1 ? (
          <>
            <Field label="Source name">
              <input
                required
                pattern=".*\S.*"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="settlement-worker…"
              />
            </Field>
            <Field label="Demo connection address">
              <input
                required
                value={connection}
                onChange={(e) => setConnection(e.target.value)}
                placeholder="demo://platform/settlement…"
              />
            </Field>
          </>
        ) : step === 2 ? (
          <Field label="Event format">
            <select
              value={format}
              onChange={(e) => {
                setFormat(e.target.value);
                setMapping("");
                setApproved(false);
                setSample(
                  e.target.value === "JSON"
                    ? '{"service":"settlement","level":"error"}'
                    : "service=settlement level=error",
                );
              }}
            >
              {["JSON", "key=value", "Custom format"].map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </Field>
        ) : (
          <>
            <Field label="Example event">
              <textarea
                value={sample}
                onChange={(e) => {
                  setSample(e.target.value);
                  setMapping("");
                  setApproved(false);
                }}
              />
            </Field>
            <button
              type="button"
              disabled={busy || !sample.trim()}
              onClick={detect}
            >
              <Sparkles />
              {busy ? "Detecting fields…" : "Detect fields"}
            </button>
            {mapping && (
              <>
                <Field label="Suggested field mapping · simulation">
                  <textarea
                    value={mapping}
                    onChange={(e) => {
                      setMapping(e.target.value);
                      setApproved(false);
                    }}
                  />
                </Field>
                <label className="check-line">
                  <input
                    type="checkbox"
                    checked={approved}
                    onChange={(e) => setApproved(e.target.checked)}
                  />
                  I reviewed the field mapping
                </label>
              </>
            )}
          </>
        )}
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <div className="buttons">
          {step > 0 && (
            <button
              type="button"
              onClick={() => {
                setStep(step - 1);
                setError("");
              }}
            >
              Back
            </button>
          )}
          <button className="primary">
            {
              [
                "Set connection",
                "Choose format",
                "Verify fields",
                "Add source",
              ][step]
            }
          </button>
        </div>
      </form>
    </Modal>
  );
}
