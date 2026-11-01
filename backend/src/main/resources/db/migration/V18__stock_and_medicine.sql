-- Stock and medicine: unit price with history, an immutable stock ledger, expected receipts, medicine taken from stock
-- by care completions, planned doses, back-filled chore marks and the daily stock notice (BR-171 to BR-177,
-- TECH-151, TECH-152).

-- supply_item.quantity stays as the balance, now only changed by the ledger service; it was already CHECK (>= 0).
ALTER TABLE supply_item
    ADD COLUMN unit_price NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0);

-- One row per price change (history, P-03); the current price is copied to supply_item.unit_price.
CREATE TABLE supply_price (
    id             BIGSERIAL PRIMARY KEY,
    supply_item_id BIGINT        NOT NULL REFERENCES supply_item (id),
    price          NUMERIC(14,2) NOT NULL CHECK (price >= 0),
    effective_from DATE          NOT NULL,
    set_by         BIGINT        NULL REFERENCES user_account (id),
    set_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX supply_price_item_idx ON supply_price (supply_item_id, id DESC);

CREATE TRIGGER supply_price_immutable BEFORE UPDATE OR DELETE ON supply_price
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- Every change of a quantity is a line; a mistake is a REVERSAL pointing at the line it undoes (BR-171).
CREATE TABLE stock_movement (
    id             BIGSERIAL PRIMARY KEY,
    supply_item_id BIGINT        NOT NULL REFERENCES supply_item (id),
    kind           VARCHAR(12)   NOT NULL CHECK (kind IN ('RECEIPT', 'USE_CARE', 'USE_FEED', 'REVERSAL', 'ADJUSTMENT')),
    delta          BIGINT        NOT NULL,
    balance_after  BIGINT        NOT NULL CHECK (balance_after >= 0),
    unit_price     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    occurred_on    DATE          NOT NULL,
    ref_type       VARCHAR(20)   NULL,
    ref_id         BIGINT        NULL,
    reverses_id    BIGINT        NULL REFERENCES stock_movement (id),
    reason         TEXT          NULL,
    actor_id       BIGINT        NULL REFERENCES user_account (id),
    created_at     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX stock_movement_item_idx ON stock_movement (supply_item_id, id DESC);
CREATE INDEX stock_movement_ref_idx ON stock_movement (ref_type, ref_id);
-- A line is reversed at most once.
CREATE UNIQUE INDEX stock_movement_reverses_unique ON stock_movement (reverses_id) WHERE reverses_id IS NOT NULL;

CREATE TRIGGER stock_movement_immutable BEFORE UPDATE OR DELETE ON stock_movement
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- The opening balance of each item as its first line, so the ledger adds up to the quantity (AC-331).
INSERT INTO stock_movement (supply_item_id, kind, delta, balance_after, unit_price, occurred_on, reason, created_at)
SELECT id, 'RECEIPT', quantity, quantity, 0, (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
       'Tồn đầu kỳ (chuyển đổi)', now()
FROM supply_item
WHERE quantity > 0;

INSERT INTO supply_price (supply_item_id, price, effective_from, set_by, set_at)
SELECT id, 0, (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, NULL, now()
FROM supply_item;

CREATE TABLE stock_expected_receipt (
    id             BIGSERIAL PRIMARY KEY,
    supply_item_id BIGINT      NOT NULL REFERENCES supply_item (id),
    expected_date  DATE        NOT NULL,
    quantity       BIGINT      NOT NULL CHECK (quantity > 0),
    state          VARCHAR(10) NOT NULL DEFAULT 'EXPECTED' CHECK (state IN ('EXPECTED', 'RECEIVED', 'CANCELLED')),
    created_by     BIGINT      NOT NULL REFERENCES user_account (id),
    created_at     TIMESTAMPTZ NOT NULL,
    closed_at      TIMESTAMPTZ NULL
);

CREATE INDEX stock_expected_item_idx ON stock_expected_receipt (supply_item_id, state);

-- One row per club-local day once the stock notice was evaluated, so a restart sends nothing twice (BR-176).
CREATE TABLE stock_notice (
    notice_date DATE        PRIMARY KEY,
    sent_at     TIMESTAMPTZ NOT NULL,
    items       INTEGER     NOT NULL
);

-- Medicine chosen from stock at a completion; old events keep the free-text product and have no unit price.
ALTER TABLE care_event
    ADD COLUMN supply_item_id BIGINT        NULL REFERENCES supply_item (id),
    ADD COLUMN quantity       BIGINT        NULL CHECK (quantity > 0),
    ADD COLUMN unit_price     NUMERIC(14,2) NULL CHECK (unit_price >= 0);

-- A planned medicine and dose on a care item feed the forecast (BR-175): both or neither.
ALTER TABLE care_item
    ADD COLUMN planned_supply_item_id BIGINT NULL REFERENCES supply_item (id),
    ADD COLUMN planned_quantity       BIGINT NULL CHECK (planned_quantity > 0),
    ADD CONSTRAINT care_item_plan_both_or_none CHECK ((planned_supply_item_id IS NULL) = (planned_quantity IS NULL));

-- A mark of a past day made by the Club Manager keeps its reason (BR-174).
ALTER TABLE chore_entry
    ADD COLUMN backfilled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN backfill_reason TEXT    NULL;

INSERT INTO club_setting (key, value) VALUES ('stock_warning_days', '7');
