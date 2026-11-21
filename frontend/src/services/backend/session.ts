// Sign-in against Amazon Cognito, without a client library: the user pool
// accepts email and password over its public JSON API.

import { backend } from "#services/backend/config.ts";

type Session = {
  email: string;
  idToken: string;
  refreshToken: string;
  /** Epoch milliseconds after which the ID token is no longer accepted. */
  expiresAt: number;
};

const STORAGE_KEY = "fans:session";
const listeners = new Set<() => void>();
let session: Session | null = read();

function read(): Session | null {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
  } catch {
    return null;
  }
}

function store(next: Session | null) {
  session = next;
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* The session stays usable in memory. */
  }
  listeners.forEach((listener) => listener());
}

async function cognito<T>(action: string, body: unknown): Promise<T> {
  const response = await fetch(`https://cognito-idp.${backend.region}.amazonaws.com/`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-amz-json-1.1",
      "X-Amz-Target": "AWSCognitoIdentityProviderService." + action,
    },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.message ?? "Sign-in service error");
  return payload as T;
}

type AuthResult = { AuthenticationResult: { IdToken: string; RefreshToken?: string; ExpiresIn: number } };

function accept(email: string, result: AuthResult, refreshToken: string) {
  const auth = result.AuthenticationResult;
  store({
    email,
    idToken: auth.IdToken,
    refreshToken: auth.RefreshToken ?? refreshToken,
    expiresAt: Date.now() + auth.ExpiresIn * 1000,
  });
}

/** Thrown by signIn for an account an admin created: the temporary password must be replaced first. */
export class NewPasswordRequired extends Error {
  readonly challenge: string;
  constructor(challenge: string) {
    super("Choose your own password to finish signing in.");
    this.challenge = challenge;
  }
}

type Challenge = { ChallengeName?: string; Session?: string };

export async function signIn(email: string, password: string) {
  const result = await cognito<AuthResult & Challenge>("InitiateAuth", {
    AuthFlow: "USER_PASSWORD_AUTH",
    ClientId: backend.userPoolClientId,
    AuthParameters: { USERNAME: email, PASSWORD: password },
  });
  if (result.ChallengeName === "NEW_PASSWORD_REQUIRED" && result.Session) throw new NewPasswordRequired(result.Session);
  if (!result.AuthenticationResult) throw new Error("This account needs a sign-in step the app does not support.");
  accept(email, result, "");
}

/** Replaces the temporary password of an account an admin created, and signs in. */
export async function setFirstPassword(email: string, password: string, challenge: string) {
  const result = await cognito<AuthResult>("RespondToAuthChallenge", {
    ChallengeName: "NEW_PASSWORD_REQUIRED",
    ClientId: backend.userPoolClientId,
    Session: challenge,
    ChallengeResponses: { USERNAME: email, NEW_PASSWORD: password },
  });
  accept(email, result, "");
}

/** Creates the account. Cognito then emails a confirmation code. */
export async function signUp(email: string, password: string) {
  await cognito("SignUp", {
    ClientId: backend.userPoolClientId,
    Username: email,
    Password: password,
    UserAttributes: [{ Name: "email", Value: email }],
  });
}

export async function confirmSignUp(email: string, code: string) {
  await cognito("ConfirmSignUp", { ClientId: backend.userPoolClientId, Username: email, ConfirmationCode: code });
}

/** Gets a new ID token. Needed after joining an organization, which the token then carries. */
export async function refreshSession() {
  if (!session) throw new Error("Not signed in");
  const { email, refreshToken } = session;
  try {
    const result = await cognito<AuthResult>("InitiateAuth", {
      AuthFlow: "REFRESH_TOKEN_AUTH",
      ClientId: backend.userPoolClientId,
      AuthParameters: { REFRESH_TOKEN: refreshToken },
    });
    accept(email, result, refreshToken);
  } catch (error) {
    // The refresh token expired or was revoked: the user has to sign in again.
    store(null);
    throw error;
  }
}

/** A token that is valid for at least another minute. */
export async function idToken(): Promise<string> {
  if (!session) throw new Error("Not signed in");
  if (session.expiresAt - Date.now() < 60_000) await refreshSession();
  return session!.idToken;
}

export function signOut() {
  store(null);
}

export const signedIn = () => session !== null;
export const signedInEmail = () => session?.email ?? "";

/** Calls the listener whenever the user signs in or out. */
export function onSessionChange(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
