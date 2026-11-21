-- 스케줄러 잠금 (ShedLock). 서버가 여러 대여도 30초마다 도는 알림 한 바퀴를 한 대만 돌린다
-- 일 하나(name)당 한 줄. 잡은 서버가 lock_until까지 갖고 있고, 그동안 다른 서버는 그 바퀴를 건너뛴다
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,                   -- 잠그는 일 이름 (reminderScheduler)
    lock_until TIMESTAMP NOT NULL,                  -- 이 시각까지 잠김 (UTC, 시간대 없음 — ShedLock이 DB 시계로 넣는다)
    locked_at TIMESTAMP NOT NULL,                   -- 잡은 시각 (UTC)
    locked_by VARCHAR(255) NOT NULL                 -- 잡은 서버 (컨테이너 이름)
);
