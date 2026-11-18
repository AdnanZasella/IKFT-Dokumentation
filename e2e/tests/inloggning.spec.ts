import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

import { ADMIN } from "../support/konton";

test("inloggningssidan har inga tillgänglighetsfel", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Inloggning" })).toBeVisible();

  const resultat = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();

  expect(resultat.violations).toEqual([]);
});

test.describe("med sparad inloggning som admin", () => {
  test.use({ storageState: ADMIN.sparadInloggning });

  test("kommer in på startsidan och skickas bort från inloggningen", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Översikt" })).toBeVisible();

    await page.goto("/login");

    await expect(page.getByRole("heading", { name: "Översikt" })).toBeVisible();
    await expect(page).toHaveURL(/\/$/);
  });
});
