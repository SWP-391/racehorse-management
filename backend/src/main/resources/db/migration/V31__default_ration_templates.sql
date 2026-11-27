-- Default ration templates (BR-265, DEC-090, TECH-F9): one per condition tag, marked "chờ xác nhận" until a Veterinarian
-- edits or confirms them. Quantities are suggestions for a horse of about 500 kg, daily totals in kg (Ngũ cốc / Cỏ /
-- Vitamin): Thường 5.0 / 7.0 / 0.05, Theo dõi 3.5 / 8.0 / 0.05, Phục hồi 2.0 / 9.0 / 0.10, Cách ly 2.5 / 8.0 / 0.05,
-- split over three meals (the last meal takes the rounding rest, so the three meals add up to the daily total).
--
-- The DO block below is also run at every start by vn.racehorse.ration.service.DefaultRationTemplates (read from this
-- file, from "DO $$" on, so the quantities live in one place): on a new database neither the author nor the feed
-- catalog exist yet when this migration runs. Keep the block free of "?" and Flyway placeholders.
--
-- Rollback: DELETE the template items, meals and rows with needs_confirmation = TRUE, then
--   ALTER TABLE ration_template DROP COLUMN needs_confirmation;

ALTER TABLE ration_template ADD COLUMN needs_confirmation BOOLEAN NOT NULL DEFAULT FALSE;

-- The seed uses what already exists: the first active feed item of the catalog for each kind (grain by the names of
-- the usual grains, hay "Cỏ...", vitamin "...vitamin...") counted in kg or g, and the first active Veterinarian (or,
-- without one, the Club Manager) as the author. When the template table is not empty, or any of these is missing,
-- nothing is added and nothing is invented; the Veterinarian then makes the templates in the application.
DO $$
DECLARE
    author_id  BIGINT;
    grain_id   BIGINT;
    hay_id     BIGINT;
    vit_id     BIGINT;
    grain_f    NUMERIC;
    hay_f      NUMERIC;
    vit_f      NUMERIC;
    spec       RECORD;
    meal       RECORD;
    template   BIGINT;
    meal_id    BIGINT;
    ids        BIGINT[];
    factors    NUMERIC[];
    k          INT;
    part       NUMERIC;
BEGIN
    IF EXISTS (SELECT 1 FROM ration_template) THEN
        RAISE NOTICE 'default ration templates skipped: ration_template is not empty';
        RETURN;
    END IF;

    SELECT id INTO author_id FROM user_account WHERE active AND role = 'VETERINARIAN' ORDER BY id LIMIT 1;
    IF author_id IS NULL THEN
        SELECT id INTO author_id FROM user_account WHERE active AND role = 'CLUB_MANAGER' ORDER BY id LIMIT 1;
    END IF;

    SELECT i.id, CASE lower(u.name) WHEN 'kg' THEN 1 ELSE 1000 END INTO grain_id, grain_f
      FROM supply_item i JOIN supply_unit u ON u.id = i.unit_id
     WHERE i.active AND i.category = 'FEED' AND lower(u.name) IN ('kg', 'g')
       AND (lower(i.name) LIKE 'yến mạch%' OR lower(i.name) LIKE 'ngô%' OR lower(i.name) LIKE 'cám%'
            OR lower(i.name) LIKE 'ngũ cốc%')
     ORDER BY i.id LIMIT 1;
    SELECT i.id, CASE lower(u.name) WHEN 'kg' THEN 1 ELSE 1000 END INTO hay_id, hay_f
      FROM supply_item i JOIN supply_unit u ON u.id = i.unit_id
     WHERE i.active AND i.category = 'FEED' AND lower(u.name) IN ('kg', 'g') AND lower(i.name) LIKE 'cỏ%'
     ORDER BY i.id LIMIT 1;
    SELECT i.id, CASE lower(u.name) WHEN 'kg' THEN 1 ELSE 1000 END INTO vit_id, vit_f
      FROM supply_item i JOIN supply_unit u ON u.id = i.unit_id
     WHERE i.active AND i.category = 'FEED' AND lower(u.name) IN ('kg', 'g') AND lower(i.name) LIKE '%vitamin%'
     ORDER BY i.id LIMIT 1;

    IF author_id IS NULL OR grain_id IS NULL OR hay_id IS NULL OR vit_id IS NULL THEN
        IF author_id IS NULL THEN
            RAISE NOTICE 'default ration templates skipped: no active Veterinarian or Club Manager';
        END IF;
        IF grain_id IS NULL THEN
            RAISE NOTICE 'default ration templates skipped: no active FEED item in kg or g named yến mạch, ngô, cám or ngũ cốc';
        END IF;
        IF hay_id IS NULL THEN
            RAISE NOTICE 'default ration templates skipped: no active FEED item in kg or g named cỏ...';
        END IF;
        IF vit_id IS NULL THEN
            RAISE NOTICE 'default ration templates skipped: no active FEED item in kg or g with vitamin in its name';
        END IF;
        RETURN;
    END IF;

    ids := ARRAY[grain_id, hay_id, vit_id];
    factors := ARRAY[grain_f, hay_f, vit_f];

    FOR spec IN
        SELECT * FROM (VALUES
            ('Thường',   'NORMAL',    ARRAY[5.0, 7.0, 0.05]),
            ('Theo dõi', 'WATCH',     ARRAY[3.5, 8.0, 0.05]),
            ('Phục hồi', 'RECOVERY',  ARRAY[2.0, 9.0, 0.10]),
            ('Cách ly',  'ISOLATION', ARRAY[2.5, 8.0, 0.05])
        ) AS t(name, tag, daily)
    LOOP
        INSERT INTO ration_template (name, tag, active, created_by, created_at, needs_confirmation)
        VALUES (spec.name, spec.tag, TRUE, author_id, now(), TRUE)
        RETURNING id INTO template;

        FOR meal IN
            SELECT * FROM (VALUES (0, 'Bữa sáng', TIME '06:00'), (1, 'Bữa trưa', TIME '12:00'),
                                  (2, 'Bữa tối', TIME '18:00')) AS m(position, name, meal_time)
        LOOP
            INSERT INTO ration_template_meal (template_id, position, name, meal_time)
            VALUES (template, meal.position, meal.name, meal.meal_time)
            RETURNING id INTO meal_id;

            FOR k IN 1..3 LOOP
                part := round(spec.daily[k] * factors[k] / 3, 2);
                IF meal.position = 2 THEN
                    part := spec.daily[k] * factors[k] - 2 * part;
                END IF;
                INSERT INTO ration_template_item (meal_id, position, item_type, supply_item_id, quantity)
                VALUES (meal_id, k - 1, (ARRAY['GRAIN', 'HAY', 'VITAMIN'])[k], ids[k], part);
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
