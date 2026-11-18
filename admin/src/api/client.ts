import createClient, { type Client, type Middleware } from "openapi-fetch";

import { CSRF_HEADER, isMutating, readCsrfToken } from "./csrf";
import type { paths } from "./schema";
import { isPasswordChangeRequired } from "./types";

export type ApiClient = Client<paths>;

/**
 * Ett anrop gav 401, men det gick inte att avgöra om sessionen gäller, eftersom förnyelsen inte gick att nå eller
 * gav serverfel. Anropet kastar då, på samma sätt som vid ett nätverksfel. Ett fel på det omgjorda anropet efter en
 * lyckad förnyelse kastar också, som ett vanligt nätverksfel.
 */
export class SessionRefreshUnavailableError extends Error {
  constructor() {
    super("Sessionen kunde inte förnyas just nu.");
    this.name = "SessionRefreshUnavailableError";
  }
}

export interface ApiClientOptions {
  /** Backendens adress. Som standard samma ursprung som appen, eftersom /api ligger bakom samma värd. */
  baseUrl?: string;
  /** Hämtas vid varje anrop. Tester byter den, och MSW lägger sig i den globala `fetch`. */
  fetch?: typeof globalThis.fetch;
  /** Förnyelsen misslyckades, så användaren är utloggad. Appen skickar användaren till inloggningen. */
  onSessionExpired: () => void;
  /** Backenden kräver lösenordsbyte (403 med `mustChangePassword`). Appen skickar användaren dit. */
  onMustChangePassword: () => void;
}

/**
 * Anrop där ett 401 betyder något annat än en utgången session, eller där förnyelsen inte hjälper: fel
 * användarnamn eller lösenord, själva förnyelsen, utloggningen och engångskoden. Adresserna är de i
 * `SecurityConfig.WITHOUT_LOGIN`.
 */
const NO_REFRESH_PATHS: ReadonlySet<keyof paths> = new Set<keyof paths>([
  "/api/v1/auth/login",
  "/api/v1/auth/refresh",
  "/api/v1/auth/logout",
  "/api/v1/auth/set-password",
]);

/**
 * Den typade API-klienten (`openapi-fetch`). Den
 * - skickar cookies,
 * - lägger CSRF-token ur cookien i `X-XSRF-TOKEN` på varje anrop som ändrar data,
 * - försöker förnya sessionen (`POST /auth/refresh`) en gång när ett anrop ger 401, och gör sedan om anropet,
 * - säger till när sessionen är slut eller när lösenordet måste bytas.
 *
 * Klienten känner inte till routern. Appen avgör vart användaren skickas, se `RequireSession`.
 */
export function createApiClient(options: ApiClientOptions): ApiClient {
  const baseUrl = options.baseUrl ?? window.location.origin;
  const fetchImpl: typeof globalThis.fetch = (...args) => (options.fetch ?? globalThis.fetch)(...args);

  const csrf: Middleware = {
    onRequest({ request }) {
      applyCsrfToken(request);
    },
  };

  // Förnyelsen går genom en egen klient utan 401-hantering, så att den inte kan förnya sig själv.
  const refreshClient = createClient<paths>({ baseUrl, credentials: "include", fetch: fetchImpl });
  refreshClient.use(csrf);

  // Samtidiga 401 delar på en förnyelse. Förnyelsecookien är engångs, så två samtidiga förnyelser
  // skulle få den andra att misslyckas.
  let refreshing: Promise<"renewed" | "rejected" | "unavailable"> | null = null;
  function refreshSession() {
    refreshing ??= renew().finally(() => {
      refreshing = null;
    });
    return refreshing;
  }
  async function renew() {
    try {
      const { response } = await refreshClient.POST("/api/v1/auth/refresh");
      if (response.ok) {
        return "renewed";
      }
      // Bara ett 401 betyder att förnyelsecookien inte gäller längre, alltså att sessionen är slut. Ett serverfel,
      // en spärr (429) eller ett CSRF-fel (403) säger inget om den.
      return response.status === 401 ? "rejected" : "unavailable";
    } catch {
      return "unavailable";
    }
  }

  // En kopia av varje anrop som kan förnyas, tagen innan det skickas, eftersom en förfrågan med innehåll bara
  // kan skickas en gång.
  const copies = new WeakMap<Request, Request>();

  const session: Middleware = {
    onRequest({ request, schemaPath }) {
      if (!NO_REFRESH_PATHS.has(schemaPath as keyof paths)) {
        copies.set(request, request.clone());
      }
    },
    async onResponse({ request, response }) {
      let result = response;

      const copy = copies.get(request);
      if (response.status === 401 && copy) {
        const outcome = await refreshSession();
        if (outcome === "unavailable") {
          // Vi vet inte om sessionen gäller. Ett kast, som vid vilket nätverksfel som helst, hindrar att anroparen tror
          // att användaren är utloggad, och att appen skickar användaren till inloggningen i onödan.
          throw new SessionRefreshUnavailableError();
        }
        if (outcome === "renewed") {
          applyCsrfToken(copy); // inloggningen och förnyelsen kan ha bytt token
          result = await fetchImpl(copy);
        }
        // Ett andra 401 efter lyckad förnyelse förnyas inte igen. Då är sessionen ändå inte användbar.
        if (outcome === "rejected" || (outcome === "renewed" && result.status === 401)) {
          options.onSessionExpired();
        }
      }

      if (result.status === 403 && (await hasPasswordChangeRequired(result))) {
        options.onMustChangePassword();
      }

      return result === response ? undefined : result;
    },
  };

  const client = createClient<paths>({ baseUrl, credentials: "include", fetch: fetchImpl });
  client.use(csrf, session);
  return client;
}

function applyCsrfToken(request: Request) {
  const token = readCsrfToken();
  if (token && isMutating(request.method)) {
    request.headers.set(CSRF_HEADER, token);
  }
}

async function hasPasswordChangeRequired(response: Response): Promise<boolean> {
  try {
    return isPasswordChangeRequired(await response.clone().json());
  } catch {
    return false;
  }
}
