package se.ikft.member;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Svaren från medlemsregistret. {@code @Schema(name)} ger klasserna tydliga namn i OpenAPI-beskrivningen.
 */
final class MemberResponses {

	private MemberResponses() {
	}

	/** En rad i listan. Vad som ingår beror på rollen, se {@link MemberService#list}. */
	@Schema(name = "MemberListItem", oneOf = { Summary.class, AdminListItem.class })
	sealed interface ListItem permits AdminListItem, Summary {

	}

	/** Det Dokumenteraren ser: bara namn och åldersgrupp. */
	@Schema(name = "MemberSummary")
	record Summary(UUID id, String firstName, String lastName, AgeGroup ageGroup) implements ListItem {
	}

	@Schema(name = "MemberAdminListItem")
	record AdminListItem(UUID id, String firstName, String lastName, LocalDate birthDate, String homeMunicipality,
			MemberStatus status, AgeGroup ageGroup) implements ListItem {
	}

	@Schema(name = "MemberDetails")
	record Details(UUID id, String firstName, String lastName, LocalDate birthDate,
			@Schema(nullable = true) String address, String homeMunicipality,
			@Schema(nullable = true) LocalDate memberSince, MemberStatus status, boolean guardianConsent,
			AgeGroup ageGroup, List<FifsFeeInfo> fifsFees, List<RoleInfo> roles) {
	}

	/** Betald moskéavgift via FIFS för ett år. */
	@Schema(name = "MemberFifsFee")
	record FifsFeeInfo(int year, LocalDate paidOn) {
	}

	@Schema(name = "MemberRole")
	record RoleInfo(UUID id, String title, LocalDate startDate, @Schema(nullable = true) LocalDate endDate) {
	}

}
