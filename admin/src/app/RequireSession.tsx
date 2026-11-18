import { Navigate, Outlet } from "react-router";

import { useSession } from "./session";
import { SessionFailed, SessionPending } from "./SessionStatus";

/**
 * Släpper bara in inloggade. En utloggad skickas till inloggningen, och ett konto som måste byta lösenord
 * skickas till lösenordsbytet och kommer inte åt något annat tills flaggan är nollställd. Det är bara en hjälp:
 * backenden stoppar anropen oavsett.
 *
 * Med `passwordChange` är det i stället sidan för det tvingade lösenordsbytet som skyddas, och bara ett konto
 * som måste byta lösenord släpps in dit.
 */
export function RequireSession({ passwordChange = false }: { passwordChange?: boolean }) {
  const session = useSession();

  if (session.isPending) {
    return <SessionPending />;
  }
  if (session.isError) {
    return <SessionFailed onRetry={() => void session.refetch()} />;
  }
  if (session.data === null) {
    return <Navigate to="/login" replace />;
  }
  if (session.data.mustChangePassword && !passwordChange) {
    return <Navigate to="/change-password" replace />;
  }
  if (!session.data.mustChangePassword && passwordChange) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
