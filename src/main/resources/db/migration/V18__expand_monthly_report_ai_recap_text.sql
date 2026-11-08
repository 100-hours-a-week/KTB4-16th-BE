-- 기존 회고와 NULL 허용을 유지하면서 최대 저장 길이를 300자로 확장한다.
ALTER TABLE monthly_reports
    MODIFY COLUMN ai_recap_text VARCHAR(300) NULL DEFAULT NULL;
