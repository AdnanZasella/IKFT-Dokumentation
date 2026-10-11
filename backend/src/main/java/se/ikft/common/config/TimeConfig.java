package se.ikft.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All tidslogik (årsskifte, uppdragsperioder, spärrtider) går via {@link Clock} så att tester kan
 * styra tiden.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	/** Föreningens tidszon. Årsskifte och datum räknas alltid i denna. */
	public static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}

}
