-- 신고가 사람에게서 왔는지(USER), 서버가 자동으로 넣었는지(SYSTEM) 나눈다 (U-15b · U-30)
-- reporter_id NULL만으로는 「탈퇴해서 신고자가 지워진 사람 신고」와 자동 신고를 구분할 수 없다
-- 처리함의 신고 수(reportCount)는 사람 신고만 센다
-- reason: MEDICAL_ADVICE / NUMBER_COMPARE / AD_SALES / PRIVACY / ABUSE / CRISIS / MISINFO / ETC (CHECK 없음, ReportReason)
ALTER TABLE reports ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE reports ADD CONSTRAINT ck_reports_source CHECK (source IN ('USER', 'SYSTEM'));
