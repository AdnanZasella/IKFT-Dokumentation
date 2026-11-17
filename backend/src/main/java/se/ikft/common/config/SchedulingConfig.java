package se.ikft.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Slår på {@code @Scheduled}. Jobben anger själva tid och tidszon ({@link TimeConfig#ZONE_ID}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
