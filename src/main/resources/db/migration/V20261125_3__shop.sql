-- 당당 상점 1단계 (P-01 메인 · P-02 상세 · P-06 판매처로 이동 · M-21 찜한 상품 · M-22 판매처로 간 기록 · 최근 본 상품)
-- 결제·배송은 앱에서 안 한다. 판매처 링크로만 보내고(제휴), 우리는 「누가 어떤 상품을 보러 갔는지」만 남긴다
-- 찜·간 기록·최근 본 상품은 users ON DELETE CASCADE → 탈퇴하면 같이 지워진다

-- 상품. 운영자가 SQL(나중엔 운영자 화면)로 넣는다. 내릴 땐 지우지 않고 active = false (찜·이동 기록이 가리키고 있어서)
CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category VARCHAR(20) NOT NULL CHECK (category IN ('SNACK', 'LUNCHBOX', 'DRINK', 'MEAL_SWAP')),
    brand VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    size_label VARCHAR(50),                              -- 「100g × 4」 같은 용량 글자
    price INTEGER NOT NULL CHECK (price >= 0),           -- 판매처 가격(원). 판매처에서 바뀔 수 있음
    content_amount INTEGER CHECK (content_amount > 0),   -- 총 용량 숫자 (100g × 4 → 400). 「100g당 가격」 계산용. 모르면 null
    content_unit VARCHAR(2) CHECK (content_unit IN ('g', 'ml')),
    shipping_note VARCHAR(30),                           -- 「무료배송」 같은 판매처 배송 글자. 모르면 null
    serving_label VARCHAR(20),                           -- 영양 기준 「1개」 (P-02 「영양 (1개)」)
    kcal NUMERIC(7,2) CHECK (kcal >= 0),                 -- 아래 네 값은 serving_label 하나 기준. 모르면 null (foods 와 같은 칸)
    carb_g NUMERIC(7,2) CHECK (carb_g >= 0),
    sugar_g NUMERIC(7,2) CHECK (sugar_g >= 0),
    protein_g NUMERIC(7,2) CHECK (protein_g >= 0),
    -- 첫 번째가 대표 사진. 글자만 든 배열만 받는다 (운영자가 SQL로 [1]·{...}·null 을 넣으면 목록·상세가 500 나지 않게 DB가 막음)
    image_urls JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(image_urls) = 'array' AND NOT jsonb_path_exists(image_urls, '$[*] ? (@.type() != "string")')),
    seller_name VARCHAR(50) NOT NULL,                    -- 「○○몰」
    -- 판매처 주소는 https 와 호스트 이름이 있어야 한다. 운영자가 실수로 javascript: · http: · https:///… 를 넣어도 DB가 막는다
    seller_url VARCHAR(500) NOT NULL CHECK (seller_url ~ '^https://[A-Za-z0-9.-]+(:[0-9]+)?([/?#]|$)'),
    affiliate BOOLEAN NOT NULL DEFAULT TRUE,             -- 제휴 링크인지 (화면에 「제휴」 표시)
    pick BOOLEAN NOT NULL DEFAULT FALSE,                 -- 이번 주 산모 PICK 배너
    sort_order INTEGER NOT NULL DEFAULT 0,               -- 작을수록 앞
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- created_at · updated_at 의 DB 기본값은 운영자 SQL 전용 (앱은 상품을 넣거나 고치지 않는다)
    -- 운영자가 SQL로 UPDATE 할 때는 updated_at = now() 도 같이 바꾸기
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_products_content CHECK ((content_amount IS NULL) = (content_unit IS NULL))   -- 숫자와 단위는 같이
);
CREATE INDEX ix_products_list ON products (sort_order, id) WHERE active;

-- 찜 (M-21). 한 사람이 한 상품에 한 번
CREATE TABLE product_likes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_product_likes UNIQUE (user_id, product_id)
);
CREATE INDEX ix_product_likes_latest ON product_likes (user_id, created_at DESC, id DESC);

