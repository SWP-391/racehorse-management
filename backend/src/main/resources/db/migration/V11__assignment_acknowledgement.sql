-- Responsible-person hand-over (BR-118, ST-065, ST-066, DEC-041, TECH-061). The state exists only while the
-- responsible person is a Groom (Q-046 default); NULL otherwise. History of changes lives in the audit log.
ALTER TABLE training_session
    ADD COLUMN assignment_ack    VARCHAR(20) NULL CHECK (assignment_ack IN ('UNCONFIRMED', 'ACKNOWLEDGED')),
    ADD COLUMN assignment_ack_at TIMESTAMPTZ NULL;

UPDATE training_session s
SET assignment_ack = 'UNCONFIRMED'
FROM user_account a
WHERE s.responsible_person_id = a.id
  AND a.role = 'GROOM';
