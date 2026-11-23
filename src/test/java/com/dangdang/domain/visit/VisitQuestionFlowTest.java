package com.dangdang.domain.visit;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 질문 흐름 테스트
@SpringBootTest
@AutoConfigureMockMvc
class VisitQuestionFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 일정 확인용 다른 사용자
    long visitId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        visitId = createVisit(access);
    }

    @AfterEach
    void clean() { // users를 지우면 visits·visit_questions도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 질문을_등록하면_201이고_출처는_USER() throws Exception {
        add(access, visitId, "{\"content\":\"  공복 혈당이 계속 높은데 괜찮을까요?  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questionId").exists())
                .andExpect(jsonPath("$.data.content").value("공복 혈당이 계속 높은데 괜찮을까요?"))   // 앞뒤 공백 뺌
                .andExpect(jsonPath("$.data.source").value("USER"))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    // 당당이 「물어볼 말 정리」 답 한 줄을 DB에 바로 넣는다 (AI 서버 없이)
    static final String DRAFT = "- 저녁 식사를 바꿔야 할까요?\n- 식후 2시간에도 재야 하나요?";

    private long chatDraft(String email, String kind) {
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return jdbc.queryForObject("INSERT INTO chat_messages (user_id, role, kind, content, payload)"
                + " VALUES (?, 'ASSISTANT', ?, '초안', jsonb_build_object('questionDraft', ?::text)) RETURNING id",
                Long.class, userId, kind, DRAFT);
    }

    @Test
    void 당당이_대화_번호를_보내면_출처는_CHATBOT() throws Exception {
        long draft = chatDraft("jisu@x.com", "QUESTION_DRAFT");

        add(access, visitId, "{\"content\":\"저녁 식사를 바꿔야 할까요?\",\"chatMessageId\":" + draft + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("CHATBOT"));
    }

    @Test
    void 초안과_다른_글이면_대화_번호를_보내도_출처는_USER() throws Exception {   // PR #120 리뷰: 아무 글이나 CHATBOT으로 꾸미지 못하게
        long draft = chatDraft("jisu@x.com", "QUESTION_DRAFT");

        add(access, visitId, "{\"content\":\"아무 말\",\"chatMessageId\":" + draft + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("USER"));
        add(access, visitId, "{\"content\":\" 식후 2시간에도   재야 하나요? \",\"chatMessageId\":" + draft + "}")
                .andExpect(jsonPath("$.data.source").value("CHATBOT"));   // 초안의 한 줄 (머리표·띄어쓰기 차이는 봐줌)
    }

    @Test
    void 남의_대화나_물어볼_말_정리가_아닌_대화면_404() throws Exception {
        long othersDraft = chatDraft("minho@x.com", "QUESTION_DRAFT");
        long info = chatDraft("jisu@x.com", "INFO");

        add(access, visitId, "{\"content\":\"질문\",\"chatMessageId\":" + othersDraft + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40405"));
        add(access, visitId, "{\"content\":\"질문\",\"chatMessageId\":" + info + "}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 출처를_CHATBOT으로_보내도_USER로_저장된다() throws Exception {
        add(access, visitId, "{\"content\":\"질문\",\"source\":\"CHATBOT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("USER"));
    }

    @Test
    void 정렬_순서를_안_보내면_맨_뒤() throws Exception {
        add(access, visitId, "{\"content\":\"첫째\"}").andExpect(jsonPath("$.data.sortOrder").value(0));
        add(access, visitId, "{\"content\":\"둘째\"}").andExpect(jsonPath("$.data.sortOrder").value(1));
    }

    @Test
    void 정렬_순서를_안_보내면_지금_가장_큰_값_다음() throws Exception {
        long big = addAndGetId("{\"content\":\"앞 질문\",\"sortOrder\":10}");
        String body = add(access, visitId, "{\"content\":\"새 질문\"}")
                .andExpect(jsonPath("$.data.sortOrder").value(11))
                .andReturn().getResponse().getContentAsString();
        long added = ((Number) JsonPath.read(body, "$.data.questionId")).longValue();

        list(access, visitId).andExpect(jsonPath("$.data[*].questionId").value(contains((int) big, (int) added)));
    }

    @Test
    void 전각_공백만_있으면_400() throws Exception {
        add(access, visitId, "{\"content\":\"　　\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해 주세요."));
        list(access, visitId).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 길이는_앞뒤_공백을_뺀_뒤로_잰다() throws Exception {
        add(access, visitId, "{\"content\":\"" + " ".repeat(10) + "가".repeat(500) + "\"}")
                .andExpect(status().isCreated());
        add(access, visitId, "{\"content\":\"" + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문은 500자까지 쓸 수 있어요."));
    }

    @Test
    void 목록은_정렬_순서_다음_먼저_만든_순() throws Exception {
        long c = addAndGetId("{\"content\":\"C\",\"sortOrder\":2}");
        long a = addAndGetId("{\"content\":\"A\",\"sortOrder\":0}");
        long b1 = addAndGetId("{\"content\":\"B1\",\"sortOrder\":1}");
        long b2 = addAndGetId("{\"content\":\"B2\",\"sortOrder\":1}");   // 같은 순서면 먼저 만든 것 먼저

        list(access, visitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].questionId").value(contains((int) a, (int) b1, (int) b2, (int) c)));
    }

    @Test
    void 일정_상세에도_질문이_나온다() throws Exception {
        long q = addAndGetId("{\"content\":\"인슐린 시간 바꿔도 되나요?\"}");

        mvc.perform(get("/api/visits/" + visitId).cookie(access))
                .andExpect(jsonPath("$.data.questions", hasSize(1)))
                .andExpect(jsonPath("$.data.questions[0].questionId").value(q))
                .andExpect(jsonPath("$.data.questions[0].content").value("인슐린 시간 바꿔도 되나요?"));
    }

    @Test
    void 내용이_비었거나_너무_길면_400() throws Exception {
        add(access, visitId, "{\"content\":\"   \"}").andExpect(status().isBadRequest());
        add(access, visitId, "{}").andExpect(status().isBadRequest());
        add(access, visitId, "{\"content\":\"" + "가".repeat(501) + "\"}").andExpect(status().isBadRequest());
        add(access, visitId, "{\"content\":\"질문\",\"sortOrder\":-1}").andExpect(status().isBadRequest());
    }

    @Test
    void 지우면_목록과_상세에서_빠지고_또_지워도_처음_시각으로_200() throws Exception {
        long q = addAndGetId("{\"content\":\"지울 질문\"}");

        String first = remove(access, visitId, q)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").value(q))
                .andExpect(jsonPath("$.data.deletedAt").exists())
                .andReturn().getResponse().getContentAsString();
        String deletedAt = JsonPath.read(first, "$.data.deletedAt");

        remove(access, visitId, q)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAt").value(deletedAt));
        list(access, visitId).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/visits/" + visitId).cookie(access))
                .andExpect(jsonPath("$.data.questions", hasSize(0)));
    }

    @Test
    void 같은_질문을_동시에_두_번_지워도_지운_시각은_하나() throws Exception {
        long q = addAndGetId("{\"content\":\"동시에 지울 질문\"}");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> deleteOnce = () -> {
            start.await();
            String body = remove(access, visitId, q).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(body, "$.data.deletedAt");
        };
        Future<String> first = pool.submit(deleteOnce);
        Future<String> second = pool.submit(deleteOnce);
        start.countDown();
        String a = first.get(10, TimeUnit.SECONDS);
        String b = second.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(a).isEqualTo(b);   // 두 응답이 같은 시각
        Instant saved = jdbc.queryForObject("SELECT deleted_at FROM visit_questions WHERE id = ?",
                java.sql.Timestamp.class, q).toInstant();
        assertThat(Instant.parse(a)).isEqualTo(saved);   // DB에 남은 시각도 같음
    }

    @Test
    void 다른_일정의_질문_id로는_못_지운다() throws Exception {
        long q = addAndGetId("{\"content\":\"질문\"}");
        long anotherVisit = createVisit(access);

        remove(access, anotherVisit, q)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40402"));
        remove(access, visitId, 999999999L).andExpect(status().isNotFound());
    }

    @Test
    void 남의_일정이나_지운_일정이면_404() throws Exception {
        long othersVisit = createVisit(other);
        list(access, othersVisit)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));
        add(access, othersVisit, "{\"content\":\"질문\"}").andExpect(status().isNotFound());

        long q = addAndGetId("{\"content\":\"질문\"}");
        remove(other, visitId, q).andExpect(status().isNotFound());   // 남이 내 질문을 지우려 함

        mvc.perform(delete("/api/visits/" + visitId).cookie(access)).andExpect(status().isOk());
        list(access, visitId).andExpect(status().isNotFound());
        add(access, visitId, "{\"content\":\"질문\"}").andExpect(status().isNotFound());
    }

    // ---------- 도우미 ----------

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private long createVisit(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/visits").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    private ResultActions add(Cookie who, long visit, String json) throws Exception {
        return mvc.perform(post("/api/visits/" + visit + "/questions").cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long addAndGetId(String json) throws Exception {
        String body = add(access, visitId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.questionId")).longValue();
    }

    private ResultActions list(Cookie who, long visit) throws Exception {
        return mvc.perform(get("/api/visits/" + visit + "/questions").cookie(who));
    }

    private ResultActions remove(Cookie who, long visit, long question) throws Exception {
        return mvc.perform(delete("/api/visits/" + visit + "/questions/" + question).cookie(who));
    }
}
