import { Logo } from "#components/logo.tsx";
import { Field } from "#components/field.tsx";
import {
  NewPasswordRequired,
  confirmSignUp,
  onSessionChange,
  setFirstPassword,
  signIn,
  signUp,
  signedIn,
} from "#services/backend/session.ts";
import { BellRing, Database, GitBranch } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";

type Step = "sign-in" | "sign-up" | "confirm" | "new-password";

const SUBMIT_LABELS: Record<Step, string> = {
  "sign-in": "Sign in",
  "sign-up": "Create account",
  confirm: "Confirm and sign in",
  "new-password": "Save password and sign in",
};

/** Shows the application to signed-in users and the sign-in form to everyone else. */
export function SignedIn({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(signedIn);
  useEffect(() => onSessionChange(() => setAuthenticated(signedIn())), []);
  return authenticated ? children : <SignIn />;
}

function SignIn() {
  const [step, setStep] = useState<Step>("sign-in"),
    [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [code, setCode] = useState(""),
    // Set when the account was created by an admin and still has its temporary password.
    [challenge, setChallenge] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (step === "new-password") {
        await setFirstPassword(email.trim(), password, challenge);
      } else if (step === "sign-up") {
        await signUp(email.trim(), password);
        setStep("confirm");
      } else {
        if (step === "confirm") await confirmSignUp(email.trim(), code.trim());
        await signIn(email.trim(), password);
      }
    } catch (failure) {
      if (failure instanceof NewPasswordRequired) {
        setChallenge(failure.challenge);
        setPassword("");
        setStep("new-password");
      } else setError((failure as Error).message);
    }
    setBusy(false);
  }
  const titles: Record<Step, string> = {
    "sign-in": "Sign in to Squawk",
    "sign-up": "Create your account",
    confirm: "Confirm your email",
    "new-password": "Choose your password",
  };
  return (
    <main className="sign-in">
      <Landing />
      <section className="surface join">
        <h2>{titles[step]}</h2>
        <p>
          {step === "confirm"
            ? "We sent a code to " +
              email +
              ". Enter it to finish creating your account."
            : step === "new-password"
              ? "Your account was created with a temporary password. Replace it with your own to sign in."
              : "Alerts, rules and on-call response for your team."}
        </p>
        <form onSubmit={submit}>
          {step === "confirm" ? (
            <Field label="Confirmation code">
              <input
                required
                inputMode="numeric"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
            </Field>
          ) : (
            <>
              {step !== "new-password" && (
                <Field label="Email">
                  <input
                    type="email"
                    required
                    autoComplete="username"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </Field>
              )}
              <Field
                label={
                  step === "sign-in"
                    ? "Password"
                    : "Password · at least 8 characters with a digit"
                }
              >
                <input
                  type="password"
                  required
                  minLength={8}
                  autoComplete={
                    step === "sign-in" ? "current-password" : "new-password"
                  }
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Field>
            </>
          )}
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          <button className="primary" disabled={busy}>
            {busy ? "Please wait…" : SUBMIT_LABELS[step]}
          </button>
        </form>
        <div className="buttons">
          {step === "sign-in" ? (
            <button onClick={() => setStep("sign-up")}>
              Create an account
            </button>
          ) : (
            <button onClick={() => setStep("sign-in")}>Back to sign in</button>
          )}
        </div>
      </section>
    </main>
  );
}

const STEPS = [
  {
    icon: Database,
    title: "Connect what you already run",
    text: "Webhooks, JSON APIs, log files and Kafka topics. A new source is a form, not a deployment.",
  },
  {
    icon: GitBranch,
    title: "Decide what matters",
    text: "Rules turn events into alerts. Describe what to catch and the assistant drafts the conditions.",
  },
  {
    icon: BellRing,
    title: "Reach the right person",
    text: "Phone, SMS, email, push and the live feed. A burst of identical events stays one alert.",
  },
];

/** What the product does, for someone who has not signed in yet. */
function Landing() {
  return (
    <section className="landing" aria-labelledby="landing-title">
      <div className="landing-brand">
        <Logo />
        Squawk
      </div>
      <h1 id="landing-title">
        When something breaks, the right person knows in seconds.
      </h1>
      <p className="landing-lead">
        Squawk watches your event sources, groups the noise into alerts and
        notifies whoever is responsible, with the runbook attached.
      </p>
      <ol className="landing-steps">
        {STEPS.map(({ icon: Icon, title, text }) => (
          <li key={title}>
            <Icon />
            <div>
              <strong>{title}</strong>
              <span>{text}</span>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
