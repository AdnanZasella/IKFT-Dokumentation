package se.ikft.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;

/**
 * Förnyelsetoken: en längre levande token som bara används för att få en ny åtkomsttoken. Den ligger i en
 * egen cookie som bara skickas till förnyelsen, så att den inte följer med varje anrop.
 */
@Component
@ConditionalOnWebApplication
public class RefreshTokens {

	/**
	 * Prefixet {@code __Host-} kräver {@code Path=/}, vilket hade skickat cookien med varje anrop. Därför
	 * används {@code __Secure-}, som kräver {@code Secure} men tillåter en egen sökväg.
	 */
	public static final String COOKIE_NAME = "__Secure-ikft_refresh";

	/** Webbläsaren skickar bara cookien hit. */
	public static final String COOKIE_PATH = "/api/v1/auth/refresh";

	private final AccessTokens accessTokens;

	private final JwtDecoder decoder;

	private final JwtProperties properties;

	private final UserAccountRepository accounts;

	RefreshTokens(AccessTokens accessTokens, JwtDecoder decoder, JwtProperties properties,
			UserAccountRepository accounts) {
		this.accessTokens = accessTokens;
		this.decoder = decoder;
		this.properties = properties;
		this.accounts = accounts;
	}

	/** En cookie med en ny förnyelsetoken för kontot. */
	public ResponseCookie issue(UserAccount account) {
		return AccessTokens.cookie(COOKIE_NAME, COOKIE_PATH,
				accessTokens.encode(account, AccessTokens.REFRESH_USE, properties.refreshValidity()),
				properties.refreshValidity());
	}

	/** En cookie som får webbläsaren att ta bort förnyelsecookien. Sökvägen måste vara densamma som när den sattes. */
	public ResponseCookie clear() {
		return AccessTokens.cookie(COOKIE_NAME, COOKIE_PATH, "", Duration.ZERO);
	}

	/**
	 * Kontot som en giltig förnyelsetoken tillhör. Tom om token saknas, är ogiltig eller utgången, inte är
	 * en förnyelsetoken eller har en tokenversion som inte längre gäller, eller om kontot saknar lösenord.
	 */
	public Optional<UserAccount> authenticate(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		}
		catch (JwtException ex) {
			return Optional.empty();
		}
		if (!AccessTokens.REFRESH_USE.equals(jwt.getClaimAsString(AccessTokens.TOKEN_USE_CLAIM))) {
			return Optional.empty();
		}
		return accountId(jwt).flatMap(accounts::findById)
			.filter(account -> account.hasPassword() && tokenVersion(jwt) == account.getTokenVersion());
	}

	private static Optional<UUID> accountId(Jwt jwt) {
		try {
			return Optional.of(UUID.fromString(jwt.getSubject()));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.empty();
		}
	}

	/** {@code -1}, som aldrig matchar ett konto, om claimet saknas eller inte är ett heltal. */
	private static long tokenVersion(Jwt jwt) {
		return switch (jwt.getClaim(AccessTokens.TOKEN_VERSION_CLAIM)) {
			case Long version -> version;
			case Integer version -> version;
			case null, default -> -1;
		};
	}

}
