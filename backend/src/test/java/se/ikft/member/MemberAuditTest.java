package se.ikft.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import se.ikft.audit.FieldChange;

class MemberAuditTest {

	@Test
	void ingenAndringGerTomLista() {
		Map<String, String> fran = Map.of("firstName", "Prova", "birthDate", "2010-05-01");

		assertThat(MemberAudit.diff(fran, Map.of("firstName", "Prova", "birthDate", "2010-05-01"))).isEmpty();
	}

	@Test
	void faltSomPaverkarBidragetLoggasMedVarden() {
		List<FieldChange> andringar = MemberAudit.diff(
				Map.of("birthDate", "2010-05-01", "homeMunicipality", "Trollhättan", "status", "ACTIVE"),
				Map.of("birthDate", "2008-05-01", "homeMunicipality", "Vänersborg", "status", "INACTIVE"));

		assertThat(andringar).containsExactlyInAnyOrder(
				FieldChange.withValues("birthDate", "2010-05-01", "2008-05-01"),
				FieldChange.withValues("homeMunicipality", "Trollhättan", "Vänersborg"),
				FieldChange.withValues("status", "ACTIVE", "INACTIVE"));
	}

	@Test
	void namnOchAdressLoggasBaraSomAndradeUtanVarden() {
		List<FieldChange> andringar = MemberAudit.diff(
				Map.of("firstName", "Gammal", "lastName", "Gammalsson", "address", "Gamla gatan 1"),
				Map.of("firstName", "Ny", "lastName", "Nysson", "address", "Nya gatan 2"));

		assertThat(andringar).containsExactlyInAnyOrder(FieldChange.named("firstName"),
				FieldChange.named("lastName"), FieldChange.named("address"));
		assertThat(andringar).allSatisfy(andring -> {
			assertThat(andring.before()).isNull();
			assertThat(andring.after()).isNull();
		});
	}

	@Test
	void ettBorttagetFaltLoggasSomAndratOchEttNyttMedVarde() {
		List<FieldChange> andringar = MemberAudit.diff(Map.of("address", "Gamla gatan 1"),
				Map.of("birthDate", "2010-05-01"));

		assertThat(andringar).containsExactlyInAnyOrder(FieldChange.named("address"),
				FieldChange.withValues("birthDate", null, "2010-05-01"));
	}

	@Test
	void snapshotUtelamnarFaltUtanVarde() {
		Member member = Member.create(new Member.Details("Prova", "Provsson", java.time.LocalDate.of(2010, 5, 1), null,
				"Trollhättan", null, false), java.time.Instant.parse("2026-01-01T00:00:00Z"));

		assertThat(MemberAudit.snapshot(member)).containsOnlyKeys("firstName", "lastName", "birthDate",
				"homeMunicipality", "guardianConsent", "status");
	}

}
