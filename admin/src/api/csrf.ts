/** Cookien som backenden sätter (se SecurityConfig.CSRF_COOKIE_NAME). Den är bara läsbar över HTTPS och localhost. */
export const CSRF_COOKIE = "__Host-XSRF-TOKEN";

/** Huvudet som backenden förväntar sig på anrop som ändrar data. */
export const CSRF_HEADER = "X-XSRF-TOKEN";

/** Hämtar värdet för en cookie ur en cookiesträng (som `document.cookie`), eller `undefined` om den saknas. */
export function parseCookie(cookies: string, name: string): string | undefined {
  for (const part of cookies.split(";")) {
    const separator = part.indexOf("=");
    if (separator === -1) {
      continue;
    }
    if (part.slice(0, separator).trim() === name) {
      const value = part.slice(separator + 1).trim();
      try {
        return decodeURIComponent(value);
      } catch {
        return value;
      }
    }
  }
  return undefined;
}

/** Läser CSRF-token ur cookien. Anropas vid varje anrop, eftersom inloggningen byter token. */
export function readCsrfToken(): string | undefined {
  return parseCookie(document.cookie, CSRF_COOKIE);
}

/** Anrop som bara läser data kräver ingen CSRF-token. */
export function isMutating(method: string): boolean {
  return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method.toUpperCase());
}
