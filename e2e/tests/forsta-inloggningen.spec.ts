import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";

import { ApiSession, skapaMedlem, utseDokumenterare } from "../support/api";
import { ADMIN, DOKUMENTERARE } from "../support/konton";

const NYTT_LOSENORD = "e2e-nytt-losenord-1";

/** Samma regler som admin-appen ska uppfylla: WCAG 2.1 nivå AA. */
async function kontrolleraTillganglighet(page: Page) {
  const resultat = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(resultat.violations).toEqual([]);
}

test.describe("första inloggningen", () => {
  // Flödet gör att Dokumenterare-kontot byter innehavare och lösenord. Efteråt sätts allt tillbaka, så att testet kan
  // köras igen mot samma backend, och dokumenterarens sparade inloggning, som innehavarbytet gjorde ogiltig, skapas om.
  test.afterEach(async ({ playwright }) => {
    const oinloggad = await ApiSession.oinloggad(playwright);
    const svar = await oinloggad.post("/api/v1/auth/login", {
      username: DOKUMENTERARE.anvandarnamn,
      password: NYTT_LOSENORD,
    });
    if (svar.ok()) {
      const aterstallning = await oinloggad.post("/api/v1/auth/change-password", {
        currentPassword: NYTT_LOSENORD,
        newPassword: DOKUMENTERARE.losenord,
      });
      expect(aterstallning.ok(), "Det gick inte att sätta tillbaka dokumenterarens lösenord").toBe(true);
      await oinloggad.request.storageState({ path: DOKUMENTERARE.sparadInloggning });
    }
    await oinloggad.avsluta();
  });

  test("logga in, tvingas byta lösenord och kommer in", async ({ page, playwright }) => {
    // Förberedelse: Admin utser en ny dokumenterare. Då byts innehavaren och kontot måste byta lösenord.
    const admin = await ApiSession.loggaIn(playwright, ADMIN);
    const unik = crypto.randomUUID().slice(0, 8);
    const medlemId = await skapaMedlem(admin, "Prov", `Förstagång-${unik}`);
    await utseDokumenterare(admin, medlemId);
    await admin.avsluta();

    await page.goto("/");
    // Utloggad skickas man till inloggningen.
    await expect(page.getByRole("heading", { name: "Inloggning" })).toBeVisible();

    await page.getByLabel("Användarnamn").fill(DOKUMENTERARE.anvandarnamn);
    await page.getByLabel("Lösenord").fill(DOKUMENTERARE.losenord);
    await page.getByRole("button", { name: "Logga in" }).click();

    // Det gamla lösenordet släpps in, men bara till lösenordsbytet.
    await expect(page.getByRole("heading", { name: "Byt lösenord" })).toBeVisible();
    await expect(page).toHaveURL(/\/change-password$/);
    await test.step("lösenordsbytet har inga tillgänglighetsfel", async () => {
      await kontrolleraTillganglighet(page);
    });

    await page.getByLabel("Nuvarande lösenord", { exact: true }).fill(DOKUMENTERARE.losenord);
    await page.getByLabel("Nytt lösenord", { exact: true }).fill(NYTT_LOSENORD);
    await page.getByLabel("Upprepa nytt lösenord", { exact: true }).fill(NYTT_LOSENORD);
    await page.getByRole("button", { name: "Byt lösenord" }).click();

    // Nu kommer man in.
    await expect(page.getByRole("heading", { name: "Översikt" })).toBeVisible();
    await expect(page).toHaveURL(/\/$/);

    // Och sidan kommer ihåg inloggningen vid omladdning.
    await page.reload();
    await expect(page.getByRole("heading", { name: "Översikt" })).toBeVisible();
  });
});
