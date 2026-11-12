package com.dangdang.domain.caresite;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CareSiteFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 병원 확인용 다른 사용자

    @BeforeEach
    void loginFirst() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 care_sites도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    // 등록하고 careSiteId 돌려줌 (201 기대)
    private long create(Cookie who, String json) throws Exception {
        String body = mvc.perform(post("/api/care-sites").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.careSiteId")).longValue();
    }

    private static String site(String name, String department) {
        return "{\"name\":\"" + name + "\",\"department\":\"" + department + "\"}";
    }

    private static String primarySite(String name, String department) {
        return "{\"name\":\"" + name + "\",\"department\":\"" + department + "\",\"primary\":true}";
    }

    // 지금 대표 병원 id 목록 (대표는 하나여야 함)
    private List<Long> primaryIds() {
        return jdbc.queryForList(
                "SELECT id FROM care_sites WHERE is_primary AND deleted_at IS NULL", Long.class);
    }

    // ---------- 등록 ----------

    @Test
    void 병원을_등록하면_201이고_대표는_기본_false_순서는_맨_뒤() throws Exception {
        create(access, site("A산부인과", "OBGYN"));
        mvc.perform(post("/api/care-sites").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"B내과\",\"department\":\"ENDO\",\"address\":\"서울시\","
                                + "\"phone\":\"02-123-4567\",\"roleNote\":\"혈당 관리\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("B내과"))
                .andExpect(jsonPath("$.data.department").value("ENDO"))
                .andExpect(jsonPath("$.data.phone").value("02-123-4567"))
                .andExpect(jsonPath("$.data.primary").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.hiraCode").doesNotExist());
    }

    @Test
    void 이름이_없거나_진료과가_없거나_길이를_넘으면_400() throws Exception {
        for (String body : List.of(
                "{\"department\":\"OBGYN\"}",
                "{\"name\":\"  \",\"department\":\"OBGYN\"}",
                "{\"name\":\"A병원\"}",
                "{\"name\":\"A병원\",\"department\":\"DENTIST\"}",
                site("가".repeat(51), "OBGYN"),
                "{\"name\":\"A병원\",\"department\":\"ETC\",\"roleNote\":\"" + "가".repeat(31) + "\"}")) {
            mvc.perform(post("/api/care-sites").cookie(access)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/care-sites")).andExpect(status().isUnauthorized());
    }

    // ---------- 대표 병원 ----------

    @Test
    void 새_병원을_대표로_등록하면_원래_대표는_풀린다() throws Exception {
        long first = create(access, primarySite("A산부인과", "OBGYN"));
        long second = create(access, primarySite("B내과", "ENDO"));
        assertThat(primaryIds()).containsExactly(second);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 수정으로_대표를_바꾸면_원래_대표는_풀리고_false면_해제된다() throws Exception {
        long first = create(access, primarySite("A산부인과", "OBGYN"));
        long second = create(access, site("B내과", "ENDO"));

        mvc.perform(patch("/api/care-sites/" + second).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primary\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primary").value(true));
        assertThat(primaryIds()).containsExactly(second);

        mvc.perform(patch("/api/care-sites/" + second).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primary\":false}"))
                .andExpect(jsonPath("$.data.primary").value(false));
        assertThat(primaryIds()).isEmpty();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 다른_사람의_대표_병원은_건드리지_않는다() throws Exception {
        long others = create(other, primarySite("C병원", "OBGYN"));
        long mine = create(access, primarySite("A산부인과", "OBGYN"));
        assertThat(primaryIds()).containsExactlyInAnyOrder(others, mine);
    }

    @Test
    void 대표_지정_요청이_동시에_여러_개_와도_모두_성공하고_대표는_하나() throws Exception {
        for (int round = 0; round < 5; round++) {
            long a = create(access, site("A산부인과" + round, "OBGYN"));
            long b = create(access, site("B내과" + round, "ENDO"));
            String newPrimary = primarySite("C병원" + round, "ETC");
            // 하나는 수정으로 대표 지정, 하나는 새 병원을 대표로 등록 -> 동시에 출발
            List<Integer> statuses = runTogether(
                    () -> mvc.perform(patch("/api/care-sites/" + a).cookie(access)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"primary\":true}"))
                            .andReturn().getResponse().getStatus(),
                    () -> mvc.perform(patch("/api/care-sites/" + b).cookie(access)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"primary\":true}"))
                            .andReturn().getResponse().getStatus(),
                    () -> mvc.perform(post("/api/care-sites").cookie(access)
                                    .contentType(MediaType.APPLICATION_JSON).content(newPrimary))
                            .andReturn().getResponse().getStatus());
            assertThat(statuses).containsExactly(200, 200, 201);   // 409 없이 줄 서서 처리됨
            assertThat(primaryIds()).hasSize(1);
        }
    }

    // 요청들을 최대한 같은 순간에 보내고 상태 코드를 순서대로 돌려줌
    @SafeVarargs
    private List<Integer> runTogether(Callable<Integer>... requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> request : requests) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 동시_요청으로_대표가_두_개_되려_하면_DB가_막는다() throws Exception {
        long mine = create(access, primarySite("A산부인과", "OBGYN"));
        Long userId = jdbc.queryForObject("SELECT user_id FROM care_sites WHERE id = ?", Long.class, mine);
        // 코드 검사를 건너뛰고 대표를 하나 더 넣어 봄 (동시 요청이 둘 다 "대표 없음"을 본 상황)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO care_sites (user_id, name, department, is_primary) VALUES (?, 'B병원', 'ENDO', true)",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 삭제된 병원은 대표였어도 상관없음
        jdbc.update("INSERT INTO care_sites (user_id, name, department, is_primary, deleted_at) "
                + "VALUES (?, 'C병원', 'ENDO', true, now())", userId);
        assertThat(primaryIds()).containsExactly(mine);
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_대표_먼저_그다음_정렬_순서이고_남의_병원은_안_보인다() throws Exception {
        create(access, "{\"name\":\"뒤\",\"department\":\"ETC\",\"sortOrder\":5}");
        create(access, "{\"name\":\"앞\",\"department\":\"ETC\",\"sortOrder\":0}");
        create(access, "{\"name\":\"대표\",\"department\":\"OBGYN\",\"primary\":true,\"sortOrder\":9}");
        create(other, site("남의병원", "OBGYN"));

        mvc.perform(get("/api/care-sites").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].name").value("대표"))
                .andExpect(jsonPath("$.data[1].name").value("앞"))
                .andExpect(jsonPath("$.data[2].name").value("뒤"));
    }

    @Test
    void 병원이_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/care-sites").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ---------- 수정 ----------

    @Test
    void 보낸_칸만_바뀌고_빈_문자열이면_비워진다() throws Exception {
        long id = create(access, "{\"name\":\"A산부인과\",\"department\":\"OBGYN\",\"phone\":\"02-1\",\"address\":\"서울\"}");
        mvc.perform(patch("/api/care-sites/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A여성병원\",\"address\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("A여성병원"))
                .andExpect(jsonPath("$.data.department").value("OBGYN"))
                .andExpect(jsonPath("$.data.phone").value("02-1"))
                .andExpect(jsonPath("$.data.address").doesNotExist());   // 등록 때 안 보낸 것과 같은 null
        assertThat(jdbc.queryForObject("SELECT address FROM care_sites WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void 바꿀_값이_없으면_400_빈_이름이면_400() throws Exception {
        long id = create(access, site("A산부인과", "OBGYN"));
        mvc.perform(patch("/api/care-sites/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40001"));
        mvc.perform(patch("/api/care-sites/" + id).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 남의_병원을_수정하거나_삭제하면_404() throws Exception {
        long others = create(other, site("C병원", "OBGYN"));
        mvc.perform(patch("/api/care-sites/" + others).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"뺏기\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));
        mvc.perform(delete("/api/care-sites/" + others).cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARE_SITE_40401"));
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_목록에서_빠지고_대표도_풀리며_두_번_지우면_404() throws Exception {
        long id = create(access, primarySite("A산부인과", "OBGYN"));
        mvc.perform(delete("/api/care-sites/" + id).cookie(access))
                .andExpect(status().isOk());

        mvc.perform(get("/api/care-sites").cookie(access))
                .andExpect(jsonPath("$.data", hasSize(0)));
        assertThat(primaryIds()).isEmpty();
        // 줄은 남아 있음 (soft delete)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_sites WHERE id = ?", Long.class, id))
                .isEqualTo(1L);

        mvc.perform(delete("/api/care-sites/" + id).cookie(access))
                .andExpect(status().isNotFound());
    }
}
