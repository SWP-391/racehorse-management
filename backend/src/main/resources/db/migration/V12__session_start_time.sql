-- Planned start time (BR-120, DEC-043, TECH-082): club-local wall time; sessions created before stay NULL.
ALTER TABLE training_session
    ADD COLUMN planned_start_time TIME NULL;

-- Late-change threshold for responsible persons (BR-123, DEC-044, TECH-086).
INSERT INTO club_setting (key, value) VALUES ('late_change_threshold_hours', '12');
