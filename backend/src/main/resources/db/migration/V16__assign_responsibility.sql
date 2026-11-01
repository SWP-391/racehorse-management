-- One assigned Head Trainer per horse, a temporary trainer with a return date, one running plan per horse, and the
-- decline and reminder state of a session assignment (BR-152 to BR-162, TECH-129, TECH-131).
-- The trainer column stays nullable here: an installation with no active Head Trainer cannot be backfilled and a
-- failing migration would stop the service. The service refuses to register or save a horse without one.
ALTER TABLE horse
    ADD COLUMN main_trainer_id BIGINT REFERENCES user_account (id),
    ADD COLUMN temp_trainer_id BIGINT REFERENCES user_account (id),
    ADD COLUMN temp_until      DATE,
    ADD CONSTRAINT horse_temp_trainer_pair CHECK ((temp_trainer_id IS NULL) = (temp_until IS NULL));

CREATE INDEX horse_main_trainer_idx ON horse (main_trainer_id);

UPDATE horse
SET main_trainer_id = (SELECT id FROM user_account WHERE role = 'HEAD_TRAINER' AND active ORDER BY id LIMIT 1)
WHERE main_trainer_id IS NULL;

-- Last refusal by the Groom (cleared on a new assignment) and the once-only markers of the reminder job.
ALTER TABLE training_session
    ADD COLUMN declined_by_id     BIGINT REFERENCES user_account (id),
    ADD COLUMN decline_reason     TEXT,
    ADD COLUMN declined_at        TIMESTAMPTZ,
    ADD COLUMN groom_reminded_at  TIMESTAMPTZ,
    ADD COLUMN trainer_warned_at  TIMESTAMPTZ;

-- Keep the highest id ACTIVE plan of a horse and cancel older duplicates, then forbid a second one.
UPDATE training_plan p
SET state = 'CANCELLED',
    cancel_reason = 'Chuyển đổi dữ liệu: mỗi ngựa chỉ một giáo án đang thực hiện',
    updated_at = now()
WHERE p.state = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM training_plan q WHERE q.horse_id = p.horse_id AND q.state = 'ACTIVE' AND q.id > p.id);

CREATE UNIQUE INDEX training_plan_one_active ON training_plan (horse_id) WHERE state = 'ACTIVE';
