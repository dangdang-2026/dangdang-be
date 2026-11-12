-- 커뮤니티 프로필 관련
CREATE TABLE community_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nickname VARCHAR(20) NOT NULL,                  -- 화면 규칙은 2~10자, 칸은 여유 있게
    show_week_badge BOOLEAN NOT NULL DEFAULT false, -- 임신 주수 배지, 기본 꺼짐
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_community_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_community_profiles_nickname UNIQUE (nickname)
);
