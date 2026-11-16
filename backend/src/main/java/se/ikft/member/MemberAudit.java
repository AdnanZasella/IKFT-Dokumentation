package se.ikft.member;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import se.ikft.audit.FieldChange;

/**
 * Vad som loggas när en medlem ändras. Bara fälten i {@link #LOGGED_WITH_VALUES} sparas med före- och
 * efter-värde, eftersom de avgör vem som räknas i bidragsunderlaget. Övriga fält, som namn och adress,
 * loggas bara som ändrade, så att loggen inte blir en andra kopia av medlemsregistret.
 */
final class MemberAudit {

	/** Fält som påverkar bidragssiffrorna. */
	static final Set<String> LOGGED_WITH_VALUES = Set.of("birthDate", "homeMunicipality", "status");

	private MemberAudit() {
	}

	/** Medlemmens loggade fält som text. Fält utan värde ingår inte. */
	static Map<String, String> snapshot(Member member) {
		Map<String, String> fields = new LinkedHashMap<>();
		put(fields, "firstName", member.getFirstName());
		put(fields, "lastName", member.getLastName());
		put(fields, "birthDate", member.getBirthDate());
		put(fields, "address", member.getAddress());
		put(fields, "homeMunicipality", member.getHomeMunicipality());
		put(fields, "memberSince", member.getMemberSince());
		put(fields, "guardianConsent", member.isGuardianConsent());
		put(fields, "status", member.getStatus());
		return fields;
	}

	/** De fält som skiljer sig mellan före och efter. Tomt om inget ändrats. */
	static List<FieldChange> diff(Map<String, String> before, Map<String, String> after) {
		Set<String> fields = new LinkedHashSet<>(after.keySet());
		fields.addAll(before.keySet());
		List<FieldChange> changes = new ArrayList<>();
		for (String field : fields) {
			String oldValue = before.get(field);
			String newValue = after.get(field);
			if (Objects.equals(oldValue, newValue)) {
				continue;
			}
			changes.add(LOGGED_WITH_VALUES.contains(field) ? FieldChange.withValues(field, oldValue, newValue)
					: FieldChange.named(field));
		}
		return changes;
	}

	private static void put(Map<String, String> fields, String name, Object value) {
		if (value != null) {
			fields.put(name, value.toString());
		}
	}

}
