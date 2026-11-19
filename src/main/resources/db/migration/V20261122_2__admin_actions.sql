-- 운영자가 한 일 기록 (누가 언제 무엇을 가렸나) — 운영자 화면 구상 §1
CREATE TABLE admin_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id BIGINT NOT NULL,                                  -- 처리한 운영자 (기록이라 FK 없이 번호만 남긴다)
    action VARCHAR(10) NOT NULL,                               -- HIDE / UNHIDE / DISMISS
    target_type VARCHAR(10) NOT NULL,                          -- POST / COMMENT
    target_id BIGINT NOT NULL,
    reason VARCHAR(30),                                        -- 가릴 때 사유 (신고 사유와 같은 말)
    memo VARCHAR(200),                                         -- 운영자 메모 (사용자에게 안 보임)
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_admin_actions_target ON admin_actions (target_type, target_id, occurred_at DESC);  -- 원문 화면의 처리 기록
CREATE INDEX ix_reports_status ON reports (status, created_at DESC);                               -- 신고 처리함
