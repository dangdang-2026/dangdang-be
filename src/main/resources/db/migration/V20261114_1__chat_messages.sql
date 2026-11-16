-- 당당이에게 물어보기 (챗봇 Q-01~Q-09) 대화. 보관: 탈퇴할 때까지, 사용자가 지우면 바로 삭제
CREATE TABLE chat_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL,                      -- USER(내가 쓴 말) / ASSISTANT(당당이 답)
    kind VARCHAR(20) NOT NULL,                      -- QUESTION / RECORDS / QUESTION_DRAFT / INFO / UNKNOWN / RISK / DOSE / CONSENT_NEEDED / FAILED
    content TEXT NOT NULL,                          -- 보여 줄 글
    payload JSONB,                                  -- 기록 카드 · 물어볼 말 초안 · 출처 · 전화 버튼 (없으면 NULL)
    helpful BOOLEAN,                                -- 도움이 됐나요 (ASK-08) — 아직 안 누름 NULL
    request_id VARCHAR(40),                         -- AI 호출 번호 (로그와 맞춰 보기용, 내용 없음)
    prompt_version VARCHAR(30),                     -- AI 서버가 쓴 프롬프트 판
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_chat_messages_user ON chat_messages (user_id, id DESC);
