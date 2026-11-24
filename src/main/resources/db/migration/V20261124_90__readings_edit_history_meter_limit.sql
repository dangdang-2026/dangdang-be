-- 혈당을 고쳤을 때 처음 값과 고친 시각 (D-05 「처음 128 · 10월 14일 13:20에 고침」, D-07 「수정됨」)
-- updated_at은 지우기·되돌리기에도 올라가서 "사용자가 고쳤는지"는 edited_at으로 따로 본다 (meals.edited_at과 같은 방식)
-- 혈당계에 숫자 대신 HI·LO가 떴을 때 (G-11)
-- value에는 혈당계 끝값(HI 600 · LO 20)을 넣고 무엇이 떴는지는 meter_limit에 둔다
-- → 평균·목표 밖·70 미만·리포트 집계가 지금 쿼리 그대로 HI는 높음, LO는 낮음으로 센다
ALTER TABLE readings
    ADD COLUMN original_value SMALLINT CHECK (original_value BETWEEN 20 AND 600),  -- 처음 저장한 값. 값을 안 고쳤으면 NULL
    ADD COLUMN edited_at TIMESTAMPTZ,                                              -- 사용자가 마지막으로 고친 시각. 안 고쳤으면 NULL
    ADD COLUMN meter_limit VARCHAR(2),                                             -- HI / LO. 숫자로 적었으면 NULL
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0,                              -- 동시에 고치면 나중 것을 409로 (JPA @Version)
    ADD CONSTRAINT ck_readings_meter_limit CHECK (
        meter_limit IS NULL
        OR (meter_limit = 'HI' AND value = 600)
        OR (meter_limit = 'LO' AND value = 20));

-- 지금까지 있던 기록은 edited_at을 비워 둔 채 시작함 (updated_at > created_at으로 채우지 않음)
-- updated_at은 식사만 붙여도 올라가서, 그걸로 채우면 "식사만 붙인 건 고친 게 아님" 규칙과 어긋남
