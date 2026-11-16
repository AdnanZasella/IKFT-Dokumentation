-- Moskéavgift via FIFS per år och föreningsroller över tid. Föreningens egen medlemsavgift läggs till senare.

CREATE TABLE member_fifs_fee (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id  uuid        NOT NULL REFERENCES member (id),
    year       integer     NOT NULL CHECK (year BETWEEN 2000 AND 2200),
    -- Dagen då avgiften markerades som betald.
    paid_on    date        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (member_id, year)
);

CREATE TABLE member_role (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id  uuid         NOT NULL REFERENCES member (id),
    title      varchar(100) NOT NULL,
    start_date date         NOT NULL,
    -- Null medan rollen pågår.
    end_date   date,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX member_role_member_id_idx ON member_role (member_id);
