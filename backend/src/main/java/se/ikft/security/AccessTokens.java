package se.ikft.security;

import java.time.Clock;
import java.time.Instant;

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
public class AccessTokens {

	public static final String COOKIE_NAME = "ikft_access";

	static final String ROLE_CLAIM = "role";

	static final String TOKEN_VERSION_CLAIM = "token_version";

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
	 * sattes från (admin-subdomänen) och skickas aldrig till den publika hemsidan.
	 */
	public ResponseCookie issue(UserAccount account) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(account.getId().toString())
			.claim(ROLE_CLAIM, account.getRole().name())
			.claim(TOKEN_VERSION_CLAIM, account.getTokenVersion())
			.issuedAt(now)
			.expiresAt(now.plus(properties.validity()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return ResponseCookie.from(COOKIE_NAME, token)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path("/")
			.maxAge(properties.validity())
			.build();
	}

}
