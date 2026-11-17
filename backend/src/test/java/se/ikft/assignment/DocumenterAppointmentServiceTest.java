package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import se.ikft.MutableClock;
import se.ikft.account.HolderChangeService;
import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.audit.AuditAction;
import se.ikft.audit.AuditEntity;
import se.ikft.audit.AuditService;
import se.ikft.common.config.TimeConfig;
import se.ikft.common.error.BadRequestException;
import se.ikft.common.error.ConflictException;
import se.ikft.common.error.NotFoundException;
import se.ikft.member.Member;
import se.ikft.member.MemberRepository;
import se.ikft.member.MemberStatus;

/**
 * Tillfälliga uppdrag och innehavarkontrollen med en klocka som testet styr och förfalskade databaser. Att
 * flera körningar och samtidiga anrop inte räknar upp tokenversionen i onödan testas mot en riktig databas i
 * {@link HolderSyncIntegrationTest}.
 * <p>
 * Stockholm är UTC+1 på vintern och UTC+2 på sommaren, så lokal midnatt är 23:00 UTC (vinter) respektive
 * 22:00 UTC (sommar) kvällen före.
 */
class DocumenterAppointmentServiceTest {

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static final UUID BERRA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static final UUID INAKTIV = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	private final MutableClock clock = new MutableClock(Instant.parse("2026-06-15T10:00:00Z"));

	private final DocumenterAssignmentRepository repository = mock(DocumenterAssignmentRepository.class);

	private final MemberRepository members = mock(MemberRepository.class);

	private final UserAccountRepository accounts = mock(UserAccountRepository.class);

	private final HolderChangeService holderChanges = mock(HolderChangeService.class);

	private final AuditService audit = mock(AuditService.class);

	private final UserAccount konto = mock(UserAccount.class);

	private final List<DocumenterAssignment> uppdrag = new ArrayList<>();

	private final DocumenterAppointmentService service = new DocumenterAppointmentService(repository, members,
			accounts, holderChanges, audit, clock);

	DocumenterAppointmentServiceTest() {
		when(repository.findByEndDateGreaterThanEqual(any())).thenAnswer(
				call -> uppdrag.stream().filter(u -> !u.getEndDate().isBefore(call.getArgument(0))).toList());
		when(repository.save(any())).thenAnswer(call -> {
			DocumenterAssignment sparat = call.getArgument(0);
			uppdrag.add(sparat);
			return sparat;
		});
		when(accounts.findForUpdateByRole(Role.DOCUMENTER)).thenReturn(Optional.of(konto));
		medlem(ANNA, MemberStatus.ACTIVE);
		medlem(BERRA, MemberStatus.ACTIVE);
		medlem(INAKTIV, MemberStatus.INACTIVE);
	}

	// ---- syncHolder: årsskifte

