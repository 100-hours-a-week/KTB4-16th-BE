CREATE TABLE daily_song_runs (
    recommendation_date DATE NOT NULL,
    target_cutoff_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATING',
    target_count BIGINT NULL DEFAULT NULL,
    owner_token CHAR(36) NULL DEFAULT NULL,
    lease_until DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalized_at DATETIME(6) NULL DEFAULT NULL,
    completed_at DATETIME(6) NULL DEFAULT NULL,

    CONSTRAINT pk_daily_song_runs PRIMARY KEY (recommendation_date),
    CONSTRAINT chk_daily_song_runs_status
        CHECK (status IN ('CREATING', 'READY', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_daily_song_runs_target_count
        CHECK (target_count IS NULL OR target_count >= 0),
    CONSTRAINT chk_daily_song_runs_target_count_required
        CHECK (status = 'CREATING' OR target_count IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_song_jobs (
    job_id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_date DATE NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    claim_token CHAR(36) NULL DEFAULT NULL,
    lease_until DATETIME(6) NULL DEFAULT NULL,
    ai_call_started_at DATETIME(6) NULL DEFAULT NULL,
    music_track_id BIGINT NULL DEFAULT NULL,
    recommend_reason TEXT NULL,
    failure_code VARCHAR(100) NULL DEFAULT NULL,
    notification_event_id CHAR(36) NOT NULL,
    notified_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6) NULL DEFAULT NULL,

    CONSTRAINT pk_daily_song_jobs PRIMARY KEY (job_id),
    CONSTRAINT uk_daily_song_jobs_date_user UNIQUE (recommendation_date, user_id),
    CONSTRAINT uk_daily_song_jobs_notification_event UNIQUE (notification_event_id),
    INDEX idx_daily_song_jobs_user (user_id),
    INDEX idx_daily_song_jobs_music_track (music_track_id),
    INDEX idx_daily_song_jobs_status_lease (status, lease_until),
    INDEX idx_daily_song_jobs_notification_due
        (status, notified_at, recommendation_date, job_id),
    CONSTRAINT fk_daily_song_jobs_run
        FOREIGN KEY (recommendation_date) REFERENCES daily_song_runs (recommendation_date),
    CONSTRAINT fk_daily_song_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_daily_song_jobs_music_track
        FOREIGN KEY (music_track_id) REFERENCES music_tracks (music_track_id),
    CONSTRAINT chk_daily_song_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_daily_song_jobs_completed_result
        CHECK (status <> 'COMPLETED' OR (
            music_track_id IS NOT NULL
            AND recommend_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(recommend_reason)) > 0
        )),
    CONSTRAINT chk_daily_song_jobs_notified_completed
        CHECK (notified_at IS NULL OR status = 'COMPLETED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_song_dispatch_outbox (
    event_id CHAR(36) NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    publish_attempt_count INT NOT NULL DEFAULT 0,
    next_publish_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claim_token CHAR(36) NULL DEFAULT NULL,
    lease_until DATETIME(6) NULL DEFAULT NULL,
    published_at DATETIME(6) NULL DEFAULT NULL,
    last_error_code VARCHAR(100) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_daily_song_dispatch_outbox PRIMARY KEY (event_id),
    CONSTRAINT uk_daily_song_dispatch_outbox_job UNIQUE (job_id),
    INDEX idx_daily_song_dispatch_outbox_publish_due
        (status, next_publish_at, lease_until),
    CONSTRAINT fk_daily_song_dispatch_outbox_job
        FOREIGN KEY (job_id) REFERENCES daily_song_jobs (job_id),
    CONSTRAINT chk_daily_song_dispatch_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_daily_song_dispatch_outbox_attempt_count
        CHECK (publish_attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
