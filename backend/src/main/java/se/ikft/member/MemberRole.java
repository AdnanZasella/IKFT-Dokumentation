package se.ikft.member;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * En föreningsroll (t.ex. ordförande) under en period. Slutdatum saknas medan rollen pågår.
 */
@Entity
@Table(name = "member_role")
class MemberRole {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID memberId;

	private String title;

	private LocalDate startDate;

	private LocalDate endDate;

	private Instant createdAt;

	protected MemberRole() {
	}

	MemberRole(UUID memberId, String title, LocalDate startDate, LocalDate endDate, Instant now) {
		this.memberId = memberId;
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.createdAt = now;
	}

	UUID getId() {
		return id;
	}

	String getTitle() {
		return title;
	}

	LocalDate getStartDate() {
		return startDate;
	}

	LocalDate getEndDate() {
		return endDate;
	}

}
