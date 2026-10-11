package se.ikft.auth;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

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

	/** BCrypt använder bara de första 72 byten av lösenordet. */
	private static final int BCRYPT_MAX_BYTES = 72;

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
		Instant now = clock.instant();
		codes.findValid(account.getId(), now).forEach(code -> code.expire(now));
		String code = SetupCodes.generate(random);
		Instant validUntil = now.plus(properties.setupCodeValidity());
		codes.save(new SetupCode(account.getId(), SetupCodes.hash(code), validUntil, now));
		return new NewSetupCode(code, validUntil);
	}

	@Transactional(readOnly = true)
	public boolean hasValidCode(UserAccount account) {
		return !codes.findValid(account.getId(), clock.instant()).isEmpty();
	}

	/**
	 * Sätter lösenordet om koden är giltig för kontot. Lösenordet sparas som BCrypt-hash,
	 * tokenversionen räknas upp och koden markeras som använd.
	 */
	@Transactional
	public void setPassword(String username, String code, String newPassword) {
		validatePassword(newPassword);
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
		account.changePassword(passwordEncoder.encode(newPassword), now);
	}

	private void validatePassword(String password) {
		int minLength = properties.passwordMinLength();
		if (password.codePointCount(0, password.length()) < minLength) {
			throw new BadRequestException("Lösenordet måste ha minst %d tecken.".formatted(minLength));
		}
		if (password.getBytes(UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new BadRequestException(
					"Lösenordet är för långt. Det får vara högst 72 byte, där till exempel å, ä och ö räknas som två.");
		}
	}

}
