-- Examinations (BR-060): append-only in the service; the first one marks the horse examined (BR-009).
CREATE TABLE examination (
    id               BIGSERIAL PRIMARY KEY,
    horse_id         BIGINT       NOT NULL REFERENCES horse (id),
    exam_date        DATE         NOT NULL,
    diagnosis        TEXT         NOT NULL,
    body_area        VARCHAR(200),
    weight_kg        NUMERIC(6, 1) CHECK (weight_kg > 0),
    treatment        TEXT,
    prescription     TEXT,
    recorded_by_id   BIGINT       NOT NULL REFERENCES user_account (id),
    recorded_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX examination_horse_idx ON examination (horse_id, exam_date);

-- Periodic care (BR-061 to BR-064).
CREATE TABLE care_item (
    id            BIGSERIAL PRIMARY KEY,
    horse_id      BIGINT       NOT NULL REFERENCES horse (id),
    care_type     VARCHAR(20)  NOT NULL CHECK (care_type IN ('VACCINATION', 'DEWORMING', 'HOOF_CHECK')),
    name          VARCHAR(200) NOT NULL,
    interval_days INT          NOT NULL CHECK (interval_days > 0),
    next_due      DATE         NOT NULL,
    created_by_id BIGINT       NOT NULL REFERENCES user_account (id),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX care_item_horse_idx ON care_item (horse_id);
CREATE INDEX care_item_due_idx ON care_item (next_due);

CREATE TABLE care_event (
    id            BIGSERIAL PRIMARY KEY,
    item_id       BIGINT       NOT NULL REFERENCES care_item (id),
    kind          VARCHAR(20)  NOT NULL CHECK (kind IN ('COMPLETION', 'OVERRIDE', 'POSTPONEMENT')),
    event_date    DATE         NOT NULL,
    performer     VARCHAR(200),
    product       VARCHAR(200),
    batch         VARCHAR(100),
    note          TEXT,
    previous_due  DATE         NOT NULL,
    new_due       DATE         NOT NULL,
    by_id         BIGINT       NOT NULL REFERENCES user_account (id),
    at            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX care_event_item_idx ON care_event (item_id, at);

-- Incident reports and their photos (BR-070, BR-071).
CREATE TABLE incident_report (
    id                  BIGSERIAL PRIMARY KEY,
    horse_id            BIGINT      NOT NULL REFERENCES horse (id),
    groom_id            BIGINT      NOT NULL REFERENCES user_account (id),
    description         TEXT        NOT NULL,
    state               VARCHAR(20) NOT NULL CHECK (state IN ('NEW', 'ACKNOWLEDGED', 'HANDLED')),
    created_at          TIMESTAMPTZ NOT NULL,
    acknowledged_at     TIMESTAMPTZ,
    acknowledged_by_id  BIGINT REFERENCES user_account (id),
    closing_note        TEXT,
    linked_examination_id BIGINT REFERENCES examination (id),
    closed_at           TIMESTAMPTZ,
    closed_by_id        BIGINT REFERENCES user_account (id)
);

CREATE INDEX incident_report_groom_idx ON incident_report (groom_id, created_at DESC);
CREATE INDEX incident_report_state_idx ON incident_report (state, created_at DESC);

CREATE TABLE incident_photo (
    id           BIGSERIAL PRIMARY KEY,
    report_id    BIGINT       NOT NULL REFERENCES incident_report (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(20)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content      BYTEA        NOT NULL
);

CREATE INDEX incident_photo_report_idx ON incident_photo (report_id);
