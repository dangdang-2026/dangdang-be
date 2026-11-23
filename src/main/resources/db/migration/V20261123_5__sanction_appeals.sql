-- 이용 제한 이의 신청 (U-17 「이의 신청하기」). 제한 하나에 한 번만
CREATE TABLE sanction_appeals (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sanction_id BIGINT NOT NULL REFERENCES user_sanctions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sanction_appeals UNIQUE (sanction_id)
);
CREATE INDEX ix_sanction_appeals_latest ON sanction_appeals (created_at DESC, id DESC);   -- 운영자 목록
