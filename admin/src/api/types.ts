import type { components } from "./schema";

/** Felsvar från backenden, RFC 9457. Beskrivs i kontraktet av `OpenApiConfig` i backenden. */
export type ProblemDetail = components["schemas"]["ProblemDetail"];

/** Det inloggade kontot, svaret från `GET /auth/me`. */
export type AccountInfo = components["schemas"]["AccountInfo"];

/** Ett 403 som skickar användaren till lösenordsbytet (`PasswordChangeRequiredFilter` i backenden). */
export function isPasswordChangeRequired(body: unknown): body is ProblemDetail & { mustChangePassword: true } {
  return (
    typeof body === "object" && body !== null && "mustChangePassword" in body && body.mustChangePassword === true
  );
}
