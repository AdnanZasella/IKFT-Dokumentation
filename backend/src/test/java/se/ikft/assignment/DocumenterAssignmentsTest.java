package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Den rena logiken för vem som är aktiv dokumenterare ett datum, utan Spring och databas.
 */
class DocumenterAssignmentsTest {

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static final UUID BERRA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static final Instant NU = Instant.parse("2026-01-01T08:00:00Z");

	@Test
	void inagetUppdragGerTomt() {
		assertThat(DocumenterAssignments.activeOn(List.of(), LocalDate.of(2026, 6, 1))).isEmpty();
	}

	@Test
	void ordinarieUppdragGallerTillOchMed31December() {
		var uppdrag = List.of(ordinarie(ANNA, "2026-01-01"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 12, 30))).isPresent();
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 12, 31))).isPresent();
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2027, 1, 1))).isEmpty();
	}

	@Test
	void ordinarieUppdragGallerInteForeStartdatumet() {
		var uppdrag = List.of(ordinarie(ANNA, "2026-06-15"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 14))).isEmpty();
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 15))).isPresent();
	}

	@Test
	void ordinarieUppdragSlutarAlltidSamma31DecemberOavsettStartdatum() {
		assertThat(ordinarie(ANNA, "2026-01-01").lastActiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(ordinarie(ANNA, "2026-12-31").lastActiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(ordinarie(ANNA, "2027-03-01").lastActiveDate()).isEqualTo(LocalDate.of(2027, 12, 31));
	}

	@Test
	void ettUppdragSomBorjar31DecemberGallerEnDag() {
		var uppdrag = List.of(ordinarie(ANNA, "2026-12-31"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 12, 31))).isPresent();
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2027, 1, 1))).isEmpty();
	}

	@Test
	void ettUppdragSomBorjar1JanuariNastaArGallerFran1Januari() {
		var uppdrag = List.of(ordinarie(ANNA, "2027-01-01"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 12, 31))).isEmpty();
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2027, 1, 1))).isPresent();
	}

	@Test
	void ettTillfalligtUppdragGarForeEttOrdinarie() {
		var ordinarie = ordinarie(ANNA, "2026-01-01");
		var tillfalligt = tillfalligt(BERRA, "2026-06-01", "2026-06-10");

		var aktivt = DocumenterAssignments.activeOn(List.of(ordinarie, tillfalligt), LocalDate.of(2026, 6, 5));

		assertThat(aktivt).containsSame(tillfalligt);
		// Ordningen i listan spelar ingen roll.
		assertThat(DocumenterAssignments.activeOn(List.of(tillfalligt, ordinarie), LocalDate.of(2026, 6, 5)))
			.containsSame(tillfalligt);
	}

	@Test
	void ettTillfalligtUppdragUpphorEfterSittSlutdatum() {
		var ordinarie = ordinarie(ANNA, "2026-01-01");
		var tillfalligt = tillfalligt(BERRA, "2026-06-01", "2026-06-10");
		var uppdrag = List.of(ordinarie, tillfalligt);

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 5, 31))).containsSame(ordinarie);
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 1))).containsSame(tillfalligt);
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 10))).containsSame(tillfalligt);
		// Dagen efter slutdatumet återgår rollen till den ordinarie.
		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 11))).containsSame(ordinarie);
	}

	@Test
	void ettTillfalligtUppdragUtanOrdinarieLamnarIngetEfterSlutdatumet() {
		var uppdrag = List.of(tillfalligt(BERRA, "2026-06-01", "2026-06-10"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2026, 6, 11))).isEmpty();
	}

	@Test
	void ettTillfalligtUppdragKanGaOverArsskiftet() {
		var uppdrag = List.of(ordinarie(ANNA, "2026-01-01"), tillfalligt(BERRA, "2026-12-20", "2027-01-10"));

		assertThat(DocumenterAssignments.activeOn(uppdrag, LocalDate.of(2027, 1, 2)))
			.map(DocumenterAssignment::getMemberId)
			.contains(BERRA);
	}

	@Test
	void gallerTvaOrdinarieGallerDetSomBorjadeSenast() {
		var gammalt = ordinarie(ANNA, "2026-01-01");
		var nytt = ordinarie(BERRA, "2026-06-01");

		assertThat(DocumenterAssignments.activeOn(List.of(gammalt, nytt), LocalDate.of(2026, 7, 1)))
			.containsSame(nytt);
		assertThat(DocumenterAssignments.activeOn(List.of(nytt, gammalt), LocalDate.of(2026, 7, 1)))
			.containsSame(nytt);
		// Före det nyas start är det gamla det som gäller.
		assertThat(DocumenterAssignments.activeOn(List.of(gammalt, nytt), LocalDate.of(2026, 5, 31)))
			.containsSame(gammalt);
	}

	@Test
	void ettUppdragSomAvslutatsIForvagGallerInteFranOchMedAvslutsdagen() {
		var uppdrag = ordinarie(ANNA, "2026-01-01");
		uppdrag.endEarly(Instant.parse("2026-03-10T12:00:00Z"));

		assertThat(DocumenterAssignments.activeOn(List.of(uppdrag), LocalDate.of(2026, 3, 9))).isPresent();
		assertThat(DocumenterAssignments.activeOn(List.of(uppdrag), LocalDate.of(2026, 3, 10))).isEmpty();
		assertThat(DocumenterAssignments.activeOn(List.of(uppdrag), LocalDate.of(2026, 3, 11))).isEmpty();
		assertThat(uppdrag.lastActiveDate()).isEqualTo(LocalDate.of(2026, 3, 9));
	}

	@Test
	void avslutsdagenRaknasIForeningensTidszon() {
		// 23:30 UTC den 9 mars är 00:30 den 10 mars i Stockholm (UTC+1), så uppdraget gäller sista gången den 9:e.
		var vinter = ordinarie(ANNA, "2026-01-01");
		vinter.endEarly(Instant.parse("2026-03-09T23:30:00Z"));
		assertThat(vinter.lastActiveDate()).isEqualTo(LocalDate.of(2026, 3, 9));

		// 22:30 UTC den 9 mars är 23:30 den 9 mars i Stockholm, så den 9:e är avslutsdagen.
		var tidigare = ordinarie(ANNA, "2026-01-01");
		tidigare.endEarly(Instant.parse("2026-03-09T22:30:00Z"));
		assertThat(tidigare.lastActiveDate()).isEqualTo(LocalDate.of(2026, 3, 8));
	}

	@Test
	void ettUppdragSomAvslutatsForeStartenHarAldrigGallt() {
		var uppdrag = ordinarie(ANNA, "2026-06-01");
		uppdrag.endEarly(Instant.parse("2026-03-01T12:00:00Z"));

		assertThat(DocumenterAssignments.activeOn(List.of(uppdrag), LocalDate.of(2026, 6, 1))).isEmpty();
		assertThat(DocumenterAssignments.activeOn(List.of(uppdrag), LocalDate.of(2026, 3, 1))).isEmpty();
		assertThat(DocumenterAssignments.hasOrdinaryDuring(List.of(uppdrag), LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31)))
			.isFalse();
	}

	@Test
	void avslutaIForvagTvaGangerBehallerForstaTidpunkten() {
		var uppdrag = ordinarie(ANNA, "2026-01-01");
		uppdrag.endEarly(Instant.parse("2026-03-10T12:00:00Z"));
		uppdrag.endEarly(Instant.parse("2026-09-01T12:00:00Z"));

		assertThat(uppdrag.getEndedAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
	}

	@Test
	void avslutaIForvagIgenMedEnTidigareTidpunktFlyttarAvslutetTillbaka() {
		var uppdrag = ordinarie(ANNA, "2026-01-01");

		assertThat(uppdrag.endEarly(Instant.parse("2026-09-01T12:00:00Z"))).isTrue();
		assertThat(uppdrag.endEarly(Instant.parse("2026-03-10T12:00:00Z"))).isTrue();

		assertThat(uppdrag.getEndedAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
		assertThat(uppdrag.lastActiveDate()).isEqualTo(LocalDate.of(2026, 3, 9));
	}

	@Test
	void avslutaIForvagMedSammaEllerSenareTidpunktAndrarInteOchRapporterarDet() {
		var uppdrag = ordinarie(ANNA, "2026-01-01");
		uppdrag.endEarly(Instant.parse("2026-03-10T12:00:00Z"));

		assertThat(uppdrag.endEarly(Instant.parse("2026-03-10T12:00:00Z"))).isFalse();
		assertThat(uppdrag.endEarly(Instant.parse("2026-03-10T12:00:01Z"))).isFalse();

		assertThat(uppdrag.getEndedAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
	}

	@Test
	void ettAvslutatOrdinarieUppdragErsattsAvEttNyttSomBorjarSammaDag() {
		var gammalt = ordinarie(ANNA, "2026-01-01");
		gammalt.endEarly(Instant.parse("2026-03-10T12:00:00Z"));
		var nytt = ordinarie(BERRA, "2026-03-10");

		assertThat(DocumenterAssignments.activeOn(List.of(gammalt, nytt), LocalDate.of(2026, 3, 9)))
			.containsSame(gammalt);
		assertThat(DocumenterAssignments.activeOn(List.of(gammalt, nytt), LocalDate.of(2026, 3, 10)))
			.containsSame(nytt);
	}

	@Test
	void slutdatumForeStartdatumKanInteSkapas() {
		assertThatThrownBy(() -> tillfalligt(BERRA, "2026-06-10", "2026-06-09"))
			.isInstanceOf(IllegalArgumentException.class);
		// Samma dag är en dags uppdrag.
		assertThat(tillfalligt(BERRA, "2026-06-10", "2026-06-10").isActiveOn(LocalDate.of(2026, 6, 10))).isTrue();
	}

	@Test
	void ordinarieUppdragForNastaArRaknasForNastaAr() {
		var uppdrag = List.of(ordinarie(ANNA, "2027-01-01"));

		assertThat(DocumenterAssignments.hasOrdinaryDuring(uppdrag, LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 12, 31)))
			.isTrue();
	}

	@Test
	void ettOrdinarieUppdragSomBorjarSenareNastaArRaknasAven() {
		var uppdrag = List.of(ordinarie(ANNA, "2027-06-01"));

		assertThat(DocumenterAssignments.hasOrdinaryDuring(uppdrag, LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 12, 31)))
			.isTrue();
	}

	@Test
	void ettOrdinarieUppdragForIArRaknasInteForNastaAr() {
		var uppdrag = List.of(ordinarie(ANNA, "2026-01-01"));

		assertThat(DocumenterAssignments.hasOrdinaryDuring(uppdrag, LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 12, 31)))
			.isFalse();
	}

	@Test
	void ettTillfalligtUppdragRaknasInteSomUtsedd() {
		var uppdrag = List.of(tillfalligt(BERRA, "2027-01-01", "2027-01-10"));

		assertThat(DocumenterAssignments.hasOrdinaryDuring(uppdrag, LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 12, 31)))
			.isFalse();
	}

	@Test
	void ettAvslutatUppdragForNastaArRaknasInte() {
		var uppdrag = ordinarie(ANNA, "2027-01-01");
		uppdrag.endEarly(Instant.parse("2026-11-20T12:00:00Z"));

		assertThat(DocumenterAssignments.hasOrdinaryDuring(List.of(uppdrag), LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 12, 31)))
			.isFalse();
	}

	private static DocumenterAssignment ordinarie(UUID medlem, String start) {
		return DocumenterAssignment.ordinary(medlem, LocalDate.parse(start), NU);
	}

	private static DocumenterAssignment tillfalligt(UUID medlem, String start, String slut) {
		return DocumenterAssignment.temporary(medlem, LocalDate.parse(start), LocalDate.parse(slut), NU);
	}

}
