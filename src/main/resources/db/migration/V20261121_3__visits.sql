-- 진료 일정 (C). 날짜를 아직 모르면 visit_at = null (목록에서 맨 뒤)
-- 서버가 다음 진료일을 추천하지 않고, 사용자가 적은 날짜만 둠
CREATE TABLE visits (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   care_site_id BIGINT REFERENCES care_sites(id) ON DELETE SET NULL,  -- 내 병원 (없어도 됨)
   visit_at TIMESTAMPTZ,                            -- 진료 날짜·시각 (미정이면 null)
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ                           -- null = 살아 있음
);

-- 내 일정 목록 (진료 날짜 순)
CREATE INDEX idx_visits_user_visit_at ON visits (user_id, visit_at) WHERE deleted_at IS NULL;

-- 진료 때 물어볼 질문 (일정 하나에 여러 개)
CREATE TABLE visit_questions (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
   content VARCHAR(500) NOT NULL,
   source VARCHAR(10) NOT NULL CHECK (source IN ('USER', 'CHATBOT')),  -- 직접 적음 / 챗봇에서 가져옴 (서버가 정함)
   sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),          -- 작을수록 위, 같으면 먼저 만든 순
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_visit_questions_visit ON visit_questions (visit_id) WHERE deleted_at IS NULL;

-- 진료 후 메모 (병원에서 들은 내용 원문 그대로, 받아쓰기 결과도 글자로)
-- ai_extract는 AI 정리 결과 자리. 메모 저장만으로는 AI를 돌리지 않아서 처음엔 항상 null
CREATE TABLE visit_notes (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   visit_id BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
   content VARCHAR(5000) NOT NULL,
   ai_extract JSONB,
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_visit_notes_visit ON visit_notes (visit_id) WHERE deleted_at IS NULL;
