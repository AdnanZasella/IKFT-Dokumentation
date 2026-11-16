package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import se.ikft.MutableClock;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.common.error.TooManyRequestsException;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * Att BCrypt körs även när svaret redan är givet, så att svarstiden inte skiljer sig, och att spärren efter
 * misslyckade inloggningar gäller även rätt lösenord, med en klocka som testet styr.
 */
class LoginServiceTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final int MAX_FORSOK = 5;

	private static final Duration SPARR = Duration.ofMinutes(15);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));

	private final UserAccountRepository accounts = mock(UserAccountRepository.class);

	private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

	private final LoginService loginService = new LoginService(accounts, passwordEncoder,
			mock(AccessTokens.class), mock(RefreshTokens.class),
			new LoginAttemptLimiter(clock, new AuthProperties(Duration.ofHours(24), 12, false, MAX_FORSOK, SPARR)),
			mock(AccountInfoService.class));

	@ParameterizedTest
	@ValueSource(ints = { 0, 74, 300 })
	void losenordSomInteKanVaraRattJamforsAndaMedBcrypt(int byteLangd) {
		when(accounts.findByUsername("admin")).thenReturn(Optional.empty());
		clearInvocations(passwordEncoder);
		// "é" är två byte i UTF-8, så 74 byte är 37 tecken.
		String losenord = (byteLangd == 74) ? "é".repeat(37) : "x".repeat(byteLangd);

		assertThatThrownBy(() -> loginService.login("admin", losenord, "10.0.0.1")).isInstanceOf(BadCredentialsException.class);

		// PasswordEncoder svarar false direkt för ett tomt lösenord, utan att köra BCrypt.
		verify(passwordEncoder).matches(argThat(raw -> raw != null && !raw.isEmpty()), anyString());
	}

	@Test
	void ratLosenordNekasUnderSparren() {
		kontoMedLosenord();
		felaTillSparren();

		assertThatThrownBy(() -> loginService.login("admin", LOSENORD, "10.0.0.1"))
			.isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
				assertThat(ex.getRetryAfter()).isEqualTo(SPARR);
				assertThat(ex.getMessage()).isEqualTo(LoginService.BLOCKED);
			});
	}

	@Test
	void sparrenSlapperNarPeriodenArSlut() {
		kontoMedLosenord();
		felaTillSparren();

		clock.advance(SPARR);

		assertThat(loginService.login("admin", LOSENORD, "10.0.0.1")).isNotNull();
	}

	@Test
	void sparrenGallerAvenFelLosenordUnderSparren() {
		kontoMedLosenord();
		felaTillSparren();

		assertThatThrownBy(() -> loginService.login("admin", "fel-losenord-helt-och-hallet", "10.0.0.1"))
			.isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void okantAnvandarnamnBehandlasSomEttKonto() {
		when(accounts.findByUsername("finns-inte")).thenReturn(Optional.empty());
		for (int i = 0; i < MAX_FORSOK; i++) {
			assertThatThrownBy(() -> loginService.login("finns-inte", LOSENORD, "10.0.0.1"))
				.isInstanceOf(BadCredentialsException.class);
		}

		// Från en annan adress, så att det är namnet som spärrar. Svaret är detsamma som för ett riktigt konto.
		assertThatThrownBy(() -> loginService.login("finns-inte", LOSENORD, "10.0.0.2"))
			.isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
				assertThat(ex.getRetryAfter()).isEqualTo(SPARR);
				assertThat(ex.getMessage()).isEqualTo(LoginService.BLOCKED);
			});
	}

	@Test
	void sparrenSlarInteTillForeSistaForsoket() {
		kontoMedLosenord();
		for (int i = 0; i < MAX_FORSOK - 1; i++) {
			assertThatThrownBy(() -> loginService.login("admin", "fel-losenord-helt-och-hallet", "10.0.0.1"))
				.isInstanceOf(BadCredentialsException.class);
		}

		assertThat(loginService.login("admin", LOSENORD, "10.0.0.1")).isNotNull();
	}

	@Test
	void lyckadInloggningNollstallerRaknarenForAnvandarnamnet() {
		kontoMedLosenord();
		for (int i = 0; i < MAX_FORSOK - 1; i++) {
			assertThatThrownBy(() -> loginService.login("admin", "fel-losenord-helt-och-hallet", "10.0.0.1"))
				.isInstanceOf(BadCredentialsException.class);
		}
		loginService.login("admin", LOSENORD, "10.0.0.9");

		// Fyra nya fel räcker inte, eftersom räknaren började om.
		for (int i = 0; i < MAX_FORSOK - 1; i++) {
			assertThatThrownBy(() -> loginService.login("admin", "fel-losenord-helt-och-hallet", "10.0.0.8"))
				.isInstanceOf(BadCredentialsException.class);
		}
		assertThat(loginService.login("admin", LOSENORD, "10.0.0.7")).isNotNull();
	}

	@Test
	void sparradAdressNekarAvenEttAnnatAnvandarnamn() {
		kontoMedLosenord();
		for (int i = 0; i < MAX_FORSOK; i++) {
			int nummer = i;
			assertThatThrownBy(() -> loginService.login("namn-" + nummer, LOSENORD, "10.0.0.1"))
				.isInstanceOf(BadCredentialsException.class);
		}

		assertThatThrownBy(() -> loginService.login("admin", LOSENORD, "10.0.0.1"))
			.isInstanceOf(TooManyRequestsException.class);
		assertThat(loginService.login("admin", LOSENORD, "10.0.0.2")).isNotNull();
	}

	private void kontoMedLosenord() {
		// Före when(...), eftersom ett anrop till spionen inne i en when() förstör stubbningen.
		String hash = passwordEncoder.encode(LOSENORD);
		UserAccount konto = mock(UserAccount.class);
		when(konto.getUsername()).thenReturn("admin");
		when(konto.hasPassword()).thenReturn(true);
		when(konto.getPasswordHash()).thenReturn(hash);
		when(accounts.findByUsername("admin")).thenReturn(Optional.of(konto));
	}

	private void felaTillSparren() {
		for (int i = 0; i < MAX_FORSOK; i++) {
			assertThatThrownBy(() -> loginService.login("admin", "fel-losenord-helt-och-hallet", "10.0.0.1"))
				.isInstanceOf(BadCredentialsException.class);
		}
	}

}
