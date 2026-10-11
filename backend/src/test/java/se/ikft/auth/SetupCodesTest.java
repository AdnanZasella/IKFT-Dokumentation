package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SetupCodesTest {

	private final SecureRandom random = new SecureRandom();

	@Test
	void kodenHarTolvTeckenIGrupperOmFyraUtanForvaxlingsbaraTecken() {
		for (int i = 0; i < 1000; i++) {
			assertThat(SetupCodes.generate(random))
				.matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");
		}
	}

	@Test
	void koderUpprepasInte() {
		Set<String> koder = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			koder.add(SetupCodes.generate(random));
		}
		assertThat(koder).hasSize(1000);
	}

	@Test
	void normaliseringTarBortBindestreckOchMellanslagOchRattarForvaxlingar() {
		assertThat(SetupCodes.normalize("7kq2-m9xr 4htd")).isEqualTo("7KQ2M9XR4HTD");
		assertThat(SetupCodes.normalize("OIL")).isEqualTo("011");
	}

	@Test
	void normaliseringTarBortTankstreckOchHardaMellanslagFranInklistradeKoder() {
		assertThat(SetupCodes.normalize("7KQ2–M9XR 4HTD")).isEqualTo("7KQ2M9XR4HTD");
	}

	@Test
	void sammaKodGerSammaHashOavsettSkrivsatt() {
		assertThat(SetupCodes.hash("7kq2 m9xr 4htd")).isEqualTo(SetupCodes.hash("7KQ2-M9XR-4HTD"))
			.matches("[0-9a-f]{64}");
	}

}
