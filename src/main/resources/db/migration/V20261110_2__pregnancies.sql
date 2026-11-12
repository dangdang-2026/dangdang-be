-- 임신 정보 (진행 중인 임신은 한 사람당 1건)


CREATE TABLE pregnancies (
     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
     due_date DATE,                                  -- 출산 예정일(선택), 주수는 계산
     diagnosis_date DATE,
     delivery_date DATE,                             -- 입력하면 산후 모드
     care_mode VARCHAR(10) NOT NULL DEFAULT 'DIET',  -- DIET / INSULIN / ORAL
     state VARCHAR(15) NOT NULL DEFAULT 'PREGNANT',  -- PREGNANT / HOSPITALIZED / POSTPARTUM / ENDED
     hospitalized_at TIMESTAMPTZ,                    -- 입원 중 알림 멈춤
     postpartum_ask_on DATE,                         -- 산후 다시 물어볼 날
     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ENDED가 아닌 줄(진행 중인 임신)은 한 사람당 하나만
CREATE UNIQUE INDEX ux_pregnancies_active ON pregnancies (user_id) WHERE state <> 'ENDED';
