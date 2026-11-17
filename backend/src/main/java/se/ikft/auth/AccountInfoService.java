package se.ikft.auth;

import java.util.UUID;

import org.springframework.stereotype.Service;

import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;

/**
 * Bygger {@link AccountInfo}, så att inloggningen och {@code GET /auth/me} svarar likadant.
 */
@Service
class AccountInfoService {

	private final UserAccountRepository accounts;

	AccountInfoService(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	/**
	 * Kontot, rollen och flaggan för tvingat lösenordsbyte kommer från inloggningen, som redan har läst
	 * kontot. Därför hämtas bara innehavarens namn.
	 */
	public AccountInfo get(UUID accountId, Role role, boolean mustChangePassword) {
		return new AccountInfo(role, accounts.findHolderName(accountId).orElse(null), mustChangePassword);
	}

	public AccountInfo get(UserAccount account) {
		return get(account.getId(), account.getRole(), account.isMustChangePassword());
	}

}
