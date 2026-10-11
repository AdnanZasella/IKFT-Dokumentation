package se.ikft.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gränserna avvisar stora värden innan de bearbetas. Användarnamnet får vara lika långt som i
 * databasen och koden har marginal för blanksteg och bindestreck.
 */
record SetPasswordRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 32) String code,
		@NotBlank @ValidPassword String newPassword) {
}
