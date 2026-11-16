package se.ikft.common.error;

import java.time.Duration;

/**
 * Ger 429 med {@code Retry-After}, till exempel när inloggningen är spärrad efter för många försök.
 */
public class TooManyRequestsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyRequestsException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	/** Hur länge klienten ska vänta innan nästa försök. */
	public Duration getRetryAfter() {
		return retryAfter;
	}

}
