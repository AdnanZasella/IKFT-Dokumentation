package se.ikft.member;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Det som skickas för att lägga till eller uppdatera en medlem. Gränserna är samma som kolumnerna i
 * {@code member}. Att födelsedatumet inte ligger i framtiden kontrolleras i {@link MemberService}, som har
 * den injicerade klockan.
 *
 * @param guardianConsent utelämnas den räknas den som falsk
 * @param status bara vid uppdatering: ändrar medlemmen mellan aktiv och inaktiv. Utelämnas den ändras
 * ingenting. Vid skapande ignoreras den, en ny medlem är alltid aktiv.
 */
record MemberRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
		@NotNull LocalDate birthDate, @Size(max = 255) String address, @NotBlank @Size(max = 100) String homeMunicipality,
		LocalDate memberSince, Boolean guardianConsent, MemberStatus status) {
}
