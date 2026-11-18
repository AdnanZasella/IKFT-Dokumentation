import { HttpResponse } from "msw";

import { CSRF_COOKIE } from "../api/csrf";
import type { paths } from "../api/schema";
import type { AccountInfo, ProblemDetail } from "../api/types";

/**
 * Adressen till en endpoint i MSW-form. Parametern är en adress ur kontraktet (`paths`), så en mock för en endpoint
 * som inte finns, eller har bytt namn, fäller typkontrollen. `{id}` blir `:id`.
 */
export function endpoint(path: keyof paths): string {
  return path.replace(/\{(\w+)\}/g, ":$1");
}

/** Ett konto som svar på `GET /auth/me`. Kroppen är typad efter kontraktet. */
export function accountInfo(overrides: Partial<AccountInfo> = {}): AccountInfo {
  return { role: "ADMIN", holderName: "Test Testsson", mustChangePassword: false, ...overrides };
}

const TITLES: Record<number, string> = {
  400: "Bad Request",
  401: "Unauthorized",
  403: "Forbidden",
  404: "Not Found",
  409: "Conflict",
};

/** Ett felsvar i samma form som backenden (RFC 9457). Kroppen är typad efter kontraktet. */
export function problem(status: number, detail: string, extra: Partial<ProblemDetail> = {}) {
  const body: ProblemDetail = { type: "about:blank", title: TITLES[status] ?? "Error", status, detail, ...extra };
  return HttpResponse.json(body, { status, headers: { "Content-Type": "application/problem+json" } });
}

/** Så svarar backenden när lösenordet måste bytas. */
export function passwordChangeRequired() {
  return problem(403, "Lösenordet måste bytas.", { mustChangePassword: true });
}

/** Backenden sätter CSRF-cookien i svaret på det första anropet. I testerna sätter vi den för hand. */
export function setCsrfCookie(token: string) {
  document.cookie = `${CSRF_COOKIE}=${token}; Secure; Path=/`;
}

export function clearCsrfCookie() {
  document.cookie = `${CSRF_COOKIE}=; Max-Age=0; Secure; Path=/`;
}
