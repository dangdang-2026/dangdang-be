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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 카드뉴스에서 담은 질문의 출처 카드 (U-28 「물어볼 것에 적기」, U-26c 「출처 카드가 내려갔어요」)
@SpringBootTest
@AutoConfigureMockMvc
class VisitQuestionCardSourceFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    static final String CARD_QUESTION = "식후 2시간에도 재야 하나요?";

    Cookie access;   // 지수
    long userId;
    long visitId;

    @BeforeEach
    void setUp() throws Exception {
        User user = grantSensitive(jdbc,
                userRepository.save(User.ofEmail("jisu@x.com", "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
        userId = user.getId();
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
        String body = mvc.perform(post("/api/visits").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        visitId = ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    @AfterEach
    void clean() {   // users를 먼저 지워야 visit_questions가 같이 지워지고(CASCADE) 카드를 지울 수 있음
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM content_cards");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 카드_질문을_그대로_담으면_출처는_CARD이고_카드_번호가_남는다() throws Exception {
        long card = card("PUBLISHED");

        add("{\"content\":\" 식후 2시간에도   재야 하나요? \",\"cardId\":" + card + "}")   // 앞뒤 공백, 띄어쓰기 차이는 봐줌
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("CARD"))
                .andExpect(jsonPath("$.data.sourceCardId").value(card))
                .andExpect(jsonPath("$.data.sourceCardWithdrawn").value(false));

        assertThat(jdbc.queryForObject("SELECT source_card_id FROM visit_questions WHERE source = 'CARD'", Long.class))
                .isEqualTo(card);
    }

    @Test
    void 카드_질문을_고쳐_적으면_출처는_USER이고_카드_번호는_없다() throws Exception {
        long card = card("PUBLISHED");

        add("{\"content\":\"식후 1시간에 재도 되나요?\",\"cardId\":" + card + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("USER"))
                .andExpect(jsonPath("$.data.sourceCardId").doesNotExist())
                .andExpect(jsonPath("$.data.sourceCardWithdrawn").value(false));
    }

    @Test
    void 정정된_카드는_바뀐_질문으로_확인한다() throws Exception {
        long card = card("CORRECTED");
        jdbc.update("UPDATE content_cards SET questions = '[\"공복 혈당 목표가 바뀌었나요?\"]'::jsonb WHERE id = ?", card);

        add("{\"content\":\"공복 혈당 목표가 바뀌었나요?\",\"cardId\":" + card + "}")
                .andExpect(jsonPath("$.data.source").value("CARD"));
        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":" + card + "}")   // 정정 전 질문
                .andExpect(jsonPath("$.data.source").value("USER"));
    }

    @Test
    void 카드를_내리면_목록과_일정_상세에서_sourceCardWithdrawn이_true() throws Exception {
        long card = card("PUBLISHED");
        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":" + card + "}").andExpect(status().isCreated());
        add("{\"content\":\"직접 적은 질문\"}").andExpect(status().isCreated());

        jdbc.update("UPDATE content_cards SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", card);

        mvc.perform(get("/api/visits/" + visitId + "/questions").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].source").value("CARD"))
                .andExpect(jsonPath("$.data[0].sourceCardId").value(card))      // 내려가도 번호는 그대로
                .andExpect(jsonPath("$.data[0].sourceCardWithdrawn").value(true))
                .andExpect(jsonPath("$.data[1].source").value("USER"))
                .andExpect(jsonPath("$.data[1].sourceCardWithdrawn").value(false));
        mvc.perform(get("/api/visits/" + visitId).cookie(access))
                .andExpect(jsonPath("$.data.questions[0].sourceCardWithdrawn").value(true));
    }

    @Test
    void 없는_카드나_올라가_있지_않은_카드면_404이고_저장하지_않는다() throws Exception {
        long withdrawn = card("WITHDRAWN");
        long draft = card("DRAFT");

        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":" + withdrawn + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40406"));
        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":" + draft + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40406"));
        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":999999999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40406"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_questions", Long.class)).isZero();
    }

    @Test
    void 대화_번호와_카드_번호를_같이_보내면_400() throws Exception {
        long card = card("PUBLISHED");

        add("{\"content\":\"" + CARD_QUESTION + "\",\"chatMessageId\":1,\"cardId\":" + card + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("당당이 대화와 카드 중 하나만 보내 주세요."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_questions", Long.class)).isZero();
    }

    @Test
    void 건강정보_동의가_없으면_카드_질문도_403() throws Exception {
        long card = card("PUBLISHED");
        jdbc.update("DELETE FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE'", userId);

        add("{\"content\":\"" + CARD_QUESTION + "\",\"cardId\":" + card + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_questions", Long.class)).isZero();
    }

    @Test
    void DB는_CARD가_아닌_질문에_카드_번호를_못_넣게_막는다() {
        long card = card("PUBLISHED");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO visit_questions (visit_id, content, source, source_card_id) VALUES (?, '질문', 'USER', ?)",
                        visitId, card))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- 도우미 ----------

    // 카드 한 장을 DB에 바로 넣는다 (운영자 API 없이). 질문은 CARD_QUESTION 하나
    private long card(String status) {
        return jdbc.queryForObject("INSERT INTO content_cards (title, body, sources, checked_on, status, questions)"
                + " VALUES ('식후 혈당 재는 때', '[]', '[]', DATE '2026-11-20', ?, jsonb_build_array(?::text))"
                + " RETURNING id", Long.class, status, CARD_QUESTION);
    }

    private ResultActions add(String json) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/questions").cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
