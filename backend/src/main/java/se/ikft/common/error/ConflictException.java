package se.ikft.common.error;

/**
 * Begäran krockar med befintligt tillstånd, t.ex. en dubblett. Blir 409 i API:et.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
