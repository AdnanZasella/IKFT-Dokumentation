package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import se.ikft.MutableClock;
import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.assignment.DocumenterAssignmentService.ActiveHolder;

/**
 * Tjänsten med en klocka som testet styr och en förfalskad databas.
 */
class DocumenterAssignmentServiceTest {

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static final UUID BERRA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	/** Stockholm är UTC+1 på vintern, så 11:00 UTC är 12:00 lokal tid. */
	private final MutableClock clock = new MutableClock(Instant.parse("2026-06-15T10:00:00Z"));

	private final DocumenterAssignmentRepository repository = mock(DocumenterAssignmentRepository.class);

	private final List<DocumenterAssignment> uppdrag = new ArrayList<>();

	private DocumenterAssignmentService service = tjanst(MonthDay.of(12, 1));

	DocumenterAssignmentServiceTest() {
		when(repository.findByEndDateGreaterThanEqual(any())).thenAnswer(
				call -> uppdrag.stream().filter(u -> !u.getEndDate().isBefore(call.getArgument(0))).toList());
		when(repository.findMemberName(ANNA)).thenReturn(java.util.Optional.of("Anna Andersson"));
		when(repository.findMemberName(BERRA)).thenReturn(java.util.Optional.of("Bertil Berg"));
	}

	@Test
	void inagetUppdragArSattGerTomt() {
		assertThat(service.current()).isEmpty();
	}

	@Test
	void ordinarieUppdragGerInnehavareTypOchSlutdatum() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));

		assertThat(service.current()).contains(
				new ActiveHolder(ANNA, "Anna Andersson", AssignmentType.ORDINARY, LocalDate.of(2026, 12, 31)));
	}

	@Test
	void sista31DecemberGallerUppdragetFortfarandeOchFran1JanuariArDetObestamt() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));

		// 31 december 23:30 lokal tid (22:30 UTC).
		flyttaTill("2026-12-31T22:30:00Z");
		assertThat(service.current()).isPresent();

		// En timme senare, 1 januari 00:30 lokal tid, är uppdraget obestämt igen.
		flyttaTill("2026-12-31T23:30:00Z");
		assertThat(service.current()).isEmpty();
	}

	@Test
	void ettTillfalligtUppdragGarForeEttOrdinarie() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20),
				clock.instant()));

		assertThat(service.current()).contains(
				new ActiveHolder(BERRA, "Bertil Berg", AssignmentType.TEMPORARY, LocalDate.of(2026, 6, 20)));
	}

	@Test
	void ettTillfalligtUppdragUpphorVidSittSlutdatum() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20),
				clock.instant()));

		// Sista dagen, 20 juni 23:30 lokal tid (21:30 UTC, sommartid UTC+2).
		flyttaTill("2026-06-20T21:30:00Z");
		assertThat(service.current()).map(ActiveHolder::memberId).contains(BERRA);

		// Efter midnatt, 21 juni 00:30 lokal tid, är det den ordinarie igen.
		flyttaTill("2026-06-20T22:30:00Z");
		assertThat(service.current()).map(ActiveHolder::memberId).contains(ANNA);
	}

	@Test
	void ettAvslutatUppdragGallerInteLangre() {
		var avslutat = DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant());
		avslutat.endEarly(clock.instant());
		uppdrag.add(avslutat);

		assertThat(service.current()).isEmpty();
	}

	@Test
	void nastaArArInteUtsettNarBaraDetPagaendeArFinns() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));

		assertThat(service.nextYearAssigned()).isFalse();
	}

	@Test
	void nastaArArUtsettNarEttOrdinarieUppdragFinnsForDet() {
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));
		uppdrag.add(DocumenterAssignment.ordinary(BERRA, LocalDate.of(2027, 1, 1), clock.instant()));

		assertThat(service.nextYearAssigned()).isTrue();
	}

	@Test
	void ettTillfalligtUppdragForNastaArRaknasInteSomUtsett() {
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 10),
				clock.instant()));

		assertThat(service.nextYearAssigned()).isFalse();
	}

	@Test
	void paminnelsenKommerFranOchMed1DecemberOmNastaArInteArUtsett() {
		flyttaTill("2026-11-30T22:30:00Z");
		assertThat(service.reminderDue(false)).isFalse();

		// 30 november 23:30 lokal tid, fortfarande före datumet.
		flyttaTill("2026-11-30T22:59:00Z");
		assertThat(service.reminderDue(false)).isFalse();

		// 1 december 00:00 lokal tid.
		flyttaTill("2026-11-30T23:00:00Z");
		assertThat(service.reminderDue(false)).isTrue();

		flyttaTill("2026-12-31T12:00:00Z");
		assertThat(service.reminderDue(false)).isTrue();
	}

	@Test
	void ingenPaminnelseNarNastaArRedanArUtsett() {
		flyttaTill("2026-12-15T12:00:00Z");

		assertThat(service.reminderDue(true)).isFalse();
	}

	@Test
	void ingenPaminnelseMittIAretForeDatumet() {
		flyttaTill("2026-01-15T12:00:00Z");
		assertThat(service.reminderDue(false)).isFalse();

		flyttaTill("2026-06-15T12:00:00Z");
		assertThat(service.reminderDue(false)).isFalse();
	}

	@Test
	void paminnelsedatumetArKonfigurerbart() {
		service = tjanst(MonthDay.of(10, 15));

		flyttaTill("2026-10-14T12:00:00Z");
		assertThat(service.reminderDue(false)).isFalse();

		flyttaTill("2026-10-15T12:00:00Z");
		assertThat(service.reminderDue(false)).isTrue();
	}

	@Test
	void adminFarAlltidRegistreraMenDokumenterareBaraMedAktivtUppdrag() {
		UserAccount admin = konto(Role.ADMIN);
		UserAccount dokumenterare = konto(Role.DOCUMENTER);

		assertThat(service.canRegister(admin)).isTrue();
		assertThat(service.canRegister(dokumenterare)).isFalse();

		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));

		assertThat(service.canRegister(admin)).isTrue();
		assertThat(service.canRegister(dokumenterare)).isTrue();
	}

	@Test
	void dokumenterareFarInteRegistreraEfterArsskiftetUtanNyttUppdrag() {
		UserAccount dokumenterare = konto(Role.DOCUMENTER);
		uppdrag.add(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 1, 1), clock.instant()));

		flyttaTill("2027-01-01T10:00:00Z");

		assertThat(service.canRegister(dokumenterare)).isFalse();
		assertThat(service.canRegister(konto(Role.ADMIN))).isTrue();

		// Ett nytt uppdrag för det nya året gör att Dokumenterare får registrera igen.
		uppdrag.add(DocumenterAssignment.ordinary(BERRA, LocalDate.of(2027, 1, 1), clock.instant()));
		assertThat(service.canRegister(dokumenterare)).isTrue();
	}

	@Test
	void ettTillfalligtUppdragGerRegistreringsratt() {
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20),
				clock.instant()));

		assertThat(service.canRegister(konto(Role.DOCUMENTER))).isTrue();
	}

	private DocumenterAssignmentService tjanst(MonthDay paminnelse) {
		return new DocumenterAssignmentService(repository, new AssignmentProperties(paminnelse), clock);
	}

	private void flyttaTill(String instant) {
		clock.advance(Duration.between(clock.instant(), Instant.parse(instant)));
	}

	private static UserAccount konto(Role roll) {
		UserAccount konto = mock(UserAccount.class);
		when(konto.getRole()).thenReturn(roll);
		return konto;
	}

}
