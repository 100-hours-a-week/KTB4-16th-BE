-- A failed Chat Server creation still belongs to its Match, so it must be retained
-- as a terminal status instead of being returned to the matching pool.
ALTER TABLE match_requests
    DROP CHECK chk_match_requests_match_by_status,
    MODIFY COLUMN status ENUM('WAITING', 'MATCHING', 'MATCHED', 'EXPIRED', 'FAILED')
        NOT NULL DEFAULT 'WAITING',
    ADD CONSTRAINT chk_match_requests_match_by_status CHECK (
        (status IN ('WAITING', 'EXPIRED') AND match_id IS NULL)
        OR
        (status IN ('MATCHING', 'MATCHED', 'FAILED') AND match_id IS NOT NULL)
    );
