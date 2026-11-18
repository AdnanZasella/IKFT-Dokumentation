import type { ReactNode } from "react";

/** Ett fel för hela sidan eller formuläret. `role="alert"` gör att skärmläsare läser upp det direkt. */
export function Alert({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="rounded-sm border border-danger bg-danger-surface px-3 py-2 text-sm text-danger">
      {children}
    </div>
  );
}
