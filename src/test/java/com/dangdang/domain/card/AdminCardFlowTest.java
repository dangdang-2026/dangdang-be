package com.dangdang.domain.card;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 카드뉴스 화면 (admin #11): 목록의 검수와 정정 날짜, 운영자 상세 (be #169)
class AdminCardFlowTest extends ReminderTestBase {

    static final String CARD = "{\"title\":\"식후 혈당, 언제 재나요?\",\"series\":\"GUIDELINE\","
            + "\"body\":[{\"title\":\"1장\",\"imageUrl\":\"https://cards.example/cards/a.png\",\"alt\":\"식후 혈당은 정해 준 시간에 잽니다.\"}],"
            + "\"sources\":[{\"title\":\"대한당뇨병학회\",\"url\":\"https://www.diabetes.or.kr\"}],\"checkedOn\":\"2026-11-09\"}";

    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("admin@x.com", "다현", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM admin_logs");
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM content_cards");
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int create() throws Exception {
        String res = postJson("/api/admin/cards", CARD).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    @Test
    void 목록에_검수와_정정_날짜가_온다() throws Exception {
        int id = create();
        mvc.perform(get("/api/admin/cards").cookie(admin))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].reviewer").doesNotExist());

        postJson("/api/admin/cards/" + id + "/review", "{\"reviewer\":\"다현\",\"reviewedOn\":\"2026-11-15\"}")
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());
        clock.set(NOW.plusSeconds(3600));
        postJson("/api/admin/cards/" + id + "/correct", "{\"summary\":\"측정 시간을 고쳤어요\"}").andExpect(status().isOk());

        mvc.perform(get("/api/admin/cards").cookie(admin))
                .andExpect(jsonPath("$.data[0].status").value("CORRECTED"))
                .andExpect(jsonPath("$.data[0].reviewer").value("다현"))
                .andExpect(jsonPath("$.data[0].reviewedOn").value("2026-11-15"))
                .andExpect(jsonPath("$.data[0].correctedAt").value(NOW.plusSeconds(3600).toString()))
                .andExpect(jsonPath("$.data[0].slideCount").value(1));
    }

    @Test
    void 운영자_상세는_쓰는_중과_내린_카드도_내용까지() throws Exception {
        int id = create();
        mvc.perform(get("/api/admin/cards/" + id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.slides[0].imageUrl").value("https://cards.example/cards/a.png"))
                .andExpect(jsonPath("$.data.slides[0].alt").value("식후 혈당은 정해 준 시간에 잽니다."));

        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/cards/" + id).cookie(admin))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.slides[0].title").value("1장"))
                .andExpect(jsonPath("$.data.sources[0].title").value("대한당뇨병학회"));
    }

    @Test
    void 없는_카드는_404_운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/cards/999999").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_40401"));
        mvc.perform(get("/api/admin/cards/1").cookie(login("jisu@x.com"))).andExpect(status().isForbidden());
    }
}
