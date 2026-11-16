package se.ikft.assignment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Ren logik för vem som är aktiv dokumenterare ett visst datum, utan Spring och databas.
 * <ul>
 * <li>Ett tillfälligt uppdrag går före ett ordinarie.</li>
 * <li>Ett ordinarie uppdrag gäller som längst till och med 31 december samma år, så från och med 1 januari
 * är uppdraget obestämt igen om inget nytt har utsetts.</li>
 * <li>Gäller flera uppdrag av samma typ gäller det som började senast.</li>
 * </ul>
 */
final class DocumenterAssignments {

	/** Tillfälligt före ordinarie, därefter det som började senast. */
	private static final Comparator<DocumenterAssignment> PRECEDENCE = Comparator
		.comparing((DocumenterAssignment assignment) -> assignment.getType() == AssignmentType.TEMPORARY ? 0 : 1)
		.thenComparing(DocumenterAssignment::getStartDate, Comparator.reverseOrder());

	private DocumenterAssignments() {
	}

	/** Uppdraget som gäller det givna datumet, eller tomt om inget uppdrag är satt. */
	static Optional<DocumenterAssignment> activeOn(Collection<DocumenterAssignment> assignments, LocalDate date) {
		return assignments.stream().filter(assignment -> assignment.isActiveOn(date)).min(PRECEDENCE);
	}

	/**
	 * Om det finns ett ordinarie uppdrag som gäller någon dag under perioden. Används för att se om nästa
	 * år redan har en utsedd dokumenterare, även om uppdraget börjar först efter 1 januari.
	 */
	static boolean hasOrdinaryDuring(Collection<DocumenterAssignment> assignments, LocalDate from, LocalDate to) {
		return assignments.stream()
			.anyMatch(assignment -> assignment.getType() == AssignmentType.ORDINARY
					&& assignment.isActiveDuring(from, to));
	}

}
