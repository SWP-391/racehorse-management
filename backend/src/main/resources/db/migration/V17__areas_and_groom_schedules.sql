-- Stable areas with a capacity, stalls as area plus slot, fixed weekly barn hours and leave of a Groom, and the
-- "làm thay" flag of a chore mark (BR-129, BR-163 to BR-169, TECH-141, TECH-144, TECH-145, TECH-147).

CREATE TABLE area (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    capacity   INTEGER     NOT NULL CHECK (capacity BETWEEN 1 AND 200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);

-- The name is unique ignoring case (BR-163).
CREATE UNIQUE INDEX area_name_unique ON area (lower(name));

-- The old free-text stall: one area per distinct spelling, then each horse is placed when its number is a whole
-- number from 1 to 200 and it is the first horse (by id) for that slot; any other stall is parked as legacy text
-- for the Club Manager to place again. Nothing is lost.
ALTER TABLE horse DROP CONSTRAINT horse_stall_both_or_none;
DROP INDEX horse_stall_unique;

ALTER TABLE horse
    ADD COLUMN area_id       BIGINT   REFERENCES area (id),
    ADD COLUMN stall_slot    INTEGER CHECK (stall_slot >= 1),
    ADD COLUMN legacy_stall  TEXT,
    ADD CONSTRAINT horse_stall_both_or_none CHECK ((area_id IS NULL) = (stall_slot IS NULL));

INSERT INTO area (name, capacity)
SELECT DISTINCT ON (lower(btrim(stall_area))) btrim(stall_area), 6
FROM horse
WHERE stall_area IS NOT NULL AND btrim(stall_area) <> ''
ORDER BY lower(btrim(stall_area)), id;

WITH numbered AS (
    SELECT h.id AS horse_id, a.id AS area_id,
           CASE WHEN btrim(h.stall_number) ~ '^[0-9]{1,3}$' THEN btrim(h.stall_number)::int END AS slot
    FROM horse h
    JOIN area a ON lower(a.name) = lower(btrim(h.stall_area))
    WHERE h.stall_area IS NOT NULL
), ranked AS (
    SELECT horse_id, area_id, slot,
           row_number() OVER (PARTITION BY area_id, slot ORDER BY horse_id) AS rn
    FROM numbered
    WHERE slot BETWEEN 1 AND 200
)
UPDATE horse
SET area_id = ranked.area_id, stall_slot = ranked.slot
FROM ranked
WHERE ranked.horse_id = horse.id AND ranked.rn = 1;

UPDATE horse
SET legacy_stall = stall_area || ', ô ' || stall_number
WHERE stall_area IS NOT NULL AND area_id IS NULL;

UPDATE area a
SET capacity = LEAST(200, GREATEST(6,
        COALESCE((SELECT max(h.stall_slot) FROM horse h WHERE h.area_id = a.id), 0),
        (SELECT count(*) FROM horse h WHERE lower(btrim(h.stall_area)) = lower(a.name))::int))::int;

ALTER TABLE horse DROP COLUMN stall_area, DROP COLUMN stall_number;

-- One horse per stall (BR-164); the last guard against two placements at the same moment.
CREATE UNIQUE INDEX horse_stall_unique ON horse (area_id, stall_slot) WHERE area_id IS NOT NULL;

-- Fixed weekly barn hours of a Groom: minutes since midnight so that 24:00 is a valid end (BR-166).
CREATE TABLE groom_work_schedule (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT   NOT NULL REFERENCES user_account (id),
    weekday    INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_min  INTEGER NOT NULL CHECK (start_min BETWEEN 0 AND 1439),
    end_min    INTEGER NOT NULL CHECK (end_min BETWEEN 1 AND 1440),
    CHECK (end_min > start_min)
);

CREATE INDEX groom_work_schedule_account_idx ON groom_work_schedule (account_id, weekday);

-- Leave is a record: cancelling keeps the row, nothing is deleted (BR-167, P-02).
CREATE TABLE groom_leave (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT      NOT NULL REFERENCES user_account (id),
    leave_date   DATE        NOT NULL,
    notice_days  INT         NOT NULL,
    late         BOOLEAN     NOT NULL,
    reason       TEXT        NULL,
    recorded_by  BIGINT      NOT NULL REFERENCES user_account (id),
    recorded_at  TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ NULL,
    cancelled_by BIGINT      NULL REFERENCES user_account (id)
);

CREATE UNIQUE INDEX groom_leave_active_unique ON groom_leave (account_id, leave_date) WHERE cancelled_at IS NULL;
CREATE INDEX groom_leave_date_idx ON groom_leave (leave_date);

CREATE TRIGGER groom_leave_no_delete
    BEFORE DELETE ON groom_leave
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- A mark made outside the Groom's barn hours or on leave is shown as "làm thay" (BR-169); old marks are not.
ALTER TABLE chore_entry ADD COLUMN on_behalf BOOLEAN NOT NULL DEFAULT FALSE;

-- Notice, in days, for a leave to count as planned (BR-167).
INSERT INTO club_setting (key, value) VALUES ('leave_notice_days', '7');
