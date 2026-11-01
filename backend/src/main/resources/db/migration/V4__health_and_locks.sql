-- Health status per horse (BR-010) and its history (BR-011). first_examined_at is set by examinations (slice 4).
CREATE TABLE horse_health (
    horse_id          BIGINT PRIMARY KEY REFERENCES horse (id),
    status            VARCHAR(20) NOT NULL
        CHECK (status IN ('ELIGIBLE', 'UNDER_OBSERVATION', 'INJURED', 'QUARANTINED')),
    first_examined_at TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE health_status_change (
    id            BIGSERIAL PRIMARY KEY,
    horse_id      BIGINT      NOT NULL REFERENCES horse (id),
    from_status   VARCHAR(20),
    to_status     VARCHAR(20) NOT NULL,
    reason        TEXT        NOT NULL,
    changed_by_id BIGINT REFERENCES user_account (id),
    changed_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX health_status_change_horse_idx ON health_status_change (horse_id, changed_at);

-- Training locks (BR-014 to BR-019). A lift ends a lock; rows are never deleted.
CREATE TABLE training_lock (
    id            BIGSERIAL PRIMARY KEY,
    horse_id      BIGINT      NOT NULL REFERENCES horse (id),
    kind          VARCHAR(10) NOT NULL CHECK (kind IN ('MANUAL', 'AUTOMATIC', 'INTAKE')),
    reason        TEXT        NOT NULL,
    placed_by_id  BIGINT REFERENCES user_account (id),
    placed_at     TIMESTAMPTZ NOT NULL,
    review_date   DATE,
    lifted_by_id  BIGINT REFERENCES user_account (id),
    lifted_at     TIMESTAMPTZ,
    return_level  VARCHAR(30) CHECK (return_level IN ('RESUME_AS_IS', 'RESUME_WITH_ADJUSTMENT')),
    CHECK ((lifted_at IS NULL) = (return_level IS NULL))
);

-- At most one active lock per horse (BR-019, TECH-015).
CREATE UNIQUE INDEX training_lock_one_active ON training_lock (horse_id) WHERE lifted_at IS NULL;

CREATE TABLE lock_review_note (
    id         BIGSERIAL PRIMARY KEY,
    lock_id    BIGINT      NOT NULL REFERENCES training_lock (id),
    author_id  BIGINT      NOT NULL REFERENCES user_account (id),
    note       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- In-app notifications (NFR-006, TECH-019).
CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES user_account (id),
    message      TEXT         NOT NULL,
    link         VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL,
    read_at      TIMESTAMPTZ
);

CREATE INDEX notification_recipient_idx ON notification (recipient_id, created_at DESC);

-- Horses registered before this slice have never been examined: give them the intake state (BR-008, TECH-016).
INSERT INTO horse_health (horse_id, status, updated_at)
SELECT id, 'UNDER_OBSERVATION', now() FROM horse;

INSERT INTO health_status_change (horse_id, from_status, to_status, reason, changed_at)
SELECT id, NULL, 'UNDER_OBSERVATION', 'Ngựa mới nhập đàn', now() FROM horse;

INSERT INTO training_lock (horse_id, kind, reason, placed_at)
SELECT id, 'INTAKE', 'Chờ khám nhập đàn', now() FROM horse;
