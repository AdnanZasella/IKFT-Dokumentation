package se.ikft.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeGroupTest {

	private static final LocalDate DAGEN = LocalDate.of(2026, 6, 15);

	@Test
	void aldernRaknasPerDatumOchStegarPaFodelsedagen() {
		LocalDate fodd = LocalDate.of(2000, 6, 15);

		assertThat(AgeGroup.ageOn(fodd, LocalDate.of(2026, 6, 14))).isEqualTo(25);
		assertThat(AgeGroup.ageOn(fodd, LocalDate.of(2026, 6, 15))).isEqualTo(26);
	}

	@Test
	void skottdagsbarnFyllerArNarDetFinnsEnDag() {
		LocalDate fodd = LocalDate.of(2000, 2, 29);

		assertThat(AgeGroup.ageOn(fodd, LocalDate.of(2026, 2, 28))).isEqualTo(25);
		assertThat(AgeGroup.ageOn(fodd, LocalDate.of(2026, 3, 1))).isEqualTo(26);
	}

	/** Gränserna 7 och 25 ingår i ungdomsgruppen. Dagen före sjuårsdagen är barnet fortfarande sex år. */
	@ParameterizedTest
	@CsvSource({ "2020-06-16, YOUNGER", // 5 år
			"2019-06-16, YOUNGER", // 6 år, dagen före sjuårsdagen
			"2019-06-15, YOUTH", // exakt 7 år
			"2001-06-16, YOUTH", // 24 år
			"2001-06-15, YOUTH", // exakt 25 år
			"2000-06-16, YOUTH", // 25 år, dagen före 26-årsdagen
			"2000-06-15, OLDER" // exakt 26 år
	})
	void gransernaArInraknade(LocalDate fodd, AgeGroup forvantad) {
		assertThat(AgeGroup.of(fodd, DAGEN, 7, 25)).isEqualTo(forvantad);
	}

	@Test
	void gransernaGarAttAndra() {
		LocalDate fodd = DAGEN.minusYears(6);

		assertThat(AgeGroup.of(fodd, DAGEN, 7, 25)).isEqualTo(AgeGroup.YOUNGER);
		assertThat(AgeGroup.of(fodd, DAGEN, 6, 25)).isEqualTo(AgeGroup.YOUTH);
		assertThat(AgeGroup.of(fodd, DAGEN, 3, 5)).isEqualTo(AgeGroup.OLDER);
	}

}
