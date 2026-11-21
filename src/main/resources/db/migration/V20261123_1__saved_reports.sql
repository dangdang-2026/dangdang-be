-- 저장한 진료 리포트 (C-07 저장·공유, C-25 기간 골라 만들기 → C-24 지난 리포트 목록)
-- 리포트 내용(숫자·표)은 저장하지 않고 볼 때마다 다시 계산함
-- (reports는 커뮤니티 신고 테이블이라 이름을 나눔)
-- 진료용(VISIT): 진료 일정만 가리킴. 기간(앞 진료일 ~ 진료 전날)과 병원은 볼 때 진료 일정에서 계산
--               (일정 날짜·병원을 나중에 고쳐도 진료 리포트 조회와 항상 같게)
-- 기간 직접(CUSTOM): 사용자가 고른 기간·병원을 그대로 저장
CREATE TABLE saved_reports (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   kind VARCHAR(10) NOT NULL CHECK (kind IN ('VISIT', 'CUSTOM')),     -- 진료용 / 기간 직접
   visit_id BIGINT REFERENCES visits(id) ON DELETE CASCADE,            -- 진료용만
   from_date DATE,                                                     -- 기간 직접만, 기간 시작 (포함)
   to_date DATE,                                                       -- 기간 직접만, 기간 끝 (포함)
   care_site_id BIGINT REFERENCES care_sites(id) ON DELETE SET NULL,   -- 기간 직접만, 보여 줄 병원 (없어도 됨)
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   CHECK (
       (kind = 'VISIT' AND visit_id IS NOT NULL AND from_date IS NULL AND to_date IS NULL AND care_site_id IS NULL)
    OR (kind = 'CUSTOM' AND visit_id IS NULL AND from_date IS NOT NULL AND to_date IS NOT NULL AND from_date <= to_date)
   )
);

-- 진료용은 진료 일정 하나에 한 번만
CREATE UNIQUE INDEX uq_saved_reports_visit
    ON saved_reports (user_id, visit_id) WHERE kind = 'VISIT';

-- 기간 직접은 같은 기간·병원이면 한 번만 (같은 기간이라도 병원이 다르면 따로 → C-24 병원 필터)
-- 병원 없이 저장한 것끼리도 같은 리포트로 보게 NULLS NOT DISTINCT
-- 병원은 소프트 삭제라 care_site_id가 null로 바뀌는 건 회원 탈퇴(리포트도 같이 지워짐) 때뿐
CREATE UNIQUE INDEX uq_saved_reports_custom
    ON saved_reports (user_id, from_date, to_date, care_site_id) NULLS NOT DISTINCT WHERE kind = 'CUSTOM';

-- 지난 리포트 목록 (내 것, 최근 저장 순)
CREATE INDEX idx_saved_reports_user_created ON saved_reports (user_id, created_at DESC, id DESC);
