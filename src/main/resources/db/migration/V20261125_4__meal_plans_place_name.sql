-- 식단 받은 곳을 글로 적기 (병원 목록에 없는 곳, 예: 보건소 영양 교실). 병원 번호(care_site_id)와 둘 다 적을 수 있음
ALTER TABLE meal_plans ADD COLUMN place_name VARCHAR(50);   -- 앞뒤 공백 지운 글, 빈 글이면 null
