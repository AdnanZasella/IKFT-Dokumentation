package se.ikft.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
			.filter(found -> AccessTokens.ACCESS_USE.equals(jwt.getClaimAsString(AccessTokens.TOKEN_USE_CLAIM)))
			.filter(found -> tokenVersion(jwt) == found.getTokenVersion())
			.orElseThrow(() -> new InvalidBearerTokenException("Token gäller inte längre."));
		// Rollen och flaggan för tvingat lösenordsbyte tas från databasen, inte från token.
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(CurrentAccount.authority(account.getRole()));
		if (account.isMustChangePassword()) {
			authorities.add(new SimpleGrantedAuthority(CurrentAccount.PASSWORD_CHANGE_REQUIRED));
		}
		return new JwtAuthenticationToken(jwt, authorities, account.getId().toString());
	}

	private static Optional<UUID> accountId(Jwt jwt) {
		try {
			return Optional.of(UUID.fromString(jwt.getSubject()));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.empty();
		}
	}

	/**
	 * {@code -1}, som aldrig matchar ett konto, om claimet saknas eller inte är ett heltal. Ett decimaltal
	 * som {@code 0.5} avrundas alltså inte till {@code 0}.
	 */
	private static long tokenVersion(Jwt jwt) {
		return switch (jwt.getClaim(AccessTokens.TOKEN_VERSION_CLAIM)) {
			case Long version -> version;
			case Integer version -> version;
			case null, default -> -1;
		};
	}

}
