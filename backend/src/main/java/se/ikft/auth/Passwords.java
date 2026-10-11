package se.ikft.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.text.Normalizer;
import java.util.Optional;

/**
 * Regler för lösenord. Lösenord normaliseras till Unicode NFC innan de kontrolleras och hashas, så att
 * t.ex. å ger samma hash oavsett om tangentbordet skickar ett tecken eller a plus en kombinerande ring.
 * Inloggningen måste normalisera på samma sätt.
 */
final class Passwords {

	/** BCrypt använder bara de första 72 byten av lösenordet. */
	static final int BCRYPT_MAX_BYTES = 72;

	/**
	 * Gräns före normaliseringen, så att stora strängar avvisas utan att bearbetas. Ligger långt över
	 * vad som kan bli 72 byte efter normaliseringen.
	 */
	private static final int MAX_RAW_LENGTH = 256;

	static final String TOO_LONG = "Lösenordet är för långt. Det får vara högst 72 byte, där till exempel å, ä och ö räknas som två.";

	private Passwords() {
	}

	static String normalize(String password) {
		return Normalizer.normalize(password, Normalizer.Form.NFC);
	}

	/** Felmeddelandet om lösenordet bryter mot reglerna, annars tomt. */
	static Optional<String> problem(String password, int minLength) {
		if (password.length() > MAX_RAW_LENGTH) {
			return Optional.of(TOO_LONG);
		}
		String normalized = normalize(password);
		if (normalized.codePointCount(0, normalized.length()) < minLength) {
			return Optional.of("Lösenordet måste ha minst %d tecken.".formatted(minLength));
		}
		if (normalized.getBytes(UTF_8).length > BCRYPT_MAX_BYTES) {
			return Optional.of(TOO_LONG);
		}
		return Optional.empty();
	}

}
