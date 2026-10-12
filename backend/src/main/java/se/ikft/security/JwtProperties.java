package se.ikft.security;

import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Inställningar för inloggningstoken ({@code ikft.security.jwt.*}). Nyckeln har ingen standard och
 * ska komma från miljövariabeln {@code IKFT_SECURITY_JWT_SECRET}. Saknas den, eller är den ogiltig,
 * startar appen inte. Som engångskommando utan webbserver behövs den inte, se {@link SecurityConfig}.
 *
 * @param secret nyckeln för HS256 som base64, minst 32 byte efter avkodning
 * @param validity hur länge en token gäller, minst en minut
 */
@Validated
@ConditionalOnWebApplication
@ConfigurationProperties("ikft.security.jwt")
public record JwtProperties(@NotBlank String secret,
		@DefaultValue("15m") @DurationMin(minutes = 1) Duration validity) {

	/** HS256 kräver en nyckel på minst 256 bitar. */
	static final int MIN_KEY_BYTES = 32;

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	public JwtProperties {
		if (secret != null && !secret.isBlank()) {
			decode(secret);
		}
	}

	SecretKey key() {
		return new SecretKeySpec(decode(secret), "HmacSHA256");
	}

	/** Utan nyckeln, så att den inte hamnar i loggen om objektet skrivs ut. */
	@Override
	public String toString() {
		return "JwtProperties[secret=****, validity=%s]".formatted(validity);
	}

	private static byte[] decode(String secret) {
		byte[] bytes;
		try {
			// Utan blanktecken, eftersom t.ex. openssl bryter raden efter 64 tecken.
			bytes = Base64.getDecoder().decode(WHITESPACE.matcher(secret).replaceAll(""));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("ikft.security.jwt.secret måste vara base64.", ex);
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalArgumentException(
					"ikft.security.jwt.secret måste vara minst %d byte.".formatted(MIN_KEY_BYTES));
		}
		return bytes;
	}

}
