package se.ikft.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Inställningar för lösenord och engångskoder ({@code ikft.auth.*}).
 *
 * @param setupCodeValidity hur länge en engångskod gäller
 * @param passwordMinLength minsta antal tecken i ett lösenord
 */
@ConfigurationProperties("ikft.auth")
public record AuthProperties(@DefaultValue("72h") Duration setupCodeValidity,
		@DefaultValue("12") int passwordMinLength) {
}
