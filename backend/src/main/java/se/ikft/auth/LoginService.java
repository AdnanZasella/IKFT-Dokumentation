package se.ikft.auth;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.security.AccessTokens;

/**
 * Inloggning med användarnamn och lösenord.
 */
@Service
@ConditionalOnWebApplication
class LoginService {

	/**
	 * Samma svar oavsett om kontot saknas, saknar lösenord eller om lösenordet är fel, så att svaret
	 * inte avslöjar vilket fel det var.
	 */
	static final String INVALID_CREDENTIALS = "Fel användarnamn eller lösenord.";

	/** Jämförs i stället för ett lösenord som inte kan vara rätt, så att BCrypt ändå körs. */
	private static final String UNUSABLE_PASSWORD = "inget-konto-har-det-har-losenordet";

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
		this.dummyHash = passwordEncoder.encode(UNUSABLE_PASSWORD);
	}

	/** Ett lyckat försök: cookien med token och kontot som det visas för klienten. */
	record LoginResult(ResponseCookie cookie, AccountInfo account) {
	}

	/**
	 * Körs utan transaktion, så att ingen databasanslutning hålls medan lösenordet jämförs. Kontot
	 * läses i en egen kort transaktion.
	 */
	public LoginResult login(String username, String password) {
		Optional<UserAccount> found = isPossibleUsername(username) ? accounts.findByUsername(username)
				: Optional.empty();
		Optional<UserAccount> withPassword = found.filter(UserAccount::hasPassword);
		// Samma regler som när lösenordet sattes. Ett lösenord som bryter mot dem kan aldrig vara rätt.
		boolean usable = Passwords.problem(password, 1).isEmpty();
		// Jämförelsen görs alltid, även när svaret redan är givet, så att svarstiden blir densamma. Ett
		// tomt lösenord jämförs inte alls av PasswordEncoder, därför jämförs ersättningen i stället.
		boolean matches = passwordEncoder.matches(usable ? Passwords.normalize(password) : UNUSABLE_PASSWORD,
				withPassword.map(UserAccount::getPasswordHash).orElse(dummyHash));
		if (!usable || !matches || withPassword.isEmpty()) {
			logFailure(found);
			throw new BadCredentialsException(INVALID_CREDENTIALS);
		}
		UserAccount account = withPassword.get();
		log.info("Inloggning lyckades för {}.", account.getUsername());
		return new LoginResult(accessTokens.issue(account), accountInfo.get(account));
	}

	/**
	 * Om något konto kan ha användarnamnet. Annars görs ingen sökning, så att t.ex. NUL, som databasen
	 * avvisar, ger samma svar som ett okänt användarnamn.
	 */
	private static boolean isPossibleUsername(String username) {
		return !username.isBlank() && username.length() <= UserAccount.MAX_USERNAME_LENGTH
				&& username.chars().noneMatch(Character::isISOControl);
	}

	/**
	 * Användarnamnet loggas bara om kontot finns, så att text från klienten inte hamnar i loggen.
	 */
	private static void logFailure(Optional<UserAccount> account) {
		account.ifPresentOrElse(found -> log.warn("Inloggning misslyckades för {}.", found.getUsername()),
				() -> log.warn("Inloggning misslyckades för ett okänt användarnamn."));
	}

}