	@Test
	void sista31DecemberKlockan2359ArInnehavarenFortfarandeDenOrdinarie() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));
		flyttaTill("2026-12-31T22:59:59Z");

		service.syncHolder();

		verify(holderChanges).changeHolder(konto, medlemmar.get(ANNA));
		verify(holderChanges, never()).clearHolder(any());
	}

	@Test
	void fran1JanuariKlockan0000TarKontotBortInnehavarenNarIngetUppdragFinns() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));
		flyttaTill("2026-12-31T23:00:00Z");

		service.syncHolder();

		verify(holderChanges).clearHolder(konto);
		verify(holderChanges, never()).changeHolder(any(), any());
	}

	@Test
	void ettUppdragForNastaArBorjarGallaPa1JanuariKlockan0000() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));
		uppdrag.add(ordinarie(BERRA, LocalDate.of(2027, 1, 1)));

		flyttaTill("2026-12-31T22:59:59Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(ANNA));
		verify(holderChanges, never()).changeHolder(konto, medlemmar.get(BERRA));

		flyttaTill("2026-12-31T23:00:00Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(BERRA));
		verify(holderChanges, never()).clearHolder(any());
	}

	// ---- syncHolder: uppdrag som börjar och slutar

	@Test
	void ettUppdragSomLagtsIForvagBorjarGallaVidLokalMidnatt() {
		LocalDate start = LocalDate.of(2026, 6, 16);
		DocumenterAssignment anna = ordinarie(ANNA, LocalDate.of(2026, 1, 1));
		anna.endEarly(start.atStartOfDay(TimeConfig.ZONE).toInstant());
		uppdrag.add(anna);
		uppdrag.add(ordinarie(BERRA, start));

		// 15 juni 23:59:59 lokal tid (sommartid).
		flyttaTill("2026-06-15T21:59:59Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(ANNA));
		verify(holderChanges, never()).changeHolder(konto, medlemmar.get(BERRA));

		// 16 juni 00:00 lokal tid.
		flyttaTill("2026-06-15T22:00:00Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(BERRA));
	}

	@Test
	void ettTillfalligtUppdragGallerTillOchMedSlutdatumetOchDarefterArDetDenOrdinarie() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20),
				clock.instant()));

		// 20 juni 23:59:59 lokal tid.
		flyttaTill("2026-06-20T21:59:59Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(BERRA));
		verify(holderChanges, never()).changeHolder(konto, medlemmar.get(ANNA));

		// 21 juni 00:00 lokal tid: rollen går tillbaka till Anna.
		flyttaTill("2026-06-20T22:00:00Z");
		service.syncHolder();
		verify(holderChanges).changeHolder(konto, medlemmar.get(ANNA));
	}

	@Test
	void ettTillfalligtUppdragSomLoptUtUtanOrdinarieGerIngenInnehavare() {
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 14),
				clock.instant()));

		service.syncHolder();

		verify(holderChanges).clearHolder(konto);
		verify(holderChanges, never()).changeHolder(any(), any());
	}

	@Test
	void syncHolderRedovisarOmInnehavarenAndrades() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));
		when(holderChanges.changeHolder(konto, medlemmar.get(ANNA))).thenReturn(true, false);

		assertThat(service.syncHolder()).isTrue();
		assertThat(service.syncHolder()).isFalse();
	}

	@Test
	void syncHolderLasKontotDirektUtanAttForstLasaDetUtanLas() {
		service.syncHolder();

		verify(accounts).findForUpdateByRole(Role.DOCUMENTER);
		verify(accounts, never()).findByRole(any());
		verify(accounts, never()).findForUpdateById(any());
	}

	@Test
	void saknasDokumenterareKontotArDetEttServerfel() {
		when(accounts.findForUpdateByRole(Role.DOCUMENTER)).thenReturn(Optional.empty());

		assertThatThrownBy(service::syncHolder).isInstanceOf(IllegalStateException.class);
	}

	// ---- appointTemporary

	@Test
	void slutdatumGransvardenISammaDagOchNastaDag() {
		LocalDate idag = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> service.appointTemporary(BERRA, idag)).isInstanceOf(BadRequestException.class)
			.hasMessage(DocumenterAppointmentService.END_NOT_AFTER_TODAY);
		assertThatThrownBy(() -> service.appointTemporary(BERRA, idag.minusDays(1)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage(DocumenterAppointmentService.END_NOT_AFTER_TODAY);
		assertThat(uppdrag).isEmpty();

		var utsedd = service.appointTemporary(BERRA, idag.plusDays(1));

		assertThat(utsedd.type()).isEqualTo(AssignmentType.TEMPORARY);
		assertThat(utsedd.startDate()).isEqualTo(idag);
		assertThat(utsedd.endDate()).isEqualTo(idag.plusDays(1));
		assertThat(utsedd.created()).isTrue();
	}

	@Test
	void idagArEttDatumIFoereningensTidszonOchInteIUtc() {
		// 15 juni 00:30 lokal tid är 14 juni 22:30 UTC.
		flyttaTill("2026-06-14T22:30:00Z");

		assertThatThrownBy(() -> service.appointTemporary(BERRA, LocalDate.of(2026, 6, 15)))
			.isInstanceOf(BadRequestException.class);
		assertThat(service.appointTemporary(BERRA, LocalDate.of(2026, 6, 16)).startDate())
			.isEqualTo(LocalDate.of(2026, 6, 15));
	}

	@Test
	void ettTillfalligtUppdragByterInnehavareDirektOchLoggas() {
		uppdrag.add(ordinarie(ANNA, LocalDate.of(2026, 1, 1)));

		service.appointTemporary(BERRA, LocalDate.of(2026, 6, 20));

		verify(holderChanges).changeHolder(konto, medlemmar.get(BERRA));
		verify(audit).record(eq(AuditEntity.DOCUMENTER_ASSIGNMENT), any(), eq(AuditAction.CREATED), any());
	}

	@Test
	void ettTillfalligtUppdragSomOverlapparEttPagaendeGer409() {
		uppdrag.add(DocumenterAssignment.temporary(ANNA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15),
				clock.instant()));

		// Det pågående slutar i dag, och det nya börjar i dag.
		assertThatThrownBy(() -> service.appointTemporary(BERRA, LocalDate.of(2026, 6, 16)))
			.isInstanceOf(ConflictException.class)
			.hasMessage(DocumenterAppointmentService.TEMPORARY_EXISTS);
		assertThat(uppdrag).hasSize(1);
		verify(holderChanges, never()).changeHolder(any(), any());
	}

	@Test
	void ettTillfalligtUppdragSomAvslutatsIDagStoppararInteEttNytt() {
		DocumenterAssignment avslutat = DocumenterAssignment.temporary(ANNA, LocalDate.of(2026, 6, 10),
				LocalDate.of(2026, 6, 30), clock.instant());
		avslutat.endEarly(LocalDate.of(2026, 6, 15).atStartOfDay(TimeConfig.ZONE).toInstant());
		uppdrag.add(avslutat);

		assertThat(service.appointTemporary(BERRA, LocalDate.of(2026, 6, 20)).created()).isTrue();
	}

	@Test
	void inaktivOchOkandMedlemGer400() {
		assertThatThrownBy(() -> service.appointTemporary(INAKTIV, LocalDate.of(2026, 6, 20)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage(DocumenterAppointmentService.INACTIVE_MEMBER);
		assertThatThrownBy(() -> service.appointTemporary(UUID.randomUUID(), LocalDate.of(2026, 6, 20)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage(DocumenterAppointmentService.UNKNOWN_MEMBER);
		assertThat(uppdrag).isEmpty();
	}

	// ---- endTemporary

	@Test
	void ettAvslutIForvagGerRollenTillbakaTillDenOrdinarie() {
		DocumenterAssignment anna = ordinarie(ANNA, LocalDate.of(2026, 1, 1));
		DocumenterAssignment berra = DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10),
				LocalDate.of(2026, 6, 30), clock.instant());
		uppdrag.add(anna);
		uppdrag.add(berra);

		service.endTemporary();

		assertThat(berra.getEndedAt()).isEqualTo(LocalDate.of(2026, 6, 15)
			.atStartOfDay(TimeConfig.ZONE)
			.toInstant());
		assertThat(berra.isActiveOn(LocalDate.of(2026, 6, 15))).isFalse();
		assertThat(anna.getEndedAt()).isNull();
		verify(holderChanges).changeHolder(konto, medlemmar.get(ANNA));
		verify(audit).record(eq(AuditEntity.DOCUMENTER_ASSIGNMENT), eq(berra.getId()), eq(AuditAction.UPDATED), any());
	}

	@Test
	void ettAvslutUtanOrdinarieUppdragTarBortInnehavaren() {
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 30),
				clock.instant()));

		service.endTemporary();

		verify(holderChanges).clearHolder(konto);
	}

	@Test
	void ettAvslutUtanTillfalligtUppdragGer404OchAndrarInget() {
		DocumenterAssignment anna = ordinarie(ANNA, LocalDate.of(2026, 1, 1));
		uppdrag.add(anna);

		assertThatThrownBy(service::endTemporary).isInstanceOf(NotFoundException.class)
			.hasMessage(DocumenterAppointmentService.NO_TEMPORARY);

		assertThat(anna.getEndedAt()).isNull();
		verify(holderChanges, never()).changeHolder(any(), any());
		verify(holderChanges, never()).clearHolder(any());
	}

	@Test
	void ettTillfalligtUppdragSomRedanLoptUtGerInteNagotAttAvsluta() {
		uppdrag.add(DocumenterAssignment.temporary(BERRA, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14),
				clock.instant()));

		assertThatThrownBy(service::endTemporary).isInstanceOf(NotFoundException.class);
	}

	// ---- Hjälpmetoder

	private final Map<UUID, Member> medlemmar = new HashMap<>();

	private void medlem(UUID id, MemberStatus status) {
		Member medlem = mock(Member.class);
		when(medlem.getId()).thenReturn(id);
		when(medlem.getStatus()).thenReturn(status);
		medlemmar.put(id, medlem);
		when(members.findById(id)).thenReturn(Optional.of(medlem));
	}

	private DocumenterAssignment ordinarie(UUID medlem, LocalDate start) {
		return DocumenterAssignment.ordinary(medlem, start, clock.instant());
	}

	private void flyttaTill(String instant) {
		clock.advance(Duration.between(clock.instant(), Instant.parse(instant)));
	}

}
