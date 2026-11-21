-- 인슐린 투여 기록 (E-02 인슐린 기록, D-10 인슐린 기록 고치기)
-- 맞은 양을 사용자가 적은 그대로 저장만 함. 서버는 권장량을 계산하지 않고 투여 알림도 만들지 않음
CREATE TABLE insulin_logs (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   insulin_type VARCHAR(10) NOT NULL CHECK (insulin_type IN ('MEAL', 'BEDTIME', 'CUSTOM')),  -- 식사 때(속효성) / 자기 전(지속형) / 직접 입력
   units NUMERIC(4,1) NOT NULL CHECK (units > 0),   -- 맞은 단위 (소수 한 자리, 999.9까지). 저장 형식 제약이지 안전 용량 기준이 아님
   taken_at TIMESTAMPTZ NOT NULL,                   -- 맞은 시각
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ                           -- null = 살아 있음
);

-- 내 기록 기간 조회 (목록·오늘 화면·기록 탭·진료 리포트), 맞은 시각 순
CREATE INDEX idx_insulin_logs_user_taken_at ON insulin_logs (user_id, taken_at) WHERE deleted_at IS NULL;
