package se.ikft.assignment;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Det som skickas för att utse en tillfällig dokumenterare. Uppdraget börjar i dag. Att slutdatumet ligger
 * efter i dag kontrolleras i {@link DocumenterAppointmentService}, som har den injicerade klockan.
 *
 * @param memberId medlemmen som utses
 * @param endDate sista dagen uppdraget gäller
 */
record TemporaryAppointRequest(@NotNull UUID memberId, @NotNull LocalDate endDate) {
}
