package se.ikft.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ett av de delade kontona (Admin eller Dokumenterare). Kontona skapas av migreringen, inte av appen.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

	@Id
	private UUID id;

	private String username;

	@Enumerated(EnumType.STRING)
	private Role role;

	/** Null tills ett lösenord satts. */
	private String passwordHash;

	private int tokenVersion;

	private UUID holderMemberId;

	private Instant createdAt;

	private Instant updatedAt;

	protected UserAccount() {
	}

	/**
	 * Sätter ett nytt lösenord och räknar upp tokenversionen så att sessioner från före bytet blir
	 * ogiltiga.
	 */
	public void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.tokenVersion++;
		this.updatedAt = now;
	}

	public boolean hasPassword() {
		return passwordHash != null;
	}

	public UUID getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public UUID getHolderMemberId() {
		return holderMemberId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
