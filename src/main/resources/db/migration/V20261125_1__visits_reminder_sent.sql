-- 진료 전날·당일 알림을 보낸 시각 (같은 일정에 두 번 보내지 않게). 진료 날짜를 바꾸면 둘 다 비워서 새 날짜로 다시 보냄
ALTER TABLE visits ADD COLUMN day_before_sent_at TIMESTAMPTZ;   -- 전날 알림 처리한 시각 (null = 아직)
ALTER TABLE visits ADD COLUMN same_day_sent_at TIMESTAMPTZ;     -- 당일 알림 처리한 시각 (null = 아직)

-- 알림 차례 찾기용: 살아 있고 날짜가 정해진 일정만, 진료 시각으로
CREATE INDEX idx_visits_visit_at_alive ON visits (visit_at) WHERE deleted_at IS NULL AND visit_at IS NOT NULL;
