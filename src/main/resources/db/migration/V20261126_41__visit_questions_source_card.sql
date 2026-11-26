-- 카드뉴스 「물어볼 것에 적기」(U-28)로 담은 질문에 출처 카드를 남긴다. 카드가 내려가면 「출처 카드가 내려갔어요」(U-26c)
-- 카드는 지우지 않고 WITHDRAWN으로 내리지만, 혹시 줄이 지워져도 질문은 남게 SET NULL
ALTER TABLE visit_questions ADD COLUMN source_card_id BIGINT REFERENCES content_cards(id) ON DELETE SET NULL;

-- source CHECK는 V20261121_3에서 이름 없이 붙여서 PostgreSQL이 지은 이름(visit_questions_source_check)이다
ALTER TABLE visit_questions DROP CONSTRAINT visit_questions_source_check;
ALTER TABLE visit_questions ADD CONSTRAINT visit_questions_source_check
    CHECK (source IN ('USER', 'CHATBOT', 'CARD'));

-- 카드 번호는 CARD 질문에만 (카드 줄이 지워져 null이 되는 건 괜찮음)
ALTER TABLE visit_questions ADD CONSTRAINT visit_questions_source_card_check
    CHECK (source_card_id IS NULL OR source = 'CARD');
