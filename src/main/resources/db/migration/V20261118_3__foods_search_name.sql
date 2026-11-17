-- 음식 검색용 이름 (단어 순서 문제 해결)
-- 식약처 이름은 "볶음밥_김치"처럼 종류_재료 순서라서 "김치볶음밥"으로 치면 안 걸림
-- 그래서 _로 나눈 조각을 사람이 부르는 순서로 붙인 이름들을 | 로 이어서 저장해 둠 (띄어쓰기 빼고 소문자)
--   볶음밥_김치          → 볶음밥_김치|볶음밥김치|김치볶음밥
--   샌드위치_햄_치즈     → … |햄샌드위치|치즈샌드위치|햄치즈샌드위치|치즈햄샌드위치
--   김치찌개_참치        → … |참치김치찌개
-- 만드는 규칙: 원래 이름 / 조각을 그대로 붙인 것 / 뒤 조각 하나 + 첫 조각
--             / (조각이 3개 이상이면) 재료를 적힌 순서대로 + 첫 조각, 전부 거꾸로
-- 생성 열이라 나중에 음식 데이터를 새로 넣어도 자동으로 채워짐
CREATE FUNCTION food_search_name(name TEXT) RETURNS TEXT
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
