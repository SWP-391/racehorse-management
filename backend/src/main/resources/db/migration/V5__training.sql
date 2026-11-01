-- Training plans and phases (BR-030, BR-031).
CREATE TABLE training_plan (
    id            BIGSERIAL PRIMARY KEY,
    horse_id      BIGINT       NOT NULL REFERENCES horse (id),
    title         VARCHAR(200),
    state         VARCHAR(20)  NOT NULL CHECK (state IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    cancel_reason TEXT,
    created_by_id BIGINT       NOT NULL REFERENCES user_account (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX training_plan_horse_idx ON training_plan (horse_id);

CREATE TABLE plan_phase (
    id               BIGSERIAL PRIMARY KEY,
    plan_id          BIGINT        NOT NULL REFERENCES training_plan (id),
    ordinal          INT           NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    goal             TEXT          NOT NULL,
    distance_m       INT           NOT NULL CHECK (distance_m > 0),
    weekly_volume_km NUMERIC(7, 1) NOT NULL CHECK (weekly_volume_km > 0),
    surface          VARCHAR(100)  NOT NULL,
    UNIQUE (plan_id, ordinal),
    CHECK (start_date <= end_date)
);

-- Sessions (BR-032 to BR-041). State changes only; rows are never deleted (DEC-003).
CREATE TABLE training_session (
    id                    BIGSERIAL PRIMARY KEY,
    plan_id               BIGINT       NOT NULL REFERENCES training_plan (id),
    horse_id              BIGINT       NOT NULL REFERENCES horse (id),
    scheduled_date        DATE         NOT NULL,
    session_type          VARCHAR(20)  NOT NULL CHECK (session_type IN ('TROT', 'GALLOP', 'TRIAL_RUN', 'OTHER')),
    planned_duration_min  INT          NOT NULL CHECK (planned_duration_min > 0),
    target_distance_m     INT CHECK (target_distance_m > 0),
    target_surface        VARCHAR(100),
    responsible_person_id BIGINT REFERENCES user_account (id),
    state                 VARCHAR(30)  NOT NULL CHECK (state IN ('DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED',
        'AUTO_CLOSED', 'MISSED', 'BLOCKED_BY_LOCK', 'AWAITING_REVIEW', 'MISSED_DUE_TO_LOCK', 'CANCELLED')),
    started_at            TIMESTAMPTZ,
    ended_at              TIMESTAMPTZ,
    end_time_automatic    BOOLEAN      NOT NULL DEFAULT FALSE,
    overtime              BOOLEAN      NOT NULL DEFAULT FALSE,
    lock_during_session   BOOLEAN      NOT NULL DEFAULT FALSE,
    missing_indicators    BOOLEAN      NOT NULL DEFAULT FALSE,
    early_end_reason      TEXT,
    cancel_reason         TEXT,
    distance_m            INT CHECK (distance_m > 0),
    duration_min          INT CHECK (duration_min > 0),
    surface               VARCHAR(100),
    intensity             VARCHAR(10) CHECK (intensity IN ('LIGHT', 'MEDIUM', 'HEAVY')),
    form                  SMALLINT CHECK (form BETWEEN 1 AND 5),
    avg_heart_rate        INT CHECK (avg_heart_rate > 0),
    avg_heart_rate_source VARCHAR(10) CHECK (avg_heart_rate_source IN ('MANUAL', 'SIMULATED', 'DEVICE')),
    max_speed             NUMERIC(5, 1) CHECK (max_speed > 0),
    max_speed_source      VARCHAR(10) CHECK (max_speed_source IN ('MANUAL', 'SIMULATED', 'DEVICE')),
    weight_kg             NUMERIC(6, 1) CHECK (weight_kg > 0),
    trainer_comment       TEXT,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX training_session_plan_idx ON training_session (plan_id, scheduled_date);
CREATE INDEX training_session_horse_idx ON training_session (horse_id, scheduled_date);
-- At most one session in progress per horse (BR-035).
CREATE UNIQUE INDEX training_session_one_in_progress ON training_session (horse_id) WHERE state = 'IN_PROGRESS';

-- Plan versions created by post-lock shifts and restores (BR-023, TECH-027).
CREATE TABLE plan_version (
    id            BIGSERIAL PRIMARY KEY,
    plan_id       BIGINT      NOT NULL REFERENCES training_plan (id),
    version_no    INT         NOT NULL,
    snapshot      TEXT        NOT NULL,
    reason        TEXT        NOT NULL,
    created_by_id BIGINT REFERENCES user_account (id),
    created_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (plan_id, version_no)
);

-- Club settings (BR-094).
CREATE TABLE club_setting (
    key   VARCHAR(60) PRIMARY KEY,
    value VARCHAR(200) NOT NULL
);

INSERT INTO club_setting (key, value) VALUES ('grace_period_minutes', '60');

-- Pending lock requests registered while a horse is training (BR-020).
CREATE TABLE pending_lock_request (
    id              BIGSERIAL PRIMARY KEY,
    horse_id        BIGINT      NOT NULL REFERENCES horse (id),
    session_id      BIGINT REFERENCES training_session (id),
    reason          TEXT        NOT NULL,
    requested_by_id BIGINT      NOT NULL REFERENCES user_account (id),
    requested_at    TIMESTAMPTZ NOT NULL,
    withdrawn_at    TIMESTAMPTZ,
    applied_at      TIMESTAMPTZ,
    applied_lock_id BIGINT REFERENCES training_lock (id)
);

CREATE UNIQUE INDEX pending_lock_request_one_open ON pending_lock_request (horse_id)
    WHERE withdrawn_at IS NULL AND applied_at IS NULL;
