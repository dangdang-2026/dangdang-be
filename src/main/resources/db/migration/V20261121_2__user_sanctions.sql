-- 커뮤니티 이용 제한 (COM-09 · U-17). 운영자가 건다. 글·댓글 쓰기만 막고 읽기는 된다
CREATE TABLE user_sanctions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,                    -- 신고 사유와 같은 말 (MEDICAL_ADVICE · ABUSE …)
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,                            -- NULL = 기간 없음 (운영자가 풀 때까지)
    lifted_at TIMESTAMPTZ,                          -- 운영자가 일찍 풀면
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,   -- 건 운영자
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_user_sanctions_user ON user_sanctions (user_id, starts_at DESC);
