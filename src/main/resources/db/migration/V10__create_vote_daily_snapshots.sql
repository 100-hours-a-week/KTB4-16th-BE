CREATE TABLE vote_daily_snapshots (
    vote_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vote_date),
    CONSTRAINT chk_vote_daily_snapshots_status
        CHECK (status IN ('BUILDING', 'READY'))
);

CREATE TABLE vote_user_daily_assignments (
    vote_date DATE NOT NULL,
    user_id BIGINT NOT NULL,
    music_genre VARCHAR(50) NOT NULL,
    vote_question_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vote_date, user_id),
    KEY idx_vote_user_daily_assignments_question (vote_question_id),
    CONSTRAINT fk_vote_user_daily_assignments_snapshot
        FOREIGN KEY (vote_date) REFERENCES vote_daily_snapshots (vote_date),
    CONSTRAINT fk_vote_user_daily_assignments_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_vote_user_daily_assignments_question
        FOREIGN KEY (vote_question_id) REFERENCES vote_questions (vote_question_id)
);
