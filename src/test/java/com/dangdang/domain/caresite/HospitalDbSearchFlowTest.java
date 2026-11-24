package com.dangdang.domain.caresite;

import com.dangdang.domain.caresite.infra.HospitalSearchClient;
import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// hospitals 표에 병원이 있으면 검색은 DB에서 (심평원은 안 부름)
@SpringBootTest
@AutoConfigureMockMvc
class HospitalDbSearchFlowTest {

    static final List<String> hiraCalls = new ArrayList<>();

    @TestConfiguration
    static class FakeHira {
        @Bean
        @Primary
        HospitalSearchClient fakeHospitalSearchClient() {
            return (keyword, page, size) -> {
                hiraCalls.add(keyword);
                return new HospitalSearchResult(List.of(), 0);
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;

    @BeforeEach
    void setUp() throws Exception {
        hiraCalls.clear();
        jdbc.update("DELETE FROM hospitals");
        hospital("CODE-1", "서울대학교병원", "상급종합");
        hospital("CODE-2", "분당서울대학교병원", "상급종합");
        hospital("CODE-3", "서울대학교치과병원", "치과병원");
        hospital("CODE-4", "CHA의과대학교 분당차병원", "종합병원");
        hospital("CODE-5", "강남세브란스병원", "상급종합");

        userRepository.save(User.ofEmail("jisu@x.com", "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM hospitals");
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private void hospital(String code, String name, String category) {
        jdbc.update("""
                INSERT INTO hospitals (hira_code, name, search_name, category, address, phone, synced_at)
                VALUES (?, ?, lower(replace(?, ' ', '')), ?, '서울', '02-000-0000', now())
                """, code, name, name, category);
    }

    @Test
    void DB에서_찾고_이름이_똑같은_병원이_맨_위_심평원은_안_부른다() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "서울대학교 병원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].hiraCode").value("CODE-1"))
                .andExpect(jsonPath("$.data.items[0].category").value("상급종합"))
                .andExpect(jsonPath("$.data.items[1].hiraCode").value("CODE-2"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        assertThat(hiraCalls).isEmpty();
    }

    @Test
    void 빈칸_대소문자_상관없이_이름_중간_글자로도_찾는다() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "cha 의과"))
                .andExpect(jsonPath("$.data.items[0].name").value("CHA의과대학교 분당차병원"));
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "세브란스"))
                .andExpect(jsonPath("$.data.items[0].hiraCode").value("CODE-5"));
    }

    @Test
    void 검색어로_시작하는_병원이_중간에_들어간_병원보다_위() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "서울대"))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].hiraCode").value("CODE-1"))     // 서울대… 중 이름 짧은 것
                .andExpect(jsonPath("$.data.items[1].hiraCode").value("CODE-3"))
                .andExpect(jsonPath("$.data.items[2].hiraCode").value("CODE-2"));    // 분당서울대…
    }

    @Test
    void 페이지를_넘기면_다음_병원() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access)
                        .param("keyword", "서울대").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].hiraCode").value("CODE-2"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 퍼센트나_밑줄은_글자_그대로_찾는다() throws Exception {
        for (String keyword : List.of("%%", "__")) {
            mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount").value(0))
                    .andExpect(jsonPath("$.data.items", hasSize(0)));
        }
    }
}
