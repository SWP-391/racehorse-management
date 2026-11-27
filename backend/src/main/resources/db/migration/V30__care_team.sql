-- Care team per horse (BR-256 to BR-260, DEC-086, DEC-087, TECH-F1, TECH-F4): a Veterinarian and a Groom beside the
-- main Head Trainer, each with an optional substitute until a date (like temp_trainer_id and temp_until). The role of
-- the person is checked by the service, not here. Nullable: waiting horses and horses nobody could take are "Chưa gán".
--
-- Rollback: ALTER TABLE horse DROP COLUMN vet_id, DROP COLUMN groom_id, DROP COLUMN temp_vet_id,
--   DROP COLUMN temp_vet_until, DROP COLUMN temp_groom_id, DROP COLUMN temp_groom_until;
ALTER TABLE horse
    ADD COLUMN vet_id           BIGINT REFERENCES user_account (id),
    ADD COLUMN groom_id         BIGINT REFERENCES user_account (id),
    ADD COLUMN temp_vet_id      BIGINT REFERENCES user_account (id),
    ADD COLUMN temp_vet_until   DATE,
    ADD COLUMN temp_groom_id    BIGINT REFERENCES user_account (id),
    ADD COLUMN temp_groom_until DATE;

ALTER TABLE horse
    ADD CONSTRAINT horse_temp_vet_ck CHECK ((temp_vet_id IS NULL) = (temp_vet_until IS NULL)),
    ADD CONSTRAINT horse_temp_groom_ck CHECK ((temp_groom_id IS NULL) = (temp_groom_until IS NULL));

CREATE INDEX horse_vet_idx ON horse (vet_id);
CREATE INDEX horse_groom_idx ON horse (groom_id);

-- Existing taken-in, not archived horses without a person of a role are shared out once (BR-260): one horse at a time
-- to the least loaded active person of the role who looks after fewer than 10 (ties go to the lowest id). A horse that
-- already has a person is never touched and no count is lowered; a person above 10 keeps all (AC-589). When nobody is
-- left under 10 the rest stays null: "Chưa gán". The numbers are logged.
DO $$
DECLARE
    spec      RECORD;
    h         RECORD;
    person_id BIGINT;
    given     INT;
    left_over INT;
    total     INT := 0;
    rest      INT := 0;
BEGIN
    FOR spec IN SELECT * FROM (VALUES ('HEAD_TRAINER', 'main_trainer_id'), ('VETERINARIAN', 'vet_id'),
                                      ('GROOM', 'groom_id')) AS t (role_name, col) LOOP
        given := 0;
        FOR h IN EXECUTE format('SELECT id FROM horse WHERE intake_state = ''ACCEPTED'' AND archived_at IS NULL '
                                || 'AND %I IS NULL ORDER BY id', spec.col) LOOP
            EXECUTE format('SELECT u.id FROM user_account u WHERE u.role = %L AND u.active '
                           || 'AND (SELECT count(*) FROM horse x WHERE x.%I = u.id AND x.intake_state = ''ACCEPTED'' '
                           || 'AND x.archived_at IS NULL) < 10 '
                           || 'ORDER BY (SELECT count(*) FROM horse x WHERE x.%I = u.id AND x.intake_state = ''ACCEPTED'' '
                           || 'AND x.archived_at IS NULL), u.id LIMIT 1', spec.role_name, spec.col, spec.col)
                INTO person_id;
            EXIT WHEN person_id IS NULL;
            EXECUTE format('UPDATE horse SET %I = $1 WHERE id = $2', spec.col) USING person_id, h.id;
            given := given + 1;
        END LOOP;
        EXECUTE format('SELECT count(*) FROM horse WHERE intake_state = ''ACCEPTED'' AND archived_at IS NULL '
                       || 'AND %I IS NULL', spec.col) INTO left_over;
        total := total + given;
        rest := rest + left_over;
        RAISE NOTICE 'care team %: % horses given a person, % left "Chưa gán"', spec.role_name, given, left_over;
    END LOOP;
    RAISE NOTICE 'care team distribution: % assignments, % places left "Chưa gán"', total, rest;
END $$;
