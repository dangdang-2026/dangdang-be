package com.dangdang.domain.reading;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 같은 혈당을 두 곳에서 동시에 고칠 때 (row_version). 하나는 200, 나중에 쓰는 쪽은 409
@SpringBootTest
@AutoConfigureMockMvc
class ReadingConcurrencyTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    Cookie access;

    @BeforeEach
    void loginFirst() throws Exception {
        grantSensitive(jdbc,
                userRepository.save(User.ofEmail("jisu@x.com", "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
        access = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 같은_혈당을_동시에_고치면_하나는_200_하나는_409이고_처음_값은_그대로() throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":100,\"measuredAt\":\"" + Instant.now() + "\",\"readingType\":\"FASTING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.readingId")).longValue();

        List<Integer> statuses = new ArrayList<>();
        List<Integer> values = List.of(120, 130);
        // 테스트가 그 줄을 먼저 잠가 둠 → 두 요청 모두 version 0으로 읽은 뒤 UPDATE에서 기다리게 됨 (순서가 운에 달리지 않게)
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (PreparedStatement ps = lock.prepareStatement("SELECT id FROM readings WHERE id = ? FOR UPDATE")) {
                ps.setLong(1, id);
                ps.executeQuery();
            }

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int value : values) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(patch("/api/readings/" + id).cookie(access)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"value\":" + value + "}"))
                            .andReturn();
                }));
            }
            start.countDown();
            waitUntilLockWaiters(2);
            lock.rollback();   // 잠금을 풀면 하나가 먼저 쓰고, 다른 하나는 row_version이 달라져 실패함

            for (Future<MvcResult> result : results) {
                statuses.add(result.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            }
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        int winner = values.get(statuses.indexOf(200));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT value, original_value, row_version FROM readings WHERE id = ?", id);
        assertThat(((Number) row.get("value")).intValue()).isEqualTo(winner);
        assertThat(((Number) row.get("original_value")).intValue()).isEqualTo(100);
        assertThat(((Number) row.get("row_version")).longValue()).isEqualTo(1L);
    }

    // 이 DB에서 잠금을 기다리는 연결이 n개가 될 때까지 (최대 10초)
    private void waitUntilLockWaiters(int n) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                    Integer.class);
            if (waiting != null && waiting >= n) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("두 요청이 잠금을 기다리는 상태가 되지 않았어요");
    }
}
