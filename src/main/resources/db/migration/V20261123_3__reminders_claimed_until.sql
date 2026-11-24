-- 「보내는 중」 표시. 스케줄러가 알림 한 건을 보내기 직전에 이 시각(지금 + 2분)까지 잡아 둔다
-- 다른 서버는 잡혀 있는 알림을 건너뛴다 → 한 바퀴가 ShedLock 최대 5분을 넘겨도 같은 알림이 두 번 가지 않는다
-- 잡은 서버가 보내다가 죽으면 2분 뒤 풀려서 다른 서버가 다시 보낸다
ALTER TABLE reminders ADD COLUMN claimed_until TIMESTAMPTZ;
