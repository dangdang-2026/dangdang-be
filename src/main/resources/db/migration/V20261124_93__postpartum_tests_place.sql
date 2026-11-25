-- 산후 검사 장소 (E-08 「어디서 하나요?」). 등록한 병원 이름을 고르거나 직접 적은 글자 그대로 (병원 표를 가리키지 않음)
ALTER TABLE postpartum_tests ADD COLUMN place_name VARCHAR(50);
