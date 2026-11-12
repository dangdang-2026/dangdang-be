package com.dangdang.domain.caresite;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.infra.HospitalSearchClient;
import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.caresite.infra.HospitalSearchResult.Hospital;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HospitalSearchFlowTest {

    // 진짜 심평원 대신: 검색어에 따라 정해진 결과를 돌려주고, 받은 page·size를 적어 둠
    static final List<String> calls = new ArrayList<>();

    @TestConfiguration
    static class FakeHira {
        @Bean
        @Primary
        HospitalSearchClient fakeHospitalSearchClient() {
            return (keyword, page, size) -> {
                calls.add(keyword + "|" + page + "|" + size);
                return switch (keyword) {
                    case "서울대학교병원" -> new HospitalSearchResult(List.of(
                            new Hospital("CODE-1", "서울대학교병원", "상급종합", "서울 종로구", "1588-5700"),
                            new Hospital("CODE-2", "분당서울대학교병원", "상급종합", "경기 성남시", null)), 4);
                    case "심평원고장" -> throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
                    default -> new HospitalSearchResult(List.of(), 0);
                };
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
    void loginFirst() throws Exception {
        calls.clear();
        userRepository.save(User.ofEmail("jisu@x.com", "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 검색하면_병원_목록과_다음_페이지_여부를_준다() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access)
                        .param("keyword", "서울대학교병원").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].hiraCode").value("CODE-1"))
                .andExpect(jsonPath("$.data.items[0].name").value("서울대학교병원"))
                .andExpect(jsonPath("$.data.items[0].category").value("상급종합"))
                .andExpect(jsonPath("$.data.items[1].phone").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 페이지는_0부터_받아서_심평원에는_1부터_보낸다_size는_50까지() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access)
                        .param("keyword", "  서울대학교병원  ").param("page", "1").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        assertThat(calls).containsExactly("서울대학교병원|2|50");   // 앞뒤 빈칸은 지우고 보냄
    }

    @Test
    void 결과가_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "없는병원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 검색어가_한_글자거나_너무_길면_400이고_심평원은_안_부른다() throws Exception {
        for (String keyword : List.of("서", "   ", "가".repeat(51))) {
            mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", keyword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40002"));
        }
        mvc.perform(get("/api/care-sites/search").cookie(access))
                .andExpect(status().isBadRequest());
        assertThat(calls).isEmpty();
    }

    @Test
    void 심평원에_연결이_안_되면_502() throws Exception {
        mvc.perform(get("/api/care-sites/search").cookie(access).param("keyword", "심평원고장"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_50201"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/care-sites/search").param("keyword", "서울대학교병원"))
                .andExpect(status().isUnauthorized());
    }
}
