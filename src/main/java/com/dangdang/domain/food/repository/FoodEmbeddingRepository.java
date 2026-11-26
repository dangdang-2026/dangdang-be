package com.dangdang.domain.food.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

// foods.name_embedding 읽고 쓰기. vector 타입은 Hibernate가 몰라서 엔티티(Food)에 넣지 않고 JdbcTemplate으로
// 벡터는 '[0.1,0.2,...]' 글자로 넘기고 SQL에서 vector로 바꾼다
@Repository
@RequiredArgsConstructor
public class FoodEmbeddingRepository {

    private final JdbcTemplate jdbc;

    // 아직 벡터가 없는 음식 이름 (같은 이름은 한 번만)
    public List<String> namesWithoutEmbedding(int limit) {
        return jdbc.queryForList(
                "SELECT DISTINCT name FROM foods WHERE name_embedding IS NULL ORDER BY name LIMIT ?",
                String.class, limit);
    }

    // 같은 이름인 줄을 한 번에 채운다. 이미 채운 줄은 건너뜀 (서버 두 대가 같이 채워도 괜찮게)
    public int save(String name, float[] vector) {
        return jdbc.update(
                "UPDATE foods SET name_embedding = CAST(? AS vector) WHERE name = ? AND name_embedding IS NULL",
                toLiteral(vector), name);
    }

    // 뜻이 가까운 음식 코드. 거리(<=>)는 코사인 거리로 0이면 같고 클수록 멀다
    // 거리가 같으면 글자 검색과 같은 출처 순서 (가정식과 외식, 급식, 프랜차이즈)
    public List<String> nearestCodes(float[] vector, double maxDistance, int limit) {
        String v = toLiteral(vector);
        return jdbc.queryForList("""
                SELECT food_code FROM foods
                WHERE name_embedding IS NOT NULL
                  AND name_embedding <=> CAST(? AS vector) < ?
                ORDER BY name_embedding <=> CAST(? AS vector),
                         CASE WHEN origin IN (1, 3, 4) THEN 0 WHEN origin IN (5, 6, 7) THEN 1 ELSE 2 END,
                         food_code
                LIMIT ?
                """, String.class, v, maxDistance, v, limit);
    }

    static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
