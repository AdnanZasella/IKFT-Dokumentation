package se.ikft.member;

import java.time.LocalDate;
import java.time.Period;

/**
 * Åldersgrupp i förhållande till bidragets åldersgränser, se {@code ikft.grant.min-age} och
 * {@code ikft.grant.max-age}.
 */
public enum AgeGroup {

	/** Yngre än lägsta åldern. */
	YOUNGER,

	/** Inom bidragets åldersgränser, gränserna inräknade. */
	YOUTH,

	/** Äldre än högsta åldern. */
	OLDER;

	/** Hela år mellan födelsedatumet och {@code date}. Åldern räknas alltid per ett visst datum. */
	public static int ageOn(LocalDate birthDate, LocalDate date) {
		return Period.between(birthDate, date).getYears();
	}

	public static AgeGroup of(LocalDate birthDate, LocalDate date, int minAge, int maxAge) {
		int age = ageOn(birthDate, date);
		if (age < minAge) {
			return YOUNGER;
		}
		return age <= maxAge ? YOUTH : OLDER;
	}

}
