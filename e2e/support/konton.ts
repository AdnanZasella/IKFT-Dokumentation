import path from "node:path";

/** Admin-appen. Vite proxar /api till backenden, så testerna ser ett enda ursprung. */
export const BAS_URL = "http://localhost:5173";

export interface Konto {
  anvandarnamn: string;
  /** Det kända lösenordet som backendens profil e2e sätter (E2eDataInitializer i backend/src/test). */
  losenord: string;
  /** Där den sparade inloggningen (storageState) för kontot ligger. Skapas av tests/auth.setup.ts. */
  sparadInloggning: string;
}

const AUTH_MAPP = path.join(import.meta.dirname, "..", ".auth");

// Lösenorden är publika och gäller bara databasen som backenden startar för E2E-körningen. Samma värden finns i
// backend/src/test/java/se/ikft/e2e/E2eDataInitializer.java. Skiljer de sig åt fallerar inloggningen i auth.setup.ts.
export const ADMIN: Konto = {
  anvandarnamn: "admin",
  losenord: "e2e-admin-losenord-1",
  sparadInloggning: path.join(AUTH_MAPP, "admin.json"),
};

export const DOKUMENTERARE: Konto = {
  anvandarnamn: "dokumenterare",
  losenord: "e2e-dokumenterare-losenord-1",
  sparadInloggning: path.join(AUTH_MAPP, "dokumenterare.json"),
};
