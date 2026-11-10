ALTER TABLE daily_song_jobs
    DROP CHECK chk_daily_song_jobs_status,
    ADD CONSTRAINT chk_daily_song_jobs_status_v2
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE daily_song_dispatch_outbox
    DROP CHECK chk_daily_song_dispatch_outbox_status,
    ADD CONSTRAINT chk_daily_song_dispatch_outbox_status_v2
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'CANCELLED'));
