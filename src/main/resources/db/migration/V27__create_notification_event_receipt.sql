-- 수신자 0명도 처리 완료로 구별하며 inbox와 같은 transaction에서만 기록합니다.
CREATE TABLE notification_event_receipt (
    event_id UUID PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
