package com.dangdang.domain.chat;

import com.dangdang.domain.chat.infra.ChatClient;
import com.dangdang.domain.chat.service.ChatService;
import com.dangdang.domain.user.service.SensitiveConsentWithdrawalService;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 당당이에게 물어보기 (Q-01~Q-08) + AI 문지기 (SYS-03) + AI 동의 철회 (M-12). 고정 시계: 한국 2026-11-09 13:00
class ChatFlowTest extends ReminderTestBase {

    @MockitoBean ChatClient chatClient;   // 진짜 AI 서버 대신
    @Autowired SensitiveConsentWithdrawalService withdrawalService;
    @Autowired ChatService chatService;

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    private void consent(Cookie who, String type, boolean granted) throws Exception {
        mvc.perform(post("/api/me/consents").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"" + type + "\",\"granted\":" + granted + "}]}"))
                .andExpect(status().isOk());
    }

    private void agreeAll(Cookie who) throws Exception {   // Q-01 「동의」 버튼
        consent(who, "OVERSEAS_TRANSFER", true);
        consent(who, "AI_CHAT_RECORDS", true);
    }

    private org.springframework.test.web.servlet.ResultActions ask(Cookie who, String content) throws Exception {
        return mvc.perform(post("/api/chat/messages").cookie(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    private void reading(Cookie who, int value, String measuredAt, String type, String slot) throws Exception {
        mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt + "\",\"readingType\":\""
                                + type + "\"" + (slot == null ? "" : ",\"slot\":\"" + slot + "\"") + "}"))
                .andExpect(status().isCreated());
    }

    private static ChatClient.Reply reply(String kind, String text) {
        return new ChatClient.Reply(kind, text, null, null, null, null, "chat-v1");
    }

    private static ChatClient.Reply unknown(String reason, String text) {
        return new ChatClient.Reply("UNKNOWN", text, null, null, null, reason, "chat-v3");
    }

    private int messageCount() {
        return jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class);
    }

    // ── 문지기 ──

    @Test
    void 국외_이전_동의가_없으면_일반_질문도_403이고_AI를_부르지_않는다() throws Exception {   // Q-01 「필수」 (11/24)
        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AI_40301"));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
        assertThat(messageCount()).isZero();
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isNull();
    }

    // ── 건강정보 동의 (M-07b 철회 뒤 저장 막기) ──

    @Test
    void 건강정보_동의가_없으면_위험_신호도_403이고_아무것도_안_남긴다() throws Exception {
        agreeAll(jisu);
        jdbc.update("DELETE FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE'", jisuId);

        ask(jisu, "피가 비쳐요")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
        assertThat(messageCount()).isZero();
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isNull();
    }

    @Test
    void 철회_뒤_기록_사용_동의만_다시_켜도_질문은_403() throws Exception {
        agreeAll(jisu);
        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());
        consent(jisu, "AI_CHAT_RECORDS", true);

        ask(jisu, "이번 주 공복 혈당 보여줘")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
    }

    @Test
    void AI를_기다리는_사이_철회하면_답을_남기지_않고_403() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willAnswer(invocation -> {
            withdrawalService.withdraw(jisuId);
            return reply("UNKNOWN", null);
        });

        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        assertThat(messageCount()).isZero();   // 질문은 철회가 지웠고, 답은 저장 안 함
    }

    @Test
    void AI를_기다리는_사이_대화를_지웠으면_답은_보여_주되_남기지_않는다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willAnswer(invocation -> {
            chatService.deleteAll(jisuId);
            return reply("UNKNOWN", null);
        });

        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.answer.id").value(nullValue()));
        assertThat(messageCount()).isZero();
    }

    @Test
    void 하루_30번을_넘으면_429() throws Exception {
        agreeAll(jisu);
        redis.opsForValue().set("ai:usage:CHAT:" + jisuId + ":2026-11-09", "30");   // 오늘 이미 30번

        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AI_42901"));
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isEqualTo("30");
    }

    @Test
    void 성공하면_오늘_횟수가_하나_는다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", null));

        ask(jisu, "아무 말").andExpect(status().isCreated());
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isEqualTo("1");
    }

    @Test
    void AI_서버가_실패하면_FAILED이고_횟수에_안_센다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willThrow(new ResourceAccessException("연결 거부"));

        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.kind").value("FAILED"))
                .andExpect(jsonPath("$.data.answer.content").value("지금은 답을 만들지 못했어요. 잠시 뒤 다시 물어봐 주세요."));
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isEqualTo("0");
    }

    @Test
    void 일반_질문만_고르면_국외_이전만_동의하고_기록은_안_쓴다() throws Exception {   // Q-02b
        consent(jisu, "OVERSEAS_TRANSFER", true);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", null));

        ask(jisu, "어제 혈당 어땠어?").andExpect(status().isCreated());
        verify(chatClient).ask(anyString(), anyString(), eq(false), any());
    }

    @Test
    void 기록_사용_동의를_철회하면_기록_없이_답하고_지난_대화는_그대로() throws Exception {   // M-12
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", null));
        ask(jisu, "첫 질문").andExpect(status().isCreated());

        consent(jisu, "AI_CHAT_RECORDS", false);

        ask(jisu, "두 번째 질문").andExpect(status().isCreated());
        verify(chatClient).ask(anyString(), eq("두 번째 질문"), eq(false), any());
        assertThat(messageCount()).isEqualTo(4);   // 철회해도 지난 대화는 지우지 않는다 (지우기는 따로)
    }

    // ── 위험 신호 (AI 안 감) ──

    @Test
    void 위험_신호는_동의가_없어도_정해진_안내와_전화_버튼() throws Exception {
        ask(jisu, "아까부터 피가 비쳐요")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL", "CALL_119")));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
    }

    @Test
    void 심한_두통도_위험_신호() throws Exception {
        ask(jisu, "어제부터 두통이 너무 심해요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"));
    }

    @Test
    void 마음이_힘든_말은_109_안내() throws Exception {
        ask(jisu, "요즘 그냥 사라지고 싶어요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("MIND"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_109", "CALL_1577_0199", "CALL_119")));
    }

    @Test
    void 인슐린_용량_질문은_AI에_안_보내고_병원_안내() throws Exception {
        agreeAll(jisu);
        ask(jisu, "인슐린 몇 단위 맞아야 해요?")
                .andExpect(jsonPath("$.data.answer.kind").value("DOSE"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL")));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isNull();
    }

    // ── 위급 안내 한 틀 (Q-07 · Q-07b · Q-07c) ──

    @Test
    void 위급_안내는_제목_본문_빨간_줄을_따로_준다() throws Exception {
        ask(jisu, "배가 자꾸 뭉치고 피가 조금 비쳐요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("URGENT"))
                .andExpect(jsonPath("$.data.answer.risk.title").value("지금은 병원에 먼저 연락해 주세요"))
                .andExpect(jsonPath("$.data.answer.risk.warning").value("피가 많이 나거나, 어지럽거나, 의식이 흐려지면 바로 119"))
                .andExpect(jsonPath("$.data.answer.content").value(containsString("태동")))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL", "CALL_119")))
                .andExpect(jsonPath("$.data.answer.reason").value(nullValue()));
    }

    @Test
    void 저혈당_말은_저혈당_안내와_병원_119() throws Exception {
        ask(jisu, "식은땀이 나고 손이 떨려요. 재 보니 62예요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("HYPO"))
                .andExpect(jsonPath("$.data.answer.risk.title").value("병원에서 안내받은 저혈당 대처를 먼저 해 주세요"))
                .andExpect(jsonPath("$.data.answer.risk.warning").value("어지럽거나 의식이 흐려지면 바로 119"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL", "CALL_119")));
        ask(jisu, "방금 혈당 58 나왔어요").andExpect(jsonPath("$.data.answer.risk.type").value("HYPO"));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
    }

    @Test
    void 혈당_140이나_날짜_숫자는_저혈당이_아니다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", null));

        ask(jisu, "식후 혈당 140 넘으면 어떡해요?").andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"));
        ask(jisu, "혈당 10월 1일부터 보여 줘").andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"));
        verify(chatClient, times(2)).ask(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void 산후에는_태동_없는_위급_안내() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-11-01', 'POSTPARTUM', now(), now())", jisuId);

        ask(jisu, "피가 많이 나요")
                .andExpect(jsonPath("$.data.answer.risk.type").value("URGENT"))
                .andExpect(jsonPath("$.data.answer.content").value(not(containsString("태동"))));
        ask(minji, "피가 많이 나요")                                   // 임신 정보 없음 → 임신 중 문구
                .andExpect(jsonPath("$.data.answer.content").value(containsString("태동")));
    }

    @Test
    void 다른_상황_PAUSED도_태동_없는_위급_안내() throws Exception {   // 무슨 일인지 모르니 태동 이야기를 꺼내지 않는다
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-12-20', 'PAUSED', now(), now())", jisuId);

        ask(jisu, "피가 많이 나요")
                .andExpect(jsonPath("$.data.answer.risk.type").value("URGENT"))
                .andExpect(jsonPath("$.data.answer.content").value(not(containsString("태동"))));
    }

    @Test
    void 다_그만두고_싶다는_말도_마음_안내와_119() throws Exception {
        ask(jisu, "요즘 너무 힘들어요. 다 그만두고 싶어요")
                .andExpect(jsonPath("$.data.answer.risk.type").value("MIND"))
                .andExpect(jsonPath("$.data.answer.risk.title").value("혼자 견디지 않아도 돼요"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_109", "CALL_1577_0199", "CALL_119")));
    }

    @Test
    void 폭력은_112_1366_119() throws Exception {
        ask(jisu, "남편이 때려")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("SAFETY"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_112", "CALL_1366", "CALL_119")));
        verify(chatClient, never()).ask(any(), any(), anyBoolean(), any());
    }

    @Test
    void 약_용량은_위급_틀이_아니다() throws Exception {
        ask(jisu, "인슐린 몇 단위 맞아야 해요?")
                .andExpect(jsonPath("$.data.answer.kind").value("DOSE"))
                .andExpect(jsonPath("$.data.answer.risk").value(nullValue()));
        ask(jisu, "인슐린 깜빡하고 안 맞았어")
                .andExpect(jsonPath("$.data.answer.kind").value("DOSE"));
    }

    // ── 답할 수 없는 이유 (버튼 고르기) ──

    @Test
    void 답할_수_없으면_이유를_넘기고_글은_AI_글_그대로() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willReturn(unknown("RUDE", "도움이 못 돼서 죄송해요."));

        ask(jisu, "바보")
                .andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.answer.reason").value("RUDE"))
                .andExpect(jsonPath("$.data.answer.content").value("도움이 못 돼서 죄송해요."))
                .andExpect(jsonPath("$.data.answer.risk").value(nullValue()));
    }

    @Test
    void 이상한_이유나_이유_없음은_NO_SOURCE() throws Exception {   // 예전 AI(reason 없음)여도 동작
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willReturn(unknown("WHATEVER", null), unknown(null, null));

        ask(jisu, "첫 질문")
                .andExpect(jsonPath("$.data.answer.reason").value("NO_SOURCE"))
                .andExpect(jsonPath("$.data.answer.content").value("그 내용은 당당이가 가진 자료에 없어요. 다니는 병원에 물어봐 주세요."));
        ask(jisu, "둘째 질문").andExpect(jsonPath("$.data.answer.reason").value("NO_SOURCE"));
    }

    @Test
    void AI가_위급이나_위기를_고르면_키워드와_같은_위급_안내() throws Exception {   // 키워드가 놓친 말
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willReturn(unknown("URGENT", "AI 예비 글"), unknown("CRISIS", "AI 예비 글"),
                        unknown("SAFETY", "AI 예비 글"));

        ask(jisu, "몸이 너무 이상해요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("URGENT"))
                .andExpect(jsonPath("$.data.answer.content").value(containsString("분만실")))   // AI 글이 아니라 BE 문장
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL", "CALL_119")))
                .andExpect(jsonPath("$.data.answer.reason").value(nullValue()));
        ask(jisu, "아무 의미가 없어요")
                .andExpect(jsonPath("$.data.answer.risk.type").value("MIND"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_109", "CALL_1577_0199", "CALL_119")));
        ask(jisu, "집에 가기 무서워요")
                .andExpect(jsonPath("$.data.answer.risk.type").value("SAFETY"))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_112", "CALL_1366", "CALL_119")));
        // AI를 불렀으니 횟수에 센다
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isEqualTo("3");
    }

    @Test
    void AI가_저혈당을_고르면_저혈당_안내이고_예전_AI처럼_모르는_이유면_NO_SOURCE() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willReturn(unknown("HYPO", "AI 예비 글"), unknown("SOMETHING_NEW", "AI 예비 글"));

        ask(jisu, "몸이 이상하게 떨리는 것 같아요")
                .andExpect(jsonPath("$.data.answer.kind").value("RISK"))
                .andExpect(jsonPath("$.data.answer.risk.type").value("HYPO"))
                .andExpect(jsonPath("$.data.answer.content").value(not(containsString("분만실"))))
                .andExpect(jsonPath("$.data.answer.actions", contains("CALL_HOSPITAL", "CALL_119")));
        ask(jisu, "다음 질문")
                .andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.answer.reason").value("NO_SOURCE"))
                .andExpect(jsonPath("$.data.answer.content").value("AI 예비 글"));
    }

    @Test
    void AI가_고른_위급_안내도_다음_질문의_AI_기록에_안_들어간다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willReturn(unknown("URGENT", "AI 예비 글"), reply("UNKNOWN", null));

        ask(jisu, "몸이 너무 이상해요");
        ask(jisu, "다음 질문");

        verify(chatClient).ask(anyString(), eq("다음 질문"), anyBoolean(), eq(List.of()));
    }

    @Test
    void 예전_대화_줄은_이유_없이_읽히고_모르는_칸이나_이름이_있어도_200() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("QUESTION_DRAFT", "예전 답"));
        ask(jisu, "첫 질문");
        ask(jisu, "둘째 질문");
        List<Long> answers = jdbc.queryForList(
                "SELECT id FROM chat_messages WHERE role = 'ASSISTANT' ORDER BY id", Long.class);
        // 예전 줄: reason·risk 칸이 아예 없음 / 나중에 생길 줄: 모르는 칸과 지금 없는 이유 이름
        jdbc.update("UPDATE chat_messages SET payload = '{\"questionDraft\":\"x\"}'::jsonb WHERE id = ?",
                answers.get(0));
        jdbc.update("UPDATE chat_messages SET payload = '{\"questionDraft\":\"y\",\"future\":1,\"reason\":\"GONE\"}'::jsonb"
                + " WHERE id = ?", answers.get(1));

        mvc.perform(get("/api/chat/messages").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].questionDraft").value("y"))
                .andExpect(jsonPath("$.data.items[0].reason").value("NO_SOURCE"))   // 모르는 이름 → NO_SOURCE
                .andExpect(jsonPath("$.data.items[0].risk").value(nullValue()))
                .andExpect(jsonPath("$.data.items[2].questionDraft").value("x"))
                .andExpect(jsonPath("$.data.items[2].reason").value(nullValue()))
                .andExpect(jsonPath("$.data.items[2].risk").value(nullValue()));
    }

    // ── 내 기록 (Q-03) ──

    @Test
    void 기록_카드는_Spring이_세서_보여_준다() throws Exception {
        agreeAll(jisu);
        reading(jisu, 92, "2026-11-05T07:10:00+09:00", "FASTING", "FASTING");
        reading(jisu, 101, "2026-11-08T07:05:00+09:00", "FASTING", "FASTING");
        reading(jisu, 135, "2026-11-08T13:30:00+09:00", "POST_MEAL", "LUNCH_AFTER");  // 다른 시점
        reading(jisu, 88, "2026-10-20T07:00:00+09:00", "FASTING", "FASTING");          // 기간 밖
        reading(minji, 99, "2026-11-06T07:00:00+09:00", "FASTING", "FASTING");         // 남의 것
        given(chatClient.ask(anyString(), anyString(), eq(true), any())).willReturn(new ChatClient.Reply(
                "RECORDS", "지난 7일 공복 혈당 기록이에요.",
                new ChatClient.Records(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9), "FASTING"),
                null, null, null, "chat-v1"));

        ask(jisu, "지난주 공복 혈당 보여 줘")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question.content").value("지난주 공복 혈당 보여 줘"))
                .andExpect(jsonPath("$.data.answer.kind").value("RECORDS"))
                .andExpect(jsonPath("$.data.answer.records.from").value("2026-11-03"))
                .andExpect(jsonPath("$.data.answer.records.slot").value("FASTING"))
                .andExpect(jsonPath("$.data.answer.records.count").value(2))
                .andExpect(jsonPath("$.data.answer.records.items[0].value").value(92))
                .andExpect(jsonPath("$.data.answer.records.items[1].value").value(101));
    }

    @Test
    void 미래_날짜와_너무_긴_기간은_잘라서_보여_준다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "RECORDS", null,
                new ChatClient.Records(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null),
                null, null, null, "chat-v1"));

        ask(jisu, "올해 기록 다 보여 줘")
                .andExpect(jsonPath("$.data.answer.content").value("요청하신 기록이에요."))
                .andExpect(jsonPath("$.data.answer.records.to").value("2026-11-09"))       // 오늘까지
                .andExpect(jsonPath("$.data.answer.records.from").value("2026-10-10"))     // 31일
                .andExpect(jsonPath("$.data.answer.records.slot").value(nullValue()));
    }

    @Test
    void 기록_사용_동의가_없으면_기록_대신_동의_안내() throws Exception {
        consent(jisu, "OVERSEAS_TRANSFER", true);       // Q-01 「비동의」 = 일반 질문만
        given(chatClient.ask(anyString(), anyString(), eq(false), any())).willReturn(new ChatClient.Reply(
                "RECORDS", null, new ChatClient.Records(null, null, null), null, null, null, "chat-v1"));

        ask(jisu, "내 기록 보여 줘")
                .andExpect(jsonPath("$.data.answer.kind").value("CONSENT_NEEDED"))
                .andExpect(jsonPath("$.data.answer.records").value(nullValue()));
        verify(chatClient).ask(anyString(), anyString(), eq(false), any());   // AI에도 기록 못 쓴다고 알렸다
    }

    // ── 물어볼 말 · 일반 정보 ──

    @Test
    void 물어볼_말_초안() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "QUESTION_DRAFT", "진료 때 이렇게 물어보면 어떨까요?", null,
                "아침 공복 혈당이 95 넘게 나온 날이 있었는데, 저녁 식사를 바꿔야 할까요?", null, null, "chat-v1"));

        ask(jisu, "공복 혈당 높을 때 뭐 물어보지")
                .andExpect(jsonPath("$.data.answer.kind").value("QUESTION_DRAFT"))
                .andExpect(jsonPath("$.data.answer.questionDraft")
                        .value("아침 공복 혈당이 95 넘게 나온 날이 있었는데, 저녁 식사를 바꿔야 할까요?"));
    }

    @Test
    void 출처가_있는_일반_정보() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "INFO", "임신성 당뇨는 대부분 출산 뒤 좋아지지만, 출산 후 검사를 받아야 해요.", null, null,
                List.of(new ChatClient.Source("대한당뇨병학회 임신성 당뇨병", "https://www.diabetes.or.kr")), null, "chat-v1"));

        ask(jisu, "출산하면 낫나요?")
                .andExpect(jsonPath("$.data.answer.kind").value("INFO"))
                .andExpect(jsonPath("$.data.answer.sources[0].title").value("대한당뇨병학회 임신성 당뇨병"));
    }

    @Test
    void 출처_없는_정보는_보여_주지_않고_병원_안내() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "INFO", "아마 괜찮을 거예요.", null, null, List.of(), null, "chat-v1"));

        ask(jisu, "괜찮을까요?")
                .andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.answer.content").value("그 내용은 당당이가 가진 자료에 없어요. 다니는 병원에 물어봐 주세요."))
                .andExpect(jsonPath("$.data.answer.reason").value("NO_SOURCE"));
    }

    @Test
    void 바로_앞_대화를_오래된_순으로_같이_보낸다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", "병원에 물어봐 주세요."));
        ask(jisu, "첫 질문");
        ask(jisu, "두 번째 질문");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatClient.Turn>> history = ArgumentCaptor.forClass(List.class);
        verify(chatClient, times(2)).ask(anyString(), anyString(), anyBoolean(), history.capture());
        assertThat(history.getAllValues().get(0)).isEmpty();
        assertThat(history.getAllValues().get(1)).containsExactly(
                new ChatClient.Turn("user", "첫 질문"), new ChatClient.Turn("assistant", "병원에 물어봐 주세요."));
    }

    // ── 목록 · 지우기 · 평가 ──

    @Test
    void 목록은_최신이_먼저_남의_대화는_안_보인다() throws Exception {
        ask(jisu, "피가 비쳐요");     // 동의 없이도 되는 위험 신호로 2줄 만들기
        ask(minji, "출혈이 있어요");

        mvc.perform(get("/api/chat/messages").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.items[1].content").value("피가 비쳐요"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursorId").value(nullValue()));
    }

    @Test
    void 대화_모두_지우기는_내_것만() throws Exception {
        ask(jisu, "피가 비쳐요");
        ask(minji, "출혈이 있어요");

        mvc.perform(delete("/api/chat/messages").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));
        assertThat(messageCount()).isEqualTo(2);   // 민지 것은 남음
    }

    @Test
    void 도움이_됐나요는_당당이_답에만() throws Exception {
        ask(jisu, "피가 비쳐요");
        Long questionId = jdbc.queryForObject("SELECT id FROM chat_messages WHERE role = 'USER'", Long.class);
        Long answerId = jdbc.queryForObject("SELECT id FROM chat_messages WHERE role = 'ASSISTANT'", Long.class);

        mvc.perform(patch("/api/chat/messages/" + answerId + "/feedback").cookie(jisu)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpful").value(true));
        mvc.perform(patch("/api/chat/messages/" + questionId + "/feedback").cookie(jisu)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHAT_40001"));
        mvc.perform(patch("/api/chat/messages/" + answerId + "/feedback").cookie(minji)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHAT_40401"));
    }

    @Test
    void 빈_말은_400_로그인_안_하면_401() throws Exception {
        ask(jisu, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(post("/api/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 리뷰 반영 (#79) ──

    @Test
    void 글_없이_출처만_오면_500이_아니라_병원_안내() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "INFO", null, null, null, List.of(new ChatClient.Source("자료", "https://example.com")), null, "chat-v1"));

        ask(jisu, "출산하면 낫나요?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.kind").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.answer.sources").value(nullValue()));
    }

    @Test
    void 어떤_예외가_나도_FAILED이고_횟수를_돌려준다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any()))
                .willThrow(new IllegalStateException("응답을 읽다가 실패"));   // RestClientException이 아닌 예외

        ask(jisu, "공복 혈당은 언제 재요?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answer.kind").value("FAILED"));
        assertThat(redis.opsForValue().get("ai:usage:CHAT:" + jisuId + ":2026-11-09")).isEqualTo("0");
        assertThat(messageCount()).isEqualTo(2);   // 질문 + FAILED 답 (답 없이 질문만 남지 않는다)
    }

    @Test
    void 위험_신호_대화는_다음_질문의_AI_기록에_안_들어간다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(reply("UNKNOWN", "병원에 물어봐 주세요."));
        ask(jisu, "첫 질문");
        ask(jisu, "요즘 그냥 사라지고 싶어요");          // RISK — AI 안 감
        ask(jisu, "인슐린 몇 단위 맞아야 해요?");        // DOSE — AI 안 감
        ask(jisu, "공복 혈당은 언제 재요?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatClient.Turn>> history = ArgumentCaptor.forClass(List.class);
        verify(chatClient, times(2)).ask(anyString(), anyString(), anyBoolean(), history.capture());
        assertThat(history.getAllValues().get(1)).containsExactly(
                new ChatClient.Turn("user", "첫 질문"), new ChatClient.Turn("assistant", "병원에 물어봐 주세요."));
    }

    @Test
    void 기록이_100개를_넘으면_최근_100개를_보여_주고_개수는_진짜로_센다() throws Exception {
        agreeAll(jisu);
        for (int i = 0; i < 105; i++) {                   // 한국 11/01 00:00부터 1시간마다 105개 → 마지막은 11/05 08:00
            java.time.Instant at = java.time.Instant.parse("2026-10-31T15:00:00Z").plusSeconds(i * 3600L);
            reading(jisu, 90 + (i % 50), at.toString(), "OTHER", null);
        }
        given(chatClient.ask(anyString(), anyString(), eq(true), any())).willReturn(new ChatClient.Reply(
                "RECORDS", null, new ChatClient.Records(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), null),
                null, null, null, "chat-v1"));

        ask(jisu, "이번 달 기록 보여 줘")
                .andExpect(jsonPath("$.data.answer.records.count").value(105))
                .andExpect(jsonPath("$.data.answer.records.items.length()").value(100))
                .andExpect(jsonPath("$.data.answer.records.items[0].measuredAt").value("2026-10-31T20:00:00Z"))   // 오래된 5개가 잘림
                .andExpect(jsonPath("$.data.answer.records.items[99].measuredAt").value("2026-11-04T23:00:00Z"));  // 가장 최근이 맨 끝
    }

    @Test
    void 미래_시작일만_오면_오늘_하루로_자른다() throws Exception {
        agreeAll(jisu);
        given(chatClient.ask(anyString(), anyString(), anyBoolean(), any())).willReturn(new ChatClient.Reply(
                "RECORDS", null, new ChatClient.Records(LocalDate.of(2026, 12, 1), null, null), null, null, null, "chat-v1"));

        ask(jisu, "다음 달 기록 보여 줘")
                .andExpect(jsonPath("$.data.answer.records.from").value("2026-11-09"))
                .andExpect(jsonPath("$.data.answer.records.to").value("2026-11-09"));
    }

    @Test
    void 대화_목록은_커서로_불러와서_새_말이_생겨도_겹치지_않는다() throws Exception {
        ask(jisu, "피가 비쳐요");           // 1, 2
        ask(jisu, "출혈이 있어요");          // 3, 4
        ask(jisu, "배가 심하게 아파요");     // 5, 6

        String first = mvc.perform(get("/api/chat/messages").cookie(jisu).param("size", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        Long cursor = com.jayway.jsonpath.JsonPath.parse(first).read("$.data.nextCursorId", Long.class);

        ask(jisu, "피가 또 비쳐요");         // 첫 장을 본 뒤 새 말 2줄이 생겨도

        mvc.perform(get("/api/chat/messages").cookie(jisu).param("size", "2").param("cursorId", cursor.toString()))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].content").value("출혈이 있어요"))   // 바로 이어서 나온다
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}
