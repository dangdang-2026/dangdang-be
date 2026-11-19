-- 저장한 카드 (COM-12 · U-27)
CREATE TABLE card_saves (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id BIGINT NOT NULL REFERENCES content_cards(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_card_saves UNIQUE (user_id, card_id)
);

-- 스플래시·커뮤니티 카드 (AD-01·AD-02 · S-01b·S-01c). 광고도 이 표에 둔다 (ad_slots 표 없음)
CREATE TABLE announcements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slot VARCHAR(10) NOT NULL,                      -- SPLASH(재실행 스플래시) / COMMUNITY(커뮤니티 메인 1칸)
    kind VARCHAR(10) NOT NULL,                      -- NEWS(당당 소식) / AD(제휴 — 「AD」 표시)
    title VARCHAR(60) NOT NULL,
    image_url VARCHAR(500),
    link_url VARCHAR(500),                          -- 누르면 갈 곳 (카드뉴스면 /cards/{id})
    content_card_id BIGINT REFERENCES content_cards(id) ON DELETE SET NULL,
    active_from TIMESTAMPTZ NOT NULL,
    active_to TIMESTAMPTZ,                          -- NULL = 내릴 때까지
    reviewed_by VARCHAR(50),                        -- 광고 심사한 사람 (AD-03)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_announcements_slot ON announcements (slot, active_from);
