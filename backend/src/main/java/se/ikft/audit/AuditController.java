package se.ikft.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ändringsloggen under {@code /api/v1/audit-log}. Bara Admin kommer åt den.
 */
@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/audit-log")
class AuditController {

	private final AuditQueryService queries;

	AuditController(AuditQueryService queries) {
		this.queries = queries;
	}

	/** Loggen för en post, senaste ändringen först. En post utan ändringar ger en tom lista. */
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<AuditEntry> history(@RequestParam AuditEntity entity, @RequestParam UUID id) {
		return queries.history(entity, id);
	}

}
