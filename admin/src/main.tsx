import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";

import { routes } from "./app/routes";
import { ServicesProvider } from "./app/ServicesProvider";
import { createServices } from "./app/services";

const services = createServices();
const router = createBrowserRouter(routes);

const root = document.getElementById("root");
if (!root) {
  throw new Error("Elementet #root saknas i index.html");
}

createRoot(root).render(
  <StrictMode>
    <ServicesProvider services={services}>
      <RouterProvider router={router} />
    </ServicesProvider>
  </StrictMode>,
);
