import type { ComponentProps } from "react";

interface ButtonProps extends ComponentProps<"button"> {
  /** `primary` för huvudhandlingen i ett formulär, `secondary` för övriga. */
  variant?: "primary" | "secondary";
}

const VARIANTS = {
  primary: "border-accent bg-accent text-white enabled:hover:bg-accent-strong",
  secondary: "border-line bg-surface text-ink enabled:hover:bg-paper",
} as const;

export function Button({ variant = "primary", className, type = "button", ...button }: ButtonProps) {
  return (
    <button
      {...button}
      type={type}
      className={`rounded-sm border px-4 py-2 text-base font-medium disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className ?? ""}`}
    />
  );
}
