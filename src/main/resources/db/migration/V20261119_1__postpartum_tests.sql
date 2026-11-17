-- 산후 검사 일정 (PP-04 · E-08·E-10·E-11). 병원에서 들은 이름·날짜를 사용자가 적는다 — 앱이 시기를 정하지 않음
CREATE TABLE postpartum_tests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(40) NOT NULL,                       -- 예: 75g 경구 당부하 검사
    test_date DATE NOT NULL,
    remind_day_before BOOLEAN NOT NULL DEFAULT TRUE, -- 전날 알림
    remind_same_day BOOLEAN NOT NULL DEFAULT TRUE,   -- 당일 알림
    result_note VARCHAR(500),                        -- 들은 그대로 (PP-05, 해석 없음)
    day_before_sent_at TIMESTAMPTZ,                  -- 전날 알림 처리한 시각 (두 번 안 보내게)
    same_day_sent_at TIMESTAMPTZ,                    -- 당일 알림 처리한 시각
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_postpartum_tests_user ON postpartum_tests (user_id, test_date);
CREATE INDEX ix_postpartum_tests_date ON postpartum_tests (test_date);
