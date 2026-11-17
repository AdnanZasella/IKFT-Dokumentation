package se.ikft.assignment;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.account.Role;
import se.ikft.assignment.DocumenterAppointmentService.Appointment;
import se.ikft.security.CurrentAccount;

/**
 * Dokumenteraruppdrag under {@code /api/v1/documenter-assignment}.
 */
@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/documenter-assignment")
class DocumenterAssignmentController {

	private final DocumenterAssignmentService service;

	private final DocumenterAppointmentService appointments;

	DocumenterAssignmentController(DocumenterAssignmentService service, DocumenterAppointmentService appointments) {
		this.service = service;
		this.appointments = appointments;
	}

	/**
	 * Utser en ordinarie dokumenterare från startdatumet till och med 31 december samma år. 201 när ett uppdrag
	 * skapades, och 200 med det befintliga när medlemmen redan har ett ordinarie uppdrag som gäller
	 * startdatumet.
	 */
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<AppointmentResponse> appoint(@Valid @RequestBody AppointRequest request) {
		Appointment appointment = appointments.appointOrdinary(request.memberId(), request.startDate());
		return ResponseEntity.status(appointment.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(AppointmentResponse.of(appointment));
	}

	/**
	 * Aktiv dokumenterare, eller att inget uppdrag är satt. Admin får också veta om nästa års dokumenterare är
	 * utsedd och om en påminnelse ska visas.
	 */
	@GetMapping("/current")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCUMENTER')")
	CurrentAssignmentResponse current(Authentication authentication) {
		if (CurrentAccount.role(authentication) == Role.ADMIN) {
			boolean nextYearAssigned = service.nextYearAssigned();
			return CurrentAssignmentResponse.of(service.current(), nextYearAssigned,
					service.reminderDue(nextYearAssigned));
		}
		return CurrentAssignmentResponse.of(service.current(), null, null);
	}

}
