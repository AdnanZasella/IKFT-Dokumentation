package se.ikft.common.error;

/**
 * Den efterfrågade posten finns inte. Blir 404 i API:et.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
