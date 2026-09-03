-- 여러 application 인스턴스의 claim과 중단 후 재시도를 원본 row에 결속합니다.
ALTER TABLE community_event_outbox
    ADD COLUMN lease_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN last_publish_error VARCHAR(120),
    ADD CONSTRAINT chk_community_outbox_lease CHECK ((lease_token IS NULL) = (lease_until IS NULL));
