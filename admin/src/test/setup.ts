import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { clearCsrfCookie } from "./mocks";
import { server } from "./server";

// Ett anrop som ingen har tänkt på ska fälla testet, inte gå ut på nätet.
beforeAll(() => {
  server.listen({ onUnhandledFrame: "error" });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  clearCsrfCookie();
});

afterAll(() => {
  server.close();
});
