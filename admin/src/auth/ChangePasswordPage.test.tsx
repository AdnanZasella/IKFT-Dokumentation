import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { ME_KEY } from "../app/services";
import { accountInfo, endpoint, problem, setCsrfCookie, tooManyRequests } from "../test/mocks";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

const ME = endpoint("/api/v1/auth/me");
const CHANGE = endpoint("/api/v1/auth/change-password");
const LOGOUT = endpoint("/api/v1/auth/logout");

/** Ett konto som måste byta lösenord, till exempel efter ett innehavarbyte. */
function masteByta() {
  server.use(http.get(ME, () => HttpResponse.json(accountInfo({ role: "DOCUMENTER", mustChangePassword: true }))));
}

async function fyllIFalten(nuvarande: string, nytt: string, upprepat = nytt) {
  await userEvent.type(await screen.findByLabelText("Nuvarande lösenord"), nuvarande);
  await userEvent.type(screen.getByLabelText("Nytt lösenord"), nytt);
  await userEvent.type(screen.getByLabelText("Upprepa nytt lösenord"), upprepat);
}

async function bytLosenord() {
  await userEvent.click(screen.getByRole("button", { name: "Byt lösenord" }));
}

describe("det tvingade lösenordsbytet", () => {
  it("förklarar varför, och visar bara lösenordsbyte och utloggning", async () => {
    masteByta();

    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Byt lösenord" })).toBeInTheDocument();
    expect(screen.getByText(/måste bytas innan du kan använda appen/)).toBeInTheDocument();
    expect(screen.getByLabelText("Nuvarande lösenord")).toHaveAttribute("autocomplete", "current-password");
    expect(screen.getByLabelText("Nytt lösenord")).toHaveAttribute("autocomplete", "new-password");
    expect(screen.getByLabelText("Upprepa nytt lösenord")).toHaveAttribute("type", "password");
    expect(screen.getAllByRole("button").map((knapp) => knapp.textContent)).toEqual(["Byt lösenord", "Logga ut"]);
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });

  it("byter lösenordet: skickar rätt fält med CSRF-token och släpper in användaren på startsidan", async () => {
    masteByta();
    let skickat: { body: unknown; csrf: string | null } | undefined;
    server.use(
      http.post(CHANGE, async ({ request }) => {
        skickat = { body: await request.json(), csrf: request.headers.get("X-XSRF-TOKEN") };
        return HttpResponse.json(accountInfo({ role: "DOCUMENTER", mustChangePassword: false }));
      }),
    );
    const { router, services } = renderApp("/change-password");
    setCsrfCookie("csrf-token");

    await fyllIFalten("det-gamla-losenordet", "ett-helt-nytt-losenord");
    await bytLosenord();

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    // Bara de två fälten backenden känner till. Upprepningen är en hjälp i formuläret.
    expect(skickat).toEqual({
      body: { currentPassword: "det-gamla-losenordet", newPassword: "ett-helt-nytt-losenord" },
      csrf: "csrf-token",
    });
    expect(services.queryClient.getQueryData(ME_KEY)).toEqual(
      accountInfo({ role: "DOCUMENTER", mustChangePassword: false }),
    );
  });

  it("ber om alla tre fälten utan att skicka något när de är tomma", async () => {
    masteByta();
    // Ingen mock för change-password: ett anrop dit fäller testet.
    renderApp("/change-password");
    await screen.findByRole("heading", { name: "Byt lösenord" });

    await bytLosenord();

    expect(await screen.findByText("Fyll i det nuvarande lösenordet.")).toBeInTheDocument();
    expect(screen.getByText("Fyll i ett nytt lösenord.")).toBeInTheDocument();
    expect(screen.getByText("Upprepa det nya lösenordet.")).toBeInTheDocument();
  });

  it("stoppar ett nytt lösenord som inte skrevs lika två gånger", async () => {
    masteByta();
    renderApp("/change-password");

    await fyllIFalten("det-gamla-losenordet", "ett-helt-nytt-losenord", "ett-helt-nytt-losenordX");
    await bytLosenord();

    expect(await screen.findByText("Lösenorden är inte lika.")).toBeInTheDocument();
    expect(screen.getByLabelText("Upprepa nytt lösenord")).toBeInvalid();
  });

  it("tar bort 'inte lika' när det nya lösenordet rättas så att de stämmer", async () => {
    masteByta();
    renderApp("/change-password");
    await fyllIFalten("det-gamla-losenordet", "ett-helt-nytt-losenord", "ett-helt-nytt-losenordX");
    await bytLosenord();
    expect(await screen.findByText("Lösenorden är inte lika.")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Nytt lösenord"), "X");

    // Valideringen körs om asynkront.
    await waitFor(() => {
      expect(screen.queryByText("Lösenorden är inte lika.")).not.toBeInTheDocument();
    });
    expect(screen.getByLabelText("Upprepa nytt lösenord")).toBeValid();
  });

  it("visar backendens fel på rätt fält när det nya lösenordet inte duger", async () => {
    masteByta();
    server.use(
      http.post(CHANGE, () =>
        problem(400, "Begäran innehåller ogiltiga fält.", {
          errors: [{ field: "newPassword", message: "Lösenordet måste ha minst 12 tecken." }],
        }),
      ),
    );
    renderApp("/change-password");

    await fyllIFalten("det-gamla-losenordet", "kort");
    await bytLosenord();

    expect(await screen.findByText("Lösenordet måste ha minst 12 tecken.")).toBeInTheDocument();
    expect(screen.getByLabelText("Nytt lösenord")).toHaveAccessibleDescription("Lösenordet måste ha minst 12 tecken.");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Byt lösenord" })).toBeEnabled();
  });

  it("visar ett fel för hela formuläret när det nuvarande lösenordet är fel", async () => {
    masteByta();
    server.use(http.post(CHANGE, () => problem(400, "Det nuvarande lösenordet är fel.")));
    const { router } = renderApp("/change-password");

    await fyllIFalten("fel-losenord-helt", "ett-helt-nytt-losenord");
    await bytLosenord();

    expect(await screen.findByRole("alert")).toHaveTextContent("Det nuvarande lösenordet är fel.");
    expect(router.state.location.pathname).toBe("/change-password");
  });

  it("visar hur länge spärren varar när för många försök har gjorts", async () => {
    masteByta();
    server.use(http.post(CHANGE, () => tooManyRequests(120)));
    renderApp("/change-password");

    await fyllIFalten("fel-losenord-helt", "ett-helt-nytt-losenord");
    await bytLosenord();

    expect(await screen.findByRole("alert")).toHaveTextContent("Försök igen om 2 minuter.");
  });

  it("visar ett fel när servern inte går att nå", async () => {
    masteByta();
    server.use(http.post(CHANGE, () => HttpResponse.error()));
    renderApp("/change-password");

    await fyllIFalten("det-gamla-losenordet", "ett-helt-nytt-losenord");
    await bytLosenord();

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att nå servern");
  });

  it("stänger av knappen medan lösenordet byts, så att det inte kan skickas två gånger", async () => {
    masteByta();
    let anrop = 0;
    server.use(
      http.post(CHANGE, async () => {
        anrop++;
        await delay(150);
        return problem(400, "Det nuvarande lösenordet är fel.");
      }),
    );
    renderApp("/change-password");
    await fyllIFalten("det-gamla-losenordet", "ett-helt-nytt-losenord");
    await bytLosenord();

    const knapp = await screen.findByRole("button", { name: "Byter lösenord…" });
    expect(knapp).toBeDisabled();
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Byt lösenord" })).toBeEnabled();
    expect(anrop).toBe(1);
  });
});

describe("utloggning från lösenordsbytet", () => {
  it("loggar ut, rensar annan cachad data och skickar användaren till inloggningen", async () => {
    masteByta();
    let csrf: string | null = null;
    server.use(
      http.post(LOGOUT, ({ request }) => {
        csrf = request.headers.get("X-XSRF-TOKEN");
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router, services } = renderApp("/change-password");
    setCsrfCookie("csrf-token");
    services.queryClient.setQueryData(["members"], [{ name: "Någon" }]);
    await screen.findByRole("heading", { name: "Byt lösenord" });

    await userEvent.click(screen.getByRole("button", { name: "Logga ut" }));

    expect(await screen.findByRole("heading", { name: "Inloggning" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(csrf).toBe("csrf-token");
    expect(services.queryClient.getQueryData(ME_KEY)).toBeNull();
    expect(services.queryClient.getQueryData(["members"])).toBeUndefined();
  });

  it("visar ett fel och behåller användaren på sidan när utloggningen misslyckas", async () => {
    masteByta();
    server.use(http.post(LOGOUT, () => HttpResponse.error()));
    const { router } = renderApp("/change-password");

    await userEvent.click(await screen.findByRole("button", { name: "Logga ut" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att nå servern");
    expect(router.state.location.pathname).toBe("/change-password");
    expect(within(screen.getByRole("main")).getByRole("button", { name: "Logga ut" })).toBeEnabled();
  });
});
