-- Email replaces the username as the sign-in identifier (BR-210, BR-211, DEC-074, TECH-221). No account has an email
-- yet, so the column is NULL for every existing account: that is "no email until the Club Manager enters it" (BR-212).
-- "Mandatory" for a new account is enforced by validation and by the service, not by NOT NULL, because of those rows.
ALTER TABLE user_account ADD COLUMN email VARCHAR(254) NULL;

ALTER TABLE user_account ADD CONSTRAINT user_account_email_shape CHECK (
    email IS NULL OR (
        email = lower(email)
        AND length(email) <= 254
        AND email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    )
);

CREATE UNIQUE INDEX user_account_email_unique ON user_account (email);

-- The owner dropped the username (DEC-074). Old audit rows keep the name they wrote into their details.
ALTER TABLE user_account DROP COLUMN username;

-- A password reset raises this number; a session opened with an older one is refused on its next request (BR-216, TECH-223).
ALTER TABLE user_account ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0;

-- One recovery code per account at a time; only a hash of the code is kept (BR-215, TECH-224).
CREATE TABLE password_recovery (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES user_account (id),
    code_hash  VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    tries      SMALLINT    NOT NULL DEFAULT 0,
    state      VARCHAR(8)  NOT NULL CHECK (state IN ('LIVE', 'SPENT', 'VOID'))
);

CREATE UNIQUE INDEX password_recovery_live ON password_recovery (account_id) WHERE state = 'LIVE';

-- Every valid request is written here for any address, known or not, so the pace limit tells nothing about an account
-- (BR-214, BR-215).
CREATE TABLE password_recovery_request (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(254) NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX password_recovery_request_email_idx ON password_recovery_request (email, requested_at);
