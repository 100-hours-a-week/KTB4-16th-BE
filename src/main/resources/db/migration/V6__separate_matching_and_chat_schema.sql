-- Keep user-selected onboarding genres separate from the AI-assigned daily vote group.
ALTER TABLE users
    ADD COLUMN preferred_genres JSON NULL AFTER music_genre,
    ADD COLUMN genre_onboarding_done BOOLEAN NOT NULL DEFAULT FALSE AFTER preferred_genres;

-- Backend owns match lifecycle; chat_room_id is an ID owned by the separate Chat DB.
CREATE TABLE matches (
    match_id BIGINT NOT NULL AUTO_INCREMENT,
    vote_question_id BIGINT NOT NULL,
    chat_room_id BIGINT NULL DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matched_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_matches PRIMARY KEY (match_id),
    CONSTRAINT fk_matches_vote_question
        FOREIGN KEY (vote_question_id) REFERENCES vote_questions(vote_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Preserve the existing room-to-question and room creation-time association for
-- each legacy room. matched_at remains NULL because V1 did not store that event time.
INSERT INTO matches (vote_question_id, chat_room_id, created_at, matched_at)
SELECT chat_room.vote_question_id, chat_room.chat_room_id, chat_room.created_at, NULL
FROM chat_rooms AS chat_room;

-- Add and populate the new relation before removing the legacy chat-room reference.
ALTER TABLE match_requests
    ADD COLUMN match_id BIGINT NULL DEFAULT NULL AFTER vote_answer_id;

UPDATE match_requests AS match_request
JOIN matches AS match_record
    ON match_record.chat_room_id = match_request.chat_room_id
SET match_request.match_id = match_record.match_id
WHERE match_request.status = 'MATCHED'
  AND match_request.chat_room_id IS NOT NULL;

-- Replace the old room-based invariant with the match lifecycle invariant.
ALTER TABLE match_requests
    DROP CHECK chk_match_requests_room_by_status,
    DROP FOREIGN KEY fk_match_requests_chat_room,
    MODIFY COLUMN status ENUM('WAITING', 'MATCHING', 'MATCHED', 'EXPIRED')
        NOT NULL DEFAULT 'WAITING',
    DROP COLUMN chat_room_id,
    ADD CONSTRAINT fk_match_requests_match
        FOREIGN KEY (match_id) REFERENCES matches(match_id),
    ADD CONSTRAINT chk_match_requests_match_by_status CHECK (
        (status IN ('WAITING', 'EXPIRED') AND match_id IS NULL)
        OR
        (status IN ('MATCHING', 'MATCHED') AND match_id IS NOT NULL)
    );

-- The room ID remains a logical reference to the Chat DB, without a cross-database FK.
ALTER TABLE notifications
    DROP FOREIGN KEY fk_notifications_chat_room;

-- Chat-owned tables are removed only after their referencing Backend FK is gone.
-- Their existing rows are intentionally not copied to this Backend database.
DROP TABLE chat_messages;
DROP TABLE chat_room_participants;
DROP TABLE chat_rooms;
