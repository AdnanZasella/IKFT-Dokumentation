package se.ikft.member;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Svaren från medlemsregistret.
 */
final class MemberResponses {

	private MemberResponses() {
	}

	/** En rad i listan. Vad som ingår beror på rollen, se {@link MemberService#list}. */
	sealed interface ListItem permits AdminListItem, Summary {

	}

	/** Det Dokumenteraren ser: bara namn och åldersgrupp. */
	record Summary(UUID id, String firstName, String lastName, AgeGroup ageGroup) implements ListItem {
	}

	record AdminListItem(UUID id, String firstName, String lastName, LocalDate birthDate, String homeMunicipality,
			MemberStatus status, AgeGroup ageGroup) implements ListItem {
	}

	record Details(UUID id, String firstName, String lastName, LocalDate birthDate, String address,
			String homeMunicipality, LocalDate memberSince, MemberStatus status, boolean guardianConsent,
			AgeGroup ageGroup, List<FifsFeeInfo> fifsFees, List<RoleInfo> roles) {
	}

	/** Betald moskéavgift via FIFS för ett år. */
	record FifsFeeInfo(int year, LocalDate paidOn) {
	}

	record RoleInfo(UUID id, String title, LocalDate startDate, LocalDate endDate) {
	}

}
