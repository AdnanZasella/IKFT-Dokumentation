package se.ikft.common.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bidragsreglerna som är konfigurerbara och inte hårdkodade ({@code ikft.grant.*}). Fler regler, som
 * minsta längd och deltagarantal, läggs till här när sammankomsterna byggs.
 *
 * @param minAge lägsta ålder som räknas i barn- och ungdomsverksamheten
 * @param maxAge högsta ålder som räknas
 */
@Validated
@ConfigurationProperties("ikft.grant")
public record GrantProperties(@DefaultValue("7") @Positive int minAge, @DefaultValue("25") @Positive int maxAge) {

	@AssertTrue(message = "ikft.grant.max-age får inte vara lägre än ikft.grant.min-age")
	boolean isAgeRangeValid() {
		return maxAge >= minAge;
	}

}
