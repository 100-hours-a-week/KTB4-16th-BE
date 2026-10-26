-- 장소·삭제 여부·최근 시각과 곡 ID를 함께 읽어 인기곡 집계의 records 접근 비용을 줄인다.
CREATE INDEX idx_records_popular_place_active_created_track
    ON records (place_id, deleted_at, created_at, music_track_id);
