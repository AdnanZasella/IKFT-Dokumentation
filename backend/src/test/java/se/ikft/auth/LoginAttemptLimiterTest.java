package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import se.ikft.MutableClock;

/**
 * Spärrlogiken med en klocka som testet styr. Gränsvärden: det femte felet spärrar, det fjärde gör det inte.
 */
class LoginAttemptLimiterTest {

	private static final Duration SPARR = Duration.ofMinutes(15);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));

	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock,
			new AuthProperties(Duration.ofHours(24), 12, false, 5, SPARR));

	@Test
	void ingenSparrInnanForsokenArSlut() {
		fel("admin", "10.0.0.1", 4);

		assertThat(limiter.blockedFor("admin", "10.0.0.1")).isEmpty();
	}

	@Test
	void femteMisslyckadeForsokSparrarAnvandarnamnet() {
		fel("admin", "10.0.0.1", 5);

		// Ett annat IP-nummer, så att det är användarnamnet som spärrar.
		assertThat(limiter.blockedFor("admin", "10.0.0.2")).contains(SPARR);
		assertThat(limiter.blockedFor("dokumenterare", "10.0.0.2")).isEmpty();
	}

	@Test
	void femteMisslyckadeForsokSparrarAdressen() {
		// Olika användarnamn från samma adress.
		for (String namn : new String[] { "a", "b", "c", "d", "e" }) {
			limiter.recordFailure(namn, "10.0.0.1");
		}

		assertThat(limiter.blockedFor("admin", "10.0.0.1")).contains(SPARR);
		assertThat(limiter.blockedFor("admin", "10.0.0.2")).isEmpty();
	}

	@Test
	void okantAnvandarnamnBehandlasSomEttKonto() {
		fel("finns-inte", "10.0.0.1", 5);

		assertThat(limiter.blockedFor("finns-inte", "10.0.0.2")).contains(SPARR);
	}

	@Test
	void versalerOchBlanktecknPaverkarInteRaknaren() {
		limiter.recordFailure("Admin", "10.0.0.1");
		limiter.recordFailure(" admin ", "10.0.0.2");
		limiter.recordFailure("ADMIN", "10.0.0.3");
		limiter.recordFailure("admin", "10.0.0.4");
		limiter.recordFailure("aDmIn", "10.0.0.5");

		assertThat(limiter.blockedFor("admin", "10.0.0.6")).isPresent();
	}

	@Test
	void sparrenSlapperNarPeriodenArSlut() {
		fel("admin", "10.0.0.1", 5);

		clock.advance(SPARR.minusSeconds(1));
		assertThat(limiter.blockedFor("admin", "10.0.0.1")).contains(Duration.ofSeconds(1));

		clock.advance(Duration.ofSeconds(1));
		assertThat(limiter.blockedFor("admin", "10.0.0.1")).isEmpty();
	}

	@Test
	void efterSparrenBorjarRaknarenOm() {
		fel("admin", "10.0.0.1", 5);
		clock.advance(SPARR);

		fel("admin", "10.0.0.1", 4);
		assertThat(limiter.blockedFor("admin", "10.0.0.1")).isEmpty();

		fel("admin", "10.0.0.1", 1);
		assertThat(limiter.blockedFor("admin", "10.0.0.1")).contains(SPARR);
	}

	@Test
	void tidigareFelGlomsNarDetGattLikaLangeSomSparrtiden() {
		fel("admin", "10.0.0.1", 4);
		clock.advance(SPARR);

		// Det gamla räknas inte längre, så ett nytt fel är bara det första.
		fel("admin", "10.0.0.1", 1);

		assertThat(limiter.blockedFor("admin", "10.0.0.1")).isEmpty();
	}

	@Test
	void ettFelSomKommerInnanFonstretArSlutRaknasMedDeTidigare() {
		fel("admin", "10.0.0.1", 4);
		clock.advance(SPARR.minusSeconds(1));

		fel("admin", "10.0.0.1", 1);

		assertThat(limiter.blockedFor("admin", "10.0.0.1")).contains(SPARR);
	}

	@Test
	void lyckadInloggningNollstallerRaknarenForAnvandarnamnet() {
		fel("admin", "10.0.0.1", 4);

		limiter.recordSuccess("admin");
		fel("admin", "10.0.0.2", 4);

		assertThat(limiter.blockedFor("admin", "10.0.0.3")).isEmpty();
	}

	@Test
	void lyckadInloggningNollstallerInteAdressensRaknare() {
		fel("admin", "10.0.0.1", 4);

		limiter.recordSuccess("admin");
		limiter.recordFailure("annan", "10.0.0.1");

		assertThat(limiter.blockedFor("nagon", "10.0.0.1")).contains(SPARR);
	}

	@Test
	void tidenKvarMinskarOchForlangsInteAvNyaFel() {
		fel("admin", "10.0.0.1", 5);
		clock.advance(Duration.ofMinutes(5));

		// Spärrade försök räknas inte, så de förlänger inte spärren.
		assertThat(limiter.blockedFor("admin", "10.0.0.1")).contains(Duration.ofMinutes(10));
	}

	@Test
	void minnetBegransasNarManyaNamnSkickas() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_TRACKED + 500; i++) {
			limiter.recordFailure("namn-" + i, "10.1." + (i / 250) + "." + (i % 250));
		}

		// Det äldsta har glömts, det senaste finns kvar och räknas.
		fel("namn-" + (LoginAttemptLimiter.MAX_TRACKED + 499), "10.9.9.9", 4);
		assertThat(limiter.blockedFor("namn-" + (LoginAttemptLimiter.MAX_TRACKED + 499), "10.8.8.8")).isPresent();
		fel("namn-0", "10.7.7.7", 4);
		assertThat(limiter.blockedFor("namn-0", "10.6.6.6")).isEmpty();
	}

	private void fel(String namn, String adress, int antal) {
		for (int i = 0; i < antal; i++) {
			limiter.recordFailure(namn, adress);
		}
	}

}
