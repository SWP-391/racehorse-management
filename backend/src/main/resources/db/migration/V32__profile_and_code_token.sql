-- Own profile (BR-254, TECH-F8): the phone number is optional and free of other rules than its length and characters.
-- Rollback: DROP TABLE login_session; DROP TABLE code_token; ALTER TABLE user_account DROP COLUMN phone;
ALTER TABLE user_account ADD COLUMN phone VARCHAR(20);

-- Step 1 of the two-step code flow gives a token; only its hash is kept, it lives 10 minutes and is used once
-- (BR-255, TECH-F7).
CREATE TABLE code_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX code_token_user_idx ON code_token (user_id);

-- The sign-ins of a person, so the profile page can list them and end one (BR-254, TECH-F8). Only a hash of the
-- session id is kept; a revoked row ends its session on the next request.
CREATE TABLE login_session (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    session_hash VARCHAR(64)  NOT NULL UNIQUE,
    user_agent   VARCHAR(300),
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX login_session_user_idx ON login_session (user_id);
