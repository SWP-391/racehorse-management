-- Trial-run videos (BR-104 to BR-109). The file lives in video storage under storage_key, never in the database.
CREATE TABLE trial_video (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT       NOT NULL REFERENCES training_session (id),
    horse_id       BIGINT       NOT NULL REFERENCES horse (id),
    file_name      VARCHAR(255) NOT NULL,
    size_bytes     BIGINT       NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 209715200),
    storage_key    VARCHAR(64)  NOT NULL UNIQUE,
    uploaded_by_id BIGINT       NOT NULL REFERENCES user_account (id),
    uploaded_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX trial_video_session_idx ON trial_video (session_id, id);
