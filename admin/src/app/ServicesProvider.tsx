import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { ServicesContext, type Services } from "./services";

export function ServicesProvider({ services, children }: { services: Services; children: ReactNode }) {
  return (
    <ServicesContext value={services}>
      <QueryClientProvider client={services.queryClient}>{children}</QueryClientProvider>
    </ServicesContext>
  );
}
