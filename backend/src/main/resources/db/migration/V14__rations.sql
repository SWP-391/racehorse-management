-- Approved ration (Flow 4, REQ-018, TECH-098): immutable versions of meals and items per horse.

CREATE TABLE ration_version (
    id             BIGSERIAL PRIMARY KEY,
    horse_id       BIGINT      NOT NULL REFERENCES horse (id),
    version_no     INT         NOT NULL CHECK (version_no > 0),
    effective_date DATE        NOT NULL,
    saved_by       BIGINT      NOT NULL REFERENCES user_account (id),
    saved_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (horse_id, version_no)
);

CREATE TABLE ration_meal (
    id         BIGSERIAL PRIMARY KEY,
    version_id BIGINT       NOT NULL REFERENCES ration_version (id),
    position   INT          NOT NULL,
    name       VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    meal_time  TIME         NOT NULL
);

-- A meal name is unique within one ration version, ignoring case (BR-138).
CREATE UNIQUE INDEX ration_meal_name_unique ON ration_meal (version_id, lower(name));

CREATE TABLE ration_item (
    id             BIGSERIAL PRIMARY KEY,
    meal_id        BIGINT        NOT NULL REFERENCES ration_meal (id),
    position       INT           NOT NULL,
    item_type      VARCHAR(10)   NOT NULL CHECK (item_type IN ('GRAIN', 'HAY', 'VITAMIN')),
    supply_item_id BIGINT        NOT NULL REFERENCES supply_item (id),
    quantity       NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    UNIQUE (meal_id, supply_item_id)
);

CREATE INDEX ration_version_horse_idx ON ration_version (horse_id, version_no DESC);

-- Versions are history: never changed or removed (BR-140, P-03).
CREATE TRIGGER ration_version_immutable BEFORE UPDATE OR DELETE ON ration_version
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
CREATE TRIGGER ration_meal_immutable BEFORE UPDATE OR DELETE ON ration_meal
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
CREATE TRIGGER ration_item_immutable BEFORE UPDATE OR DELETE ON ration_item
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
