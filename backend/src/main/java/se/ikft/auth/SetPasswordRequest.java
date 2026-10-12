package se.ikft.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import se.ikft.account.UserAccount;

/**
 * Gränserna avvisar stora värden innan de bearbetas. Användarnamnet får vara lika långt som i
 * databasen och koden har marginal för blanksteg och bindestreck.
 */
record SetPasswordRequest(@NotBlank @Size(max = UserAccount.MAX_USERNAME_LENGTH) String username,
		@NotBlank @Size(max = 32) String code, @NotBlank @ValidPassword String newPassword) {
}
