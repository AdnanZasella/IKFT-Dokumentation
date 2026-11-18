import { describe, expect, it } from "vitest";

import { describeFailure, formatWait, outcome, retryAfterSeconds } from "./errors";

function response(status: number, headers: Record<string, string> = {}) {
  return new Response(null, { status, headers });
}

describe("formatWait", () => {
  it.each([
    [1, "1 sekund"],
    [30, "30 sekunder"],
    [59, "59 sekunder"],
    [60, "1 minut"],
    [61, "2 minuter"],
    [900, "15 minuter"],
    [7140, "119 minuter"],
    [7141, "2 timmar"],
    [7200, "2 timmar"],
    [7201, "3 timmar"],
  ])("%i sekunder blir %s", (seconds, text) => {
    expect(formatWait(seconds)).toBe(text);
  });

  it("räknar aldrig ner till noll sekunder", () => {
    expect(formatWait(0)).toBe("1 sekund");
  });
});

describe("retryAfterSeconds", () => {
  it("läser hela sekunder", () => {
    expect(retryAfterSeconds(response(429, { "Retry-After": "900" }))).toBe(900);
  });

  it.each([["saknas", {}], ["är ett datum", { "Retry-After": "Wed, 21 Oct 2026 07:28:00 GMT" }], ["är tomt", { "Retry-After": " " }]])(
    "ger inget värde när huvudet %s",
    (_namn, headers) => {
      expect(retryAfterSeconds(response(429, headers))).toBeUndefined();
    },
  );
});

describe("describeFailure", () => {
  it("visar backendens text för fel i begäran", () => {
    const failure = describeFailure(response(401), { status: 401, detail: "Fel användarnamn eller lösenord." });

    expect(failure).toEqual({ message: "Fel användarnamn eller lösenord.", fields: {} });
  });

  it("samlar felen per fält, och den första texten för ett fält vinner", () => {
    const failure = describeFailure(response(400), {
      status: 400,
      detail: "Begäran innehåller ogiltiga fält.",
      errors: [
        { field: "newPassword", message: "Lösenordet måste ha minst 12 tecken." },
        { field: "newPassword", message: "En andra text." },
        { field: "currentPassword", message: "Får inte vara tomt." },
      ],
    });

    expect(failure.fields).toEqual({
      newPassword: "Lösenordet måste ha minst 12 tecken.",
      currentPassword: "Får inte vara tomt.",
    });
  });

  it.each([
    ["en text", { status: 502, errors: "bad gateway" }],
    ["ett objekt", { status: 400, errors: {} }],
    ["poster utan fält", { status: 400, errors: [null, 7, { message: "Saknar fält." }, { field: 3, message: "Fel typ." }] }],
    ["en lista i stället för ett objekt", [{ field: "a", message: "b" }]],
  ])("kastar inte när errors är %s, utan ger inga fältfel", (_namn, body) => {
    const failure = describeFailure(response(400), body);

    expect(failure.fields).toEqual({});
  });

  it("anger hur länge en spärr varar", () => {
    const failure = describeFailure(response(429, { "Retry-After": "900" }), { status: 429, detail: "Spärrad." });

    expect(failure.message).toBe("För många misslyckade försök. Försök igen om 15 minuter.");
  });

  it("säger bara 'senare' när Retry-After saknas", () => {
    expect(describeFailure(response(429), undefined).message).toBe("För många misslyckade försök. Försök igen senare.");
  });

  it("döljer serverns text vid serverfel", () => {
    const failure = describeFailure(response(500), { status: 500, detail: "NullPointerException i LoginService" });

    expect(failure.message).toBe("Något gick fel hos servern. Försök igen om en stund.");
  });

  it("har en egen text när svaret saknar kropp eller kroppen inte är JSON", () => {
    expect(describeFailure(response(403), undefined).message).toBe("Åtgärden kunde inte genomföras.");
    expect(describeFailure(response(403), "Forbidden").message).toBe("Åtgärden kunde inte genomföras.");
  });
});

describe("outcome", () => {
  it("ger svaret när anropet lyckas", async () => {
    const result = await outcome(() => Promise.resolve({ data: { id: 1 }, response: response(200) }));

    expect(result).toEqual({ ok: true, data: { id: 1 } });
  });

  it("ger ett misslyckande för ett felsvar", async () => {
    const result = await outcome(() =>
      Promise.resolve({ error: { status: 400, detail: "Fel." }, response: response(400) }),
    );

    expect(result).toEqual({ ok: false, failure: { message: "Fel.", fields: {} } });
  });

  it("gör ett nätverksfel till ett misslyckande i stället för att kasta", async () => {
    const result = await outcome(() => Promise.reject(new TypeError("Failed to fetch")));

    expect(result).toEqual({
      ok: false,
      failure: { message: "Det gick inte att nå servern. Kontrollera anslutningen och försök igen.", fields: {} },
    });
  });
});
