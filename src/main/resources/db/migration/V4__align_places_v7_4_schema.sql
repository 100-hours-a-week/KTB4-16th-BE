ALTER TABLE places
    DROP COLUMN external_place_id,
    DROP COLUMN place_name,
    CHANGE COLUMN dong_name legal_dong_name VARCHAR(100) NULL DEFAULT NULL,
    ADD COLUMN legal_dong_code VARCHAR(20) NULL DEFAULT NULL AFTER place_id,
    ADD INDEX idx_places_legal_dong_code (legal_dong_code),
    ADD CONSTRAINT uk_places_coordinates UNIQUE (latitude, longitude),
    ADD CONSTRAINT chk_places_legal_dong_pair CHECK (
        (legal_dong_code IS NULL AND legal_dong_name IS NULL)
        OR
        (legal_dong_code IS NOT NULL AND legal_dong_name IS NOT NULL)
    );
