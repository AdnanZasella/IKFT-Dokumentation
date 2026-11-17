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
	 * (#18).</li>
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
		Member member = members.findById(memberId).orElseThrow(() -> new BadRequestException(UNKNOWN_MEMBER));
		if (member.getStatus() != MemberStatus.ACTIVE) {
			throw new BadRequestException(INACTIVE_MEMBER);
		}
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
				other.endEarly(endAt);
				audit.record(AuditEntity.DOCUMENTER_ASSIGNMENT, other.getId(), AuditAction.UPDATED,
						List.of(FieldChange.withValues("endedAt", null, endAt.toString())));
			}
		}
		DocumenterAssignment created = assignments
			.save(DocumenterAssignment.ordinary(memberId, startDate, clock.instant()));
		audit.record(AuditEntity.DOCUMENTER_ASSIGNMENT, created.getId(), AuditAction.CREATED,
				List.of(FieldChange.withValues("memberId", null, memberId.toString()),
						FieldChange.withValues("type", null, created.getType().name()),
						FieldChange.withValues("startDate", null, created.getStartDate().toString()),
						FieldChange.withValues("endDate", null, created.getEndDate().toString())));
		if (!startDate.isAfter(today)) {
			applyActiveHolder(account, today);
		}
		return appointment(created, member, true);
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

	private UserAccount lockDocumenterAccount() {
		UserAccount account = accounts.findByRole(Role.DOCUMENTER)
			.orElseThrow(() -> new IllegalStateException("Dokumenterare-kontot saknas i databasen."));
		return accounts.findForUpdateById(account.getId())
			.orElseThrow(() -> new IllegalStateException("Dokumenterare-kontot saknas i databasen."));
	}

	/**
	 * Gör den som är aktiv dokumenterare i dag till kontots innehavare. Är det redan den personen händer
	 * ingenting. Ett aktivt tillfälligt uppdrag går före det ordinarie.
	 */
	private void applyActiveHolder(UserAccount account, LocalDate today) {
		DocumenterAssignments.activeOn(assignments.findByEndDateGreaterThanEqual(today), today)
			.ifPresent(active -> holderChanges.changeHolder(account, members.findById(active.getMemberId())
				.orElseThrow(() -> new IllegalStateException("Medlemmen saknas: " + active.getMemberId()))));
	}

	private static Appointment appointment(DocumenterAssignment assignment, Member member, boolean created) {
		return new Appointment(assignment.getId(), member.getId(), member.getFirstName() + " " + member.getLastName(),
				assignment.getType(), assignment.getStartDate(), assignment.lastActiveDate(), created);
	}

}
