package se.ikft.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import se.ikft.account.UserAccount;

/**
 * Skapar inloggningstoken och cookien den skickas i. Allt om cookiens namn och attribut finns här,
 * så att inloggning, lösenordsbyte och förnyelse sätter likadana cookies.
 */
@Component
@ConditionalOnWebApplication
public class AccessTokens {

	/** Med prefixet {@code __Host-} kan cookien bara sättas av värden själv, inte av en annan subdomän. */
	public static final String COOKIE_NAME = "__Host-ikft_access";

	static final String TOKEN_VERSION_CLAIM = "token_version";

	/** Skiljer åtkomsttoken från förnyelsetoken, så att den ena inte kan användas som den andra. */
	static final String TOKEN_USE_CLAIM = "token_use";

	static final String ACCESS_USE = "access";

	static final String REFRESH_USE = "refresh";

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	AccessTokens(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * En cookie med en ny token för kontot. Cookien saknar {@code Domain}, så den gäller bara värden den
	 * sattes från (admin-subdomänen) och skickas aldrig till den publika hemsidan. Token saknar roll,
	 * eftersom rollen läses från databasen vid varje anrop.
	 */
	public ResponseCookie issue(UserAccount account) {
		return cookie(COOKIE_NAME, "/", encode(account, ACCESS_USE, properties.validity()), properties.validity());
	}

	/** En cookie som får webbläsaren att ta bort inloggningscookien. */
	public ResponseCookie clear() {
		return cookie(COOKIE_NAME, "/", "", Duration.ZERO);
	}

	/** Signerar en token för kontot. Används också för förnyelsetoken, se {@link RefreshTokens}. */
	String encode(UserAccount account, String use, Duration validity) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(account.getId().toString())
			.claim(TOKEN_VERSION_CLAIM, account.getTokenVersion())
			.claim(TOKEN_USE_CLAIM, use)
			.issuedAt(now)
			.expiresAt(now.plus(validity))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** Samma attribut för alla cookies med token, så att de inte kan skilja sig åt. */
	static ResponseCookie cookie(String name, String path, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(path)
			.maxAge(maxAge)
			.build();
	}

}
