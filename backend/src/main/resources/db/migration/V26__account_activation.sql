-- An account the Club Manager creates without a password is pending until its owner activates it with a mailed code
-- (BR-235 to BR-238, DEC-076, TECH-231). A pending account is also not active, so every existing gate that refuses an
-- inactive account refuses it too; the flag only tells "invited" from "deactivated".
ALTER TABLE user_account ADD COLUMN pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_account ADD CONSTRAINT user_account_pending_not_active CHECK (NOT (pending AND active));

-- The purpose picks the expiry (RESET 5 minutes, ACTIVATION 24 hours) and the mail text (TECH-233). Old codes are resets.
ALTER TABLE password_recovery ADD COLUMN purpose VARCHAR(10) NOT NULL DEFAULT 'RESET'
    CHECK (purpose IN ('RESET', 'ACTIVATION'));
