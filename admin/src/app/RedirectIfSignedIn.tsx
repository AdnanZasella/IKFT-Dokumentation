import { Navigate, Outlet } from "react-router";

import { useSession } from "./session";
import { SessionFailed, SessionPending } from "./SessionStatus";

/**
 * Skyddar inloggningssidan: den som redan är inloggad skickas till startsidan (och därifrån vidare till
 * lösenordsbytet om kontot måste byta lösenord, se `RequireSession`).
 *
 * Kontot hämtas först, också för en utloggad. Det första anropet sätter CSRF-cookien, och inloggningen kräver den.
 * Formuläret visas därför inte förrän svaret finns.
 */
export function RedirectIfSignedIn() {
  const session = useSession();

  if (session.isPending) {
    return <SessionPending />;
  }
  if (session.isError) {
    return <SessionFailed onRetry={() => void session.refetch()} />;
  }
  if (session.data !== null) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
