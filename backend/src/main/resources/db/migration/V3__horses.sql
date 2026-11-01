-- Horse registry (BR-001 to BR-003, BR-006, BR-007). Horses are archived, never deleted (TECH-009).
-- The unique constraint covers archived horses too (BR-002).
CREATE TABLE horse (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100) NOT NULL UNIQUE,
    sex                 VARCHAR(10)  NOT NULL CHECK (sex IN ('MALE', 'FEMALE')),
    birth_date          DATE         NOT NULL,
    coat_colour         VARCHAR(100),
    breed               VARCHAR(100),
    owner_id            BIGINT       NOT NULL REFERENCES user_account (id),
    archived_at         TIMESTAMPTZ,
    archive_reason      VARCHAR(20) CHECK (archive_reason IN ('SOLD', 'RETIRED', 'DECEASED', 'OTHER')),
    archive_note        TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CHECK ((archived_at IS NULL) = (archive_reason IS NULL))
);

CREATE INDEX horse_owner_idx ON horse (owner_id);

-- Two-generation pedigree (BR-004, TECH-010): one row per known ancestor position.
CREATE TABLE pedigree_ancestor (
    id                BIGSERIAL PRIMARY KEY,
    horse_id          BIGINT      NOT NULL REFERENCES horse (id),
    position          VARCHAR(10) NOT NULL
        CHECK (position IN ('SIRE', 'DAM', 'SIRE_SIRE', 'SIRE_DAM', 'DAM_SIRE', 'DAM_DAM')),
    ancestor_horse_id BIGINT REFERENCES horse (id),
    ancestor_name     VARCHAR(200),
    UNIQUE (horse_id, position),
    CHECK ((ancestor_horse_id IS NULL) <> (ancestor_name IS NULL)),
    CHECK (ancestor_horse_id IS NULL OR ancestor_horse_id <> horse_id)
);
