import type { ReactNode } from "react";

/** Ramen runt sidorna som visas före själva appen: inloggning och tvingat lösenordsbyte. */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-6 px-4 py-10">
      <header className="flex flex-col gap-1">
        <p className="text-sm text-muted">Islamiska Kulturföreningen i Trollhättan</p>
        <h1 className="text-2xl font-semibold">{title}</h1>
      </header>
      {children}
    </main>
  );
}
