import { type ComponentProps, useId } from "react";

interface TextFieldProps extends ComponentProps<"input"> {
  label: string;
  /** Felet för fältet. Kopplas till fältet så att skärmläsare läser upp det. */
  error?: string;
}

/** Ett textfält med synlig etikett. Etiketten och felet är kopplade till fältet, så att tester och skärmläsare hittar dem. */
export function TextField({ label, error, id, className, ...input }: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = `${inputId}-error`;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <input
        {...input}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`rounded-sm border bg-surface px-3 py-2 text-base ${error ? "border-danger" : "border-line"} ${className ?? ""}`}
      />
      {error ? (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
