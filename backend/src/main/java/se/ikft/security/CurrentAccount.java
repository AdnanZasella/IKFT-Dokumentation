package se.ikft.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import se.ikft.account.Role;

/**
 * Det inloggade kontot, som {@link AccountJwtAuthenticationConverter} redan har läst från databasen.
 * Inloggningens namn är kontots id och dess enda roll är kontots roll.
 */
public final class CurrentAccount {

	private static final String ROLE_PREFIX = "ROLE_";

	private CurrentAccount() {
	}

	public static UUID id(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	public static Role role(Authentication authentication) {
		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith(ROLE_PREFIX))
			.map(authority -> Role.valueOf(authority.substring(ROLE_PREFIX.length())))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Inloggningen saknar roll."));
	}

	static GrantedAuthority authority(Role role) {
		return new SimpleGrantedAuthority(ROLE_PREFIX + role.name());
	}

}
