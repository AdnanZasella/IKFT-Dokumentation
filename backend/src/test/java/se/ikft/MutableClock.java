package se.ikft;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * En klocka som tester kan flytta fram, för tidslogik som spärrtider.
 */
public final class MutableClock extends Clock {

	private Instant now;

	private final ZoneId zone;

	public MutableClock(Instant start) {
		this(start, ZoneId.of("Europe/Stockholm"));
	}

	private MutableClock(Instant start, ZoneId zone) {
		this.now = start;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}

}
