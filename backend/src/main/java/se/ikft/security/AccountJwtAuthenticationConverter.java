package se.ikft.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;

/**
 * Gör en verifierad token till en inloggning. Kontot läses från databasen vid varje anrop, så att en
 * token slutar gälla så fort tokenversionen räknats upp (lösenordsbyte, återställning, innehavarbyte).
 * Inloggningens namn är kontots id.
 */
class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccountRepository accounts;

	AccountJwtAuthenticationConverter(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UserAccount account = accountId(jwt).flatMap(accounts::findById)
			.filter(found -> tokenVersion(jwt) == found.getTokenVersion())
			.orElseThrow(() -> new InvalidBearerTokenException("Token gäller inte längre."));
		// Rollen tas från databasen, inte från token.
		return new JwtAuthenticationToken(jwt, List.of(CurrentAccount.authority(account.getRole())),
				account.getId().toString());
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
		return (jwt.getClaim(AccessTokens.TOKEN_VERSION_CLAIM) instanceof Number number) ? number.longValue() : -1;
	}

}
