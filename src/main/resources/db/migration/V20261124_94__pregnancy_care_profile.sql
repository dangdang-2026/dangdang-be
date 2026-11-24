-- 임신 정보에 관리 모드 값 검사 · 연락 기준 메모 · 메모 적은 시각 추가 (M-02 · M-03 · M-01 · G-05)
-- diagnosis_date(진단일), care_mode(관리 모드) 칸은 V20261110_2에서 이미 만들어 둠 → 엔티티에만 안 붙어 있었다
-- 건강정보 동의를 철회하면 pregnancies 줄이 통째로 지워지므로 새 칸도 같이 지워진다 (SensitiveConsentWithdrawalService)
ALTER TABLE pregnancies
    ADD CONSTRAINT ck_pregnancies_care_mode CHECK (care_mode IN ('DIET', 'INSULIN', 'ORAL')),
    ADD COLUMN contact_memo VARCHAR(100),             -- 연락 기준 메모 (사용자가 병원에서 들은 그대로, 없으면 NULL)
    ADD COLUMN contact_memo_updated_at TIMESTAMPTZ;   -- 연락 기준 메모를 마지막으로 적은 시각 (G-05 「10월 1일에 적은 메모」). 메모가 없으면 NULL
