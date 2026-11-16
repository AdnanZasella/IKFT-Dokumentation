package se.ikft.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Det nuvarande lösenordet har en övre gräns så att stora värden avvisas innan de bearbetas. Det nya
 * lösenordet följer samma regler som när lösenordet sattes med en engångskod.
 */
record ChangePasswordRequest(@NotBlank @Size(max = 256) String currentPassword,
		@NotBlank @ValidPassword String newPassword) {
}
