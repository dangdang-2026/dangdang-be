-- 저장한 진료 리포트 (C-07 저장·공유, C-25 기간 골라 만들기 → C-24 지난 리포트 목록)
-- 리포트 내용(숫자·표)은 저장하지 않고 볼 때마다 다시 계산함. 여기엔 기간·병원만 남김
-- (reports는 커뮤니티 신고 테이블이라 이름을 나눔)
CREATE TABLE saved_reports (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
   kind VARCHAR(10) NOT NULL CHECK (kind IN ('VISIT', 'CUSTOM')),     -- 진료용 / 기간 직접
   from_date DATE NOT NULL,                                            -- 기간 시작 (포함)
   to_date DATE NOT NULL,                                              -- 기간 끝 (포함)
   visit_id BIGINT REFERENCES visits(id) ON DELETE CASCADE,            -- 진료용이면 그 진료 일정, 기간 직접이면 null
   care_site_id BIGINT REFERENCES care_sites(id) ON DELETE SET NULL,   -- 보여 줄 병원 (없어도 됨)
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   CHECK (from_date <= to_date),
   CHECK ((kind = 'VISIT') = (visit_id IS NOT NULL))                   -- 진료용만 진료 일정이 있음
);

-- 같은 kind·기간·진료 일정은 한 번만 저장 (기간 직접은 visit_id가 null이라 NULLS NOT DISTINCT로 null끼리도 같은 값으로 봄)
-- 지난 리포트 목록(내 것, 최근 저장 순)도 이 인덱스 앞부분(user_id)을 씀
CREATE UNIQUE INDEX uq_saved_reports_period
    ON saved_reports (user_id, kind, from_date, to_date, visit_id) NULLS NOT DISTINCT;
