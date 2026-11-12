-- 한 장 요약 카드
CREATE TABLE content_cards (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   title VARCHAR(100) NOT NULL,
   series VARCHAR(30),                             -- BEFORE_VISIT / GUIDELINE / POSTPARTUM / MIND
   body JSONB NOT NULL,                            -- 슬라이드 목록
   sources JSONB NOT NULL,                         -- 공식 출처
   checked_on DATE NOT NULL,                       -- 원문 확인일
   reviewer VARCHAR(50),
   reviewed_on DATE,
   status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',    -- DRAFT / PUBLISHED / CORRECTED / WITHDRAWN / RECHECK
   corrected_at TIMESTAMPTZ,
   correction_note TEXT,
   withdrawn_at TIMESTAMPTZ,
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 게시글
CREATE TABLE posts (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   profile_id BIGINT REFERENCES community_profiles(id) ON DELETE SET NULL,
   content_card_id BIGINT REFERENCES content_cards(id) ON DELETE SET NULL,
   category VARCHAR(20) NOT NULL,                  -- PostCategory enum 값 (CHECK 없음 → 값이 바뀌어도 SQL은 그대로)
   sub_category VARCHAR(10),                       -- FREE / QUESTION (U-22, 이번엔 안 씀)
   title VARCHAR(100) NOT NULL,
   content TEXT NOT NULL,
   is_anonymous BOOLEAN NOT NULL DEFAULT false,
   author_stage_label VARCHAR(20),                 -- 글 쓸 때 시기 (주수 원천 결정 뒤에 채움)
   poll JSONB,                                     -- 투표 항목 2~5개, 올린 뒤 수정 불가
   is_notice BOOLEAN NOT NULL DEFAULT false,
   view_count INT NOT NULL DEFAULT 0,
   hidden_reason VARCHAR(50),                      -- 운영정책으로 가려진 이유
   safety_flag VARCHAR(20),                        -- URGENT_MEDICAL / MIND (6주차)
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ
);

-- 임시 저장 글 (U-16). 익명 토글을 되살리려고 DDL에 is_anonymous 한 칸을 더했다
CREATE TABLE post_drafts (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 profile_id BIGINT NOT NULL REFERENCES community_profiles(id) ON DELETE CASCADE,
 category VARCHAR(20),
 title VARCHAR(100),
 content TEXT,
 is_anonymous BOOLEAN NOT NULL DEFAULT false,
 poll JSONB,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_post_drafts_profile UNIQUE (profile_id)   -- 한 사람에 임시 글 하나
);

CREATE UNIQUE INDEX ux_posts_card ON posts (content_card_id) WHERE content_card_id IS NOT NULL;
CREATE INDEX ix_posts_latest ON posts (created_at DESC, id DESC);                -- 최신순 목록
CREATE INDEX ix_posts_category_latest ON posts (category, created_at DESC, id DESC); -- 게시판별 목록
