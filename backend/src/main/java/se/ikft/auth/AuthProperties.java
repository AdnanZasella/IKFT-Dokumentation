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
 * @param resetAdminPassword skapar en ny engångskod för Admin vid start även om kontot har lösenord,
 * se {@link AdminSetupCodeInitializer}. Ska bara vara satt under återställningen.
 * @param loginMaxAttempts antal misslyckade inloggningar per användarnamn eller IP-adress innan inloggningen
 * spärras, se {@link LoginAttemptLimiter}
 * @param loginLockout hur länge inloggningen är spärrad, minst en minut. Det är också hur länge ett
 * misslyckat försök räknas.
 */
@Validated
@ConfigurationProperties("ikft.auth")
public record AuthProperties(@DefaultValue("24h") @DurationMin(minutes = 1) Duration setupCodeValidity,
		@DefaultValue("12") @Positive int passwordMinLength, @DefaultValue("false") boolean resetAdminPassword,
		@DefaultValue("5") @Positive int loginMaxAttempts,
		@DefaultValue("15m") @DurationMin(minutes = 1) Duration loginLockout) {
}
