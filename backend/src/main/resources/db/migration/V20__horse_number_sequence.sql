-- Registration numbers are generated: NH-YYYY-NNNN from this sequence (BR-192, BR-193).
-- It starts after the largest suffix of existing numbers in that format, so an older typed number never collides.
CREATE SEQUENCE horse_number_seq START WITH 1 INCREMENT BY 1;

SELECT setval('horse_number_seq',
       COALESCE((SELECT MAX(substring(registration_number FROM '^NH-[0-9]{4}-([0-9]+)$')::bigint)
                   FROM horse WHERE registration_number ~ '^NH-[0-9]{4}-[0-9]+$'), 0) + 1,
       false);
