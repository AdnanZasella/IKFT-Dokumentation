import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";

/** Visas medan kontot hämtas (`GET /auth/me`). */
export function SessionPending() {
  return <p role="status">Laddar…</p>;
}

/** Visas när kontot inte gick att hämta, till exempel vid nätverksfel. Då vet vi inte om användaren är inloggad. */
export function SessionFailed({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="mx-auto flex max-w-sm flex-col gap-3 px-4 py-10">
      <Alert>Det gick inte att hämta inloggningen. Kontrollera anslutningen.</Alert>
      <Button variant="secondary" onClick={onRetry}>
        Försök igen
      </Button>
    </div>
  );
}
