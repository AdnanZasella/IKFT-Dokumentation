-- Dokumenteraruppdrag: vilken medlem som är aktiv dokumenterare under en period. Det är uppdraget, inte
-- inloggningen, som avgör vem som är ansvarig. Uppdraget gäller ett år i taget, och ett tillfälligt
-- uppdrag har ett bestämt slutdatum.

CREATE TABLE documenter_assignment (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id  uuid         NOT NULL REFERENCES member (id),
    type       varchar(20)  NOT NULL CHECK (type IN ('ORDINARY', 'TEMPORARY')),
    start_date date         NOT NULL,
    -- Sista dagen uppdraget gäller (inklusive).
    end_date   date         NOT NULL,
    -- Satt när uppdraget avslutats i förtid. Uppdraget gäller då inte från och med det datumet.
    ended_at   timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date),
    -- Ett ordinarie uppdrag gäller som längst till och med 31 december samma år som det börjar.
    CHECK (type <> 'ORDINARY' OR end_date <= make_date(extract(year FROM start_date)::int, 12, 31))
);

CREATE INDEX documenter_assignment_member_idx ON documenter_assignment (member_id);
CREATE INDEX documenter_assignment_end_date_idx ON documenter_assignment (end_date);
