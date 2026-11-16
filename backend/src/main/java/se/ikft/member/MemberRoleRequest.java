package se.ikft.member;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * En föreningsroll under en period. Slutdatumet utelämnas medan rollen pågår.
 */
record MemberRoleRequest(@NotBlank @Size(max = 100) String title, @NotNull LocalDate startDate, LocalDate endDate) {
}
