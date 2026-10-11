package se.ikft.auth;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.common.error.BadRequestException;

/**
 * Engångskoder för att sätta lösenord: det första Admin-lösenordet och som reservväg om ett lösenord
 * glömts bort.
 */
@Service
public class SetupCodeService {

	/**
	 * Samma svar oavsett om kontot saknas eller om koden är fel, utgången, använd eller ersatt, så att
	 * svaret inte avslöjar vilket fel det var.
	 */
	static final String INVALID_CODE = "Användarnamnet eller koden är fel, eller så har koden gått ut.";

	private final SecureRandom random = new SecureRandom();

	private final SetupCodeRepository codes;

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final AuthProperties properties;

	private final Clock clock;

	SetupCodeService(SetupCodeRepository codes, UserAccountRepository accounts, PasswordEncoder passwordEncoder,
			AuthProperties properties, Clock clock) {
		this.codes = codes;
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	/** En nyss skapad kod i klartext. Klartexten finns bara här och lagras aldrig. */
	public record NewSetupCode(String code, Instant validUntil) {
	}

	/** Skapar en ny kod för kontot. Kontots tidigare koder slutar gälla. */
	@Transactional
	public NewSetupCode createFor(UserAccount account) {
		lock(account);
		Instant now = clock.instant();
		expireValidCodes(account, now);
		return create(account, now);
	}

	/**
	 * Skapar en kod om kontot varken har lösenord eller giltig kod. Kontrollen och skapandet sker under
	 * samma lås, så att två instanser som startar samtidigt inte skapar var sin kod.
	 */
	@Transactional
	public Optional<NewSetupCode> createIfMissing(UserAccount account) {
		UserAccount locked = lock(account);
		Instant now = clock.instant();
		if (locked.hasPassword() || !codes.findValid(locked.getId(), now).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(create(locked, now));
	}

	private UserAccount lock(UserAccount account) {
		return accounts.findForUpdateById(account.getId())
			.orElseThrow(() -> new IllegalStateException("Kontot finns inte: " + account.getId()));
	}

	private NewSetupCode create(UserAccount account, Instant now) {
		String code = SetupCodes.generate(random);
		Instant validUntil = now.plus(properties.setupCodeValidity());
		codes.save(new SetupCode(account.getId(), SetupCodes.hash(code), validUntil, now));
		return new NewSetupCode(code, validUntil);
	}

	private void expireValidCodes(UserAccount account, Instant now) {
		codes.findValid(account.getId(), now).forEach(code -> code.expire(now));
	}

	/**
	 * Sätter lösenordet om koden är giltig för kontot. Lösenordet ska redan vara kontrollerat med
	 * {@link ValidPassword}. Det normaliseras och sparas som BCrypt-hash,
	 * tokenversionen räknas upp och koden markeras som använd. Kontots övriga koder slutar gälla, så att
	 * ingen annan kod kan byta lösenordet igen.
	 */
	@Transactional
	public void setPassword(String username, String code, String newPassword) {
		// Låset gör att samma kod inte kan användas av två samtidiga anrop.
		UserAccount account = accounts.findForUpdateByUsername(username)
			.orElseThrow(() -> new BadRequestException(INVALID_CODE));
		Instant now = clock.instant();
		byte[] hash = SetupCodes.hash(code).getBytes(US_ASCII);
		SetupCode setupCode = codes.findValid(account.getId(), now)
			.stream()
			.filter(candidate -> MessageDigest.isEqual(candidate.getCodeHash().getBytes(US_ASCII), hash))
			.findFirst()
			.orElseThrow(() -> new BadRequestException(INVALID_CODE));
		setupCode.markUsed(now);
		expireValidCodes(account, now);
		account.changePassword(passwordEncoder.encode(Passwords.normalize(newPassword)), now);
	}

}
