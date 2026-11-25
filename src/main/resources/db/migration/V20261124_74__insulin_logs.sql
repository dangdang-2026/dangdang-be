-- 인슐린 맞은 기록 (INS-01 · E-02 · D-10). 서버는 맞은 양을 그대로 저장만 함 (권장량 계산·투여 알림 없음, R4)
CREATE TABLE insulin_logs (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   insulin_type VARCHAR(10) NOT NULL CHECK (insulin_type IN ('MEAL', 'BEDTIME')),   -- 식사 때(속효성) / 자기 전(지속형)
   meal_slot VARCHAR(10) CHECK (meal_slot IN ('BREAKFAST', 'LUNCH', 'DINNER')),     -- 식사 때일 때만: 아침·점심·저녁 식사
   units NUMERIC(4,1) NOT NULL CHECK (units > 0),   -- 맞은 단위 (0 초과 999.9 이하, 소수 한 자리)
   taken_at TIMESTAMPTZ NOT NULL,                   -- 맞은 시각
   edited_at TIMESTAMPTZ,                           -- 마지막으로 고친 시각 (한 번도 안 고쳤으면 null → 「수정됨」 표시)
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ,                          -- null = 살아 있음
   -- 식사 때면 끼니가 꼭 있고, 자기 전이면 끼니가 없음
   CONSTRAINT ck_insulin_logs_meal_slot CHECK ((insulin_type = 'MEAL') = (meal_slot IS NOT NULL))
);

-- 내 기록 기간 조회 (목록 · 오늘 화면 · 기록 탭 · 달력 · 진료 리포트) + 지난 기록 불러오기(latest)도 이 인덱스로
CREATE INDEX idx_insulin_logs_user_taken_at ON insulin_logs (user_id, taken_at) WHERE deleted_at IS NULL;
