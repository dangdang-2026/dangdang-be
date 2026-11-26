-- 운영자 처리 기록 (운영 v2 · 처리 기록). 신고 처리는 admin_actions 에 이미 남는다 → 그 밖의 운영자 일을 여기에 남긴다
-- 이용 제한 걸기·풀기, 당당 소식 올리기·내리기, 카드뉴스 만들기·올리기·정정·검수·내리기
-- 기록은 고치거나 지우지 않는다. 대상이 나중에 바뀌어도 그때 이름이 보이게 target_label 에 적어 둔다
CREATE TABLE admin_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id BIGINT NOT NULL,                       -- 한 운영자 (admin_actions 처럼 기록이라 FK 없이 번호만)
    action VARCHAR(30) NOT NULL,                    -- AdminLogAction (SANCTION_CREATE · CARD_WITHDRAW …)
    target_type VARCHAR(20) NOT NULL,               -- SANCTION / ANNOUNCEMENT / CARD
    target_id BIGINT NOT NULL,
    target_label VARCHAR(100),                      -- 그때의 닉네임·제목
    reason VARCHAR(50),                             -- 이용 제한 사유 · 정정 한 줄
    detail VARCHAR(200),                            -- 기간 · 자리 같은 덧붙임 (사람이 읽는 글)
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_admin_logs_occurred ON admin_logs (occurred_at DESC, id DESC);          -- 처리 기록 목록
CREATE INDEX ix_admin_logs_target ON admin_logs (target_type, target_id, occurred_at DESC);   -- 이 제한을 누가 풀었나
