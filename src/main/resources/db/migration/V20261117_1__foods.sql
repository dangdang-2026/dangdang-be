-- 음식 (식약처 「전국통합식품영양성분정보(음식) 표준데이터」, 공공데이터포털 15100070)
-- 영양값은 원본 그대로 기준량(100g 또는 100ml)당 값임. 1인분 값은 응답 때 serving_amount로 계산함
-- 모르는 영양값은 null로 두고 0으로 바꾸지 않음 (0g과 "모름"을 구분)
CREATE TABLE foods (
   food_code VARCHAR(30) PRIMARY KEY,              -- 식품코드 (예: D102-053000000-0001)
   name VARCHAR(100) NOT NULL,                     -- 식품명 (예: 가래떡_흑미)
   rep_name VARCHAR(100),                          -- 대표식품명 (예: 가래떡). 검색·AI 후보 이름 맞출 때 씀
   category VARCHAR(30),                           -- 식품대분류명 (예: 빵 및 과자류)
   origin SMALLINT NOT NULL,                       -- 식품기원코드 1 가정식 / 2 프랜차이즈 / 3 외식(분석) / 4 외식(산출) / 5 초등급식 / 6 중고등급식 / 7 산업체급식
   maker VARCHAR(100),                             -- 업체명 (프랜차이즈만, 나머지 null)
   base_unit VARCHAR(2) NOT NULL,                  -- g / ml (영양값 기준이 100g인지 100ml인지)
   serving_amount NUMERIC(7,1),                    -- 1인분 양 (식품중량, base_unit 단위). 모르면 null
   kcal NUMERIC(7,2),                              -- 아래 다섯 값은 기준량당. 모르면 null
   carb_g NUMERIC(7,2),
   sugar_g NUMERIC(7,2),
   protein_g NUMERIC(7,2),
   fat_g NUMERIC(7,2),
   source_version DATE NOT NULL                    -- 데이터기준일 (원천 버전)
);
