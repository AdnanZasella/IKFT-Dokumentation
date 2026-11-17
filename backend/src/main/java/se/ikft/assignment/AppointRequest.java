package se.ikft.assignment;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Det som skickas för att utse en ordinarie dokumenterare. Att startdatumet inte ligger före i dag och inte
 * efter nästa år kontrolleras i {@link DocumenterAppointmentService}, som har den injicerade klockan.
 *
 * @param memberId medlemmen som utses
 * @param startDate första dagen uppdraget gäller
 */
record AppointRequest(@NotNull UUID memberId, @NotNull LocalDate startDate) {
}
