package se.ikft.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * En engångskod för att sätta lösenord på ett konto. Bara hashen av koden lagras.
 */
@Entity
@Table(name = "setup_code")
public class SetupCode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userAccountId;

	private String codeHash;

	private Instant validUntil;

	private Instant usedAt;

	private Instant createdAt;

	protected SetupCode() {
	}

	SetupCode(UUID userAccountId, String codeHash, Instant validUntil, Instant createdAt) {
		this.userAccountId = userAccountId;
		this.codeHash = codeHash;
		this.validUntil = validUntil;
		this.createdAt = createdAt;
	}

	/** Används när en ny kod skapas för kontot, så att den gamla slutar gälla. */
	void expire(Instant now) {
		validUntil = now;
	}

	void markUsed(Instant now) {
		usedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserAccountId() {
		return userAccountId;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
