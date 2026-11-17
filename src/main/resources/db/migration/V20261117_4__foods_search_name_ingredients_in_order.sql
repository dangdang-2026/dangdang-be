-- 음식 검색용 이름 규칙 추가: 재료를 원래 순서대로 붙이고 종류를 맨 뒤에
-- 조각이 3개 이상인 이름에서 사람들은 재료를 적힌 순서대로 말함
--   샌드위치_햄_치즈 → 햄치즈샌드위치 (전에는 거꾸로인 치즈햄샌드위치만 있었음)
--   무국_소고기_두부 → 소고기두부무국
-- 생성 열은 함수만 바꾸면 이미 저장된 값이 안 바뀌어서, 열을 지우고 다시 만들어 전부 새로 채움
ALTER TABLE foods DROP COLUMN search_name;

CREATE OR REPLACE FUNCTION food_search_name(name TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT concat_ws('|',
        s.n,
        array_to_string(s.a, ''),
        (SELECT string_agg(s.a[k] || s.a[1], '|' ORDER BY k) FROM generate_subscripts(s.a, 1) k WHERE k > 1),
        CASE WHEN cardinality(s.a) > 2
             THEN (SELECT string_agg(s.a[k], '' ORDER BY k) FROM generate_subscripts(s.a, 1) k WHERE k > 1) || s.a[1] END,
        CASE WHEN cardinality(s.a) > 2
             THEN (SELECT string_agg(s.a[k], '' ORDER BY k DESC) FROM generate_subscripts(s.a, 1) k) END)
    FROM (SELECT lower(replace(name, ' ', '')) AS n,
                 array_remove(string_to_array(lower(replace(name, ' ', '')), '_'), '') AS a) s
$$;

ALTER TABLE foods ADD COLUMN search_name TEXT GENERATED ALWAYS AS (food_search_name(name)) STORED;
