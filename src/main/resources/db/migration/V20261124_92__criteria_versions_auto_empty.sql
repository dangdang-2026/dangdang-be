-- 기준 없는 날짜에 혈당을 저장해서 서버가 만든 빈 기준 표시 (사용자가 칸을 다 비워 등록한 「아직 몰라요」 기준과 구분)
-- 지난 날짜 기준을 적을 때 이것만 「다음 기준」으로 안 치고 지우며, 날짜로 기준을 찾을 때 진짜 기준보다 뒤로 미룸
ALTER TABLE criteria_versions ADD COLUMN auto_empty BOOLEAN NOT NULL DEFAULT false;

-- 이미 있는 줄: 서버가 만든 빈 기준과 같은 모양(모든 칸 NULL)이면 표시. 잴 때를 빈 배열([])로 보낸 등록은 사용자가 적은 것으로 둠
UPDATE criteria_versions
SET auto_empty = true
WHERE care_site_id IS NULL
  AND post_meal_minutes IS NULL
  AND measure_slots IS NULL
  AND fasting_target IS NULL
  AND post_meal_target IS NULL
  AND source_note IS NULL;