-- 판매처로 간 기록 (M-22). 「구매」가 아니라 「보러 감」 — 실제로 샀는지는 모른다
-- 가격·판매처 이름은 그때 값을 복사 (상품 값이 나중에 바뀌어도 기록은 그대로)
CREATE TABLE product_visits (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    price INTEGER NOT NULL CHECK (price >= 0),
    seller_name VARCHAR(50) NOT NULL,
    visited_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_product_visits_latest ON product_visits (user_id, visited_at DESC, id DESC);

-- 최근 본 상품 (M-01 v2 「최근 7일 · 3개」). 상품 상세(P-02)를 열면 남는다
-- 한 사람·한 상품에 한 줄, 다시 보면 시각만 새로. 화면엔 최근 7일 것만
CREATE TABLE product_views (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_product_views UNIQUE (user_id, product_id)
);
CREATE INDEX ix_product_views_latest ON product_views (user_id, viewed_at DESC, id DESC);

-- ===== 예시 상품 (seed) =====
-- 피그마 예시 상품, 데모용. 제휴 링크가 아니라 affiliate = FALSE. 판매처 주소는 임시로 각 쇼핑몰 메인 (실제 제휴 상품이 정해지면 active = false 로 내리고 새로 넣기)
-- 사진은 S3 버킷 dangdang-2026-assets 의 shop/ 폴더 (서울 리전, shop/ 만 공개 읽기). 상품마다 3장: -1 대표 · -2 확대 · -3 다른 부분
-- AI로 만든 임시 사진이라 실제 제품이 아님. -2 · -3 은 그 AI 사진에서 확대하거나 다른 부분을 자른 것 (실제 제휴 상품이 정해지면 판매처가 준 사진으로)
-- 가격·용량·영양·배송은 피그마 값 그대로. 피그마에 없는 칸은 NULL (용량이 없으면 100g당 가격도 안 나옴)
-- 이름·용량에 효능 표현을 넣지 않는다. 혈당 후기 숫자(「목표 안 n/m명」)는 2단계라 넣지 않음
INSERT INTO products (category, brand, name, size_label, price, content_amount, content_unit, shipping_note,
                      serving_label, kcal, carb_g, sugar_g, protein_g, image_urls, seller_name, seller_url,
                      affiliate, pick, sort_order) VALUES
('SNACK',     '브랜드 B', '무가당 그릭요거트, 100g, 4개입', '100g × 4', 9800,  400,  'g',  '무료배송',
 '1개', 90, 5, 4, 10, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/greek-yogurt-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/greek-yogurt-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/greek-yogurt-3.jpg"]', '컬리', 'https://www.kurly.com', FALSE, TRUE, 1),
('SNACK',     '브랜드 A', '저당 그래놀라 300g',            '300g',     12900, 300,  'g',  NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/low-sugar-granola-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/low-sugar-granola-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/low-sugar-granola-3.jpg"]', '널담', 'https://www.nuldam.com', FALSE, FALSE, 2),
('SNACK',     '브랜드 A', '아몬드 크래커 10봉',            '10봉',     11500, NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/almond-cracker-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/almond-cracker-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/almond-cracker-3.jpg"]', '널담', 'https://www.nuldam.com', FALSE, FALSE, 3),
('MEAL_SWAP', '브랜드 C', '두부면 비빔면 2인분',           '2인분',    6900,  NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/tofu-bibim-noodle-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/tofu-bibim-noodle-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/tofu-bibim-noodle-3.jpg"]', '그리팅', 'https://www.greating.co.kr', FALSE, FALSE, 4),
('LUNCHBOX',  '브랜드 D', '현미 곤약 주먹밥 5개',          '5개',      11500, NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/brown-rice-konjac-riceball-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/brown-rice-konjac-riceball-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/brown-rice-konjac-riceball-3.jpg"]', '그리팅', 'https://www.greating.co.kr', FALSE, FALSE, 5),
-- 아래 두 개는 가격이 피그마에 없어 임의 (비슷한 상품 수준)
('SNACK',     '브랜드 D', '프로틴 그래놀라 바',            NULL,       2500,  NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/protein-granola-bar-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/protein-granola-bar-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/protein-granola-bar-3.jpg"]', '널담', 'https://www.nuldam.com', FALSE, FALSE, 6),
('SNACK',     '브랜드 B', '그래놀라 요거트 컵',            NULL,       3500,  NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, '["https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/granola-yogurt-cup-1.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/granola-yogurt-cup-2.jpg", "https://dangdang-2026-assets.s3.ap-northeast-2.amazonaws.com/shop/granola-yogurt-cup-3.jpg"]', '컬리', 'https://www.kurly.com', FALSE, FALSE, 7);
