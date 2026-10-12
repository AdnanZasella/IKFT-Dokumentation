package se.ikft.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gränserna avvisar stora värden innan de bearbetas. Lösenordets längd kontrolleras inte mot
 * lösenordsreglerna här, eftersom ett för långt lösenord ska ge samma svar som ett fel lösenord.
 */
record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 256) String password) {
}
