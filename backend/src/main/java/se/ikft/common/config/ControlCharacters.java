package se.ikft.common.config;

/**
 * Vilka tecken som får förekomma i text från klienten. Kontrolltecken som NUL är aldrig text, och
 * Postgres godtar inte NUL alls. Radbrytning, vagnretur och tabb är tillåtna, eftersom längre texter kan
 * innehålla dem.
 */
public final class ControlCharacters {

	private ControlCharacters() {
	}

	/** Om texten innehåller ett kontrolltecken som inte är radbrytning, vagnretur eller tabb. */
	public static boolean containsDisallowed(String text) {
		return text.chars().anyMatch(ControlCharacters::isDisallowed);
	}

	private static boolean isDisallowed(int character) {
		return Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t';
	}

}
