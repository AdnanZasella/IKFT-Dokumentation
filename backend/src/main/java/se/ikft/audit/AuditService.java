package se.ikft.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.UserAccountRepository;
import se.ikft.security.CurrentAccount;

/**
 * Skriver ändringsloggen. Den som ändrar en post anropar {@link #record} i samma transaktion som
 * ändringen, så att ändringen och loggposten antingen sparas båda eller ingen av dem.
 */
@Service
public class AuditService {

	private final JdbcClient jdbc;

	private final UserAccountRepository accounts;

	private final Clock clock;

	AuditService(JdbcClient jdbc, UserAccountRepository accounts, Clock clock) {
		this.jdbc = jdbc;
		this.accounts = accounts;
		this.clock = clock;
	}

	/**
	 * Loggar en ändring av den inloggade användaren. Kräver en pågående transaktion, så att en ändring
	 * aldrig sparas utan sin loggpost.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(AuditEntity entity, UUID entityId, AuditAction action, List<FieldChange> changes) {
		UUID accountId = currentAccountId();
		UUID holderId = accountId == null ? null
				: accounts.findById(accountId).map(account -> account.getHolderMemberId()).orElse(null);
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO audit_log (id, entity_type, entity_id, action, account_id, holder_member_id, created_at)
				VALUES (:id, :entityType, :entityId, :action, :accountId, :holderId, :createdAt)
				""")
			.param("id", id)
			.param("entityType", entity.name())
			.param("entityId", entityId)
			.param("action", action.name())
			.param("accountId", accountId)
			.param("holderId", holderId)
			.param("createdAt", Timestamp.from(Instant.now(clock)))
			.update();
		for (FieldChange change : changes) {
			jdbc.sql("""
					INSERT INTO audit_log_change (audit_log_id, field, before_value, after_value)
					VALUES (:auditLogId, :field, :before, :after)
					""")
				.param("auditLogId", id)
				.param("field", change.field())
				.param("before", change.before())
				.param("after", change.after())
				.update();
		}
	}

	/** Den inloggade användarens konto, eller null om ingen är inloggad. */
	private static UUID currentAccountId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			return null;
		}
		return CurrentAccount.id(authentication);
	}

}
