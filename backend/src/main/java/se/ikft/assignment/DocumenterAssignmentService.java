package se.ikft.assignment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.Role;
import se.ikft.account.UserAccount;

/**
 * Vem som är dokumenterare just nu. Det är uppdraget, inte inloggningen, som avgör det.
 */
@Service
public class DocumenterAssignmentService {

	private final DocumenterAssignmentRepository assignments;

	private final AssignmentProperties properties;

	private final Clock clock;

	DocumenterAssignmentService(DocumenterAssignmentRepository assignments, AssignmentProperties properties,
			Clock clock) {
		this.assignments = assignments;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Aktiv dokumenterare.
	 *
	 * @param memberId medlemmen som har uppdraget
	 * @param memberName medlemmens för- och efternamn
	 * @param type ordinarie eller tillfälligt
	 * @param endDate sista dagen uppdraget gäller
	 */
	public record ActiveHolder(UUID memberId, String memberName, AssignmentType type, LocalDate endDate) {
	}

	/** Aktiv dokumenterare i dag, eller tomt om inget uppdrag är satt. */
	@Transactional(readOnly = true)
	public Optional<ActiveHolder> current() {
		LocalDate today = LocalDate.now(clock);
		return DocumenterAssignments.activeOn(assignments.findByEndDateGreaterThanEqual(today), today)
			.map(assignment -> new ActiveHolder(assignment.getMemberId(),
					assignments.findMemberName(assignment.getMemberId()).orElse(null), assignment.getType(),
					assignment.lastActiveDate()));
	}

	/** Om ett ordinarie uppdrag redan är utsett för någon del av nästa kalenderår. */
	@Transactional(readOnly = true)
	public boolean nextYearAssigned() {
		LocalDate today = LocalDate.now(clock);
		LocalDate from = LocalDate.of(today.getYear() + 1, 1, 1);
		LocalDate to = LocalDate.of(today.getYear() + 1, 12, 31);
		List<DocumenterAssignment> candidates = assignments.findByEndDateGreaterThanEqual(from);
		return DocumenterAssignments.hasOrdinaryDuring(candidates, from, to);
	}

	/**
	 * Om Admin ska påminnas om att utse nästa års dokumenterare: det är inte gjort, och det konfigurerade
	 * datumet (som standard 1 december) har passerats.
	 *
	 * @param nextYearAssigned svaret från {@link #nextYearAssigned()}
	 */
	public boolean reminderDue(boolean nextYearAssigned) {
		LocalDate today = LocalDate.now(clock);
		LocalDate reminderFrom = properties.reminderFrom().atYear(today.getYear());
		return !nextYearAssigned && !today.isBefore(reminderFrom);
	}

	/**
	 * Om kontot får registrera sammankomster och möten: Admin alltid, och Dokumenterare bara med ett aktivt
	 * uppdrag. Börjar användas när sammankomsterna byggs (Fas 2).
	 */
	@Transactional(readOnly = true)
	public boolean canRegister(UserAccount account) {
		return account.getRole() == Role.ADMIN || current().isPresent();
	}

}
