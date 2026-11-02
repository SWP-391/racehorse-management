-- Units of supplies become a catalog; an item points to a unit row instead of holding free text (BR-194, BR-195).
-- Rollback (one statement per step, kept for the operator):
--   ALTER TABLE supply_item ADD COLUMN unit VARCHAR(50);
--   UPDATE supply_item i SET unit = u.name FROM supply_unit u WHERE u.id = i.unit_id;
--   ALTER TABLE supply_item ALTER COLUMN unit SET NOT NULL; ALTER TABLE supply_item DROP COLUMN unit_id; DROP TABLE supply_unit;
CREATE TABLE supply_unit (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX supply_unit_name_uk ON supply_unit (lower(name));

INSERT INTO supply_unit (name)
VALUES ('kg'), ('g'), ('lít'), ('ml'), ('chai'), ('lọ'), ('ống'), ('viên'), ('gói'), ('túi'), ('bao'),
       ('cái'), ('cuộn'), ('bộ'), ('hộp'), ('liều');

-- Units already in use that are not in the list: one row per spelling ignoring case and spaces, the most frequent kept.
INSERT INTO supply_unit (name)
SELECT DISTINCT ON (k) norm
  FROM (SELECT lower(btrim(regexp_replace(unit, '\s+', ' ', 'g'))) AS k,
               btrim(regexp_replace(unit, '\s+', ' ', 'g')) AS norm,
               count(*) AS c
          FROM supply_item GROUP BY 1, 2) s
 WHERE NOT EXISTS (SELECT 1 FROM supply_unit u WHERE lower(u.name) = s.k)
 ORDER BY k, c DESC, norm;

ALTER TABLE supply_item ADD COLUMN unit_id BIGINT REFERENCES supply_unit (id);
UPDATE supply_item i SET unit_id = u.id
  FROM supply_unit u
 WHERE lower(u.name) = lower(btrim(regexp_replace(i.unit, '\s+', ' ', 'g')));
ALTER TABLE supply_item ALTER COLUMN unit_id SET NOT NULL;
ALTER TABLE supply_item DROP COLUMN unit;
