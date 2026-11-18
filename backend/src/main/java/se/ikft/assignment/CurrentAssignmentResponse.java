package se.ikft.assignment;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import se.ikft.assignment.DocumenterAssignmentService.ActiveHolder;

/**
 * Svaret på {@code GET /documenter-assignment/current}.
 *
 * @param assigned om något uppdrag är satt. Om inte är fälten om dokumenteraren tomma.
 * @param memberId aktiv dokumenterare
 * @param memberName aktiv dokumenterares namn
 * @param type ordinarie eller tillfälligt
 * @param endDate sista dagen uppdraget gäller
 * @param nextYearAssigned bara för Admin: om nästa års dokumenterare redan är utsedd
 * @param reminder bara för Admin: om Admin ska påminnas om att utse nästa års dokumenterare
 */
record CurrentAssignmentResponse(boolean assigned, @Schema(nullable = true) UUID memberId,
		@Schema(nullable = true) String memberName, @Schema(nullable = true) AssignmentType type,
		@Schema(nullable = true) LocalDate endDate,
		@Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Boolean nextYearAssigned,
		@Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Boolean reminder) {

	static CurrentAssignmentResponse of(Optional<ActiveHolder> holder, Boolean nextYearAssigned, Boolean reminder) {
		return holder
			.map(active -> new CurrentAssignmentResponse(true, active.memberId(), active.memberName(), active.type(),
					active.endDate(), nextYearAssigned, reminder))
			.orElseGet(() -> new CurrentAssignmentResponse(false, null, null, null, null, nextYearAssigned,
					reminder));
	}

}
