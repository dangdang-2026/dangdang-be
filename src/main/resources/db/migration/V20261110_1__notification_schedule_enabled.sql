-- 진료·검사 알림 사용 여부 (scheduleEnabled, 기본값 true)
ALTER TABLE notification_preferences
    ADD COLUMN schedule_enabled BOOLEAN NOT NULL DEFAULT true;
