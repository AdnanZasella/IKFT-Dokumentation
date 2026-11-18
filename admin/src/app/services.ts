import { QueryClient } from "@tanstack/react-query";
import { createContext, use } from "react";

import { type ApiClient, createApiClient } from "../api/client";

/** Cache-nyckeln för det inloggade kontot (`GET /auth/me`). `null` betyder utloggad. */
export const ME_KEY = ["me"] as const;

export interface Services {
  api: ApiClient;
  queryClient: QueryClient;
}

/**
 * Skapar klienten och cachen som hör ihop. Appen har en uppsättning, och varje test skapar sin egen, så att
 * ingen cache läcker mellan testerna.
 */
export function createServices(options: { baseUrl?: string; fetch?: typeof globalThis.fetch } = {}): Services {
  const queryClient = new QueryClient({
    defaultOptions: {
      // Backenden är sanningen. Ett nytt försök av ett misslyckat anrop döljer bara felet, och en förfrågan
      // som ändrar data får aldrig göras om av sig själv.
      queries: { retry: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
  const api = createApiClient({
    ...options,
    // Sessionen är slut: gör kontot till "utloggat", och RequireSession skickar användaren till inloggningen.
    onSessionExpired: () => {
      queryClient.setQueryData(ME_KEY, null);
    },
    // Hämta kontot på nytt, så att flaggan mustChangePassword syns och RequireSession skickar användaren
    // till lösenordsbytet.
    onMustChangePassword: () => {
      void queryClient.invalidateQueries({ queryKey: ME_KEY });
    },
  });
  return { api, queryClient };
}

export const ServicesContext = createContext<Services | null>(null);

export function useServices(): Services {
  const services = use(ServicesContext);
  if (!services) {
    throw new Error("useServices måste användas inom ServicesProvider");
  }
  return services;
}

export function useApi(): ApiClient {
  return useServices().api;
}
