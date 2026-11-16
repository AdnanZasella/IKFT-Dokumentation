package se.ikft.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import se.ikft.account.UserAccount;

/**
 * Begränsar misslyckade inloggningar per användarnamn och per IP-adress. Efter
 * {@link AuthProperties#loginMaxAttempts()} misslyckade försök spärras användarnamnet, respektive
 * adressen, under {@link AuthProperties#loginLockout()}. Räknaren hålls i minnet, eftersom appen körs som
 * en enda instans. Den nollställs vid omstart och delas inte mellan instanser.
 * <p>
 * Användarnamn räknas oavsett om kontot finns, så att en spärr inte avslöjar vilka konton som finns.
 * Försök under en spärr räknas inte och förlänger den inte.
 */
@Component
@ConditionalOnWebApplication
class LoginAttemptLimiter {

	/**
	 * Högsta antal användarnamn respektive adresser som minnet håller reda på, så att någon som skickar
	 * många olika namn inte kan fylla minnet. När taket nås glöms äldsta posten bort.
	 */
	static final int MAX_TRACKED = 10_000;

	private final Clock clock;

	private final int maxAttempts;

	private final Duration lockout;

	private final Map<String, Entry> byUsername = new LinkedHashMap<>();

	private final Map<String, Entry> byAddress = new LinkedHashMap<>();

	LoginAttemptLimiter(Clock clock, AuthProperties properties) {
		this.clock = clock;
		this.maxAttempts = properties.loginMaxAttempts();
		this.lockout = properties.loginLockout();
	}

	/** Hur länge inloggningen är spärrad för användarnamnet eller adressen, eller tom om den inte är det. */
	synchronized Optional<Duration> blockedFor(String username, String address) {
		Instant now = clock.instant();
		Duration byName = remaining(byUsername, usernameKey(username), now);
		Duration byIp = remaining(byAddress, address, now);
		Duration longest = byName.compareTo(byIp) >= 0 ? byName : byIp;
		return longest.isZero() ? Optional.empty() : Optional.of(longest);
	}

	/** Räknar ett misslyckat försök. Det som slår i taket spärrar. */
	synchronized void recordFailure(String username, String address) {
		Instant now = clock.instant();
		fail(byUsername, usernameKey(username), now);
		fail(byAddress, address, now);
	}

	/** En lyckad inloggning nollställer räknaren för användarnamnet, men inte för adressen. */
	synchronized void recordSuccess(String username) {
		byUsername.remove(usernameKey(username));
	}

	synchronized void clear() {
		byUsername.clear();
		byAddress.clear();
	}

	private Duration remaining(Map<String, Entry> entries, String key, Instant now) {
		Entry entry = current(entries, key, now);
		if (entry == null || entry.lockedUntil == null) {
			return Duration.ZERO;
		}
		return Duration.between(now, entry.lockedUntil);
	}

	private void fail(Map<String, Entry> entries, String key, Instant now) {
		Entry entry = current(entries, key, now);
		if (entry == null) {
			entry = new Entry(now);
			entries.put(key, entry);
			trim(entries, now);
		}
		entry.failures++;
		entry.lastFailure = now;
		if (entry.failures >= maxAttempts && entry.lockedUntil == null) {
			entry.lockedUntil = now.plus(lockout);
		}
	}

	/** Posten för nyckeln, eller null. En post vars spärr eller räknefönster har gått ut tas bort. */
	private Entry current(Map<String, Entry> entries, String key, Instant now) {
		Entry entry = entries.get(key);
		if (entry != null && entry.isStale(now, lockout)) {
			entries.remove(key);
			return null;
		}
		return entry;
	}

	private void trim(Map<String, Entry> entries, Instant now) {
		if (entries.size() <= MAX_TRACKED) {
			return;
		}
		entries.values().removeIf(entry -> entry.isStale(now, lockout));
		for (Iterator<String> oldest = entries.keySet().iterator(); entries.size() > MAX_TRACKED
				&& oldest.hasNext();) {
			oldest.next();
			oldest.remove();
		}
	}

	/** Samma namn med olika versaler räknas som ett, och långa namn kortas så att nycklarna inte växer. */
	private static String usernameKey(String username) {
		String key = username.strip().toLowerCase(Locale.ROOT);
		return key.length() > UserAccount.MAX_USERNAME_LENGTH + 1 ? key.substring(0, UserAccount.MAX_USERNAME_LENGTH + 1)
				: key;
	}

	private static final class Entry {

		int failures;

		Instant lastFailure;

		Entry(Instant now) {
			this.lastFailure = now;
		}

		/** Null tills spärren slagit till. */
		Instant lockedUntil;

		/** Spärren har släppt, eller så var det längre än spärrtiden sedan det senaste felet. */
		boolean isStale(Instant now, Duration lockout) {
			return lockedUntil != null ? !now.isBefore(lockedUntil) : !now.isBefore(lastFailure.plus(lockout));
		}

	}

}
