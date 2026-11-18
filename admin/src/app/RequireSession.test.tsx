import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { accountInfo, endpoint, passwordChangeRequired, problem } from "../test/mocks";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";
import { ME_KEY } from "./services";

const ME = endpoint("/api/v1/auth/me");
const REFRESH = endpoint("/api/v1/auth/refresh");

describe("appens start", () => {
  it("hämtar kontot först, och visar sidan för en inloggad", async () => {
    server.use(http.get(ME, () => HttpResponse.json(accountInfo())));

    renderApp("/");

    expect(screen.getByRole("status")).toHaveTextContent("Laddar");
    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
  });

  it("skickar en utloggad till inloggningen när förnyelsen också avslås", async () => {
    server.use(
      http.get(ME, () => problem(401, "Inloggning krävs.")),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
    );

    const { router } = renderApp("/");

    expect(await screen.findByRole("heading", { name: "Inloggning" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it("förnyar sessionen och visar sidan när åtkomsttoken har gått ut men förnyelsen gäller", async () => {
    let anrop = 0;
    server.use(
      http.get(ME, () => (++anrop === 1 ? problem(401, "Inloggning krävs.") : HttpResponse.json(accountInfo()))),
      http.post(REFRESH, () => HttpResponse.json(accountInfo())),
    );

    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
  });

  it("skickar ett konto som måste byta lösenord till lösenordsbytet, från vilken sida som helst", async () => {
    server.use(http.get(ME, () => HttpResponse.json(accountInfo({ mustChangePassword: true }))));

    const { router } = renderApp("/");

    expect(await screen.findByRole("heading", { name: "Byt lösenord" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/change-password");
  });

  it("släpper inte in ett konto som inte måste byta lösenord på sidan för det tvingade lösenordsbytet", async () => {
    server.use(http.get(ME, () => HttpResponse.json(accountInfo())));

    renderApp("/change-password");

    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
  });

  it("skickar en utloggad till inloggningen också från lösenordsbytet", async () => {
    server.use(
      http.get(ME, () => problem(401, "Inloggning krävs.")),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
    );

    renderApp("/change-password");

    expect(await screen.findByRole("heading", { name: "Inloggning" })).toBeInTheDocument();
  });

  it("visar inloggningen utan att hämta kontot", async () => {
    // Ingen mock för /auth/me: ett anrop dit fäller testet (onUnhandledFrame: "error").
    renderApp("/login");

    expect(await screen.findByRole("heading", { name: "Inloggning" })).toBeInTheDocument();
  });

  it("skickar inte en användare till inloggningen när förnyelsen bara är otillgänglig", async () => {
    let refreshAnrop = 0;
    let meAnrop = 0;
    server.use(
      http.get(ME, () => (++meAnrop <= 1 ? problem(401, "Inloggning krävs.") : HttpResponse.json(accountInfo()))),
      http.post(REFRESH, () => (++refreshAnrop === 1 ? problem(500, "Ett oväntat fel inträffade.") : HttpResponse.json(accountInfo()))),
    );

    const { router } = renderApp("/");

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att hämta inloggningen");
    expect(router.state.location.pathname).toBe("/");
    await userEvent.click(screen.getByRole("button", { name: "Försök igen" }));
    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
  });

  it("visar ett fel med möjlighet att försöka igen när kontot inte går att hämta", async () => {
    let anrop = 0;
    server.use(
      http.get(ME, () => (++anrop === 1 ? problem(500, "Ett oväntat fel inträffade.") : HttpResponse.json(accountInfo()))),
    );

    renderApp("/");

    expect(await screen.findByRole("alert")).toHaveTextContent("Det gick inte att hämta inloggningen");
    await userEvent.click(screen.getByRole("button", { name: "Försök igen" }));
    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();
  });
});

describe("under pågående session", () => {
  it("skickar användaren till lösenordsbytet när ett anrop får 403 med mustChangePassword", async () => {
    let flaggaSatt = false;
    server.use(
      http.get(ME, () => {
        // Första svaret: ingen flagga. Efter ett innehavarbyte har backenden satt den.
        return HttpResponse.json(accountInfo({ mustChangePassword: flaggaSatt }));
      }),
      http.get(endpoint("/api/v1/members"), () => passwordChangeRequired()),
    );
    const { services, router } = renderApp("/");
    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();

    flaggaSatt = true;
    await services.api.GET("/api/v1/members");

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/change-password");
    });
    expect(await screen.findByRole("heading", { name: "Byt lösenord" })).toBeInTheDocument();
  });

  it("skickar användaren till inloggningen när sessionen går ut under ett anrop", async () => {
    server.use(
      http.get(ME, () => HttpResponse.json(accountInfo())),
      http.get(endpoint("/api/v1/members"), () => problem(401, "Inloggning krävs.")),
      http.post(REFRESH, () => problem(401, "Förnyelsen gäller inte längre.")),
    );
    const { services, router } = renderApp("/");
    expect(await screen.findByRole("heading", { name: "Översikt" })).toBeInTheDocument();

    await services.api.GET("/api/v1/members");

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/login");
    });
    expect(services.queryClient.getQueryData(ME_KEY)).toBeNull();
  });
});
