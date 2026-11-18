import { render } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";

import { routes } from "../app/routes";
import { ServicesProvider } from "../app/ServicesProvider";
import { createServices } from "../app/services";

/** Renderar appen med egen cache och klient, på en given adress. Anropen tas emot av MSW, se `server`. */
export function renderApp(initialPath = "/") {
  const services = createServices();
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  render(
    <ServicesProvider services={services}>
      <RouterProvider router={router} />
    </ServicesProvider>,
  );
  return { router, services };
}
