-- Payment of a closed invoice by bank transfer (BR-218 to BR-226, DEC-073, TECH-231). The invoice itself never changes
-- (BR-180, BR-224): the payment status is a separate row.

-- One payment code per invoice, made the first time its owner opens the payment (BR-219).
CREATE TABLE invoice_payment_code (
    invoice_id BIGINT      PRIMARY KEY REFERENCES invoice (id),
    code       VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

-- Every accepted notice of a transfer in, applied or not. (provider, provider_id) counts one transfer once (BR-220).
CREATE TABLE bank_transfer (
    id             BIGSERIAL PRIMARY KEY,
    provider       VARCHAR(10)   NOT NULL,
    provider_id    VARCHAR(64)   NOT NULL,
    amount         NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    content        TEXT          NOT NULL DEFAULT '',
    account_number VARCHAR(30)   NOT NULL DEFAULT '',
    reference      VARCHAR(64)   NOT NULL DEFAULT '',
    transferred_at TIMESTAMPTZ   NULL,
    received_at    TIMESTAMPTZ   NOT NULL,
    simulated      BOOLEAN       NOT NULL,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('APPLIED', 'UNMATCHED', 'ASSIGNED')),
    reason         VARCHAR(20)   NULL CHECK (reason IN ('NO_CODE', 'UNKNOWN_CODE', 'AMOUNT_MISMATCH', 'ALREADY_PAID')),
    invoice_id     BIGINT        NULL REFERENCES invoice (id),
    UNIQUE (provider, provider_id)
);

-- An invoice is paid by at most one transfer, even under a race (BR-220).
CREATE UNIQUE INDEX bank_transfer_paying_invoice ON bank_transfer (invoice_id)
    WHERE status IN ('APPLIED', 'ASSIGNED');

CREATE INDEX bank_transfer_status_received ON bank_transfer (status, received_at DESC);

-- An invoice is paid exactly when this row exists (BR-224).
CREATE TABLE invoice_payment (
    invoice_id  BIGINT      PRIMARY KEY REFERENCES invoice (id),
    transfer_id BIGINT      NOT NULL UNIQUE REFERENCES bank_transfer (id),
    paid_at     TIMESTAMPTZ NOT NULL,
    via         VARCHAR(10) NOT NULL CHECK (via IN ('TRANSFER', 'ASSIGNED'))
);
