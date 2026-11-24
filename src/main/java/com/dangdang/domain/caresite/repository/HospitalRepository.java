package com.dangdang.domain.caresite.repository;

import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.caresite.infra.HospitalSearchResult.Hospital;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

// hospitals 표 (심평원 병원 목록을 받아 둔 것). 엔티티 없이 SQL로 바로 읽고 씀
// - 8만 줄을 넣고 고치는 일이라 JPA save보다 batch INSERT … ON CONFLICT가 훨씬 빠름
@Repository
@RequiredArgsConstructor
public class HospitalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String UPSERT = """
            INSERT INTO hospitals (hira_code, name, search_name, category, address, phone, synced_at)
            VALUES (:hiraCode, :name, lower(replace(:name, ' ', '')), :category, :address, :phone, :syncedAt)
            ON CONFLICT (hira_code) DO UPDATE SET
                name = EXCLUDED.name, search_name = EXCLUDED.search_name, category = EXCLUDED.category,
                address = EXCLUDED.address, phone = EXCLUDED.phone, synced_at = EXCLUDED.synced_at
            """;

    // 받은 한 쪽을 넣거나 고침. 코드·이름이 없는 줄은 버림. 넣은 줄 수를 돌려줌
    public int upsert(List<Hospital> hospitals, Instant syncedAt) {
        Timestamp at = Timestamp.from(syncedAt);
        SqlParameterSource[] params = hospitals.stream()
                .filter(h -> h.hiraCode() != null && h.name() != null)
                .map(h -> new MapSqlParameterSource()
                        .addValue("hiraCode", h.hiraCode())
                        .addValue("name", h.name())
                        .addValue("category", h.category())
                        .addValue("address", h.address())
                        .addValue("phone", h.phone())
                        .addValue("syncedAt", at))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, params);
        return params.length;
    }

    // 이번 적재 때 안 나온 줄(= 목록에서 빠진 병원) 지우기
    public int deleteSyncedBefore(Instant startedAt) {
        return jdbc.update("DELETE FROM hospitals WHERE synced_at < :startedAt",
                new MapSqlParameterSource("startedAt", Timestamp.from(startedAt)));
    }

    // 한 줄이라도 있는지 (없으면 검색은 심평원으로)
    public boolean hasAny() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate()
                .queryForObject("SELECT EXISTS (SELECT 1 FROM hospitals)", Boolean.class));
    }

    public long count() {
        Long n = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM hospitals", Long.class);
        return n == null ? 0 : n;
    }

    // 이름 검색. keyword는 이미 빈칸 지우고 소문자로 바꾼 것
    // 순서: 이름이 똑같은 병원 -> 이름이 검색어로 시작 -> 나머지, 같은 칸에선 이름 짧은 순
    public HospitalSearchResult search(String keyword, long offset, int limit) {
        String escaped = escapeLike(keyword);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("contains", "%" + escaped + "%")
                .addValue("prefix", escaped + "%")
                .addValue("offset", offset)
                .addValue("limit", limit);

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM hospitals WHERE search_name LIKE :contains", p, Integer.class);
        if (total == null || total == 0) {
            return new HospitalSearchResult(List.of(), 0);
        }
        List<Hospital> rows = jdbc.query("""
                SELECT hira_code, name, category, address, phone
                FROM hospitals
                WHERE search_name LIKE :contains
                ORDER BY CASE WHEN search_name = :keyword THEN 0
                              WHEN search_name LIKE :prefix THEN 1
                              ELSE 2 END,
                         length(search_name), name, hira_code
                LIMIT :limit OFFSET :offset
                """, p, (rs, i) -> new Hospital(
                rs.getString("hira_code"), rs.getString("name"), rs.getString("category"),
                rs.getString("address"), rs.getString("phone")));
        return new HospitalSearchResult(rows, total);
    }

    // 검색어 속 %, _ 를 글자 그대로 찾게 (안 그러면 "%%"가 전부 걸림). PostgreSQL LIKE 기본 이스케이프 = \
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
