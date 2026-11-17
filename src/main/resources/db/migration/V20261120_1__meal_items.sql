-- 식사에 붙은 음식 항목 (식사 하나에 여러 개)
-- 식약처 음식(foods)에서 고르면 food_code를 두고, 그때의 영양값을 아래 칸에 복사해 둠 (스냅샷)
--   나중에 foods 데이터를 새 버전으로 바꿔도 지난 식사 기록의 영양값이 저절로 바뀌지 않게
-- 직접 적은 음식이면 food_code와 복사한 영양값은 null, 사용자가 적은 값은 nutrition_override에 둠
-- 먹은 양: weight가 있으면 그 무게, 없으면 1인분 양(serving_amount, 모르면 100) × servings
--   먹은 영양값 = 100당 값 × 먹은 양 ÷ 100
CREATE TABLE meal_items (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   meal_id BIGINT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
   food_code VARCHAR(30) REFERENCES foods(food_code), -- 식약처 음식이면 코드, 직접 적은 음식이면 null
   name VARCHAR(100) NOT NULL,                      -- 보이는 이름 (고른 그때의 displayName 또는 직접 적은 이름)
   servings NUMERIC(4,2) NOT NULL DEFAULT 1 CHECK (servings > 0), -- 먹은 양 (인분)
   weight NUMERIC(6,1) CHECK (weight > 0),          -- 직접 잰 먹은 양 (unit 단위). 있으면 servings 대신 씀
   unit VARCHAR(2) NOT NULL DEFAULT 'g' CHECK (unit IN ('g', 'ml')),
   -- 아래는 고른 그때의 foods 값 복사 (100g·100ml당, 모르면 null)
   serving_amount NUMERIC(7,1),                     -- 1인분 양 (unit 단위)
   kcal NUMERIC(7,2),
   carb_g NUMERIC(7,2),
   sugar_g NUMERIC(7,2),
   protein_g NUMERIC(7,2),
   fat_g NUMERIC(7,2),
   nutrition_source VARCHAR(4) NOT NULL CHECK (nutrition_source IN ('DB', 'USER')), -- DB 공공 값 / USER 직접 고친 값
   nutrition_override JSONB,                        -- 직접 적은 영양값 {kcal, carbG, sugarG, proteinG, fatG}. 먹은 양 전체 기준
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   deleted_at TIMESTAMPTZ                           -- null = 살아 있음 (식사처럼 지운 시각만 적음)
);

CREATE INDEX idx_meal_items_meal ON meal_items (meal_id) WHERE deleted_at IS NULL;
