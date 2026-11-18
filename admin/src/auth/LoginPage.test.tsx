import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { accountInfo, endpoint, problem, setCsrfCookie, tooManyRequests } from "../test/mocks";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";
import { ME_KEY } from "../app/services";

const ME = endpoint("/api/v1/auth/me");
const REFRESH = endpoint("/api/v1/auth/refresh");
const LOGIN = endpoint("/api/v1/auth/login");

/** Ingen är inloggad: kontot ger 401 och förnyelsen avslås. */
function utloggad() {
  server.use(
    http.get(ME, () => problem(401, "Inloggning krävs.")),
    http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
  );
}

async function fyllIOchLoggaIn(anvandarnamn = "admin", losenord = "ett-losenord-12345") {
  await userEvent.type(await screen.findByLabelText("Användarnamn"), anvandarnamn);
  await userEvent.type(screen.getByLabelText("Lösenord"), losenord);
  await userEvent.click(screen.getByRole("button", { name: "Logga in" }));
}

describe("inloggningssidan", () => {
  it("visar fälten för användarnamn och lösenord för den som inte är inloggad", async () => {
    utloggad();

    renderApp("/login");

    expect(await screen.findByRole("heading", { name: "Inloggning" })).toBeInTheDocument();
    expect(screen.getByLabelText("Användarnamn")).toBeInTheDocument();
    expect(screen.getByLabelText("Lösenord")).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "Logga in" })).toBeEnabled();
  });

  it("hämtar kontot innan formuläret visas, eftersom det anropet sätter CSRF-cookien", async () => {
    let svara: () => void = () => undefined;
    const vantar = new Promise<void>((resolve) => {
      svara = resolve;
    });
    server.use(
      http.get(ME, async () => {
        await vantar;
        return problem(401, "Inloggning krävs.");
      }),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
    );

    renderApp("/login");

    expect(screen.getByRole("status")).toHaveTextContent("Laddar");
    expect(screen.queryByLabelText("Användarnamn")).not.toBeInTheDocument();
    svara();
    expect(await screen.findByLabelText("Användarnamn")).toBeInTheDocument();
  });

  it("skickar den som redan är inloggad bort från inloggningen", async () => {
    server.use(http.get(ME, () => HttpResponse.json(accountInfo())));

    const { router } = renderApp("/login");

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("skickar ett inloggat konto som måste byta lösenord vidare till lösenordsbytet", async () => {
    server.use(http.get(ME, () => HttpResponse.json(accountInfo({ mustChangePassword: true }))));

    const { router } = renderApp("/login");

    expect(await screen.findByRole("heading", { name: "Byt lösenord" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/change-password");
  });

  it("visar ett fel med möjlighet att försöka igen när kontot inte går att hämta", async () => {
    let anrop = 0;
    server.use(
      http.get(ME, () => (++anrop === 1 ? problem(500, "Ett oväntat fel inträffade.") : problem(401, "Inloggning krävs."))),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
    );

    renderApp("/login");

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att hämta inloggningen");
    await userEvent.click(screen.getByRole("button", { name: "Försök igen" }));
    expect(await screen.findByLabelText("Användarnamn")).toBeInTheDocument();
  });

  it("ber om användarnamn och lösenord utan att skicka något när fälten är tomma", async () => {
    utloggad();
    // Ingen mock för /auth/login: ett anrop dit fäller testet.
    renderApp("/login");

    await userEvent.click(await screen.findByRole("button", { name: "Logga in" }));

    expect(await screen.findByText("Fyll i användarnamn.")).toBeInTheDocument();
    expect(screen.getByText("Fyll i lösenord.")).toBeInTheDocument();
    expect(screen.getByLabelText("Användarnamn")).toBeInvalid();
    expect(screen.getByLabelText("Användarnamn")).toHaveAccessibleDescription("Fyll i användarnamn.");
  });

  it("loggar in: skickar uppgifterna med CSRF-token, lägger kontot i cachen och visar sidan utan ny hämtning", async () => {
    let meAnrop = 0;
    let skickat: { body: unknown; csrf: string | null } | undefined;
    server.use(
      http.get(ME, () => {
        meAnrop++;
        return problem(401, "Inloggning krävs.");
      }),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
      http.post(LOGIN, async ({ request }) => {
        skickat = { body: await request.json(), csrf: request.headers.get("X-XSRF-TOKEN") };
        return HttpResponse.json(accountInfo({ role: "DOCUMENTER", holderName: "Berra Berglund" }));
      }),
    );
    const { router, services } = renderApp("/login");
    setCsrfCookie("token-fran-me");

    await fyllIOchLoggaIn("dokumenterare", "ett-losenord-12345");

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(skickat).toEqual({
      body: { username: "dokumenterare", password: "ett-losenord-12345" },
      csrf: "token-fran-me",
    });
    expect(services.queryClient.getQueryData(ME_KEY)).toEqual(
      accountInfo({ role: "DOCUMENTER", holderName: "Berra Berglund" }),
    );
    // Kontot från inloggningen räcker. En ny hämtning skulle lämna cachen på "utloggad" tills den var klar.
    expect(meAnrop).toBe(1);
  });

  it("skickar ett konto som måste byta lösenord direkt till lösenordsbytet efter inloggningen", async () => {
    utloggad();
    server.use(http.post(LOGIN, () => HttpResponse.json(accountInfo({ mustChangePassword: true }))));
    const { router } = renderApp("/login");

    await fyllIOchLoggaIn();

    expect(await screen.findByRole("heading", { name: "Byt lösenord" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/change-password");
  });

  it("visar felet vid fel användarnamn eller lösenord och stannar på sidan", async () => {
    utloggad();
    server.use(http.post(LOGIN, () => problem(401, "Fel användarnamn eller lösenord.")));
    const { router } = renderApp("/login");

    await fyllIOchLoggaIn();

    expect(await screen.findByRole("alert")).toHaveTextContent("Fel användarnamn eller lösenord.");
    expect(router.state.location.pathname).toBe("/login");
    expect(screen.getByRole("button", { name: "Logga in" })).toBeEnabled();
    // Uppgifterna finns kvar, så att användaren kan rätta en felskrivning.
    expect(screen.getByLabelText("Användarnamn")).toHaveValue("admin");
  });

  it("visar hur länge spärren varar vid för många försök", async () => {
    utloggad();
    server.use(http.post(LOGIN, () => tooManyRequests(900)));
    renderApp("/login");

    await fyllIOchLoggaIn();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "För många misslyckade försök. Försök igen om 15 minuter.",
    );
  });

  it("visar en egen text vid serverfel, utan serverns text", async () => {
    utloggad();
    server.use(http.post(LOGIN, () => problem(500, "NullPointerException i LoginService")));
    renderApp("/login");

    await fyllIOchLoggaIn();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Något gick fel hos servern");
    expect(alert).not.toHaveTextContent("NullPointerException");
  });

  it("visar ett fel och låter användaren försöka igen när servern inte går att nå", async () => {
    utloggad();
    server.use(http.post(LOGIN, () => HttpResponse.error()));
    renderApp("/login");

    await fyllIOchLoggaIn();

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att nå servern");
    expect(screen.getByRole("button", { name: "Logga in" })).toBeEnabled();
  });

  it("stänger av knappen medan inloggningen skickas, så att den inte kan skickas två gånger", async () => {
    utloggad();
    let anrop = 0;
    server.use(
      http.post(LOGIN, async () => {
        anrop++;
        await delay(150);
        return problem(401, "Fel användarnamn eller lösenord.");
      }),
    );
    renderApp("/login");

    await fyllIOchLoggaIn();

    const knapp = await screen.findByRole("button", { name: "Loggar in…" });
    expect(knapp).toBeDisabled();
    await userEvent.click(knapp);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Logga in" })).toBeEnabled();
    expect(anrop).toBe(1);
  });

  it("tar bort det gamla felet när användaren försöker igen", async () => {
    utloggad();
    let anrop = 0;
    server.use(
      http.post(LOGIN, async () => {
        anrop++;
        if (anrop === 1) {
          return problem(401, "Fel användarnamn eller lösenord.");
        }
        await delay(150);
        return HttpResponse.json(accountInfo());
      }),
    );
    renderApp("/login");
    await fyllIOchLoggaIn();
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Logga in" }));

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
