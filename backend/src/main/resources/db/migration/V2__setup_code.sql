-- Engångskoder för att sätta lösenord: det första Admin-lösenordet och som reservväg.
-- Koden lagras aldrig i klartext, bara som SHA-256-hash.

CREATE TABLE setup_code (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id uuid        NOT NULL REFERENCES user_account (id),
    code_hash       varchar(64) NOT NULL,
    -- Sätts till nu när en ny kod skapas för kontot, så att den gamla slutar gälla.
    valid_until     timestamptz NOT NULL,
    -- Null tills koden använts.
    used_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX setup_code_user_account_id_idx ON setup_code (user_account_id);
