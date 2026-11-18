import { useState } from "react";
import { useForm } from "react-hook-form";

import { outcome } from "../api/errors";
import { ME_KEY, useApi, useServices } from "../app/services";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { TextField } from "../ui/TextField";
import { AuthLayout } from "./AuthLayout";
import { useLogout } from "./useLogout";

interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/**
 * Det tvingade lösenordsbytet: den enda sidan ett konto kommer åt tills lösenordet är bytt (efter ett
 * innehavarbyte), se `RequireSession`. Utloggning finns kvar, eftersom den som inte vet det gamla lösenordet
 * annars inte kan lämna sidan.
 *
 * Valideringen i backenden är sanningen. Klienten kontrollerar bara att fälten är ifyllda och att det nya
 * lösenordet skrevs lika två gånger. Längd och övriga regler visas ur backendens svar.
 */
export function ChangePasswordPage() {
  const api = useApi();
  const { queryClient } = useServices();
  const logout = useLogout();
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    getValues,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordForm>();

  async function onSubmit({ currentPassword, newPassword }: ChangePasswordForm) {
    setFailure(null);
    const result = await outcome(() =>
      api.POST("/api/v1/auth/change-password", { body: { currentPassword, newPassword } }),
    );
    if (!result.ok) {
      const { message, fields } = result.failure;
      let shownOnField = false;
      for (const name of ["currentPassword", "newPassword"] as const) {
        const fieldMessage = fields[name];
        if (fieldMessage) {
          setError(name, { type: "server", message: fieldMessage });
          shownOnField = true;
        }
      }
      if (!shownOnField) {
        setFailure(message);
      }
      return;
    }
    // Kontot i cachen har inte längre flaggan, och då skickar RequireSession användaren vidare.
    queryClient.setQueryData(ME_KEY, result.data);
  }

  return (
    <AuthLayout title="Byt lösenord">
      <p className="text-sm text-muted">
        Lösenordet måste bytas innan du kan använda appen. Välj ett nytt lösenord som bara du känner till.
      </p>
      <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="flex flex-col gap-4">
        {failure ? <Alert>{failure}</Alert> : null}
        <TextField
          label="Nuvarande lösenord"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register("currentPassword", { required: "Fyll i det nuvarande lösenordet." })}
        />
        <TextField
          label="Nytt lösenord"
          type="password"
          autoComplete="new-password"
          error={errors.newPassword?.message}
          {...register("newPassword", {
            required: "Fyll i ett nytt lösenord.",
            // Upprepningen kontrolleras om när det här fältet ändras, så att ett gammalt "inte lika" inte blir kvar.
            deps: ["confirmPassword"],
          })}
        />
        <TextField
          label="Upprepa nytt lösenord"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register("confirmPassword", {
            required: "Upprepa det nya lösenordet.",
            validate: (value) => value === getValues("newPassword") || "Lösenorden är inte lika.",
          })}
        />
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Byter lösenord…" : "Byt lösenord"}
        </Button>
      </form>
      <div className="flex flex-col gap-2 border-t border-line pt-4">
        {logout.isError ? <Alert>{logout.error.message}</Alert> : null}
        <Button variant="secondary" onClick={() => logout.mutate()} disabled={logout.isPending}>
          Logga ut
        </Button>
      </div>
    </AuthLayout>
  );
}
