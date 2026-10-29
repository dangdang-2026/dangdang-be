-- 다니는 병원
CREATE TABLE care_sites (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    department VARCHAR(15) NOT NULL,                -- OBGYN / ENDO / NUTRITION / ETC
    hira_code VARCHAR(100),                         -- 심평원 암호화 요양기호 (검색해서 고른 경우만)
    address VARCHAR(200),
    phone VARCHAR(20),
    role_note VARCHAR(30),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- 병원 기준 버전 (덮어쓰지 않고 쌓는다)
CREATE TABLE criteria_versions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    care_site_id BIGINT REFERENCES care_sites(id) ON DELETE SET NULL,
    post_meal_minutes SMALLINT CHECK (post_meal_minutes BETWEEN 30 AND 180),  -- NULL = 아직 몰라요
    measure_slots JSONB,                            -- ["FASTING","LUNCH_AFTER", ...]
    fasting_target SMALLINT,                        -- 공복 목표(미만), NULL이면 비교 안 함
    post_meal_target SMALLINT,                      -- 식후 목표(미만)
    source_note VARCHAR(255),
    effective_from DATE NOT NULL,                   -- 적용 시작일 (한국 날짜)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 식사
CREATE TABLE meals (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   meal_slot VARCHAR(10) NOT NULL,                 -- BREAKFAST / LUNCH / DINNER / SNACK
   started_at TIMESTAMPTZ NOT NULL,                -- 식사 시작 누른 시각
   started_offline BOOLEAN NOT NULL DEFAULT false,
   note VARCHAR(255),
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ
);

-- 혈당
CREATE TABLE readings (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  meal_id BIGINT REFERENCES meals(id) ON DELETE SET NULL,
  criteria_version_id BIGINT NOT NULL REFERENCES criteria_versions(id),  -- 잴 때의 기준
  reading_type VARCHAR(10) NOT NULL,              -- FASTING / POST_MEAL / OTHER
  slot VARCHAR(20),                               -- FASTING / BREAKFAST_AFTER / ... / BEDTIME
  scheduled_at TIMESTAMPTZ,                       -- 알림 예정 시각 (늦게 잼 계산)
  measured_at TIMESTAMPTZ NOT NULL,
  value SMALLINT NOT NULL,                        -- mg/dL
  entered_late BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ,
  CHECK (value BETWEEN 20 AND 600)
);

-- 측정 알림
CREATE TABLE reminders (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   meal_id BIGINT REFERENCES meals(id),
   reading_id BIGINT REFERENCES readings(id) ON DELETE SET NULL,  -- 저장되면 연결
   slot VARCHAR(20) NOT NULL,
   scheduled_at TIMESTAMPTZ NOT NULL,              -- 보낼 시각
   status VARCHAR(10) NOT NULL,                    -- SCHEDULED / SENT / OPENED / DONE / SKIPPED / PAUSED / CANCELED
   sent_at TIMESTAMPTZ,
   opened_at TIMESTAMPTZ,
   renotified BOOLEAN NOT NULL DEFAULT false,
   snoozed_until TIMESTAMPTZ,
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 웹 푸시 구독
CREATE TABLE push_subscriptions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL,
    p256dh VARCHAR(255) NOT NULL,
    auth_secret VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint)
);

-- 알림함 (H-15) — 알림함 DDL(MySQL식)을 PostgreSQL로
CREATE TABLE notifications (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   reminder_id BIGINT REFERENCES reminders(id) ON DELETE SET NULL,  -- 측정 알림일 때만
   type VARCHAR(20) NOT NULL,                      -- MEASURE / VISIT_BEFORE / VISIT_TODAY / VISIT_NOTE / POSTPARTUM
   title VARCHAR(100) NOT NULL,
   body VARCHAR(200),
   deep_link VARCHAR(200) NOT NULL,                -- 누르면 갈 화면
   sent_at TIMESTAMPTZ NOT NULL,
   read_at TIMESTAMPTZ
);

-- 자주 찾는 조건 (ERD 끝의 인덱스 + 지도에서 추가한 것)
CREATE UNIQUE INDEX ux_reminders_reading ON reminders (reading_id) WHERE reading_id IS NOT NULL;
CREATE INDEX ix_reminders_due ON reminders (status, scheduled_at);          -- 스케줄러가 30초마다
CREATE INDEX ix_readings_user_time ON readings (user_id, measured_at DESC); -- 오늘·기록 화면
CREATE INDEX ix_meals_user_time ON meals (user_id, started_at DESC);        -- 오늘·기록 화면
CREATE INDEX ix_criteria_current ON criteria_versions (user_id, effective_from DESC, id DESC); -- 지금 기준 찾기
CREATE INDEX ix_notifications_user_time ON notifications (user_id, sent_at DESC); -- 알림함