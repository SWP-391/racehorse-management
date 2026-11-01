-- One body-region catalog for the whole application (BR-110, DEC-036, TECH-054, TECH-055).
-- Examinations recorded before keep their free-text body_area unchanged; new ones use body_region (+ detail).
ALTER TABLE examination
    ADD COLUMN body_region VARCHAR(20) CHECK (body_region IN ('HEAD', 'NECK', 'WITHERS', 'L_SHOULDER', 'R_SHOULDER', 'CHEST', 'BACK', 'LOIN', 'BELLY', 'CROUP', 'L_HIP', 'R_HIP', 'TAIL_BASE', 'LF_ELBOW', 'LF_FOREARM', 'LF_CARPUS', 'LF_CANNON', 'LF_FETLOCK', 'LF_PASTERN', 'LF_HOOF', 'RF_ELBOW', 'RF_FOREARM', 'RF_CARPUS', 'RF_CANNON', 'RF_FETLOCK', 'RF_PASTERN', 'RF_HOOF', 'LH_STIFLE', 'LH_GASKIN', 'LH_HOCK', 'LH_CANNON', 'LH_FETLOCK', 'LH_PASTERN', 'LH_HOOF', 'RH_STIFLE', 'RH_GASKIN', 'RH_HOCK', 'RH_CANNON', 'RH_FETLOCK', 'RH_PASTERN', 'RH_HOOF')),
    ADD COLUMN body_detail TEXT;

-- Optional region on an incident report (BR-111).
ALTER TABLE incident_report
    ADD COLUMN body_region VARCHAR(20) CHECK (body_region IN ('HEAD', 'NECK', 'WITHERS', 'L_SHOULDER', 'R_SHOULDER', 'CHEST', 'BACK', 'LOIN', 'BELLY', 'CROUP', 'L_HIP', 'R_HIP', 'TAIL_BASE', 'LF_ELBOW', 'LF_FOREARM', 'LF_CARPUS', 'LF_CANNON', 'LF_FETLOCK', 'LF_PASTERN', 'LF_HOOF', 'RF_ELBOW', 'RF_FOREARM', 'RF_CARPUS', 'RF_CANNON', 'RF_FETLOCK', 'RF_PASTERN', 'RF_HOOF', 'LH_STIFLE', 'LH_GASKIN', 'LH_HOCK', 'LH_CANNON', 'LH_FETLOCK', 'LH_PASTERN', 'LH_HOOF', 'RH_STIFLE', 'RH_GASKIN', 'RH_HOCK', 'RH_CANNON', 'RH_FETLOCK', 'RH_PASTERN', 'RH_HOOF'));

-- Đã hủy (nhập nhầm) for a mark created by mistake (BR-112, ST-064, TECH-056).
ALTER TABLE injury_mark DROP CONSTRAINT injury_mark_status_check;
ALTER TABLE injury_mark ADD CONSTRAINT injury_mark_status_check
    CHECK (status IN ('NEW', 'RECOVERING', 'RECOVERED', 'CANCELLED'));

ALTER TABLE injury_mark_event DROP CONSTRAINT injury_mark_event_from_status_check;
ALTER TABLE injury_mark_event ADD CONSTRAINT injury_mark_event_from_status_check
    CHECK (from_status IN ('NEW', 'RECOVERING', 'RECOVERED', 'CANCELLED'));
ALTER TABLE injury_mark_event DROP CONSTRAINT injury_mark_event_to_status_check;
ALTER TABLE injury_mark_event ADD CONSTRAINT injury_mark_event_to_status_check
    CHECK (to_status IN ('NEW', 'RECOVERING', 'RECOVERED', 'CANCELLED'));

-- Sessions assigned to a person (BR-114).
CREATE INDEX training_session_responsible_idx ON training_session (responsible_person_id, scheduled_date DESC);
