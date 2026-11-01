-- One photo per horse (BR-184): two small renditions kept in photo storage under random keys, never in the database.
ALTER TABLE horse
    ADD COLUMN photo_key        VARCHAR(64),
    ADD COLUMN photo_thumb_key  VARCHAR(64),
    ADD COLUMN photo_updated_at TIMESTAMPTZ;
