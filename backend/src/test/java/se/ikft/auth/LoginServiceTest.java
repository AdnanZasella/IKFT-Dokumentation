package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import se.ikft.account.UserAccountRepository;
import se.ikft.security.AccessTokens;

/**
 * Att BCrypt körs även när svaret redan är givet, så att svarstiden inte skiljer sig.
 */
class LoginServiceTest {

	private final UserAccountRepository accounts = mock(UserAccountRepository.class);

	private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

	private final LoginService loginService = new LoginService(accounts, passwordEncoder,
			mock(AccessTokens.class), mock(AccountInfoService.class));

	@ParameterizedTest
	@ValueSource(ints = { 0, 74, 300 })
	void losenordSomInteKanVaraRattJamforsAndaMedBcrypt(int byteLangd) {
		when(accounts.findByUsername("admin")).thenReturn(Optional.empty());
		clearInvocations(passwordEncoder);
		// "é" är två byte i UTF-8, så 74 byte är 37 tecken.
		String losenord = (byteLangd == 74) ? "é".repeat(37) : "x".repeat(byteLangd);

		assertThatThrownBy(() -> loginService.login("admin", losenord)).isInstanceOf(BadCredentialsException.class);

		// PasswordEncoder svarar false direkt för ett tomt lösenord, utan att köra BCrypt.
		verify(passwordEncoder).matches(argThat(raw -> raw != null && !raw.isEmpty()), anyString());
	}

}
