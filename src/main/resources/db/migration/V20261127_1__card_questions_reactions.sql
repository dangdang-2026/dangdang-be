-- 카드뉴스 요청 반영 (11/20 언니 요청)
-- 1) U-28 「물어볼 것에 적기」 질문 목록 (카드마다 운영자가 적음, 없으면 빈 목록)
ALTER TABLE content_cards ADD COLUMN questions JSONB NOT NULL DEFAULT '[]'::jsonb;
-- 2) 정정 요약 한 줄 (자세한 내용은 기존 correction_note)
ALTER TABLE content_cards ADD COLUMN correction_summary VARCHAR(60);

-- 3) 카드 공감 — 누른 사람만 알면 되고 숫자는 안 보여 준다 (COM-12 「공감 수 숨김」)
CREATE TABLE card_reactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id BIGINT NOT NULL REFERENCES content_cards(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_card_reactions UNIQUE (user_id, card_id)
);
