import { useMutation } from "@tanstack/react-query";

import { outcome } from "../api/errors";
import { ME_KEY, useApi, useServices } from "../app/services";

/**
 * Loggar ut: rensar cookies hos backenden och gör kontot till "utloggat", varpå `RequireSession` skickar användaren
 * till inloggningen. Övrig cachad data tas bort, så att nästa användare i samma webbläsare inte ser den.
 * Misslyckas anropet förblir användaren inloggad, och felet finns i `error`.
 */
export function useLogout() {
  const api = useApi();
  const { queryClient } = useServices();
  return useMutation({
    mutationFn: async () => {
      const result = await outcome(() => api.POST("/api/v1/auth/logout"));
      if (!result.ok) {
        throw new Error(result.failure.message);
      }
    },
    onSuccess: () => {
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_KEY[0] });
      queryClient.setQueryData(ME_KEY, null);
    },
  });
}
