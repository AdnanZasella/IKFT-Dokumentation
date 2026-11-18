import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";

/** Visas medan kontot hämtas (`GET /auth/me`). Har en landmärkesregion, så att skärmläsare har något att utgå från. */
export function SessionPending() {
  return (
    <main className="mx-auto max-w-sm px-4 py-10">
      <p role="status">Laddar…</p>
    </main>
  );
}

/** Visas när kontot inte gick att hämta, till exempel vid nätverksfel. Då vet vi inte om användaren är inloggad. */
export function SessionFailed({ onRetry }: { onRetry: () => void }) {
  return (
    <main className="mx-auto flex max-w-sm flex-col gap-3 px-4 py-10">
      <Alert>Det gick inte att hämta inloggningen. Kontrollera anslutningen.</Alert>
      <Button variant="secondary" onClick={onRetry}>
        Försök igen
      </Button>
    </main>
  );
}
