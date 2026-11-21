import type { ApiError } from "#services/backend/api-types.ts";
import { backend } from "#services/backend/config.ts";
import { idToken } from "#services/backend/session.ts";

/** A failed API call. `message` comes from the server and is written for people. */
export class ApiFailure extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(backend.apiUrl + path, {
    method,
    headers: { Authorization: "Bearer " + (await idToken()), "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) return undefined as T;
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const error = (payload as ApiError | null)?.error;
    throw new ApiFailure(response.status, error?.code ?? "ERROR", error?.message ?? "Request failed");
  }
  return payload as T;
}
