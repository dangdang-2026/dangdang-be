-- 내 음식 (MY-04b,M-13, M-17, M-18). 나만 보임. 지워도 지난 끼니 기록은 그대로 (끼니는 이름·값을 따로 저장)
CREATE TABLE my_foods (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    similar_food_code VARCHAR(30) REFERENCES foods(food_code) ON DELETE SET NULL,  -- 비슷한 공공 음식 (언니 foods 표, 없을 수 있음)
    usual_quantity VARCHAR(10),                     -- HALF / ONE / ONE_HALF
    usual_grams SMALLINT CHECK (usual_grams BETWEEN 1 AND 2000),  -- 보통 먹는 양 g (저울 쓰는 사람, 선택)
    nutrition_override JSONB,                       -- 내가 입력한 1인분 값 {carbG, proteinG, fatG, sugarG, kcal}
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_my_foods_user ON my_foods (user_id, updated_at DESC);

-- 내 식사 안내 (MEAL-09, M-15). 받은 표현 그대로, 한 사람 한 장. 식단표 사진은 서버에 안 올림
CREATE TABLE meal_plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    care_site_id BIGINT REFERENCES care_sites(id) ON DELETE SET NULL,  -- 받은 곳 (없을 수 있음)
    received_on DATE,                               -- 받은 날
    meal_guide VARCHAR(2000),                       -- 끼니 안내 원문
    snack_guide VARCHAR(1000),                      -- 간식 안내 원문
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 식사 기록 설정 (M-20). 양을 인분으로 적을지 그램으로 적을지 — 줄이 없으면 인분(SERVING)
CREATE TABLE meal_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    portion_unit VARCHAR(10) NOT NULL DEFAULT 'SERVING' CHECK (portion_unit IN ('SERVING', 'GRAM')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
