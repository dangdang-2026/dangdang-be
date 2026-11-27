-- 식사 항목에 상점 상품을 고를 수 있게 (be #192 상점 혈당 후기)
-- 상품을 고른 식사에 붙은 식후 혈당이 그 상품의 후기로 모인다 (PRODUCT_REVIEW 동의한 사람만)
-- 상품을 지워도 기록은 남고 상품 연결만 끊는다
ALTER TABLE meal_items ADD COLUMN product_id BIGINT REFERENCES products(id) ON DELETE SET NULL;

-- 상품별 후기를 셀 때 쓴다 (상품을 고른 살아 있는 항목만)
CREATE INDEX idx_meal_items_product ON meal_items (product_id) WHERE product_id IS NOT NULL AND deleted_at IS NULL;
