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
 * Att en medlem har betalat moskéavgiften via FIFS ett visst år (föreningens egen medlemsavgift hanteras senare). Det finns högst en rad per medlem och år.
 */
@Entity
@Table(name = "member_fifs_fee")
class MemberFifsFee {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID memberId;

	private int year;

	private LocalDate paidOn;

	private Instant createdAt;

	protected MemberFifsFee() {
	}

	MemberFifsFee(UUID memberId, int year, LocalDate paidOn, Instant now) {
		this.memberId = memberId;
		this.year = year;
		this.paidOn = paidOn;
		this.createdAt = now;
	}

	int getYear() {
		return year;
	}

	LocalDate getPaidOn() {
		return paidOn;
	}

}
