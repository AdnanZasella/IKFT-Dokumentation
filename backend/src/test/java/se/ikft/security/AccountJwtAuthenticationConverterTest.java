package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;

class AccountJwtAuthenticationConverterTest {

	private final UserAccountRepository accounts = mock(UserAccountRepository.class);

	private final AccountJwtAuthenticationConverter converter = new AccountJwtAuthenticationConverter(accounts);

	@Test
	void adminFarRoleAdmin() {
		assertThat(authorities(Role.ADMIN)).containsExactly("ROLE_ADMIN");
	}

	@Test
	void dokumenterareFarRoleDocumenter() {
		assertThat(authorities(Role.DOCUMENTER)).containsExactly("ROLE_DOCUMENTER");
	}

	@Test
	void namnetArKontotsId() {
		UUID id = UUID.randomUUID();
		konto(id, Role.ADMIN, 0);

		assertThat(converter.convert(jwt(id.toString(), 0)).getName()).isEqualTo(id.toString());
	}

	@Test
	void annanTokenversionAvvisas() {
		UUID id = UUID.randomUUID();
		konto(id, Role.ADMIN, 2);

		assertThatThrownBy(() -> converter.convert(jwt(id.toString(), 1)))
			.isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void subjectSomInteArEttIdAvvisas() {
		assertThatThrownBy(() -> converter.convert(jwt("admin", 0))).isInstanceOf(InvalidBearerTokenException.class);
	}

	private List<String> authorities(Role role) {
		UUID id = UUID.randomUUID();
		konto(id, role, 0);
		AbstractAuthenticationToken token = converter.convert(jwt(id.toString(), 0));
		return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

	private void konto(UUID id, Role role, int tokenVersion) {
		UserAccount konto = mock(UserAccount.class);
		when(konto.getId()).thenReturn(id);
		when(konto.getRole()).thenReturn(role);
		when(konto.getTokenVersion()).thenReturn(tokenVersion);
		when(accounts.findById(any())).thenReturn(Optional.empty());
		when(accounts.findById(id)).thenReturn(Optional.of(konto));
	}

	private static Jwt jwt(String subject, int tokenVersion) {
		Instant nu = Instant.parse("2026-10-12T10:00:00Z");
		return Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(subject)
			.claim(AccessTokens.TOKEN_VERSION_CLAIM, (long) tokenVersion)
			.issuedAt(nu)
			.expiresAt(nu.plusSeconds(60))
			.build();
	}

}
