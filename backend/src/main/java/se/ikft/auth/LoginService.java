package se.ikft.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.security.AccessTokens;

/**
 * Inloggning med användarnamn och lösenord.
 */
@Service
class LoginService {

	/**
	 * Samma svar oavsett om kontot saknas, saknar lösenord eller om lösenordet är fel, så att svaret
	 * inte avslöjar vilket fel det var.
	 */
	static final String INVALID_CREDENTIALS = "Fel användarnamn eller lösenord.";

	private static final Logger log = LoggerFactory.getLogger(LoginService.class);

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokens accessTokens;

	private final AccountInfoService accountInfo;

	/**
	 * Jämförs med när det inte finns någon riktig hash, så att svarstiden inte avslöjar om kontot
	 * finns eller har lösenord.
	 */
	private final String dummyHash;

	LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AccessTokens accessTokens,
			AccountInfoService accountInfo) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.accountInfo = accountInfo;
		this.dummyHash = passwordEncoder.encode("inget-konto-har-det-har-losenordet");
	}

	/** Ett lyckat försök: cookien med token och kontot som det visas för klienten. */
	record LoginResult(ResponseCookie cookie, AccountInfo account) {
	}

	@Transactional(readOnly = true)
	public LoginResult login(String username, String password) {
		Optional<UserAccount> found = accounts.findByUsername(username);
		Optional<UserAccount> withPassword = found.filter(UserAccount::hasPassword);
		String normalized = Passwords.normalize(password);
		// BCrypt använder bara de första 72 byten, så ett längre lösenord kan aldrig vara rätt.
		boolean tooLong = normalized.getBytes(UTF_8).length > Passwords.BCRYPT_MAX_BYTES;
		// Jämförelsen görs alltid, även när svaret redan är givet, så att svarstiden blir densamma.
		boolean matches = passwordEncoder.matches(tooLong ? "" : normalized,
				withPassword.map(UserAccount::getPasswordHash).orElse(dummyHash));
		if (tooLong || !matches || withPassword.isEmpty()) {
			logFailure(found);
			throw new BadCredentialsException(INVALID_CREDENTIALS);
		}
		UserAccount account = withPassword.get();
		log.info("Inloggning lyckades för {}.", account.getUsername());
		return new LoginResult(accessTokens.issue(account), accountInfo.get(account));
	}

	/**
	 * Användarnamnet loggas bara om kontot finns, så att text från klienten inte hamnar i loggen.
	 */
	private static void logFailure(Optional<UserAccount> account) {
		account.ifPresentOrElse(found -> log.warn("Inloggning misslyckades för {}.", found.getUsername()),
				() -> log.warn("Inloggning misslyckades för ett okänt användarnamn."));
	}

}
