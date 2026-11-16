package se.ikft.auth;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.auth.LoginService.LoginResult;
import se.ikft.common.error.BadRequestException;
import se.ikft.common.error.TooManyRequestsException;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * Byte av det egna lösenordet för ett inloggat konto.
 */
@Service
@ConditionalOnWebApplication
class PasswordChangeService {

	static final String WRONG_CURRENT = "Det nuvarande lösenordet är fel.";

	static final String SAME_PASSWORD = "Det nya lösenordet måste skilja sig från det nuvarande.";

	private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokens accessTokens;

	private final RefreshTokens refreshTokens;

	private final LoginAttemptLimiter limiter;

	private final AccountInfoService accountInfo;

	private final Clock clock;

	PasswordChangeService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
			AccessTokens accessTokens, RefreshTokens refreshTokens, LoginAttemptLimiter limiter,
			AccountInfoService accountInfo, Clock clock) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.limiter = limiter;
		this.accountInfo = accountInfo;
		this.clock = clock;
	}

	/**
	 * Byter lösenordet om det nuvarande stämmer och räknar upp tokenversionen, så att alla andra sessioner
	 * loggas ut. Den som bytte får nya cookies och förblir inloggad. Lösenordet ska redan vara kontrollerat
	 * med {@link ValidPassword}.
	 * <p>
	 * Fel nuvarande lösenord räknas som misslyckade inloggningar, så att någon med en stulen session inte
	 * kan gissa lösenordet i obegränsad takt. Kontot är låst under hela bytet, så att två samtidiga byten
	 * inte krockar.
	 */
	@Transactional
	public LoginResult changePassword(UUID accountId, String currentPassword, String newPassword, String address) {
		UserAccount account = accounts.findForUpdateById(accountId)
			.orElseThrow(() -> new IllegalStateException("Kontot finns inte: " + accountId));
		limiter.blockedFor(account.getUsername(), address).ifPresent(wait -> {
			log.warn("Byte av lösenord nekades eftersom det är spärrat för {}.", account.getUsername());
			throw new TooManyRequestsException(LoginService.BLOCKED, wait);
		});
		if (!currentMatches(account, currentPassword)) {
			limiter.recordFailure(account.getUsername(), address);
			log.warn("Byte av lösenord misslyckades för {}: fel nuvarande lösenord.", account.getUsername());
			throw new BadRequestException(WRONG_CURRENT);
		}
		limiter.recordSuccess(account.getUsername());
		String normalized = Passwords.normalize(newPassword);
		if (passwordEncoder.matches(normalized, account.getPasswordHash())) {
			throw new BadRequestException(SAME_PASSWORD);
		}
		account.changePassword(passwordEncoder.encode(normalized), clock.instant());
		// Loggas först efter commit, så att raden bara finns om lösenordet faktiskt byttes.
		String changed = account.getUsername();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				log.info("Lösenordet för {} byttes av kontots innehavare.", changed);
			}
		});
		// Med den nya tokenversionen, så att den som bytte förblir inloggad.
		return new LoginResult(accessTokens.issue(account), refreshTokens.issue(account), accountInfo.get(account));
	}

	/** Ett lösenord som bryter mot reglerna kan aldrig vara rätt och jämförs inte. */
	private boolean currentMatches(UserAccount account, String currentPassword) {
		return account.hasPassword() && Passwords.problem(currentPassword, 1).isEmpty()
				&& passwordEncoder.matches(Passwords.normalize(currentPassword), account.getPasswordHash());
	}

}
