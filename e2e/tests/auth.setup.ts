import { test as setup } from "@playwright/test";

import { ApiSession, type Playwright } from "../support/api";
import { ADMIN, DOKUMENTERARE, type Konto } from "../support/konton";

// Inloggning görs en gång per roll och sparas som storageState (docs/07-teststrategi.md). Ett test går i stället
// igenom inloggningen i gränssnittet: forsta-inloggningen.spec.ts. Tester som behöver en roll väljer filen med
// test.use({ storageState: ... }).

async function sparaInloggning(konto: Konto, playwright: Playwright) {
  const session = await ApiSession.loggaIn(playwright, konto);
  await session.request.storageState({ path: konto.sparadInloggning });
  await session.avsluta();
}

setup("logga in som admin och spara inloggningen", async ({ playwright }) => {
  await sparaInloggning(ADMIN, playwright);
});

setup("logga in som dokumenterare och spara inloggningen", async ({ playwright }) => {
  await sparaInloggning(DOKUMENTERARE, playwright);
});
