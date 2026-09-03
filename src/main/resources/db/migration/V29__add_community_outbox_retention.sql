-- 원본은 완료 확인 또는 근거 있는 운영 종료 이후에만 유한 보존 창을 시작합니다.
ALTER TABLE community_event_outbox
 ADD COLUMN completion_checked_at TIMESTAMPTZ,
 ADD COLUMN eligible_at TIMESTAMPTZ,
 ADD COLUMN terminal_at TIMESTAMPTZ,
 ADD COLUMN terminal_operator VARCHAR(1000),
 ADD COLUMN terminal_reason VARCHAR(1000),
 ADD COLUMN terminal_impact VARCHAR(1000),
 ADD COLUMN terminal_evidence VARCHAR(1000),
 ADD CONSTRAINT community_outbox_terminal_evidence CHECK (
   (terminal_at IS NULL AND terminal_operator IS NULL AND terminal_reason IS NULL AND terminal_impact IS NULL AND terminal_evidence IS NULL)
   OR (terminal_at IS NOT NULL AND eligible_at IS NOT NULL AND LENGTH(TRIM(terminal_operator))>0
    AND LENGTH(TRIM(terminal_reason))>0 AND LENGTH(TRIM(terminal_impact))>0 AND LENGTH(TRIM(terminal_evidence))>0
    AND terminal_operator IS NOT NULL AND terminal_reason IS NOT NULL AND terminal_impact IS NOT NULL AND terminal_evidence IS NOT NULL));
CREATE INDEX community_outbox_completion_scan ON community_event_outbox(completion_checked_at NULLS FIRST,id) WHERE eligible_at IS NULL;
CREATE INDEX community_outbox_retention ON community_event_outbox(eligible_at,id) WHERE eligible_at IS NOT NULL;
