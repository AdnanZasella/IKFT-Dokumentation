package se.ikft.assignment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.HolderChangeService;
import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.audit.AuditAction;
import se.ikft.audit.AuditEntity;
import se.ikft.audit.AuditService;
import se.ikft.audit.FieldChange;
import se.ikft.common.config.TimeConfig;
import se.ikft.common.error.BadRequestException;
import se.ikft.common.error.ConflictException;
import se.ikft.common.error.NotFoundException;
import se.ikft.member.Member;
import se.ikft.member.MemberRepository;
import se.ikft.member.MemberStatus;

/**
 * Att utse dokumenterare. Läsningen av vem som är dokumenterare just nu finns i
 * {@link DocumenterAssignmentService}.
 */
@Service
public class DocumenterAppointmentService {

	static final String UNKNOWN_MEMBER = "Medlemmen finns inte.";

	static final String INACTIVE_MEMBER = "En inaktiv medlem kan inte utses.";

	static final String START_IN_THE_PAST = "Startdatumet kan inte ligga före i dag.";

	static final String START_TOO_FAR_AHEAD = "Startdatumet kan som senast vara i slutet av nästa år.";

	static final String END_NOT_AFTER_TODAY = "Slutdatumet måste ligga efter i dag.";

	static final String TEMPORARY_EXISTS = "Ett tillfälligt uppdrag pågår redan. Avsluta det först.";

	static final String NO_TEMPORARY = "Inget tillfälligt uppdrag pågår.";

	private final DocumenterAssignmentRepository assignments;

	private final MemberRepository members;

	private final UserAccountRepository accounts;

	private final HolderChangeService holderChanges;

	private final AuditService audit;

	private final Clock clock;

