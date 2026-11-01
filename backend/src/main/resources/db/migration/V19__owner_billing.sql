-- Owner billing: a service price list with effective dates, charges created as work is recorded, owner periods of a
-- horse, month closing and immutable invoices (BR-178 to BR-183, TECH-163).

-- A kind of examination or treatment the Veterinarian picks when saving an examination; the price lives apart (BR-178).
CREATE TABLE exam_fee_type (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX exam_fee_type_name_unique ON exam_fee_type (lower(name));

-- One row per price; the price on a day is the row with the greatest effective_from not after it. A row is never
-- edited and its date is never in the past when it is written, so a price cannot reach work already recorded.
CREATE TABLE service_price (
    id             BIGSERIAL PRIMARY KEY,
    kind           VARCHAR(12)   NOT NULL CHECK (kind IN ('DAILY_FEE', 'EXAM_FEE')),
    fee_type_id    BIGINT        NULL REFERENCES exam_fee_type (id),
    amount         NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    effective_from DATE          NOT NULL,
    created_by     BIGINT        NULL REFERENCES user_account (id),
    created_at     TIMESTAMPTZ   NOT NULL,
    CHECK ((kind = 'EXAM_FEE') = (fee_type_id IS NOT NULL))
);

CREATE UNIQUE INDEX service_price_unique ON service_price (kind, coalesce(fee_type_id, 0), effective_from);

CREATE TRIGGER service_price_immutable BEFORE UPDATE OR DELETE ON service_price
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

ALTER TABLE examination ADD COLUMN fee_type_id BIGINT NULL REFERENCES exam_fee_type (id);

-- Who owned a horse on which day (BR-181). Charges keep the owner they were made for.
CREATE TABLE horse_owner_period (
    id        BIGSERIAL PRIMARY KEY,
    horse_id  BIGINT NOT NULL REFERENCES horse (id),
    owner_id  BIGINT NOT NULL REFERENCES user_account (id),
    from_date DATE   NOT NULL,
    to_date   DATE   NULL,
    CHECK (to_date IS NULL OR to_date >= from_date)
);

CREATE UNIQUE INDEX horse_owner_period_open ON horse_owner_period (horse_id) WHERE to_date IS NULL;
CREATE INDEX horse_owner_period_horse_idx ON horse_owner_period (horse_id, from_date);

INSERT INTO horse_owner_period (horse_id, owner_id, from_date, to_date)
SELECT id, owner_id, (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, NULL
FROM horse;

CREATE TABLE invoice (
    id        BIGSERIAL PRIMARY KEY,
    owner_id  BIGINT        NOT NULL REFERENCES user_account (id),
    month     VARCHAR(7)       NOT NULL,
    number    VARCHAR(30)   NOT NULL,
    total     NUMERIC(14,2) NOT NULL,
    closed_at TIMESTAMPTZ   NOT NULL,
    closed_by BIGINT        NULL REFERENCES user_account (id),
    UNIQUE (owner_id, month)
);

-- The lines are copies, so a later rename of a horse or a supply changes nothing on a closed invoice (BR-180).
CREATE TABLE invoice_line (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT        NOT NULL REFERENCES invoice (id),
    horse_id    BIGINT        NULL,
    horse_name  VARCHAR(200)  NULL,
    category    VARCHAR(12)   NOT NULL,
    description VARCHAR(300)  NOT NULL,
    work_date   DATE          NOT NULL,
    charge_date DATE          NOT NULL,
    quantity    NUMERIC(14,2) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    amount      NUMERIC(14,2) NOT NULL,
    reason      VARCHAR(500)  NULL
);

CREATE INDEX invoice_line_invoice_idx ON invoice_line (invoice_id);

CREATE TRIGGER invoice_immutable BEFORE UPDATE OR DELETE ON invoice
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
CREATE TRIGGER invoice_line_immutable BEFORE UPDATE OR DELETE ON invoice_line
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- charge_date is the statement month; work_date the day of the work (they differ only when work is recorded into a
-- month that is already closed). voided_at removes a charge from every statement (a cancelled mark).
CREATE TABLE charge (
    id              BIGSERIAL PRIMARY KEY,
    horse_id        BIGINT        NULL REFERENCES horse (id),
    owner_id        BIGINT        NOT NULL REFERENCES user_account (id),
    category        VARCHAR(12)   NOT NULL CHECK (category IN ('MEDICINE', 'FEED', 'DAILY_FEE', 'EXAM', 'ADJUSTMENT')),
    charge_date     DATE          NOT NULL,
    work_date       DATE          NOT NULL,
    description     VARCHAR(300)  NOT NULL,
    quantity        NUMERIC(14,2) NOT NULL,
    unit_price      NUMERIC(14,2) NOT NULL,
    amount          NUMERIC(14,2) NOT NULL,
    idempotency_key VARCHAR(200)  NOT NULL UNIQUE,
    source_ref      VARCHAR(100)  NULL,
    reason          VARCHAR(500)  NULL,
    voided_at       TIMESTAMPTZ   NULL,
    void_reason     VARCHAR(200)  NULL,
    invoice_id      BIGINT        NULL REFERENCES invoice (id),
    created_by      BIGINT        NULL REFERENCES user_account (id),
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX charge_owner_idx ON charge (owner_id, charge_date);
CREATE INDEX charge_horse_idx ON charge (horse_id, charge_date);
CREATE INDEX charge_source_idx ON charge (source_ref);

-- A charge that is on an invoice can no longer change; voiding or re-pointing it is refused by the database.
CREATE FUNCTION charge_guard_invoiced() RETURNS trigger AS $$
BEGIN
    IF OLD.invoice_id IS NOT NULL AND (TG_OP = 'DELETE' OR NEW IS DISTINCT FROM OLD) THEN
        RAISE EXCEPTION 'charge % is on a closed invoice', OLD.id;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER charge_guard BEFORE UPDATE OR DELETE ON charge
    FOR EACH ROW EXECUTE FUNCTION charge_guard_invoiced();

-- The daily fee of a club day is created once, after the day; a restart or a second instance cannot repeat it.
CREATE TABLE billing_day_run (
    day            DATE PRIMARY KEY,
    processed_at   TIMESTAMPTZ NOT NULL,
    horses_charged INT         NOT NULL
);

CREATE TABLE billing_month (
    month     VARCHAR(7) PRIMARY KEY,
    closed_at TIMESTAMPTZ NOT NULL,
    closed_by BIGINT      NULL REFERENCES user_account (id)
);
