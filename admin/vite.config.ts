import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Lokalt proxar Vite /api till backenden, så att webbläsaren ser ett enda ursprung och cookies
// (Secure, SameSite=Strict, __Host-) fungerar. I drift ligger /api bakom samma värd, se docs/08-frontend.md.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    // https, så att Secure-cookies (CSRF-cookien) går att sätta och läsa i testerna.
    environmentOptions: { jsdom: { url: "https://localhost:5173" } },
    setupFiles: ["./src/test/setup.ts"],
    restoreMocks: true,
  },
});
