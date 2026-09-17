-- MULO initial schema
-- Target DB: MySQL 8.4
-- Source: MULO table definition v6 + latest ERD
-- IMPORTANT: After this migration has been applied to a shared/developer DB, do not edit it.
--            Add V2__, V3__ ... migrations for subsequent schema changes.

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    active_email VARCHAR(254)
        GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN email ELSE NULL END) STORED,
    password_hash VARCHAR(60) NOT NULL,
    nickname VARCHAR(10) NOT NULL,
    active_nickname VARCHAR(10)
        GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN nickname ELSE NULL END) STORED,
    music_genre VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,
    deleted_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_active_email UNIQUE (active_email),
    CONSTRAINT uk_users_active_nickname UNIQUE (active_nickname),
    CONSTRAINT chk_users_nickname_length CHECK (CHAR_LENGTH(nickname) BETWEEN 2 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_tokens_user UNIQUE (user_id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE places (
    place_id BIGINT NOT NULL AUTO_INCREMENT,
    external_place_id VARCHAR(32) NULL DEFAULT NULL,
    place_name VARCHAR(255) NULL DEFAULT NULL,
    dong_name VARCHAR(100) NULL DEFAULT NULL,
    latitude DECIMAL(11, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_places PRIMARY KEY (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE music_tracks (
    music_track_id BIGINT NOT NULL AUTO_INCREMENT,
    external_track_id VARCHAR(22) NOT NULL,
    title VARCHAR(255) NOT NULL,
    artist_name VARCHAR(255) NOT NULL,
    album_image_url VARCHAR(255) NOT NULL,
    external_url VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_music_tracks PRIMARY KEY (music_track_id),
    CONSTRAINT uk_music_tracks_external_track_id UNIQUE (external_track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE records (
    record_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    music_track_id BIGINT NOT NULL,
    weather_condition ENUM(
        'CLEAR', 'CLOUDY', 'OVERCAST', 'RAIN', 'SNOW', 'RAIN_SNOW', 'SHOWER'
    ) NULL DEFAULT NULL,
    temperature DECIMAL(3, 1) NULL DEFAULT NULL,
    mood_score TINYINT NOT NULL,
    comment VARCHAR(80) NULL DEFAULT NULL,
    deleted_at DATETIME NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_records PRIMARY KEY (record_id),
    CONSTRAINT fk_records_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_records_place
        FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT fk_records_music_track
        FOREIGN KEY (music_track_id) REFERENCES music_tracks(music_track_id),
    CONSTRAINT chk_records_mood_score CHECK (mood_score BETWEEN -50 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE record_photos (
    record_photo_id BIGINT NOT NULL AUTO_INCREMENT,
    record_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_record_photos PRIMARY KEY (record_photo_id),
    CONSTRAINT uk_record_photos_record UNIQUE (record_id),
    CONSTRAINT fk_record_photos_record
        FOREIGN KEY (record_id) REFERENCES records(record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE uploads (
    upload_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_uploads PRIMARY KEY (upload_id),
    CONSTRAINT fk_uploads_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Reserved for a future server-side draft feature. Current V1 product policy uses browser local storage.
CREATE TABLE record_drafts (
    record_draft_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    place_id BIGINT NULL DEFAULT NULL,
    music_track_id BIGINT NULL DEFAULT NULL,
    mood_score TINYINT NOT NULL,
    comment VARCHAR(80) NULL DEFAULT NULL,
    photo_url VARCHAR(255) NULL DEFAULT NULL,
    expires_at DATETIME NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_record_drafts PRIMARY KEY (record_draft_id),
    CONSTRAINT fk_record_drafts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_record_drafts_place
        FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT fk_record_drafts_music_track
        FOREIGN KEY (music_track_id) REFERENCES music_tracks(music_track_id),
    CONSTRAINT chk_record_drafts_mood_score CHECK (mood_score BETWEEN -50 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE friend_requests (
    friend_request_id BIGINT NOT NULL AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    user_low_id BIGINT
        GENERATED ALWAYS AS (LEAST(requester_id, addressee_id)) STORED,
    user_high_id BIGINT
        GENERATED ALWAYS AS (GREATEST(requester_id, addressee_id)) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_friend_requests PRIMARY KEY (friend_request_id),
    CONSTRAINT fk_friend_requests_requester
        FOREIGN KEY (requester_id) REFERENCES users(user_id),
    CONSTRAINT fk_friend_requests_addressee
        FOREIGN KEY (addressee_id) REFERENCES users(user_id),
    CONSTRAINT chk_friend_requests_not_self CHECK (requester_id <> addressee_id),
    CONSTRAINT uk_friend_requests_user_pair UNIQUE (user_low_id, user_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE friendships (
    friendship_id BIGINT NOT NULL AUTO_INCREMENT,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_friendships PRIMARY KEY (friendship_id),
    CONSTRAINT fk_friendships_user_low
        FOREIGN KEY (user_low_id) REFERENCES users(user_id),
    CONSTRAINT fk_friendships_user_high
        FOREIGN KEY (user_high_id) REFERENCES users(user_id),
    CONSTRAINT chk_friendships_user_order CHECK (user_low_id < user_high_id),
    CONSTRAINT uk_friendships_user_pair UNIQUE (user_low_id, user_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE monthly_reports (
    monthly_report_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    report_year SMALLINT NOT NULL,
    report_month SMALLINT NOT NULL,
    record_count INTEGER NOT NULL DEFAULT 0,
    top_place_id BIGINT NULL DEFAULT NULL,
    top_artist_name VARCHAR(255) NULL DEFAULT NULL,
    ai_recap_text VARCHAR(100) NULL DEFAULT NULL,
    ai_recap_status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_monthly_reports PRIMARY KEY (monthly_report_id),
    CONSTRAINT fk_monthly_reports_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_monthly_reports_top_place
        FOREIGN KEY (top_place_id) REFERENCES places(place_id),
    CONSTRAINT uk_monthly_reports_user_period UNIQUE (user_id, report_year, report_month),
    CONSTRAINT chk_monthly_reports_month CHECK (report_month BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE monthly_mood_stats (
    mood_stat_id BIGINT NOT NULL AUTO_INCREMENT,
    monthly_report_id BIGINT NOT NULL,
    average_mood_score DECIMAL(3, 1) NOT NULL,

    CONSTRAINT pk_monthly_mood_stats PRIMARY KEY (mood_stat_id),
    CONSTRAINT fk_monthly_mood_stats_report
        FOREIGN KEY (monthly_report_id) REFERENCES monthly_reports(monthly_report_id),
    CONSTRAINT uk_monthly_mood_stats_report UNIQUE (monthly_report_id),
    CONSTRAINT chk_monthly_mood_stats_score CHECK (average_mood_score BETWEEN -50 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE monthly_photo_scene_stats (
    photo_scene_stat_id BIGINT NOT NULL AUTO_INCREMENT,
    monthly_report_id BIGINT NOT NULL,
    scene_tag VARCHAR(50) NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    ratio TINYINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT pk_monthly_photo_scene_stats PRIMARY KEY (photo_scene_stat_id),
    CONSTRAINT fk_monthly_photo_scene_stats_report
        FOREIGN KEY (monthly_report_id) REFERENCES monthly_reports(monthly_report_id),
    CONSTRAINT uk_monthly_photo_scene_stats_report_tag UNIQUE (monthly_report_id, scene_tag),
    CONSTRAINT chk_monthly_photo_scene_stats_ratio CHECK (ratio BETWEEN 0 AND 100),
    CONSTRAINT chk_monthly_photo_scene_stats_count CHECK (count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_playlists (
    recommendation_playlist_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_recommendation_playlists PRIMARY KEY (recommendation_playlist_id),
    CONSTRAINT fk_recommendation_playlists_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_recommendation_playlists_user UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_playlist_items (
    recommendation_playlist_item_id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_playlist_id BIGINT NOT NULL,
    music_track_id BIGINT NOT NULL,
    music_order INTEGER NOT NULL,

    CONSTRAINT pk_recommendation_playlist_items PRIMARY KEY (recommendation_playlist_item_id),
    CONSTRAINT fk_recommendation_playlist_items_playlist
        FOREIGN KEY (recommendation_playlist_id)
        REFERENCES recommendation_playlists(recommendation_playlist_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_playlist_items_music
        FOREIGN KEY (music_track_id) REFERENCES music_tracks(music_track_id),
    CONSTRAINT uk_recommendation_playlist_items_rank UNIQUE (recommendation_playlist_id, music_order),
    CONSTRAINT chk_recommendation_playlist_items_music_order CHECK (music_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vote_questions (
    vote_question_id BIGINT NOT NULL AUTO_INCREMENT,
    question_text VARCHAR(100) NOT NULL,
    option_a VARCHAR(100) NOT NULL,
    option_b VARCHAR(100) NOT NULL,
    music_genre VARCHAR(50) NOT NULL,
    vote_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_vote_questions PRIMARY KEY (vote_question_id),
    CONSTRAINT uk_vote_questions_genre_date UNIQUE (music_genre, vote_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vote_answers (
    vote_answer_id BIGINT NOT NULL AUTO_INCREMENT,
    vote_question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    selected_option VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_vote_answers PRIMARY KEY (vote_answer_id),
    CONSTRAINT fk_vote_answers_question
        FOREIGN KEY (vote_question_id) REFERENCES vote_questions(vote_question_id),
    CONSTRAINT fk_vote_answers_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_vote_answers_user_question UNIQUE (user_id, vote_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_rooms (
    chat_room_id BIGINT NOT NULL AUTO_INCREMENT,
    vote_question_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_chat_rooms PRIMARY KEY (chat_room_id),
    CONSTRAINT fk_chat_rooms_vote_question
        FOREIGN KEY (vote_question_id) REFERENCES vote_questions(vote_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_requests (
    match_request_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    vote_answer_id BIGINT NOT NULL,
    status ENUM('WAITING', 'MATCHED', 'EXPIRED') NOT NULL DEFAULT 'WAITING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chat_room_id BIGINT NULL DEFAULT NULL,

    CONSTRAINT pk_match_requests PRIMARY KEY (match_request_id),
    CONSTRAINT fk_match_requests_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_match_requests_vote_answer
        FOREIGN KEY (vote_answer_id) REFERENCES vote_answers(vote_answer_id),
    CONSTRAINT fk_match_requests_chat_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_match_requests_vote_answer UNIQUE (vote_answer_id),
    CONSTRAINT chk_match_requests_room_by_status CHECK (
        (status = 'MATCHED' AND chat_room_id IS NOT NULL)
        OR
        (status IN ('WAITING', 'EXPIRED') AND chat_room_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_room_participants (
    chat_room_participant_id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME NULL DEFAULT NULL,
    left_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_chat_room_participants PRIMARY KEY (chat_room_participant_id),
    CONSTRAINT fk_chat_room_participants_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_room_participants_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_chat_room_participants_room_user UNIQUE (chat_room_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
    chat_message_id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL COMMENT '메시지가 전송된 채팅방 ID',
    user_id BIGINT NOT NULL COMMENT '메시지를 전송한 사용자 ID',
    content VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_chat_messages PRIMARY KEY (chat_message_id),
    CONSTRAINT fk_chat_messages_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type ENUM(
        'MONTHLY_REPORT_CREATED',
        'AI_PLAYLIST',
        'MATCH_COMPLETED',
        'FRIEND_REQUEST',
        'FRIEND_ACCEPTED'
    ) NOT NULL,
    monthly_report_id BIGINT NULL DEFAULT NULL,
    recommendation_playlist_id BIGINT NULL DEFAULT NULL,
    chat_room_id BIGINT NULL DEFAULT NULL,
    friendship_id BIGINT NULL DEFAULT NULL,
    friend_request_id BIGINT NULL DEFAULT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(100) NOT NULL,
    read_at DATETIME NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_notifications_monthly_report
        FOREIGN KEY (monthly_report_id) REFERENCES monthly_reports(monthly_report_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_notifications_playlist
        FOREIGN KEY (recommendation_playlist_id) REFERENCES recommendation_playlists(recommendation_playlist_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_notifications_chat_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_notifications_friendship
        FOREIGN KEY (friendship_id) REFERENCES friendships(friendship_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_notifications_friend_request
        FOREIGN KEY (friend_request_id) REFERENCES friend_requests(friend_request_id)
        ON DELETE SET NULL,
    CONSTRAINT uk_notifications_monthly_report UNIQUE (monthly_report_id),
    CONSTRAINT uk_notifications_playlist UNIQUE (recommendation_playlist_id),
    CONSTRAINT uk_notifications_friendship UNIQUE (friendship_id),
    CONSTRAINT uk_notifications_friend_request UNIQUE (friend_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
