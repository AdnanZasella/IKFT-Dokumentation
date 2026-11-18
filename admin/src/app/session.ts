import { useQuery } from "@tanstack/react-query";

import type { AccountInfo } from "../api/types";
import { ME_KEY, useApi } from "./services";

/** Det inloggade kontot, eller `null` när ingen är inloggad. */
export type Session = AccountInfo | null;

/**
 * Appen börjar varje sidladdning med `GET /auth/me`: det sätter CSRF-cookien och visar om användaren är inloggad.
 * Klienten har då redan försökt förnya sessionen om svaret var 401, så ett 401 här betyder utloggad.
 */
export function useSession() {
  const api = useApi();
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<Session> => {
      const { data, response } = await api.GET("/api/v1/auth/me");
      if (data) {
        return data;
      }
      if (response.status === 401) {
        return null;
      }
      throw new Error(`GET /auth/me svarade ${String(response.status)}`);
    },
    staleTime: Infinity,
  });
}
