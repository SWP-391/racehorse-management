-- Exactly one Club Manager (BR-242, DEC-079, TECH-A2, TECH-A3).
--
-- The Club Manager whose email equals the Flyway placeholder bootstrap_email (fed from BOOTSTRAP_EMAIL through
-- app.bootstrap.email) is kept. When the placeholder is empty or matches no Club Manager, the oldest Club Manager
-- (lowest id) is kept and a warning is raised. Every other Club Manager is hard-deleted after every row that pointed at
-- it has been re-pointed to the kept one. No address is written here.
--
-- Foreign keys to user_account(id) found by scanning V1..V26 (the block below does not depend on this list, it reads
-- pg_constraint, so a table added later is covered too):
--   audit_entry(actor_id); horse(owner_id, main_trainer_id, temp_trainer_id); health_status_change(changed_by_id);
--   training_lock(placed_by_id, lifted_by_id); lock_review_note(author_id); notification(recipient_id);
--   training_plan(created_by_id); training_session(responsible_person_id, declined_by_id); plan_version(created_by_id);
--   pending_lock_request(requested_by_id); examination(recorded_by_id); care_item(created_by_id); care_event(by_id);
--   incident_report(groom_id, acknowledged_by_id, closed_by_id); telemetry_threshold(set_by_id);
--   injury_mark(created_by_id); injury_mark_event(by_id); trial_video(uploaded_by_id);
--   chore_entry(groom_id, cancelled_by); restock_proposal(groom_id, decided_by); ration_version(saved_by);
--   groom_work_schedule(account_id); groom_leave(account_id, recorded_by, cancelled_by); supply_price(set_by);
--   stock_movement(actor_id); stock_expected_receipt(created_by); service_price(created_by);
--   horse_owner_period(owner_id); invoice(owner_id, closed_by); charge(owner_id, created_by); billing_month(closed_by);
--   password_recovery(account_id, also holds the activation codes, purpose ACTIVATION, V26);
--   conversation(started_by_id, state_by_id); conversation_participant(account_id); message(author_id);
--   conversation_read(account_id).
-- Sessions are held by the servlet container and tokens are not stored, so no session or token table exists to clean.
--
-- Several of these tables are append-only through triggers, so the user triggers of each table are switched off for
-- the re-pointing and back on right after; the whole script is one transaction.
DO $$
DECLARE
    configured TEXT := lower(btrim('${bootstrap_email}'));
    keep       BIGINT;
    doomed     BIGINT[];
    fk         RECORD;
    tbl        TEXT;
    tables     TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF configured <> '' THEN
        SELECT id INTO keep FROM user_account WHERE role = 'CLUB_MANAGER' AND email = configured ORDER BY id LIMIT 1;
    END IF;
    IF keep IS NULL THEN
        SELECT id INTO keep FROM user_account WHERE role = 'CLUB_MANAGER' ORDER BY active DESC, id LIMIT 1;
        IF keep IS NOT NULL AND EXISTS (SELECT 1 FROM user_account WHERE role = 'CLUB_MANAGER' AND id <> keep) THEN
            RAISE WARNING 'V27: no Club Manager matches the bootstrap email; keeping an active (else the oldest) Club Manager (id %)', keep;
        END IF;
    END IF;

    SELECT array_agg(id) INTO doomed FROM user_account WHERE role = 'CLUB_MANAGER' AND id <> keep;

    IF keep IS NOT NULL AND doomed IS NOT NULL THEN
        -- Recovery and activation codes of a deleted manager are deleted, not handed over.
        DELETE FROM password_recovery WHERE account_id = ANY (doomed);

        -- A deleted manager that shares a conversation with the kept one (or with another deleted one) would collide
        -- on the primary key after the update, so those rows go first.
        DELETE FROM conversation_participant p WHERE p.account_id = ANY (doomed) AND (
            EXISTS (SELECT 1 FROM conversation_participant k WHERE k.conversation_id = p.conversation_id AND k.account_id = keep)
            OR EXISTS (SELECT 1 FROM conversation_participant q WHERE q.conversation_id = p.conversation_id
                       AND q.account_id = ANY (doomed) AND q.account_id < p.account_id));
        DELETE FROM conversation_read p WHERE p.account_id = ANY (doomed) AND (
            EXISTS (SELECT 1 FROM conversation_read k WHERE k.conversation_id = p.conversation_id AND k.account_id = keep)
            OR EXISTS (SELECT 1 FROM conversation_read q WHERE q.conversation_id = p.conversation_id
                       AND q.account_id = ANY (doomed) AND q.account_id < p.account_id));

        FOR fk IN
            SELECT DISTINCT c.conrelid::regclass::TEXT AS tbl, a.attname::TEXT AS col
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
            WHERE c.contype = 'f' AND c.confrelid = 'user_account'::regclass
        LOOP
            IF NOT (fk.tbl = ANY (tables)) THEN
                tables := tables || fk.tbl;
                EXECUTE format('ALTER TABLE %s DISABLE TRIGGER USER', fk.tbl);
            END IF;
        END LOOP;

        FOR fk IN
            SELECT DISTINCT c.conrelid::regclass::TEXT AS tbl, a.attname::TEXT AS col
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
            WHERE c.contype = 'f' AND c.confrelid = 'user_account'::regclass
        LOOP
            EXECUTE format('UPDATE %s SET %I = $1 WHERE %I = ANY ($2)', fk.tbl, fk.col, fk.col) USING keep, doomed;
        END LOOP;

        FOREACH tbl IN ARRAY tables LOOP
            EXECUTE format('ALTER TABLE %s ENABLE TRIGGER USER', tbl);
        END LOOP;

        DELETE FROM user_account WHERE id = ANY (doomed);
    END IF;
END
$$;

-- At most one row may hold the role; the application also refuses to assign it (CLUB_MANAGER_NOT_ASSIGNABLE).
CREATE UNIQUE INDEX user_account_single_club_manager ON user_account (role) WHERE role = 'CLUB_MANAGER';
