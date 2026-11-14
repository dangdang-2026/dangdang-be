-- 안 보는 사람 (한쪽 방향)
CREATE TABLE profile_blocks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,          -- 안 보기 한 사람
    blocked_profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,  -- 안 보이는 사람
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_profile_blocks UNIQUE (profile_id, blocked_profile_id),
    CONSTRAINT ck_profile_blocks_not_self CHECK (profile_id <> blocked_profile_id)
);

-- 신고
CREATE TABLE reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT REFERENCES community_profiles(id) ON DELETE SET NULL,  -- 글쓴이에게 안 알려짐
    target_type VARCHAR(10) NOT NULL,               -- POST / COMMENT
    target_id BIGINT NOT NULL,                      -- 신고한 글과 댓글 id
    reason VARCHAR(30) NOT NULL,                    -- MEDICAL_ADVICE / NUMBER_COMPARE / AD_SALES / PRIVACY / ABUSE / ETC
    detail VARCHAR(200),                            -- ETC일 때
    status VARCHAR(10) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED / HIDDEN / DISMISSED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMPTZ,
    CONSTRAINT uk_reports_once UNIQUE (reporter_id, target_type, target_id)
);

-- 투표 응답
CREATE TABLE poll_votes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,
    option_index SMALLINT NOT NULL,                 -- 고른 항목 번호 (0부터)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_poll_votes UNIQUE (post_id, profile_id)
);

CREATE INDEX ix_profile_blocks_list ON profile_blocks (profile_id, created_at DESC, id DESC);  -- 차단 목록
CREATE INDEX ix_reports_target ON reports (target_type, target_id);                             -- 운영자 화면
