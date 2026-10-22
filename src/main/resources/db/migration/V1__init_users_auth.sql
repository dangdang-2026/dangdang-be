CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 사용자
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(254),                             -- 카카오는 없을 수 있음
    display_name VARCHAR(10) NOT NULL,              -- 부를 이름
    auth_provider VARCHAR(10) NOT NULL,             -- EMAIL / KAKAO
    provider_subject VARCHAR(255),                  -- 카카오 회원번호
    password_hash VARCHAR(255),                     -- 이메일 가입만
    role VARCHAR(10) NOT NULL DEFAULT 'USER',       -- USER / ADMIN
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_provider_subject UNIQUE (auth_provider, provider_subject)
);
-- 이메일은 대소문자 무시하고 하나만 (비어 있는 건 검사 안 함)
CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE email IS NOT NULL;
-- 동의 이력 (동의·철회할 때마다 한 줄씩 쌓음)
CREATE TABLE consent_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consent_type VARCHAR(30) NOT NULL,
    document_version VARCHAR(40) NOT NULL,
    granted BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_consent_latest ON consent_events (user_id, consent_type, occurred_at DESC);
-- 알림 설정 (1인 1줄)
CREATE TABLE notification_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    measurement_enabled BOOLEAN NOT NULL DEFAULT true,
    renotify_enabled BOOLEAN NOT NULL DEFAULT true,
    hide_preview BOOLEAN NOT NULL DEFAULT true,
    fasting_alarm_time TIME NOT NULL DEFAULT '07:00',
    bedtime_alarm_time TIME NOT NULL DEFAULT '22:00',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id)
);