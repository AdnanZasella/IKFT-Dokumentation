package se.ikft.auth;

import java.time.Duration;

import jakarta.validation.constraints.Positive;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Inställningar för lösenord och engångskoder ({@code ikft.auth.*}). Ogiltiga värden stoppar
 * uppstarten, i stället för att t.ex. ge koder som redan gått ut.
 *
 * @param setupCodeValidity hur länge en engångskod gäller, minst en minut
 * @param passwordMinLength minsta antal tecken i ett lösenord
 */
@Validated
@ConfigurationProperties("ikft.auth")
public record AuthProperties(@DefaultValue("72h") @DurationMin(minutes = 1) Duration setupCodeValidity,
		@DefaultValue("12") @Positive int passwordMinLength) {
}
