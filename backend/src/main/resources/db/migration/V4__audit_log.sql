-- Ändringsloggen. Den sparar vem som ändrade vad och när, men bara få värden: de fält som påverkar
-- bidragssiffrorna (t.ex. födelsedatum och hemkommun) sparas med före- och efter-värde, övriga fält
-- (t.ex. namn och adress) bara som att de ändrats. Det håller personuppgifterna i loggen till ett minimum.

CREATE TABLE audit_log (
    id               uuid         PRIMARY KEY,
    -- Ger en stabil ordning även när två poster får samma tidpunkt.
    seq              bigint       GENERATED ALWAYS AS IDENTITY,
    entity_type      varchar(30)  NOT NULL CHECK (entity_type IN ('MEMBER')),
    entity_id        uuid         NOT NULL,
    action           varchar(20)  NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'DEACTIVATED')),
    -- Kontot som gjorde ändringen. Null om ändringen inte gjordes av en inloggad användare.
    account_id       uuid         REFERENCES user_account (id),
    -- Kontots innehavare då ändringen gjordes. Null om kontot saknade innehavare.
    holder_member_id uuid         REFERENCES member (id),
    created_at       timestamptz  NOT NULL
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, seq DESC);

CREATE TABLE audit_log_change (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_log_id uuid         NOT NULL REFERENCES audit_log (id),
    field        varchar(50)  NOT NULL,
    -- Null för fält som bara loggas som ändrade, och för värden som saknades.
    before_value varchar(255),
    after_value  varchar(255)
);

CREATE INDEX audit_log_change_audit_log_id_idx ON audit_log_change (audit_log_id);
