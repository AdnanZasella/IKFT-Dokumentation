import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { accountInfo, endpoint, passwordChangeRequired, problem, setCsrfCookie } from "../test/mocks";
import { server } from "../test/server";
import { createApiClient, SessionRefreshUnavailableError } from "./client";
import { CSRF_HEADER } from "./csrf";

const ME = endpoint("/api/v1/auth/me");
const LOGIN = endpoint("/api/v1/auth/login");
const REFRESH = endpoint("/api/v1/auth/refresh");
const CHANGE_PASSWORD = endpoint("/api/v1/auth/change-password");

const onSessionExpired = vi.fn();
const onMustChangePassword = vi.fn();

// MSW bygger om förfrågan och tappar credentials, så den mäts här, innan anropet lämnar klienten.
const credentialsSkickade: string[] = [];

function newClient() {
  return createApiClient({
    onSessionExpired,
    onMustChangePassword,
    fetch: (input, init) => {
      credentialsSkickade.push(input instanceof Request ? input.credentials : "saknas");
      return fetch(input, init);
    },
  });
}

beforeEach(() => {
  credentialsSkickade.length = 0;
  onSessionExpired.mockReset();
  onMustChangePassword.mockReset();
});

describe("CSRF", () => {
  it("skickar token ur cookien i X-XSRF-TOKEN på ett anrop som ändrar data", async () => {
    setCsrfCookie("token-1");
    let header: string | null = null;
    server.use(
      http.post(CHANGE_PASSWORD, ({ request }) => {
        header = request.headers.get(CSRF_HEADER);
        return HttpResponse.json(accountInfo());
      }),
    );

    const { data } = await newClient().POST("/api/v1/auth/change-password", {
      body: { currentPassword: "gammalt", newPassword: "nytt-lösenord" },
    });

    expect(header).toBe("token-1");
    expect(credentialsSkickade).toEqual(["include"]); // cookies skickas med
    expect(data).toEqual(accountInfo());
  });

  it("läser token på nytt vid varje anrop", async () => {
    const headers: (string | null)[] = [];
    server.use(
      http.post(CHANGE_PASSWORD, ({ request }) => {
        headers.push(request.headers.get(CSRF_HEADER));
        return HttpResponse.json(accountInfo());
      }),
    );
    const client = newClient();
    const body = { currentPassword: "a", newPassword: "b" };

    setCsrfCookie("före");
    await client.POST("/api/v1/auth/change-password", { body });
    setCsrfCookie("efter");
    await client.POST("/api/v1/auth/change-password", { body });

    expect(headers).toEqual(["före", "efter"]);
  });

  it("skickar ingen token på ett anrop som bara läser", async () => {
    setCsrfCookie("token-1");
    let header: string | null = "ej anropad";
    server.use(
      http.get(ME, ({ request }) => {
        header = request.headers.get(CSRF_HEADER);
        return HttpResponse.json(accountInfo());
      }),
    );

    await newClient().GET("/api/v1/auth/me");

    expect(header).toBeNull();
  });

  it("skickar anropet utan huvud när cookien saknas, så att backenden avgör", async () => {
    let header: string | null = "ej anropad";
    server.use(
      http.post(CHANGE_PASSWORD, ({ request }) => {
        header = request.headers.get(CSRF_HEADER);
        return problem(403, "Ogiltig CSRF-token.");
      }),
    );

    const { response } = await newClient().POST("/api/v1/auth/change-password", {
      body: { currentPassword: "a", newPassword: "b" },
    });

    expect(header).toBeNull();
    expect(response.status).toBe(403);
    expect(onMustChangePassword).not.toHaveBeenCalled();
  });
});

