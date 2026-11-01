-- Stable care (Flow 4): daily chores, stalls, supply catalog, restock proposals, evening reminder (TECH-090).

-- One stall per horse: area and number both set or both null (BR-129); unique ignoring case among horses.
ALTER TABLE horse
    ADD COLUMN stall_area   VARCHAR(100) NULL,
    ADD COLUMN stall_number VARCHAR(50)  NULL,
    ADD CONSTRAINT horse_stall_both_or_none CHECK ((stall_area IS NULL) = (stall_number IS NULL));

CREATE UNIQUE INDEX horse_stall_unique
    ON horse (lower(stall_area), lower(stall_number))
    WHERE stall_area IS NOT NULL AND stall_number IS NOT NULL;

-- A done chore is an entry; a not-done chore has no row. Cancelled entries stay as history (BR-125, BR-126).
CREATE TABLE chore_entry (
    id            BIGSERIAL PRIMARY KEY,
    horse_id      BIGINT      NOT NULL REFERENCES horse (id),
    chore_date    DATE        NOT NULL,
    chore         VARCHAR(20) NOT NULL CHECK (chore IN ('FEEDING', 'STALL_CLEANING', 'WASHING', 'ICE_SOAK')),
    groom_id      BIGINT      NOT NULL REFERENCES user_account (id),
    marked_at     TIMESTAMPTZ NOT NULL,
    cancelled_at  TIMESTAMPTZ NULL,
    cancel_reason TEXT        NULL,
    cancelled_by  BIGINT      NULL REFERENCES user_account (id)
);

-- At most one active entry per horse, day and chore (BR-125); guards double marking under concurrency.
CREATE UNIQUE INDEX chore_entry_active_unique
    ON chore_entry (horse_id, chore_date, chore)
    WHERE cancelled_at IS NULL;

CREATE INDEX chore_entry_date_idx ON chore_entry (chore_date);
CREATE INDEX chore_entry_horse_idx ON chore_entry (horse_id, chore_date DESC);

-- Entries are never deleted (P-02).
CREATE TRIGGER chore_entry_no_delete
    BEFORE DELETE ON chore_entry
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TABLE supply_item (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    category   VARCHAR(20)  NOT NULL CHECK (category IN ('FEED', 'MEDICINE', 'TOOLS')),
    unit       VARCHAR(50)  NOT NULL,
    quantity   BIGINT       NOT NULL CHECK (quantity >= 0),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL
);

-- The name is unique within a category, ignoring case (BR-130).
CREATE UNIQUE INDEX supply_item_name_category_unique ON supply_item (lower(name), category);

CREATE TABLE restock_proposal (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT      NOT NULL REFERENCES supply_item (id),
    groom_id        BIGINT      NOT NULL REFERENCES user_account (id),
    quantity        BIGINT      NOT NULL CHECK (quantity > 0),
    note            TEXT        NULL,
    state           VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by      BIGINT      NULL REFERENCES user_account (id),
    decided_at      TIMESTAMPTZ NULL,
    decision_reason TEXT        NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX restock_proposal_groom_idx ON restock_proposal (groom_id, created_at DESC);

-- One row per club-local day once the 18:00 reminder was evaluated (BR-128, TECH-096).
CREATE TABLE chore_reminder (
    reminder_date   DATE        PRIMARY KEY,
    evaluated_at    TIMESTAMPTZ NOT NULL,
    notified_grooms INTEGER     NOT NULL,
    pending_horses  INTEGER     NOT NULL,
    pending_chores  INTEGER     NOT NULL
);
