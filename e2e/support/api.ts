import { type APIRequestContext, type APIResponse, expect, type PlaywrightWorkerArgs } from "@playwright/test";

import { BAS_URL, type Konto } from "./konton";

/** Fixturen `playwright` i ett test. */
export type Playwright = PlaywrightWorkerArgs["playwright"];

const CSRF_COOKIE = "__Host-XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

/**
 * Anrop mot backendens API (via Vites proxy, som appen), för att förbereda testdata och städa. Själva flödet som testas
 * går alltid genom gränssnittet. En session loggar in på nytt för varje test, så den beror inte på sparade
 * inloggningar som kan ha gått ut.
 */
export class ApiSession {
  private constructor(readonly request: APIRequestContext) {}

  /**
   * Loggar in via API:et. Det första anropet (`GET /auth/me`) ger CSRF-cookien, som alla anrop som ändrar data
   * kräver. Inloggningen byter token, så den läses på nytt vid varje anrop.
   */
  static async loggaIn(playwright: Playwright, konto: Konto, losenord = konto.losenord): Promise<ApiSession> {
    const session = await ApiSession.oinloggad(playwright);
    const svar = await session.post("/api/v1/auth/login", { username: konto.anvandarnamn, password: losenord });
    expect(
      svar.status(),
      `Inloggningen som ${konto.anvandarnamn} misslyckades. Körs backenden med profilen e2e?`,
    ).toBe(200);
    return session;
  }

  /** En session som har CSRF-token men inte är inloggad. */
  static async oinloggad(playwright: Playwright): Promise<ApiSession> {
    const request = await playwright.request.newContext({ baseURL: BAS_URL });
    // 401 är väntat. Svaret sätter CSRF-cookien.
    await request.get("/api/v1/auth/me");
    return new ApiSession(request);
  }

  async post(url: string, data?: unknown): Promise<APIResponse> {
    return this.request.post(url, { data, headers: { [CSRF_HEADER]: await this.csrfToken() } });
  }

  async avsluta() {
    await this.request.dispose();
  }

  private async csrfToken(): Promise<string> {
    const { cookies } = await this.request.storageState();
    const token = cookies.find((cookie) => cookie.name === CSRF_COOKIE)?.value;
    expect(token, "CSRF-cookien saknas. Svarar backenden via Vites proxy på localhost:8080?").toBeTruthy();
    return token ?? "";
  }
}

/** Dagens datum i Stockholm som YYYY-MM-DD, eftersom backenden räknar datum i Europe/Stockholm. */
export function dagensDatum(): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Stockholm" }).format(new Date());
}

/** Lägger till en påhittad medlem med unikt namn. Returnerar medlemmens id. */
export async function skapaMedlem(admin: ApiSession, fornamn: string, efternamn: string): Promise<string> {
  const svar = await admin.post("/api/v1/members", {
    firstName: fornamn,
    lastName: efternamn,
    birthDate: "1990-01-01",
    homeMunicipality: "Trollhättan",
  });
  expect(svar.status(), "Det gick inte att skapa medlemmen").toBe(201);
  return ((await svar.json()) as { id: string }).id;
}

/**
 * Utser medlemmen till ordinarie dokumenterare från i dag. Innehavaren byts direkt, och Dokumenterare-kontot måste då
 * byta lösenord vid nästa inloggning.
 */
export async function utseDokumenterare(admin: ApiSession, medlemId: string) {
  const svar = await admin.post("/api/v1/documenter-assignment", { memberId: medlemId, startDate: dagensDatum() });
  expect(svar.ok(), "Det gick inte att utse dokumenteraren").toBe(true);
}
