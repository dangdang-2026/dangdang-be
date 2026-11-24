-- 심평원 병원기본목록을 미리 받아 두는 표 (병원 검색을 심평원 대신 여기서 함)
-- 한 줄 = 병원 하나. 매주 다시 받아서 고치고, 목록에서 빠진(문 닫은) 병원은 지움
CREATE TABLE hospitals (
    hira_code   VARCHAR(100) PRIMARY KEY,          -- ykiho: 암호화 요양기호 (care_sites.hira_code와 같은 값)
    name        VARCHAR(200) NOT NULL,             -- yadmNm
    search_name VARCHAR(200) NOT NULL,             -- 검색용: 빈칸 지우고 소문자 ("CHA 의과대학교" -> "cha의과대학교")
    category    VARCHAR(30),                       -- clCdNm: 상급종합·종합병원·병원·의원 등
    address     VARCHAR(500),
    phone       VARCHAR(30),
    synced_at   TIMESTAMPTZ NOT NULL               -- 마지막으로 받은 시각 (이번에 안 받은 줄 = 문 닫은 병원)
);

-- 이름 중간 글자 검색(LIKE '%서울대%')도 인덱스를 타게 (pg_trgm은 V1에서 켜 둠)
CREATE INDEX ix_hospitals_search_name_trgm ON hospitals USING gin (search_name gin_trgm_ops);
