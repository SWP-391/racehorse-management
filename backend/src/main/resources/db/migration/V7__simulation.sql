-- Per-horse alert thresholds set by the Head Trainer; no defaults (BR-052).
CREATE TABLE telemetry_threshold (
    id          BIGSERIAL PRIMARY KEY,
    horse_id    BIGINT        NOT NULL REFERENCES horse (id),
    measure     VARCHAR(20)   NOT NULL CHECK (measure IN ('HEART_RATE', 'SPEED')),
    limit_value NUMERIC(6, 1) NOT NULL CHECK (limit_value > 0),
    set_by_id   BIGINT        NOT NULL REFERENCES user_account (id),
    set_at      TIMESTAMPTZ   NOT NULL,
    UNIQUE (horse_id, measure)
);

-- At most one alert per session and measure (TECH-039).
CREATE TABLE telemetry_alert (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT        NOT NULL REFERENCES training_session (id),
    horse_id      BIGINT        NOT NULL REFERENCES horse (id),
    measure       VARCHAR(20)   NOT NULL CHECK (measure IN ('HEART_RATE', 'SPEED')),
    limit_value   NUMERIC(6, 1) NOT NULL,
    value         NUMERIC(6, 1) NOT NULL,
    offset_second INT           NOT NULL,
    raised_at     TIMESTAMPTZ   NOT NULL,
    data_source   VARCHAR(10)   NOT NULL CHECK (data_source IN ('MANUAL', 'SIMULATED', 'DEVICE')),
    UNIQUE (session_id, measure)
);

CREATE INDEX telemetry_alert_horse_idx ON telemetry_alert (horse_id, raised_at DESC);
