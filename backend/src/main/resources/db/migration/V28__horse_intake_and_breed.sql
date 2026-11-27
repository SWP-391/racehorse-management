-- Horse intake state and breed catalog (BR-243 to BR-246, DEC-080, DEC-081, TECH-B1, TECH-B3).

-- A registered horse waits for the Club Manager; existing rows count as taken in (AC-545).
ALTER TABLE horse
    ADD COLUMN intake_state   VARCHAR(10) NOT NULL DEFAULT 'ACCEPTED'
        CHECK (intake_state IN ('PENDING', 'ACCEPTED', 'REFUSED', 'WITHDRAWN')),
    ADD COLUMN refusal_reason TEXT,
    ADD COLUMN accepted_at    TIMESTAMPTZ;

UPDATE horse SET accepted_at = created_at;

ALTER TABLE horse ALTER COLUMN intake_state SET DEFAULT 'PENDING';
ALTER TABLE horse ALTER COLUMN main_trainer_id DROP NOT NULL;
ALTER TABLE horse
    ADD CONSTRAINT horse_intake_ck CHECK (
        (intake_state = 'ACCEPTED') = (accepted_at IS NOT NULL)
        AND (intake_state <> 'REFUSED' OR refusal_reason IS NOT NULL));
CREATE INDEX horse_intake_state_idx ON horse (intake_state);

-- Breed catalog; entries are never deleted, the protected entry "Khác" is never deactivated.
CREATE TABLE breed (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(100) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    protected_entry BOOLEAN      NOT NULL DEFAULT FALSE,
    CHECK (NOT protected_entry OR active)
);
CREATE UNIQUE INDEX breed_name_lower_uq ON breed (lower(btrim(name)));

INSERT INTO breed (name, protected_entry) VALUES
    ('Thoroughbred', FALSE), ('Arabian', FALSE), ('Anglo-Arab', FALSE), ('Quarter Horse', FALSE),
    ('Standardbred', FALSE), ('Ngựa nội Việt Nam', FALSE), ('Khác', TRUE);

ALTER TABLE horse ADD COLUMN breed_id BIGINT REFERENCES breed (id);
CREATE INDEX horse_breed_idx ON horse (breed_id);

-- Old free text: case-insensitive trimmed match to an entry, else "Khác"; empty text stays without a breed (BR-003).
UPDATE horse h SET breed_id = COALESCE(
    (SELECT b.id FROM breed b WHERE lower(btrim(b.name)) = lower(btrim(h.breed))),
    (SELECT b.id FROM breed b WHERE b.protected_entry))
WHERE h.breed IS NOT NULL AND btrim(h.breed) <> '';

ALTER TABLE horse DROP COLUMN breed;
