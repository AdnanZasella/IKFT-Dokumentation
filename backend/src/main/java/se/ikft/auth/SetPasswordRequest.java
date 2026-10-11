package se.ikft.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Lösenordskraven kontrolleras i {@link SetupCodeService}, eftersom minsta längd är konfigurerbar.
 */
record SetPasswordRequest(@NotBlank String username, @NotBlank String code, @NotBlank String newPassword) {
}
