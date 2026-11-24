package com.dangdang.domain.caresite;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.infra.HospitalListClient;
import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.caresite.infra.HospitalSearchResult.Hospital;
import com.dangdang.domain.caresite.service.HospitalSyncService;
import com.dangdang.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 심평원 전체 목록 -> hospitals 적재
@SpringBootTest
class HospitalSyncFlowTest {

    // 쪽 번호 -> 돌려줄 결과 (없으면 빈 쪽). 값이 RuntimeException이면 던짐
    static final Map<Integer, Object> pages = new HashMap<>();
    static final List<Integer> calls = new ArrayList<>();

    @TestConfiguration
    static class FakeHiraList {
        @Bean
        @Primary
        HospitalListClient fakeHospitalListClient() {
            return (page, size) -> {
                calls.add(page);
                Object p = pages.get(page);
                if (p instanceof RuntimeException e) {
                    throw e;
                }
                return p == null ? new HospitalSearchResult(List.of(), 0) : (HospitalSearchResult) p;
            };
        }
    }

    @Autowired HospitalSyncService hospitalSyncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        pages.clear();
        calls.clear();
        jdbc.update("DELETE FROM hospitals");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM hospitals");
    }

    private static Hospital h(String code, String name, String phone) {
        return new Hospital(code, name, "의원", "서울 종로구", phone);
    }

    private void oldHospital(String code) {
        jdbc.update("""
                INSERT INTO hospitals (hira_code, name, search_name, synced_at)
                VALUES (?, '문닫은의원', '문닫은의원', '2020-01-01T00:00:00Z')
                """, code);
    }

    private String phoneOf(String code) {
        return jdbc.queryForObject("SELECT phone FROM hospitals WHERE hira_code = ?", String.class, code);
    }

    @Test
    void 쪽마다_받아서_저장하고_검색용_이름도_만든다() {
        pages.put(1, new HospitalSearchResult(List.of(h("C1", "서울대학교 병원", "1"), h("C2", "당당의원", null)), 3));
        pages.put(2, new HospitalSearchResult(List.of(h("C3", "CHA 병원", "3")), 3));

        HospitalSyncService.Result result = hospitalSyncService.syncAll();

        assertThat(result.completed()).isTrue();
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.saved()).isEqualTo(3);
        assertThat(calls).containsExactly(1, 2);   // 다 받았으면 3쪽은 안 부름
        assertThat(jdbc.queryForObject("SELECT search_name FROM hospitals WHERE hira_code = 'C1'", String.class))
                .isEqualTo("서울대학교병원");
        assertThat(jdbc.queryForObject("SELECT search_name FROM hospitals WHERE hira_code = 'C3'", String.class))
                .isEqualTo("cha병원");
    }

    @Test
    void 다시_받으면_값을_고치고_목록에서_빠진_병원은_지운다() {
        oldHospital("GONE");
        oldHospital("C1");
        pages.put(1, new HospitalSearchResult(List.of(h("C1", "당당의원", "02-111-1111")), 1));

        HospitalSyncService.Result result = hospitalSyncService.syncAll();

        assertThat(result.removed()).isEqualTo(1);
        assertThat(phoneOf("C1")).isEqualTo("02-111-1111");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hospitals", Long.class)).isEqualTo(1);
    }

    @Test
    void 코드나_이름이_없는_줄은_버린다() {
        pages.put(1, new HospitalSearchResult(List.of(h(null, "이름만", null), h("C1", null, null), h("C2", "당당의원", null)), 3));

        hospitalSyncService.syncAll();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM hospitals", Long.class)).isEqualTo(1);
    }

    @Test
    void 중간에_계속_실패하면_받은_것은_남기고_옛_병원은_안_지운다() {
        oldHospital("GONE");
        pages.put(1, new HospitalSearchResult(List.of(h("C1", "가", null), h("C2", "나", null)), 3));
        pages.put(2, new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE));

        HospitalSyncService.Result result = hospitalSyncService.syncAll();

        assertThat(result.completed()).isFalse();
        assertThat(calls).containsExactly(1, 2, 2, 2);   // 2쪽은 3번까지 다시 해 봄
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hospitals", Long.class)).isEqualTo(3);   // GONE + 2
    }
    @Test
    void 요양병원_정신병원_치과_한방은_저장하지_않고_예전에_있던_줄도_지운다() {
        oldHospital("NURSING-OLD");
        pages.put(1, new HospitalSearchResult(List.of(
                new Hospital("A", "당당산부인과의원", "의원", "서울", null),
                new Hospital("B", "당당종합병원", "종합병원", "서울", null),
                new Hospital("C", "당당요양병원", "요양병원", "서울", null),
                new Hospital("D", "당당정신병원", "정신병원", "서울", null),
                new Hospital("E", "당당치과의원", "치과의원", "서울", null),
                new Hospital("F", "당당한의원", "한의원", "서울", null)), 6));

        HospitalSyncService.Result result = hospitalSyncService.syncAll();

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.removed()).isEqualTo(1);   // 다 받았으니(6/6) 옛 줄은 지움
        assertThat(jdbc.queryForList("SELECT hira_code FROM hospitals ORDER BY hira_code", String.class))
                .containsExactly("A", "B");
    }
}