	DocumenterAppointmentService(DocumenterAssignmentRepository assignments, MemberRepository members,
			UserAccountRepository accounts, HolderChangeService holderChanges, AuditService audit, Clock clock) {
		this.assignments = assignments;
		this.members = members;
		this.accounts = accounts;
		this.holderChanges = holderChanges;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Uppdraget som utsågs.
	 *
	 * @param created false om medlemmen redan hade ett ordinarie uppdrag som gäller startdatumet, och inget
	 *            nytt skapades
	 */
	public record Appointment(UUID id, UUID memberId, String memberName, AssignmentType type, LocalDate startDate,
			LocalDate endDate, boolean created) {
	}

	/**
	 * Utser en ordinarie dokumenterare från och med startdatumet till och med 31 december samma år.
	 * <ul>
	 * <li>Ordinarie uppdrag som gäller någon dag under perioden avslutas från startdatumet. Ett som pågår
	 * i dag och ersätts från i dag gäller alltså inte längre, och ett som ersätts först nästa år gäller
	 * året ut.</li>
	 * <li>Börjar uppdraget i dag byts innehavaren direkt med {@link HolderChangeService}, om inte ett
	 * tillfälligt uppdrag gäller, som går före. Börjar det senare byts innehavaren när det börjar gälla
	 * av {@link HolderSyncJob}.</li>
	 * <li>Har medlemmen redan ett ordinarie uppdrag som gäller startdatumet händer ingenting.</li>
	 * </ul>
	 * Kontot är låst under hela utseendet, så att två Admin som utser samtidigt inte krockar.
	 *
	 * @throws BadRequestException om medlemmen saknas eller är inaktiv, eller om startdatumet ligger före i dag
	 *             eller efter nästa år
	 */
	@Transactional
	public Appointment appointOrdinary(UUID memberId, LocalDate startDate) {
		LocalDate today = LocalDate.now(clock);
		validateStart(startDate, today);
		Member member = activeMember(memberId);
		UserAccount account = lockDocumenterAccount();
		List<DocumenterAssignment> candidates = assignments.findByEndDateGreaterThanEqual(startDate);

		Optional<DocumenterAssignment> existing = candidates.stream()
			.filter(assignment -> assignment.getType() == AssignmentType.ORDINARY
					&& assignment.getMemberId().equals(memberId) && assignment.isActiveOn(startDate))
			.findFirst();
		if (existing.isPresent()) {
			applyActiveHolder(account, today);
			return appointment(existing.get(), member, false);
		}

		LocalDate periodEnd = LocalDate.of(startDate.getYear(), 12, 31);
		// Avslutas från början av startdagen, så att uppdraget inte gäller från och med den dagen.
		Instant endAt = startDate.atStartOfDay(TimeConfig.ZONE).toInstant();
		for (DocumenterAssignment other : candidates) {
			if (other.getType() == AssignmentType.ORDINARY && other.isActiveDuring(startDate, periodEnd)) {
				endEarly(other, endAt);
			}
		}
		DocumenterAssignment created = assignments
			.save(DocumenterAssignment.ordinary(memberId, startDate, clock.instant()));
		recordCreated(created);
		if (!startDate.isAfter(today)) {
			applyActiveHolder(account, today);
		}
		return appointment(created, member, true);
	}

	/**
	 * Utser en tillfällig dokumenterare från och med i dag till och med slutdatumet. Det tillfälliga uppdraget
	 * går före det ordinarie, så innehavaren byts direkt med {@link HolderChangeService}. Rollen går tillbaka
	 * till den ordinarie dokumenteraren när perioden är slut, av jobbet i {@link HolderSyncJob}, eller direkt
	 * med {@link #endTemporary()}. Kontot är låst under hela utseendet.
	 *
	 * @throws BadRequestException om medlemmen saknas eller är inaktiv, eller om slutdatumet inte ligger efter
	 *             i dag
	 * @throws ConflictException om ett tillfälligt uppdrag redan pågår eller överlappar perioden. Det avslutas
	 *             först med {@link #endTemporary()}.
	 */
	@Transactional
	public Appointment appointTemporary(UUID memberId, LocalDate endDate) {
		LocalDate today = LocalDate.now(clock);
		if (!endDate.isAfter(today)) {
			throw new BadRequestException(END_NOT_AFTER_TODAY);
		}
		Member member = activeMember(memberId);
		UserAccount account = lockDocumenterAccount();
		boolean overlaps = assignments.findByEndDateGreaterThanEqual(today)
			.stream()
			.anyMatch(assignment -> assignment.getType() == AssignmentType.TEMPORARY
					&& assignment.isActiveDuring(today, endDate));
		if (overlaps) {
			throw new ConflictException(TEMPORARY_EXISTS);
		}
		DocumenterAssignment created = assignments
			.save(DocumenterAssignment.temporary(memberId, today, endDate, clock.instant()));
		recordCreated(created);
		applyActiveHolder(account, today);
		return appointment(created, member, true);
	}

	/**
	 * Avslutar det tillfälliga uppdraget som gäller i dag, från och med i dag. Rollen går då tillbaka till den
	 * ordinarie dokumenteraren, vilket räknas som ett innehavarbyte, eller till ingen om inget ordinarie
	 * uppdrag gäller. Kontot låses före läsningen, så att två samtidiga avslut inte båda lyckas.
	 *
	 * @throws NotFoundException om inget tillfälligt uppdrag pågår
	 */
	@Transactional
	public void endTemporary() {
		LocalDate today = LocalDate.now(clock);
		UserAccount account = lockDocumenterAccount();
		DocumenterAssignment temporary = assignments.findByEndDateGreaterThanEqual(today)
			.stream()
			.filter(assignment -> assignment.getType() == AssignmentType.TEMPORARY && assignment.isActiveOn(today))
			.findFirst()
			.orElseThrow(() -> new NotFoundException(NO_TEMPORARY));
		endEarly(temporary, today.atStartOfDay(TimeConfig.ZONE).toInstant());
		applyActiveHolder(account, today);
	}

	/**
	 * Gör den som är aktiv dokumenterare i dag till kontots innehavare, eller tar bort innehavaren om inget
	 * uppdrag gäller. Det täcker att ett tillfälligt uppdrag löpt ut, att ett uppdrag som lagts i förväg börjar
	 * gälla, och att ett årsskifte passerats utan nytt uppdrag. Körs av {@link HolderSyncJob} vid midnatt och
	 * vid uppstart. Är kontots innehavare redan rätt händer ingenting, så upprepade och samtidiga körningar
	 * (flera instanser, eller jobbet mot ett anrop från Admin) räknar inte upp tokenversionen i onödan.
	 *
	 * @return true om innehavaren ändrades
	 */
	@Transactional
	public boolean syncHolder() {
		return applyActiveHolder(lockDocumenterAccount(), LocalDate.now(clock));
	}

	private Member activeMember(UUID memberId) {
		Member member = members.findById(memberId).orElseThrow(() -> new BadRequestException(UNKNOWN_MEMBER));
		if (member.getStatus() != MemberStatus.ACTIVE) {
			throw new BadRequestException(INACTIVE_MEMBER);
		}
		return member;
	}

	private void recordCreated(DocumenterAssignment created) {
		audit.record(AuditEntity.DOCUMENTER_ASSIGNMENT, created.getId(), AuditAction.CREATED,
				List.of(FieldChange.withValues("memberId", null, created.getMemberId().toString()),
						FieldChange.withValues("type", null, created.getType().name()),
						FieldChange.withValues("startDate", null, created.getStartDate().toString()),
						FieldChange.withValues("endDate", null, created.getEndDate().toString())));
	}

	/** Avslutar uppdraget från tidpunkten och loggar det, om slutet ändrades. */
	private void endEarly(DocumenterAssignment assignment, Instant endAt) {
		Instant before = assignment.getEndedAt();
		if (assignment.endEarly(endAt)) {
			audit.record(AuditEntity.DOCUMENTER_ASSIGNMENT, assignment.getId(), AuditAction.UPDATED,
					List.of(FieldChange.withValues("endedAt", before == null ? null : before.toString(),
							endAt.toString())));
		}
	}

	/** Från och med i dag, och som senast under nästa kalenderår (ett ordinarie uppdrag gäller ett år i taget). */
	private static void validateStart(LocalDate startDate, LocalDate today) {
		if (startDate.isBefore(today)) {
			throw new BadRequestException(START_IN_THE_PAST);
		}
		if (startDate.getYear() > today.getYear() + 1) {
			throw new BadRequestException(START_TOO_FAR_AHEAD);
		}
	}

	/**
	 * Låser Dokumenterare-kontot direkt, utan att först läsa det utan lås. Annars kan en samtidig utsedd
	 * hinna uppdatera kontot medan vi väntar på låset, och vi räknar då upp tokenversionen från ett
	 * föråldrat värde.
	 */
	private UserAccount lockDocumenterAccount() {
		return accounts.findForUpdateByRole(Role.DOCUMENTER)
			.orElseThrow(() -> new IllegalStateException("Dokumenterare-kontot saknas i databasen."));
	}

	/**
	 * Gör den som är aktiv dokumenterare i dag till kontots innehavare, eller tar bort innehavaren om inget
	 * uppdrag gäller. Är innehavaren redan rätt händer ingenting. Ett aktivt tillfälligt uppdrag går före det
	 * ordinarie. Uppdragen läses efter att kontot låsts (av anroparen).
	 *
	 * @return true om innehavaren ändrades
	 */
	private boolean applyActiveHolder(UserAccount account, LocalDate today) {
		Optional<DocumenterAssignment> active = DocumenterAssignments
			.activeOn(assignments.findByEndDateGreaterThanEqual(today), today);
		if (active.isEmpty()) {
			return holderChanges.clearHolder(account);
		}
		Member holder = members.findById(active.get().getMemberId())
			.orElseThrow(() -> new IllegalStateException("Medlemmen saknas: " + active.get().getMemberId()));
		return holderChanges.changeHolder(account, holder);
	}

	private static Appointment appointment(DocumenterAssignment assignment, Member member, boolean created) {
		return new Appointment(assignment.getId(), member.getId(), member.getFirstName() + " " + member.getLastName(),
				assignment.getType(), assignment.getStartDate(), assignment.lastActiveDate(), created);
	}

}
