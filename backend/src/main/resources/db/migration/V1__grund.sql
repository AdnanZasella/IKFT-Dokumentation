-- Grundschema: medlemsregister och de två delade kontona.
-- Personnummer lagras aldrig, bara födelsedatum (dataminimering).

CREATE TABLE member (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name        varchar(100) NOT NULL,
    last_name         varchar(100) NOT NULL,
    birth_date        date         NOT NULL,
    address           varchar(255),
    home_municipality varchar(100) NOT NULL,
    member_since      date,
    status            varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                                   CHECK (status IN ('ACTIVE', 'INACTIVE')),
    guardian_consent  boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE user_account (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username         varchar(50)  NOT NULL UNIQUE,
    role             varchar(20)  NOT NULL CHECK (role IN ('ADMIN', 'DOCUMENTER')),
    -- Null tills innehavaren satt ett lösenord med engångskod.
    password_hash    varchar(100),
    -- Räknas upp vid innehavarbyte och lösenordsbyte så att gamla tokens blir ogiltiga.
    token_version    integer      NOT NULL DEFAULT 0,
    holder_member_id uuid         REFERENCES member (id),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

INSERT INTO user_account (username, role) VALUES
    ('admin', 'ADMIN'),
    ('dokumenterare', 'DOCUMENTER');