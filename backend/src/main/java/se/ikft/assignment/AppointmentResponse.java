package se.ikft.assignment;

import java.time.LocalDate;
import java.util.UUID;

import se.ikft.assignment.DocumenterAppointmentService.Appointment;

/**
 * Svaret på {@code POST /documenter-assignment}.
 *
 * @param id uppdragets id
 * @param memberId medlemmen som har uppdraget
 * @param memberName medlemmens namn
 * @param type ordinarie eller tillfälligt
 * @param startDate första dagen uppdraget gäller
 * @param endDate sista dagen uppdraget gäller
 */
record AppointmentResponse(UUID id, UUID memberId, String memberName, AssignmentType type, LocalDate startDate,
		LocalDate endDate) {

	static AppointmentResponse of(Appointment appointment) {
		return new AppointmentResponse(appointment.id(), appointment.memberId(), appointment.memberName(),
				appointment.type(), appointment.startDate(), appointment.endDate());
	}

}
