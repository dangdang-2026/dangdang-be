-- 이메일 계정에 카카오 연결 (L-10 「카카오도 연결할까요?」 · M-02 「로그인 방식」)
-- provider_subject(카카오 회원번호)를 가입 방식과 상관없이 쓴다: 카카오로 가입한 계정 + 카카오를 연결한 이메일 계정
ALTER TABLE users ADD COLUMN kakao_linked_at TIMESTAMPTZ;   -- 이메일 계정이 카카오를 연결한 때 (카카오 가입 계정은 NULL)

-- 카카오 회원번호는 어느 계정이든 하나만. 지금은 카카오 가입 계정만 값이 있어서 겹치는 줄이 없다
-- (기존 uk_users_provider_subject는 (가입 방식, 번호) 쌍이라 「이메일 계정 + 같은 카카오」를 못 막는다)
CREATE UNIQUE INDEX ux_users_kakao_subject ON users (provider_subject) WHERE provider_subject IS NOT NULL;