describe("401 och förnyelse", () => {
  it("förnyar sessionen en gång och gör om anropet med innehåll och ny CSRF-token", async () => {
    setCsrfCookie("token-1");
    const seen: { csrf: string | null; body: unknown }[] = [];
    let refreshCsrf: string | null = null;
    server.use(
      http.post(CHANGE_PASSWORD, async ({ request }) => {
        seen.push({ csrf: request.headers.get(CSRF_HEADER), body: await request.json() });
        return seen.length === 1 ? problem(401, "Inloggning krävs.") : HttpResponse.json(accountInfo());
      }),
      http.post(REFRESH, ({ request }) => {
        // Förnyelsen kräver CSRF-token (SecurityConfig.WITHOUT_LOGIN) och skickar cookies.
        refreshCsrf = request.headers.get(CSRF_HEADER);
        setCsrfCookie("token-2"); // förnyelsen kan byta token
        return HttpResponse.json(accountInfo());
      }),
    );

    const { data, response } = await newClient().POST("/api/v1/auth/change-password", {
      body: { currentPassword: "a", newPassword: "b" },
    });

    expect(response.status).toBe(200);
    expect(data).toEqual(accountInfo());
    expect(refreshCsrf).toBe("token-1");
    // Det första anropet, förnyelsen och det omgjorda anropet skickar alla cookies.
    expect(credentialsSkickade).toEqual(["include", "include", "include"]);
    const body = { currentPassword: "a", newPassword: "b" };
    expect(seen).toEqual([
      { csrf: "token-1", body },
      { csrf: "token-2", body },
    ]);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it("förnyar och gör om ett anrop som bara läser", async () => {
    let anrop = 0;
    server.use(
      http.get(ME, () => (++anrop === 1 ? problem(401, "Inloggning krävs.") : HttpResponse.json(accountInfo()))),
      http.post(REFRESH, () => HttpResponse.json(accountInfo())),
    );

    const { data } = await newClient().GET("/api/v1/auth/me");

    expect(data).toEqual(accountInfo());
    expect(anrop).toBe(2);
  });

  it("säger till att sessionen är slut när förnyelsen avslås, och ger det första 401 tillbaka", async () => {
    let refreshAnrop = 0;
    server.use(
      http.get(ME, () => problem(401, "Inloggning krävs.")),
      http.post(REFRESH, () => {
        refreshAnrop++;
        return problem(401, "Förnyelsen gäller inte längre.");
      }),
    );

    const { error, response } = await newClient().GET("/api/v1/auth/me");

    expect(response.status).toBe(401);
    expect(error?.detail).toBe("Inloggning krävs.");
    expect(refreshAnrop).toBe(1);
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it("förnyar inte en gång till när det omgjorda anropet också ger 401", async () => {
    let refreshAnrop = 0;
    let meAnrop = 0;
    server.use(
      http.get(ME, () => {
        meAnrop++;
        return problem(401, "Inloggning krävs.");
      }),
      http.post(REFRESH, () => {
        refreshAnrop++;
        return HttpResponse.json(accountInfo());
      }),
    );

    const { response } = await newClient().GET("/api/v1/auth/me");

    expect(response.status).toBe(401);
    expect(refreshAnrop).toBe(1);
    expect(meAnrop).toBe(2);
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it("delar på en förnyelse när flera anrop ger 401 samtidigt", async () => {
    let refreshAnrop = 0;
    let fatt401 = 0;
    let giltig = false;
    // Förnyelsen väntar tills båda anropen har fått sitt 401, så att båda hinner begära den. Ingen fast väntetid.
    let bada401: () => void = () => undefined;
    const badaHarFatt401 = new Promise<void>((klar) => {
      bada401 = klar;
    });
    server.use(
      http.get(ME, () => {
        if (giltig) {
          return HttpResponse.json(accountInfo());
        }
        if (++fatt401 === 2) {
          bada401();
        }
        return problem(401, "Inloggning krävs.");
      }),
      http.post(REFRESH, async () => {
        refreshAnrop++;
        await badaHarFatt401;
        giltig = true;
        return HttpResponse.json(accountInfo());
      }),
    );
    const client = newClient();

    const svar = await Promise.all([client.GET("/api/v1/auth/me"), client.GET("/api/v1/auth/me")]);

    expect(svar.map((s) => s.response.status)).toEqual([200, 200]);
    expect(fatt401).toBe(2);
    expect(refreshAnrop).toBe(1);
  });

  it("ger båda anropen 401 och säger till att sessionen är slut när den delade förnyelsen avslås", async () => {
    let fatt401 = 0;
    let bada401: () => void = () => undefined;
    const badaHarFatt401 = new Promise<void>((klar) => {
      bada401 = klar;
    });
    server.use(
      http.get(ME, () => {
        if (++fatt401 === 2) {
          bada401();
        }
        return problem(401, "Inloggning krävs.");
      }),
      http.post(REFRESH, async () => {
        await badaHarFatt401;
        return problem(401, "Förnyelsen gäller inte längre.");
      }),
    );
    const client = newClient();

    const svar = await Promise.all([client.GET("/api/v1/auth/me"), client.GET("/api/v1/auth/me")]);

    expect(svar.map((s) => s.response.status)).toEqual([401, 401]);
    expect(onSessionExpired).toHaveBeenCalled();
  });

  it("förnyar en gång till vid ett senare 401, när den första förnyelsen är klar", async () => {
    let refreshAnrop = 0;
    let giltig = false;
    server.use(
      http.get(ME, () => (giltig ? HttpResponse.json(accountInfo()) : problem(401, "Inloggning krävs."))),
      http.post(REFRESH, () => {
        refreshAnrop++;
        giltig = true;
        return HttpResponse.json(accountInfo());
      }),
    );
    const client = newClient();

    await client.GET("/api/v1/auth/me");
    giltig = false;
    await client.GET("/api/v1/auth/me");

    expect(refreshAnrop).toBe(2);
  });

  it.each([
    ["serverfel", () => problem(500, "Ett oväntat fel inträffade.")],
    ["spärr", () => problem(429, "För många försök.")],
    ["CSRF-fel", () => problem(403, "Ogiltig CSRF-token.")],
    ["nätverksfel", () => HttpResponse.error()],
  ])("kastar och säger inte att sessionen är slut när förnyelsen ger %s", async (_namn, svar) => {
    server.use(
      http.get(ME, () => problem(401, "Inloggning krävs.")),
      http.post(REFRESH, svar),
    );

    await expect(newClient().GET("/api/v1/auth/me")).rejects.toBeInstanceOf(SessionRefreshUnavailableError);

    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it("kastar när det omgjorda anropet inte når fram efter en lyckad förnyelse", async () => {
    let anrop = 0;
    server.use(
      http.get(ME, () => (++anrop === 1 ? problem(401, "Inloggning krävs.") : HttpResponse.error())),
      http.post(REFRESH, () => HttpResponse.json(accountInfo())),
    );

    await expect(newClient().GET("/api/v1/auth/me")).rejects.toThrow();

    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it("förnyar inte och säger inte att sessionen är slut när inloggningen ger 401 (fel lösenord)", async () => {
    let refreshAnrop = 0;
    server.use(
      http.post(LOGIN, () => problem(401, "Fel användarnamn eller lösenord.")),
      http.post(REFRESH, () => {
        refreshAnrop++;
        return HttpResponse.json(accountInfo());
      }),
    );

    const { error, response } = await newClient().POST("/api/v1/auth/login", {
      body: { username: "admin", password: "fel" },
    });

    expect(response.status).toBe(401);
    expect(error?.detail).toBe("Fel användarnamn eller lösenord.");
    expect(refreshAnrop).toBe(0);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it.each(["/api/v1/auth/logout", "/api/v1/auth/set-password"] as const)(
    "förnyar inte ett 401 från %s",
    async (path) => {
      let refreshAnrop = 0;
      server.use(
        http.post(endpoint(path), () => problem(401, "Nej.")),
        http.post(REFRESH, () => {
          refreshAnrop++;
          return HttpResponse.json(accountInfo());
        }),
      );
      const client = newClient();

      const { response } =
        path === "/api/v1/auth/logout"
          ? await client.POST(path)
          : await client.POST(path, { body: { username: "admin", code: "X", newPassword: "y" } });

      expect(response.status).toBe(401);
      expect(refreshAnrop).toBe(0);
      expect(onSessionExpired).not.toHaveBeenCalled();
    },
  );

  it("förnyar inte ett 401 från förnyelsen själv, och kastar inte i en slinga", async () => {
    let refreshAnrop = 0;
    server.use(
      http.post(REFRESH, () => {
        refreshAnrop++;
        return problem(401, "Förnyelsen gäller inte längre.");
      }),
    );

    const { response } = await newClient().POST("/api/v1/auth/refresh");

    expect(response.status).toBe(401);
    expect(refreshAnrop).toBe(1);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it("kastar vid nätverksfel på själva anropet, utan att förnya eller säga att sessionen är slut", async () => {
    server.use(http.get(ME, () => HttpResponse.error()));

    await expect(newClient().GET("/api/v1/auth/me")).rejects.toThrow();

    expect(onSessionExpired).not.toHaveBeenCalled();
  });
});

describe("403 med mustChangePassword", () => {
  it("säger till att lösenordet måste bytas och ger svaret tillbaka med kroppen oläst", async () => {
    server.use(http.get(endpoint("/api/v1/members"), () => passwordChangeRequired()));

    const { error, response } = await newClient().GET("/api/v1/members");

    expect(response.status).toBe(403);
    expect(error?.mustChangePassword).toBe(true);
    expect(onMustChangePassword).toHaveBeenCalledTimes(1);
  });

  it("säger inte till vid ett vanligt 403 (fel roll)", async () => {
    server.use(http.get(endpoint("/api/v1/audit-log"), () => problem(403, "Behörighet saknas.")));

    const { error } = await newClient().GET("/api/v1/audit-log", { params: { query: { entity: "MEMBER", id: "1" } } });

    expect(error?.detail).toBe("Behörighet saknas.");
    expect(error?.mustChangePassword).toBeUndefined();
    expect(onMustChangePassword).not.toHaveBeenCalled();
  });

  it("säger inte till när ett 403 inte har någon JSON-kropp", async () => {
    server.use(http.get(endpoint("/api/v1/members"), () => new HttpResponse("Förbjudet", { status: 403 })));

    const { response } = await newClient().GET("/api/v1/members");

    expect(response.status).toBe(403);
    expect(onMustChangePassword).not.toHaveBeenCalled();
  });

  it("säger till också på det omgjorda anropet efter en förnyelse", async () => {
    let anrop = 0;
    server.use(
      http.get(endpoint("/api/v1/members"), () => (++anrop === 1 ? problem(401, "Inloggning krävs.") : passwordChangeRequired())),
      http.post(REFRESH, () => HttpResponse.json(accountInfo({ mustChangePassword: true }))),
    );

    const { response } = await newClient().GET("/api/v1/members");

    expect(response.status).toBe(403);
    expect(onMustChangePassword).toHaveBeenCalledTimes(1);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });
});

describe("felsvar", () => {
  it("ger felkroppen typad som ProblemDetail, med fältfel vid 400", async () => {
    server.use(
      http.post(LOGIN, () =>
        problem(400, "Begäran innehåller ogiltiga fält.", {
          errors: [{ field: "username", message: "får inte vara null" }],
        }),
      ),
    );

    const { data, error } = await newClient().POST("/api/v1/auth/login", {
      body: { username: "x", password: "y" },
    });

    expect(data).toBeUndefined();
    expect(error?.status).toBe(400);
    expect(error?.errors).toEqual([{ field: "username", message: "får inte vara null" }]);
  });
});
