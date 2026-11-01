-- Accounts: one role per person (BR-090, TECH-011). Accounts are deactivated, never deleted (NFR-004).
CREATE TABLE user_account (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    display_name  VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL
        CHECK (role IN ('HEAD_TRAINER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER', 'CLUB_MANAGER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Audit log: append-only (BR-092, TECH-008). The trigger makes immutability independent of application code.
CREATE TABLE audit_entry (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES user_account (id),
    actor_name  VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   BIGINT,
    details     TEXT         NOT NULL DEFAULT '{}'
);

CREATE INDEX audit_entry_occurred_at_idx ON audit_entry (occurred_at);
CREATE INDEX audit_entry_actor_idx ON audit_entry (actor_id);

CREATE TRIGGER audit_entry_append_only
    BEFORE UPDATE OR DELETE ON audit_entry
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER audit_entry_no_truncate
    BEFORE TRUNCATE ON audit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();
