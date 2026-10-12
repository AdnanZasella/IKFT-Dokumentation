package se.ikft.auth;

import jakarta.validation.constraints.NotNull;

/**
 * Fälten valideras bara för att finnas. Ett tomt eller för långt användarnamn eller lösenord ska ge samma
 * svar som ett fel lösenord, så de kontrolleras i {@link LoginService}.
 */
record LoginRequest(@NotNull String username, @NotNull String password) {
}
