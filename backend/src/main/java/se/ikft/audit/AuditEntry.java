package se.ikft.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import se.ikft.account.Role;

/**
 * En post i ändringsloggen, som Admin ser den.
 *
 * @param accountRole kontot som gjorde ändringen, eller null om ingen inloggad användare gjorde den
 * @param holderName innehavarens namn då ändringen gjordes, eller null om kontot saknade innehavare
 * @param changes vilka fält som ändrades, i den ordning de loggades
 */
public record AuditEntry(UUID id, AuditEntity entityType, UUID entityId, AuditAction action, Role accountRole,
		String holderName, Instant createdAt, List<FieldChange> changes) {
}
