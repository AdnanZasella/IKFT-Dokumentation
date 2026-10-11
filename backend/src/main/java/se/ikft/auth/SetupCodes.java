package se.ikft.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Skapar, normaliserar och hashar engångskoder. Koderna ska gå att läsa upp muntligt, så de använder
 * Crockfords base32 utan I, L, O och U och visas i grupper om fyra, t.ex. {@code 7KQ2-M9XR-4HTD}.
 */
final class SetupCodes {

	static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

	private static final int LENGTH = 12;

	private static final int GROUP_SIZE = 4;

	private SetupCodes() {
	}

	/** 12 tecken ur 32 möjliga, alltså 60 bitar slump. */
	static String generate(SecureRandom random) {
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < LENGTH; i++) {
			if (i > 0 && i % GROUP_SIZE == 0) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/**
	 * Tar bort bindestreck och blanksteg, gör om till versaler och rättar tecken som lätt förväxlas
	 * (O blir 0, I och L blir 1).
	 */
	static String normalize(String code) {
		StringBuilder normalized = new StringBuilder();
		for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
			if (c == '-' || Character.isWhitespace(c)) {
				continue;
			}
			normalized.append(switch (c) {
				case 'O' -> '0';
				case 'I', 'L' -> '1';
				default -> c;
			});
		}
		return normalized.toString();
	}

	/**
	 * SHA-256 av den normaliserade koden, som hex. En snabb hash räcker eftersom koden har 60 bitar
	 * slump, gäller kort tid och bara kan användas en gång.
	 */
	static String hash(String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).getBytes(UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 saknas i JVM:en", ex);
		}
	}

}
