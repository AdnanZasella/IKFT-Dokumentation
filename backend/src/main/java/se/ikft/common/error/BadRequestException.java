package se.ikft.common.error;

/**
 * Begäran är ogiltig av ett skäl som inte går att uttrycka med Bean Validation. Blir 400 i API:et.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
