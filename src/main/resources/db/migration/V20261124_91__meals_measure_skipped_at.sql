-- H-05 「이번 끼니는 건너뛸게요」 — 이 식사의 식후 측정을 건너뛴 시각 (null = 안 건너뜀)
ALTER TABLE meals ADD COLUMN measure_skipped_at TIMESTAMPTZ;
