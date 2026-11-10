-- 추천창을 닫으면 목록을 폐기하므로 화면 복원용 테이블만 제거한다.
-- V7에서 알림 FK는 이미 제거됐으며 평가 이력(ai_recommendation_*)은 독립적으로 보존한다.
DROP TABLE recommendation_playlist_items;
DROP TABLE recommendation_playlists;
