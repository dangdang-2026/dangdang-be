-- 식사 자체(끼니·시작 시각·메모)를 마지막으로 고친 시각
-- updated_at은 음식 항목이 바뀔 때도 올라가서(충돌 확인용), 기록 화면의 "수정됨" 표시는 이 칸으로 판단함
-- 한 번도 안 고쳤으면 null
ALTER TABLE meals ADD COLUMN edited_at TIMESTAMPTZ;

-- 이미 고친 적이 있는 식사(수정 시각이 만든 시각보다 늦음)는 그 시각을 고친 시각으로 채움
-- 이 시점까지는 음식 항목 기능이 없어서 updated_at이 늦어진 건 식사를 고친 것뿐임
UPDATE meals SET edited_at = updated_at WHERE updated_at > created_at;
