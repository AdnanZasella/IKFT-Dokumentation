-- Att utse en dokumenterare loggas i ändringsloggen med uppdraget som post. Utan ändringen avvisar databasen
-- entity_type DOCUMENTER_ASSIGNMENT.

ALTER TABLE audit_log DROP CONSTRAINT audit_log_entity_type_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_entity_type_check
    CHECK (entity_type IN ('MEMBER', 'ACCOUNT', 'DOCUMENTER_ASSIGNMENT'));
