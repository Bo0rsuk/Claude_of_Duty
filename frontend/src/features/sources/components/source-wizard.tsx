import { Field } from "#components/field.tsx";
import { Modal } from "#components/modal.tsx";
import { detectFields } from "#features/sources/services/field-detection.ts";
import { type Preset, loadPresets } from "#features/sources/services/source-presets.ts";
import { sourceTypes, useWorkspaceContext } from "#features/workspace";
import { Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export function SourceWizard({ onClose }: { onClose: () => void }) {
  const { data, commit, setSourceId, setCompactDetail, account } = useWorkspaceContext();
  const [step, setStep] = useState(0),
    [type, setType] = useState(sourceTypes[0]),
    [name, setName] = useState(""),
    [connection, setConnection] = useState(""),
    [format, setFormat] = useState("JSON"),
    [sample, setSample] = useState('{"service":"settlement","level":"error"}'),
    [mapping, setMapping] = useState(""),
    [approved, setApproved] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [presets, setPresets] = useState<Preset[]>([]),
    [presetName, setPresetName] = useState("");
  const preset = presets.find((candidate) => candidate.name === presetName);
  // Ignore a result that arrives after the wizard was closed.
  const open = useRef(true);
  useEffect(
    () => () => {
      open.current = false;
    },
    [],
  );
  useEffect(() => {
    // Without the presets the wizard still sets up a source by hand.
    loadPresets()
      .then((loaded) => open.current && setPresets(loaded))
      .catch(() => undefined);
  }, []);
  function detect() {
    setBusy(true);
    setApproved(false);
    setError("");
    detectFields(sample, format)
      .then((detected) => open.current && setMapping(detected))
      .catch(() => open.current && setError("No fields detected. Check the event format and try again."))
      .finally(() => open.current && setBusy(false));
  }
  // A webhook's address is assigned by the server when the source is saved.
  const assignedAddress = Boolean(account) && (preset ? preset.type : type) === "Webhook";
  // A preset already knows the event format and the fields, so only the connection is left to fill in.
  const names = preset ? ["Source type", "Connection"] : ["Source type", "Connection", "Event format", "Verify fields"];
  const last = names.length - 1;
  return (
    <Modal title="Add source" onClose={onClose}>
      <p className="muted">
        Step {step + 1} of {names.length} · {names[step]}
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (step === 1 && data.sources.some((s) => s.id === name.trim())) {
            setError("Choose a unique source name.");
            return;
          }
          setError("");
          if (step < last) {
            setStep(step + 1);
            return;
          }
          if (!preset && (!approved || !mapping.trim())) {
            setError("Review and confirm the field mapping.");
            return;
          }
          const id = name.trim();
          commit(
            {
              ...data,
              sources: [
                ...data.sources,
                preset
                  ? {
                      id,
                      type: preset.type,
                      format: preset.format,
                      connection,
                      mapping: preset.mapping,
                      last: Date.now(),
                      preset: preset.setup,
                    }
                  : { id, type, format, connection, mapping, last: Date.now() },
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
          <>
            {presets.length > 0 && (
              <Field label="Start from">
                <select
                  value={presetName}
                  onChange={(e) => {
                    const chosen = presets.find((candidate) => candidate.name === e.target.value);
                    setPresetName(e.target.value);
                    setConnection(chosen?.connection ?? "");
                  }}
                >
                  <option value="">A source set up by hand</option>
                  {presets.map((candidate) => (
                    <option key={candidate.name} value={candidate.name}>
                      {candidate.name}
                    </option>
                  ))}
                </select>
              </Field>
            )}
            {preset ? (
              <>
                <dl className="plain-dl">
                  <dt>Type</dt>
                  <dd>{preset.type}</dd>
                  <dt>Format</dt>
                  <dd>{preset.format}</dd>
                </dl>
                <details>
                  <summary>Field mapping</summary>
                  <pre>{preset.mapping}</pre>
                </details>
                <p className="helper">The event format and the fields are already set up. Only the connection is left.</p>
              </>
            ) : (
              <Field label="Source type">
                <select value={type} onChange={(e) => setType(e.target.value)}>
                  {sourceTypes.map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </select>
              </Field>
            )}
          </>
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
            {assignedAddress ? (
              <p className="helper">The address to send events to is shown after the source is added.</p>
            ) : (
              <Field label={account ? "Connection address" : "Demo connection address"}>
                <input
                  required
                  value={connection}
                  onChange={(e) => setConnection(e.target.value)}
                  placeholder={
                    !account
                      ? "demo://platform/settlement…"
                      : (preset ? preset.type : type) === "Kafka topic"
                        ? "broker.example.com:9092/topic"
                        : "https://example.com/events"
                  }
                />
              </Field>
            )}
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
                <Field label={account ? "Detected fields · mark the level, message and time" : "Suggested field mapping · simulation"}>
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
            {step === last ? "Add source" : ["Set connection", "Choose format", "Verify fields"][step]}
          </button>
        </div>
      </form>
    </Modal>
  );
}
