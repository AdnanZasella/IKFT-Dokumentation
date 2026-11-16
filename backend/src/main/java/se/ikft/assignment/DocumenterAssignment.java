package se.ikft.assignment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import se.ikft.common.config.TimeConfig;

/**
 * Ett uppdrag som dokumenterare för en medlem under en period. Perioden är {@code startDate} till och
 * med {@code endDate}. Ett uppdrag som avslutas i förtid får {@code endedAt}, och gäller då inte från och
 * med det datumet.
 */
@Entity
@Table(name = "documenter_assignment")
class DocumenterAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID memberId;

	@Enumerated(EnumType.STRING)
	private AssignmentType type;

	private LocalDate startDate;

	private LocalDate endDate;

	private Instant endedAt;

	private Instant createdAt;

	protected DocumenterAssignment() {
	}

	private DocumenterAssignment(UUID memberId, AssignmentType type, LocalDate startDate, LocalDate endDate,
			Instant now) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Slutdatumet kan inte ligga före startdatumet.");
		}
		this.memberId = memberId;
		this.type = type;
		this.startDate = startDate;
		this.endDate = endDate;
		this.createdAt = now;
	}

	/** Ett ordinarie uppdrag från startdatumet till och med 31 december samma år. */
	static DocumenterAssignment ordinary(UUID memberId, LocalDate startDate, Instant now) {
		return new DocumenterAssignment(memberId, AssignmentType.ORDINARY, startDate, endOfYear(startDate), now);
	}

	/** Ett tillfälligt uppdrag från startdatumet till och med slutdatumet. */
	static DocumenterAssignment temporary(UUID memberId, LocalDate startDate, LocalDate endDate, Instant now) {
		return new DocumenterAssignment(memberId, AssignmentType.TEMPORARY, startDate, endDate, now);
	}

	/** Avslutar uppdraget i förtid. Ett som redan är avslutat ändras inte. */
	void endEarly(Instant now) {
		if (endedAt == null) {
			endedAt = now;
		}
	}

	/**
	 * Sista dagen uppdraget gäller. Det är slutdatumet, men ett ordinarie uppdrag gäller som längst till och
	 * med 31 december samma år som det börjar, och ett som avslutats i förtid gäller inte från och med den
	 * dag det avslutades (i föreningens tidszon). Ligger det före startdatumet har uppdraget aldrig gällt.
	 */
	LocalDate lastActiveDate() {
		LocalDate last = endDate;
		if (type == AssignmentType.ORDINARY && endOfYear(startDate).isBefore(last)) {
			last = endOfYear(startDate);
		}
		if (endedAt != null) {
			LocalDate dayBeforeEnd = endedAt.atZone(TimeConfig.ZONE).toLocalDate().minusDays(1);
			if (dayBeforeEnd.isBefore(last)) {
				last = dayBeforeEnd;
			}
		}
		return last;
	}

	/** Om uppdraget gäller det givna datumet. */
	boolean isActiveOn(LocalDate date) {
		return !date.isBefore(startDate) && !date.isAfter(lastActiveDate());
	}

	/**
	 * Om uppdraget gäller någon dag under perioden från och med {@code from} till och med {@code to}. Ett
	 * uppdrag som avslutats före sin start har aldrig gällt, hur perioden än ligger.
	 */
	boolean isActiveDuring(LocalDate from, LocalDate to) {
		LocalDate last = lastActiveDate();
		return !last.isBefore(startDate) && !startDate.isAfter(to) && !last.isBefore(from);
	}

	private static LocalDate endOfYear(LocalDate date) {
		return LocalDate.of(date.getYear(), 12, 31);
	}

	UUID getId() {
		return id;
	}

	UUID getMemberId() {
		return memberId;
	}

	AssignmentType getType() {
		return type;
	}

	LocalDate getStartDate() {
		return startDate;
	}

	LocalDate getEndDate() {
		return endDate;
	}

	Instant getEndedAt() {
		return endedAt;
	}

}
