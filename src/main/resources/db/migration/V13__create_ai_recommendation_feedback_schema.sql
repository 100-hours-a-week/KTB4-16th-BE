CREATE TABLE ai_recommendation_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recommendation_type ENUM('PHOTO', 'PLAYLIST') NOT NULL,
    recommendation_attempt TINYINT NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ai_recommendation_results PRIMARY KEY (id),
    CONSTRAINT fk_ai_recommendation_results_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT chk_ai_recommendation_results_type_attempt CHECK (
        (recommendation_type = 'PHOTO' AND recommendation_attempt IS NULL)
        OR (recommendation_type = 'PLAYLIST' AND recommendation_attempt BETWEEN 1 AND 3)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_recommendation_result_tracks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_id BIGINT NOT NULL,
    external_track_id VARCHAR(22) NOT NULL,
    `rank` INTEGER NOT NULL,

    CONSTRAINT pk_ai_recommendation_result_tracks PRIMARY KEY (id),
    CONSTRAINT fk_ai_recommendation_result_tracks_result
        FOREIGN KEY (recommendation_id) REFERENCES ai_recommendation_results(id),
    CONSTRAINT uk_ai_recommendation_result_tracks_rank UNIQUE (recommendation_id, `rank`),
    CONSTRAINT chk_ai_recommendation_result_tracks_rank CHECK (`rank` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_recommendation_feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    feedback ENUM('LIKE', 'DISLIKE') NOT NULL,
    selected_track_id VARCHAR(22) NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ai_recommendation_feedbacks PRIMARY KEY (id),
    CONSTRAINT fk_ai_recommendation_feedbacks_result
        FOREIGN KEY (recommendation_id) REFERENCES ai_recommendation_results(id),
    CONSTRAINT fk_ai_recommendation_feedbacks_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_ai_recommendation_feedbacks_result UNIQUE (recommendation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
