package se.ikft.auth;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.common.error.NotFoundException;

/**
 * Bygger {@link AccountInfo}, så att inloggningen och {@code GET /auth/me} svarar likadant.
 */
@Service
class AccountInfoService {

	private final UserAccountRepository accounts;

	AccountInfoService(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Transactional(readOnly = true)
	public AccountInfo get(UUID accountId) {
		return get(accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Kontot finns inte.")));
	}

	@Transactional(readOnly = true)
	public AccountInfo get(UserAccount account) {
		return new AccountInfo(account.getRole(), accounts.findHolderName(account.getId()).orElse(null));
	}

}
