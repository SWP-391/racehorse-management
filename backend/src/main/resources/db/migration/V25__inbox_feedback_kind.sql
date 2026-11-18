-- A conversation is either an ordinary message or a feedback to the Club Manager role, shown highlighted in the chat.
-- Feedback is only ever addressed to the Club Manager role.
ALTER TABLE conversation ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'MESSAGE' CHECK (kind IN ('MESSAGE', 'FEEDBACK'));
ALTER TABLE conversation ADD CONSTRAINT conversation_feedback_to_role CHECK (kind = 'MESSAGE' OR to_role IS NOT NULL);
