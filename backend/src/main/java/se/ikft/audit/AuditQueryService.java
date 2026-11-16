package se.ikft.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.Role;

/**
 * Läser ändringsloggen för en post, senaste ändringen först.
 */
@Service
class AuditQueryService {

	private final JdbcClient jdbc;

	AuditQueryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	List<AuditEntry> history(AuditEntity entity, UUID entityId) {
		List<Row> rows = jdbc.sql("""
				SELECT a.id, a.action, ua.role AS account_role, m.first_name || ' ' || m.last_name AS holder_name,
				       a.created_at
				FROM audit_log a
				LEFT JOIN user_account ua ON ua.id = a.account_id
				LEFT JOIN member m ON m.id = a.holder_member_id
				WHERE a.entity_type = :entityType AND a.entity_id = :entityId
				ORDER BY a.seq DESC
				""")
			.param("entityType", entity.name())
			.param("entityId", entityId)
			.query((rs, rowNum) -> new Row(rs.getObject("id", UUID.class), rs.getString("action"),
					rs.getString("account_role"), rs.getString("holder_name"),
					rs.getTimestamp("created_at").toInstant()))
			.list();
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<FieldChange>> changes = new LinkedHashMap<>();
		jdbc.sql("""
				SELECT audit_log_id, field, before_value, after_value
				FROM audit_log_change
				WHERE audit_log_id IN (:ids)
				ORDER BY id
				""")
			.param("ids", rows.stream().map(Row::id).toList())
			.query((rs, rowNum) -> {
				changes.computeIfAbsent(rs.getObject("audit_log_id", UUID.class), key -> new ArrayList<>())
					.add(new FieldChange(rs.getString("field"), rs.getString("before_value"),
							rs.getString("after_value")));
				return null;
			})
			.list();
		return rows.stream()
			.map(row -> new AuditEntry(row.id(), entity, entityId, AuditAction.valueOf(row.action()),
					row.accountRole() == null ? null : Role.valueOf(row.accountRole()), row.holderName(),
					row.createdAt(), changes.getOrDefault(row.id(), List.of())))
			.toList();
	}

	private record Row(UUID id, String action, String accountRole, String holderName, Instant createdAt) {
	}

}
