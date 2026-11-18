-- Inbox of conversations between allowed pairs of roles (BR-227 to BR-234, DEC-075, TECH-239).
-- A message is never edited or deleted: the code has no path for it (BR-227).

CREATE TABLE conversation (
    id            BIGSERIAL    PRIMARY KEY,
    subject       VARCHAR(120) NOT NULL CHECK (char_length(subject) BETWEEN 1 AND 120),
    horse_id      BIGINT       NULL REFERENCES horse (id),
    -- Only the Club Manager role can be addressed as a role; every Club Manager then sees the conversation (BR-231).
    to_role       VARCHAR(20)  NULL CHECK (to_role = 'CLUB_MANAGER'),
    started_by_id BIGINT       NOT NULL REFERENCES user_account (id),
    state         VARCHAR(6)   NOT NULL CHECK (state IN ('OPEN', 'CLOSED')),
    created_at    TIMESTAMPTZ  NOT NULL,
    state_by_id   BIGINT       NULL REFERENCES user_account (id),
    state_at      TIMESTAMPTZ  NULL
);

-- The starter and, for a conversation between two people, the other person. Role-addressed conversations hold only
-- the starter: the Club Managers reach them through to_role.
CREATE TABLE conversation_participant (
    conversation_id BIGINT NOT NULL REFERENCES conversation (id),
    account_id      BIGINT NOT NULL REFERENCES user_account (id),
    PRIMARY KEY (conversation_id, account_id)
);
CREATE INDEX conversation_participant_account_idx ON conversation_participant (account_id);

CREATE TABLE message (
    id              BIGSERIAL   PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversation (id),
    author_id       BIGINT      NOT NULL REFERENCES user_account (id),
    body            TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    sent_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX message_conversation_idx ON message (conversation_id, id);

CREATE TABLE conversation_read (
    conversation_id      BIGINT NOT NULL REFERENCES conversation (id),
    account_id           BIGINT NOT NULL REFERENCES user_account (id),
    last_read_message_id BIGINT NOT NULL,
    PRIMARY KEY (conversation_id, account_id)
);
