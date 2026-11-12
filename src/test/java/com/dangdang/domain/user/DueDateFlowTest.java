package com.dangdang.domain.user;

import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.user.dto.request.DueDateUpdateRequest;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.DueDateService;
import com.dangdang.global.config.ClockConfig;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DueDateFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PregnancyRepository pregnancyRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired DueDateService dueDateService;
    @Autowired TransactionTemplate tx;

    Cookie access;
    Long userId;
    Long otherId;

    @BeforeEach
    void loginFirst() throws Exception {
        userId = userRepository
                .save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()))
                .getId();
        otherId = userRepository
                .save(User.ofEmail("minji@x.com", "민지", passwordEncoder.encode("abcd1234"), Instant.now()))
                .getId();
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() { // users를 지우면 pregnancies도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(patch("/api/me/due-date").cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int countOf(Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM pregnancies WHERE user_id = ?", Integer.class, id);
    }

    private LocalDate activeDueDate(Long id) {
        return jdbc.queryForObject(
                "SELECT due_date FROM pregnancies WHERE user_id = ? AND state <> 'ENDED'", LocalDate.class, id);
    }

    private void insertRow(Long id, String dueDate, String state) {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state) VALUES (?, ?::date, ?)", id, dueDate, state);
    }

    @Test
    void 처음이면_진행_중인_임신을_새로_만든다() throws Exception {
        save("{\"dueDate\":\"2026-12-20\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dueDate").value("2026-12-20"));

        assertThat(countOf(userId)).isEqualTo(1);
        assertThat(activeDueDate(userId)).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(jdbc.queryForObject("SELECT state FROM pregnancies WHERE user_id = ?", String.class, userId))
                .isEqualTo("PREGNANT");
        assertThat(jdbc.queryForObject("SELECT care_mode FROM pregnancies WHERE user_id = ?", String.class, userId))
                .isEqualTo("DIET");
    }

    @Test
    void 다시_보내면_같은_줄의_날짜만_바뀐다() throws Exception {
        save("{\"dueDate\":\"2026-12-20\"}").andExpect(status().isOk());
        save("{\"dueDate\":\"2027-01-03\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").value("2027-01-03"));

        assertThat(countOf(userId)).isEqualTo(1);
        assertThat(activeDueDate(userId)).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    void 다른_사람의_임신은_건드리지_않는다() throws Exception {
        insertRow(otherId, "2026-11-30", "PREGNANT");

        save("{\"dueDate\":\"2026-12-20\"}").andExpect(status().isOk());

        assertThat(activeDueDate(otherId)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(countOf(otherId)).isEqualTo(1);
        assertThat(countOf(userId)).isEqualTo(1);
    }

    @Test
    void 끝난_임신만_있으면_새_줄을_만들고_끝난_줄은_그대로다() throws Exception {
        insertRow(userId, "2024-05-01", "ENDED");

        save("{\"dueDate\":\"2026-12-20\"}").andExpect(status().isOk());

        assertThat(countOf(userId)).isEqualTo(2);
        assertThat(activeDueDate(userId)).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(jdbc.queryForObject(
                "SELECT due_date FROM pregnancies WHERE user_id = ? AND state = 'ENDED'", LocalDate.class, userId))
                .isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    void 진행_중인_줄은_산후여도_그_줄을_고친다() throws Exception {
        insertRow(userId, "2026-10-01", "POSTPARTUM");

        save("{\"dueDate\":\"2026-10-05\"}").andExpect(status().isOk());

        assertThat(countOf(userId)).isEqualTo(1);
        assertThat(activeDueDate(userId)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void 날짜가_없거나_형식이_틀리면_400() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"dueDate\":null}",
                "{\"dueDate\":\"\"}",
                "{\"dueDate\":\"2026/12/20\"}",
                "{\"dueDate\":\"2026-2-3\"}",
                "{\"dueDate\":\"20261220\"}",
                "{\"dueDate\":\"2026-02-30\"}",
                "{\"dueDate\":\"2026-12-20T00:00:00Z\"}",
                "{\"dueDate\":20261220}",
                "{\"dueDate\":")) {
            save(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(countOf(userId)).isZero();
    }

    @Test
    void 동시에_여러_번_보내도_한_줄만_생긴다() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return dueDateService.save(userId, new DueDateUpdateRequest(LocalDate.of(2026, 12, 20)));
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get();   // 하나라도 실패했으면 여기서 예외가 나서 테스트가 실패한다
        }
        pool.shutdown();

        assertThat(countOf(userId)).isEqualTo(1);
    }

    @Test
    void 예정일을_저장해도_이름용_version은_그대로다() throws Exception {
        save("{\"dueDate\":\"2026-12-20\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT row_version FROM users WHERE id = ?", Long.class, userId)).isZero();
    }

    @Test
    void 시각은_Clock_빈에서_온다() {
        Instant first = Instant.parse("2026-11-10T03:00:00Z");
        Instant second = Instant.parse("2026-11-10T04:00:00Z");

        saveAt(first);
        saveAt(second);

        var row = jdbc.queryForMap("SELECT created_at, updated_at FROM pregnancies WHERE user_id = ?", userId);
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(first);
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(second);
    }

    private void saveAt(Instant now) {
        DueDateService fixed = new DueDateService(
                userRepository, pregnancyRepository, Clock.fixed(now, ClockConfig.KST));
        tx.executeWithoutResult(status ->
                fixed.save(userId, new DueDateUpdateRequest(LocalDate.of(2026, 12, 20))));
    }

    @Test
    void 진행_중인_줄이_두_개면_DB가_막고_끝난_줄은_여러_개_된다() {
        insertRow(userId, "2026-12-20", "PREGNANT");
        insertRow(userId, "2024-05-01", "ENDED");
        insertRow(userId, "2022-05-01", "ENDED");

        assertThatThrownBy(() -> insertRow(userId, "2027-01-01", "HOSPITALIZED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countOf(userId)).isEqualTo(3);
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(patch("/api/me/due-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2026-12-20\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40101"));
    }
}
