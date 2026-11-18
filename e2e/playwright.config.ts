import { defineConfig, devices } from "@playwright/test";

import { BAS_URL } from "./support/konton";

/**
 * E2E mot den riktiga backenden och en riktig Postgres, aldrig mot mockar (docs/07-teststrategi.md, nivå 4).
 *
 * Backenden startas före testerna, med profilen e2e (kända lösenord och påhittade medlemmar):
 *   cd backend && ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=e2e
 * Playwright startar Vite för admin-appen, eller använder den som redan körs lokalt.
 */
export default defineConfig({
  testDir: "./tests",
  // En delad databas: ett arbetsflöde i taget tills testerna är isolerade per arbetare.
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: BAS_URL,
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
  projects: [
    // Loggar in en gång per roll och sparar inloggningen (storageState) i .auth/.
    { name: "setup", testMatch: /auth\.setup\.ts/ },
    {
      name: "skrivbord",
      use: { ...devices["Desktop Chrome"] },
      dependencies: ["setup"],
      testIgnore: /auth\.setup\.ts/,
    },
    {
      name: "mobil",
      use: { ...devices["Pixel 7"] },
      dependencies: ["setup"],
      testIgnore: /auth\.setup\.ts/,
    },
  ],
  webServer: {
    command: "npm run dev -- --port 5173 --strictPort",
    cwd: "../admin",
    url: BAS_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
