-- Injury marks on the shared 3D model (BR-096 to BR-103): keyed by region code, never by coordinates.
CREATE TABLE injury_mark (
    id             BIGSERIAL PRIMARY KEY,
    horse_id       BIGINT      NOT NULL REFERENCES horse (id),
    examination_id BIGINT      NOT NULL REFERENCES examination (id),
    region_code    VARCHAR(20) NOT NULL CHECK (region_code IN ('HEAD', 'NECK', 'WITHERS', 'L_SHOULDER', 'R_SHOULDER', 'CHEST', 'BACK', 'LOIN', 'BELLY', 'CROUP', 'L_HIP', 'R_HIP', 'TAIL_BASE', 'LF_ELBOW', 'LF_FOREARM', 'LF_CARPUS', 'LF_CANNON', 'LF_FETLOCK', 'LF_PASTERN', 'LF_HOOF', 'RF_ELBOW', 'RF_FOREARM', 'RF_CARPUS', 'RF_CANNON', 'RF_FETLOCK', 'RF_PASTERN', 'RF_HOOF', 'LH_STIFLE', 'LH_GASKIN', 'LH_HOCK', 'LH_CANNON', 'LH_FETLOCK', 'LH_PASTERN', 'LH_HOOF', 'RH_STIFLE', 'RH_GASKIN', 'RH_HOCK', 'RH_CANNON', 'RH_FETLOCK', 'RH_PASTERN', 'RH_HOOF')),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'RECOVERING', 'RECOVERED')),
    created_by_id  BIGINT      NOT NULL REFERENCES user_account (id),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX injury_mark_horse_idx ON injury_mark (horse_id, created_at DESC);

CREATE TABLE injury_mark_event (
    id           BIGSERIAL PRIMARY KEY,
    mark_id      BIGINT      NOT NULL REFERENCES injury_mark (id),
    from_status  VARCHAR(20) CHECK (from_status IN ('NEW', 'RECOVERING', 'RECOVERED')),
    to_status    VARCHAR(20) NOT NULL CHECK (to_status IN ('NEW', 'RECOVERING', 'RECOVERED')),
    event_date   DATE        NOT NULL,
    note         TEXT,
    by_id        BIGINT      NOT NULL REFERENCES user_account (id),
    at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX injury_mark_event_mark_idx ON injury_mark_event (mark_id, id);

-- History is never overwritten and marks are never deleted (BR-097, BR-103).
CREATE TRIGGER injury_mark_event_append_only
    BEFORE UPDATE OR DELETE ON injury_mark_event
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER injury_mark_no_delete
    BEFORE DELETE ON injury_mark
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
