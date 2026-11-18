import { useState } from "react";
import { useForm } from "react-hook-form";

import { outcome } from "../api/errors";
import { ME_KEY, useApi, useServices } from "../app/services";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/TextField";
import { AuthLayout } from "./AuthLayout";

interface LoginForm {
  username: string;
  password: string;
}

/**
 * Inloggning med användarnamn och lösenord. Sidan nås bara av den som inte är inloggad, se `RedirectIfSignedIn`.
 * Efter en lyckad inloggning läggs kontot i cachen, och då skickar `RedirectIfSignedIn` vidare. Kontot cachas
 * för alltid (`staleTime: Infinity`), så en ny hämtning vore inte bara onödig: tills den var klar skulle
 * cachen fortfarande säga "utloggad".
 */
export function LoginPage() {
  const api = useApi();
  const { queryClient } = useServices();
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>();

  async function onSubmit(values: LoginForm) {
    setFailure(null);
    const result = await outcome(() => api.POST("/api/v1/auth/login", { body: values }));
    if (!result.ok) {
      setFailure(result.failure.message);
      return;
    }
    queryClient.setQueryData(ME_KEY, result.data);
  }

  return (
    <AuthLayout title="Inloggning">
      <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="flex flex-col gap-4">
        {failure ? <Alert>{failure}</Alert> : null}
        <TextField
          label="Användarnamn"
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          error={errors.username?.message}
          {...register("username", { required: "Fyll i användarnamn." })}
        />
        <TextField
          label="Lösenord"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password", { required: "Fyll i lösenord." })}
        />
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Loggar in…" : "Logga in"}
        </Button>
      </form>
    </AuthLayout>
  );
}
