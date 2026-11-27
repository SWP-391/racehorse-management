-- Ration fits condition (BR-248 to BR-251): condition tag and mismatch reason on each version, Veterinarian templates.

-- Existing versions are NORMAL. Adding columns does not fire the immutability trigger (it is row-level on UPDATE).
ALTER TABLE ration_version
    ADD COLUMN condition_tag    VARCHAR(10)   NOT NULL DEFAULT 'NORMAL'
        CHECK (condition_tag IN ('NORMAL', 'WATCH', 'RECOVERY', 'ISOLATION')),
    ADD COLUMN mismatch_reason  VARCHAR(1000);

-- Templates are switched off, never deleted (BR-249).
CREATE TABLE ration_template (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    tag        VARCHAR(10)  NOT NULL CHECK (tag IN ('NORMAL', 'WATCH', 'RECOVERY', 'ISOLATION')),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by BIGINT       NOT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ration_template_name_unique ON ration_template (lower(name)) WHERE active;

CREATE TABLE ration_template_meal (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT       NOT NULL REFERENCES ration_template (id),
    position    INT          NOT NULL,
    name        VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    meal_time   TIME         NOT NULL
);

CREATE TABLE ration_template_item (
    id             BIGSERIAL PRIMARY KEY,
    meal_id        BIGINT        NOT NULL REFERENCES ration_template_meal (id),
    position       INT           NOT NULL,
    item_type      VARCHAR(10)   NOT NULL CHECK (item_type IN ('GRAIN', 'HAY', 'VITAMIN')),
    supply_item_id BIGINT        NOT NULL REFERENCES supply_item (id),
    quantity       NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    UNIQUE (meal_id, supply_item_id)
);
