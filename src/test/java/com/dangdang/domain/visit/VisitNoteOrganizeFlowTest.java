package com.dangdang.domain.visit;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.entity.VisitNoteExtract;
import com.dangdang.domain.visit.entity.VisitNoteExtract.Field;
import com.dangdang.domain.visit.enums.ExtractStatus;
import com.dangdang.domain.visit.infra.VisitNoteOrganizeClient;
import com.dangdang.global.config.ClockConfig;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진료 메모 AI 정리 흐름 테스트. AI 서버는 가짜로 바꿈
@SpringBootTest
@AutoConfigureMockMvc
class VisitNoteOrganizeFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean VisitNoteOrganizeClient organizeClient;   // 진짜 AI 서버 대신

    static final String MEMO = "공복이랑 점심 식후에 재래요. 공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요. 다음 진료는 2주 뒤 금요일이에요.";

    // 명세 예시와 같은 모양의 AI 결과
    static final VisitNoteExtract EXTRACT = new VisitNoteExtract(
            new Field<>(List.of(MeasureSlot.FASTING, MeasureSlot.LUNCH_AFTER), "공복이랑 점심 식후에 재래요.", ExtractStatus.FOUND),
            new Field<>(95, "공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요.", ExtractStatus.FOUND),
            new Field<>(140, "공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요.", ExtractStatus.FOUND),
            null,                                                         // 메모에 없는 칸
            new Field<>(LocalDate.of(2026, 11, 27), "다음 진료는 2주 뒤 금요일이에요.", ExtractStatus.CHECK_NEEDED),
            true,
            "visit-note-v1");

    Cookie access;   // 지수(주인공)
    Cookie other;
    long userId;
    long visitId;
    long noteId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        userId = userRepository.findAll().stream().filter(u -> "jisu@x.com".equals(u.getEmail()))
                .findFirst().orElseThrow().getId();
        visitId = createVisit(access, "{\"visitAt\":\"2026-11-14T10:30:00+09:00\"}");
        noteId = createNote(access, visitId, MEMO);
        consent(access, "OVERSEAS_TRANSFER", true);
    }

    @AfterEach
    void clean() { // users를 지우면 visits·visit_notes도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 정리하면_칸마다_값과_근거와_상태를_돌려주고_메모에_저장한다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);

        organize(access, visitId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteId").value(noteId))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.value").value(95))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.evidence").value("공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요."))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.status").value("FOUND"))
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.value").value(contains("FASTING", "LUNCH_AFTER")))
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.status").value("FOUND"))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes").value(nullValue()))
                .andExpect(jsonPath("$.data.aiExtract.nextVisitDate.value").value("2026-11-27"))
                .andExpect(jsonPath("$.data.aiExtract.nextVisitDate.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.insulinSkipped").value(true))
                .andExpect(jsonPath("$.data.aiExtract.promptVersion").value("visit-note-v1"));

        String saved = jdbc.queryForObject("SELECT ai_extract::text FROM visit_notes WHERE id = ?", String.class, noteId);
        assertThat((Integer) JsonPath.read(saved, "$.fastingTarget.value")).isEqualTo(95);
        assertThat((String) JsonPath.read(saved, "$.nextVisitDate.status")).isEqualTo("CHECK_NEEDED");
        assertThat(usedToday()).isEqualTo("1");   // 오늘 1번 씀
        // AI 정리만 했으니 사용자가 고친 게 아님 → updatedAt은 그대로 (화면에 「수정됨」이 안 뜨게)
        assertThat(jdbc.queryForObject("SELECT updated_at = created_at FROM visit_notes WHERE id = ?", Boolean.class, noteId)).isTrue();
    }

    @Test
    void 근거가_메모에_없거나_다음_진료일이_지난_날짜면_확인_필요로_잴_때는_겹침을_빼고_하루_순서로() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                new Field<>(java.util.Arrays.asList(MeasureSlot.LUNCH_AFTER, MeasureSlot.FASTING, MeasureSlot.LUNCH_AFTER),
                        "…점심 식후에 재래요.", ExtractStatus.FOUND),                              // 「…」로 줄인 근거도 메모에 있으면 OK
                new Field<>(100, "공복은 100 미만이래요.", ExtractStatus.FOUND),                   // 메모에 없는 근거 → 확인 필요
                new Field<>(140, "식후는   140 미만으로\n보자고 하셨어요.", ExtractStatus.FOUND),  // 공백·줄바꿈만 다르면 같은 문장
                null,
                new Field<>(LocalDate.of(2026, 11, 7), "다음 진료는 2주 뒤 금요일이에요.", ExtractStatus.FOUND),   // 진료일(11/14)보다 앞 → 확인 필요
                false, "visit-note-v1"));

        organize(access, visitId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.value").value(contains("FASTING", "LUNCH_AFTER")))
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.status").value("FOUND"))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.value").value(140))
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.status").value("CHECK_NEEDED"))   // 근거는 맞지만 식후 시점이 없어서 (짝)
                .andExpect(jsonPath("$.data.aiExtract.nextVisitDate.status").value("CHECK_NEEDED"));
    }

    @Test
    void 식후_시점과_목표는_짝이라_하나만_찾으면_둘_다_확인_필요() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                null, null,
                new Field<>(140, "공복은 95 미만, 식후는 140 미만으로 보자고 하셨어요.", ExtractStatus.FOUND),   // 목표만
                null, null, false, "visit-note-v1"));

        organize(access, visitId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.value").value(140))
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes").value(nullValue()));
    }

    @Test
    void 식후_시점과_목표를_둘_다_찾으면_그대로_하나라도_확인_필요면_둘_다_확인_필요() throws Exception {
        String memo = "식후 1시간에 재고 식후는 120 미만으로 보자고 하셨어요.";
        long note = createNote(access, visitId, memo);
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                null, null,
                new Field<>(120, "식후는 120 미만으로 보자고 하셨어요.", ExtractStatus.FOUND),
                new Field<>(60, "식후 1시간에 재고", ExtractStatus.FOUND),
                null, false, "visit-note-v1"));

        organize(access, visitId, note)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.status").value("FOUND"))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes.value").value(60))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes.status").value("FOUND"));

        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                null, null,
                new Field<>(120, "식후는 120 미만으로 보자고 하셨어요.", ExtractStatus.FOUND),
                new Field<>(60, "식후 1시간에 재고", ExtractStatus.CHECK_NEEDED),   // AI가 확신 못 함
                null, false, "visit-note-v1"));

        organize(access, visitId, note)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.value").value(120))
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes.status").value("CHECK_NEEDED"));
    }

    @Test
    void 잴_때에_모르는_값이_섞이면_빼고_확인_필요로() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                new Field<>(java.util.Arrays.asList(MeasureSlot.FASTING, null), "공복이랑 점심 식후에 재래요.", ExtractStatus.FOUND),
                null, null, null, null, false, "visit-note-v1"));

        organize(access, visitId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.value").value(contains("FASTING")))
                .andExpect(jsonPath("$.data.aiExtract.measureSlots.status").value("CHECK_NEEDED"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void AI_응답을_정리하다_실패해도_502이고_오늘_횟수를_돌려준다() throws Exception {
        // 숫자 칸에 글자가 들어온 이상한 응답 → 정리 중 예외
        Field broken = new Field("아흔다섯", "공복은 95 미만", ExtractStatus.FOUND);
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                null, broken, null, null, null, false, "visit-note-v1"));

        organize(access, visitId, noteId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("VISIT_50201"));
        assertThat(usedToday()).isEqualTo("0");
    }

    @Test
    void AI에는_메모와_한국_기준_진료일을_보낸다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);

        organize(access, visitId, noteId).andExpect(status().isOk());
        verify(organizeClient).organize(anyString(), eq(MEMO), eq(LocalDate.of(2026, 11, 14)));
    }

    @Test
    void 진료_날짜가_미정이면_오늘을_진료일로_보낸다() throws Exception {
        long undecided = createVisit(access, "{}");
        long note = createNote(access, undecided, MEMO);
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);

        organize(access, undecided, note).andExpect(status().isOk());
        verify(organizeClient).organize(anyString(), eq(MEMO), eq(LocalDate.now(ClockConfig.KST)));
    }

    @Test
    void 국외_이전_동의가_없으면_403이고_AI를_부르지_않는다() throws Exception {
        consent(access, "OVERSEAS_TRANSFER", false);

        organize(access, visitId, noteId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AI_40301"));
        verify(organizeClient, never()).organize(any(), any(), any());
    }

    @Test
    void 하루_10번을_넘으면_429() throws Exception {
        redis.opsForValue().set(usageKey(), "10");   // 오늘 이미 10번

        organize(access, visitId, noteId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI_42901"));
        verify(organizeClient, never()).organize(any(), any(), any());
        assertThat(usedToday()).isEqualTo("10");
    }

    @Test
    void AI_서버가_실패하면_502이고_오늘_횟수를_돌려준다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willThrow(new ResourceAccessException("timeout"));

        organize(access, visitId, noteId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("VISIT_50201"));
        assertThat(usedToday()).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT ai_extract FROM visit_notes WHERE id = ?", String.class, noteId)).isNull();
    }

    @Test
    void AI_답이_비었으면_502() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(null);

        organize(access, visitId, noteId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("VISIT_50201"));
        assertThat(usedToday()).isEqualTo("0");
    }

    @Test
    void 메모가_3000자를_넘으면_400이고_횟수를_안_쓴다() throws Exception {
        long longNote = createNote(access, visitId, "가".repeat(3001));

        organize(access, visitId, longNote)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40002"));
        verify(organizeClient, never()).organize(any(), any(), any());
        assertThat(redis.opsForValue().get(usageKey())).isNull();
    }

    @Test
    void 정리하는_동안_메모가_바뀌면_409이고_저장하지_않는다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willAnswer(inv -> {
            // AI를 기다리는 사이 다른 기기에서 메모를 고친 상황
            jdbc.update("UPDATE visit_notes SET content = '고친 메모' WHERE id = ?", noteId);
            return EXTRACT;
        });

        organize(access, visitId, noteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40901"));
        assertThat(jdbc.queryForObject("SELECT ai_extract FROM visit_notes WHERE id = ?", String.class, noteId)).isNull();
        assertThat(usedToday()).isEqualTo("0");   // 결과를 못 받았으니 오늘 횟수를 돌려줌
    }

    @Test
    void 정리하는_동안_앞뒤_공백만_바뀌면_같은_메모로_보고_저장한다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willAnswer(inv -> {
            jdbc.update("UPDATE visit_notes SET content = ? WHERE id = ?", MEMO + "\n\n", noteId);
            return EXTRACT;
        });

        organize(access, visitId, noteId).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ai_extract FROM visit_notes WHERE id = ?", String.class, noteId)).isNotNull();
        assertThat(usedToday()).isEqualTo("1");
    }

    @Test
    void AI_응답의_빈_칸은_null로_상태가_없거나_범위_밖이면_확인_필요로() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(new VisitNoteExtract(
                new Field<>(List.of(), "…", ExtractStatus.FOUND),                  // 빈 목록 → 칸 없음
                new Field<>(95, "공복은 95 미만", null),                            // 상태 없음 → 확인 필요
                new Field<>(null, "식후는…", ExtractStatus.FOUND),                  // 값 없음 → 칸 없음
                new Field<>(999, "식후 999분", ExtractStatus.FOUND),                // 범위 밖 → 확인 필요
                new Field<>(LocalDate.of(2026, 11, 27), "2주 뒤", ExtractStatus.FOUND),   // 정상 → 그대로
                false, "visit-note-v1"));

        organize(access, visitId, noteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.measureSlots").value(nullValue()))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.value").value(95))
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.postMealTarget").value(nullValue()))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes.value").value(999))
                .andExpect(jsonPath("$.data.aiExtract.postMealMinutes.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.aiExtract.nextVisitDate.status").value("FOUND"));
        // 저장된 것도 정리된 모양
        mvc.perform(get("/api/visits/" + visitId + "/notes").cookie(access))
                .andExpect(jsonPath("$.data[0].aiExtract.fastingTarget.status").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data[0].aiExtract.measureSlots").value(nullValue()));
    }

    @Test
    void 정리_결과는_메모_조회와_일정_상세에도_나온다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);
        organize(access, visitId, noteId).andExpect(status().isOk());

        mvc.perform(get("/api/visits/" + visitId + "/notes").cookie(access))
                .andExpect(jsonPath("$.data[0].aiExtract.fastingTarget.value").value(95))
                .andExpect(jsonPath("$.data[0].aiExtract.nextVisitDate.value").value("2026-11-27"))   // DB에서 읽어도 날짜 글자로
                .andExpect(jsonPath("$.data[0].aiExtract.postMealMinutes").value(nullValue()));
        mvc.perform(get("/api/visits/" + visitId).cookie(access))
                .andExpect(jsonPath("$.data.notes[0].aiExtract.insulinSkipped").value(true));
    }

    @Test
    void 정리한_적_없는_메모는_aiExtract가_null() throws Exception {
        mvc.perform(get("/api/visits/" + visitId + "/notes").cookie(access))
                .andExpect(jsonPath("$.data[0].aiExtract").value(nullValue()));
    }

    @Test
    void 메모_내용을_고치면_정리_결과가_비워진다() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);
        organize(access, visitId, noteId).andExpect(status().isOk());

        updateNote(noteId, "다른 내용으로 고친 메모")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT ai_extract FROM visit_notes WHERE id = ?", String.class, noteId)).isNull();
    }

    @Test
    void 앞뒤_공백만_바꿔_고치면_정리_결과는_그대로() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);
        organize(access, visitId, noteId).andExpect(status().isOk());

        updateNote(noteId, "  " + MEMO + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.value").value(95));
    }

    @Test
    void 같은_내용을_다시_보내면_정리_결과는_그대로() throws Exception {
        given(organizeClient.organize(anyString(), any(), any())).willReturn(EXTRACT);
        organize(access, visitId, noteId).andExpect(status().isOk());

        updateNote(noteId, MEMO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiExtract.fastingTarget.value").value(95));
    }

    @Test
    void 남의_일정이나_다른_일정의_메모면_404() throws Exception {
        long othersVisit = createVisit(other, "{}");
        long othersNote = createNote(other, othersVisit, MEMO);
        organize(access, othersVisit, othersNote)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40401"));

        long anotherVisit = createVisit(access, "{}");
        organize(access, anotherVisit, noteId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VISIT_40403"));
        verify(organizeClient, never()).organize(any(), any(), any());
    }

    // ---------- 도우미 ----------

    private String usageKey() {
        return "ai:usage:VISIT_NOTE:" + userId + ":" + LocalDate.now(ClockConfig.KST);
    }

    private String usedToday() {
        String v = redis.opsForValue().get(usageKey());
        return v == null ? "0" : v;
    }

    private Cookie signupAndLogin(String email) throws Exception {
        grantSensitive(jdbc,
                userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now())));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private void consent(Cookie who, String type, boolean granted) throws Exception {
        mvc.perform(post("/api/me/consents").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"" + type + "\",\"granted\":" + granted + "}]}"))
                .andExpect(status().isOk());
    }

    private long createVisit(Cookie who, String json) throws Exception {
        String body = mvc.perform(post("/api/visits").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.visitId")).longValue();
    }

    private long createNote(Cookie who, long visit, String content) throws Exception {
        String body = mvc.perform(post("/api/visits/" + visit + "/notes").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.noteId")).longValue();
    }

    private ResultActions updateNote(long note, String content) throws Exception {
        return mvc.perform(patch("/api/visits/" + visitId + "/notes/" + note).cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"));
    }

    private ResultActions organize(Cookie who, long visit, long note) throws Exception {
        return mvc.perform(post("/api/visits/" + visit + "/notes/" + note + "/organize").cookie(who));
    }
}
