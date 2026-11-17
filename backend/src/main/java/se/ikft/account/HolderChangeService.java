package se.ikft.account;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import se.ikft.audit.AuditAction;
import se.ikft.audit.AuditEntity;
import se.ikft.audit.AuditService;
import se.ikft.audit.FieldChange;
import se.ikft.member.Member;

/**
 * Byte av innehavare för Dokumenterare-kontot. Används av både ordinarie och tillfälliga uppdrag, så att
 * alla byten gör samma sak.
 */
@Service
public class HolderChangeService {

	/** Fältet i ändringsloggen. Bara medlemmens id sparas, aldrig namnet. */
	static final String HOLDER_FIELD = "holderMemberId";

	private static final Logger log = LoggerFactory.getLogger(HolderChangeService.class);

	private final UserAccountRepository accounts;

	private final AuditService audit;

	private final Clock clock;

	HolderChangeService(UserAccountRepository accounts, AuditService audit, Clock clock) {
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Gör medlemmen till kontots innehavare. Tokenversionen räknas upp, så att alla sessioner för kontot
	 * loggas ut, och bytet loggas. Lösenordet behålls och ingen engångskod skapas: den förra innehavaren
	 * lämnar över lösenordet, och den nya måste byta det vid första inloggningen, så kontot flaggas
	 * ({@link UserAccount#isMustChangePassword()}). Allt sker i en transaktion.
	 * <p>
	 * Om medlemmen redan är innehavare händer ingenting. Kontot läses om under lås, så att ett byte som
	 * görs samtidigt inte missas.
	 *
	 * @return true om innehavaren byttes
	 * @throws IllegalArgumentException om kontot inte är Dokumenterare-kontot. Admin-kontot byter inte
	 *             innehavare.
	 */
	@Transactional
	public boolean changeHolder(UserAccount account, Member member) {
		UserAccount locked = accounts.findForUpdateById(account.getId())
			.orElseThrow(() -> new IllegalStateException("Kontot finns inte: " + account.getId()));
		if (locked.getRole() != Role.DOCUMENTER) {
			throw new IllegalArgumentException("Bara Dokumenterare-kontot byter innehavare.");
		}
		UUID before = locked.getHolderMemberId();
		UUID after = member.getId();
		if (after.equals(before)) {
			return false;
		}
		locked.changeHolder(after, clock.instant());
		audit.record(AuditEntity.ACCOUNT, locked.getId(), AuditAction.UPDATED, List.of(FieldChange
			.withValues(HOLDER_FIELD, before == null ? null : before.toString(), after.toString())));
		// Loggas först efter commit, så att raden bara finns om bytet faktiskt gjordes.
		String username = locked.getUsername();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				log.info("Innehavaren av {} byttes.", username);
			}
		});
		return true;
	}

}
