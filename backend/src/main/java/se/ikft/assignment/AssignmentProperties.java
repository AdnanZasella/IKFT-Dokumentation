package se.ikft.assignment;

import java.time.MonthDay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Inställningar för dokumenteraruppdrag ({@code ikft.assignment.*}).
 *
 * @param reminderFrom från och med detta datum varje år påminns Admin om att nästa års dokumenterare inte är
 * utsedd, skrivet som {@code --MM-dd}, till exempel {@code --12-01}
 */
@ConfigurationProperties("ikft.assignment")
public record AssignmentProperties(@DefaultValue("--12-01") MonthDay reminderFrom) {
}
