import type { ProblemDetail } from "./types";

/** Ett misslyckat anrop, beskrivet så att en sida kan visa det. */
export interface Failure {
  /** Texten för hela formuläret. */
  message: string;
  /** Fel per fält, ur `errors` i svaret (400 med ogiltiga fält). Nyckeln är fältets namn i förfrågan. */
  fields: Record<string, string>;
}

export type Outcome<D> = { ok: true; data: D } | { ok: false; failure: Failure };

const NETWORK_MESSAGE = "Det gick inte att nå servern. Kontrollera anslutningen och försök igen.";
const SERVER_MESSAGE = "Något gick fel hos servern. Försök igen om en stund.";
const FALLBACK_MESSAGE = "Åtgärden kunde inte genomföras.";

/** Så många sekunder ska klienten vänta, ur `Retry-After`, eller `undefined` om huvudet saknas eller är ogiltigt. */
export function retryAfterSeconds(response: Response): number | undefined {
  const value = response.headers.get("Retry-After");
  if (value === null || !/^\d+$/.test(value.trim())) {
    return undefined;
  }
  return Number(value);
}

/** En väntetid i sekunder som text: "30 sekunder", "1 minut", "15 minuter", "3 timmar". Avrundas uppåt. */
export function formatWait(seconds: number): string {
  if (seconds < 60) {
    const s = Math.max(1, Math.ceil(seconds));
    return s === 1 ? "1 sekund" : `${String(s)} sekunder`;
  }
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 120) {
    return minutes === 1 ? "1 minut" : `${String(minutes)} minuter`;
  }
  return `${String(Math.ceil(minutes / 60))} timmar`;
}

function isProblem(body: unknown): body is ProblemDetail {
  return typeof body === "object" && body !== null;
}

/**
 * Gör ett felsvar från backenden till något en sida kan visa. Backendens `detail` är skriven för användaren
 * på svenska för fel i begäran (4xx), så den visas som den är. Serverfel (5xx) visar en egen text, eftersom
 * det som står i dem inte hjälper användaren.
 */
export function describeFailure(response: Response, body: unknown): Failure {
  const problem = isProblem(body) ? body : undefined;
  const fields: Record<string, string> = {};
  for (const error of problem?.errors ?? []) {
    fields[error.field] ??= error.message;
  }

  if (response.status === 429) {
    const wait = retryAfterSeconds(response);
    return {
      message:
        wait === undefined
          ? "För många misslyckade försök. Försök igen senare."
          : `För många misslyckade försök. Försök igen om ${formatWait(wait)}.`,
      fields,
    };
  }
  if (response.status >= 500) {
    return { message: SERVER_MESSAGE, fields };
  }
  return { message: problem?.detail ?? FALLBACK_MESSAGE, fields };
}

export function networkFailure(): Failure {
  return { message: NETWORK_MESSAGE, fields: {} };
}

/**
 * Kör ett anrop mot API-klienten och ger antingen svaret eller ett misslyckande som går att visa. Ett
 * nätverksfel (klienten kastar) blir också ett misslyckande, så att en sida inte behöver fånga det själv.
 */
export async function outcome<D>(
  call: () => Promise<{ data?: D; error?: unknown; response: Response }>,
): Promise<Outcome<D | undefined>> {
  try {
    const { data, error, response } = await call();
    if (response.ok) {
      return { ok: true, data };
    }
    return { ok: false, failure: describeFailure(response, error) };
  } catch {
    return { ok: false, failure: networkFailure() };
  }
}
