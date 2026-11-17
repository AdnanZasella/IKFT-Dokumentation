package se.ikft.assignment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.account.Role;
import se.ikft.security.CurrentAccount;

/**
 * Dokumenteraruppdrag under {@code /api/v1/documenter-assignment}.
 */
@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/documenter-assignment")
class DocumenterAssignmentController {

	private final DocumenterAssignmentService service;

	DocumenterAssignmentController(DocumenterAssignmentService service) {
		this.service = service;
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
