package se.ikft.member;

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

/**
 * En medlem i föreningen. Bara födelsedatum lagras, aldrig personnummer.
 */
@Entity
@Table(name = "member")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String firstName;

	private String lastName;

	private LocalDate birthDate;

	private String address;

	private String homeMunicipality;

	private LocalDate memberSince;

	@Enumerated(EnumType.STRING)
	private MemberStatus status;

	private boolean guardianConsent;

	private Instant createdAt;

	private Instant updatedAt;

	protected Member() {
	}

	/** Uppgifterna från ett anrop, redan rensade och kontrollerade av {@link MemberService}. */
	record Details(String firstName, String lastName, LocalDate birthDate, String address, String homeMunicipality,
			LocalDate memberSince, boolean guardianConsent) {
	}

	static Member create(Details details, Instant now) {
		Member member = new Member();
		member.status = MemberStatus.ACTIVE;
		member.createdAt = now;
		member.update(details, now);
		return member;
	}

	void update(Details details, Instant now) {
		this.firstName = details.firstName();
		this.lastName = details.lastName();
		this.birthDate = details.birthDate();
		this.address = details.address();
		this.homeMunicipality = details.homeMunicipality();
		this.memberSince = details.memberSince();
		this.guardianConsent = details.guardianConsent();
		this.updatedAt = now;
	}

	void setStatus(MemberStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public String getAddress() {
		return address;
	}

	public String getHomeMunicipality() {
		return homeMunicipality;
	}

	public LocalDate getMemberSince() {
		return memberSince;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public boolean isGuardianConsent() {
		return guardianConsent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
