package com.dangdang.domain.chat.service;

import com.dangdang.domain.chat.enums.ChatUnknownReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// 말 목록 시험 — 저혈당 줄은 ai tests/test_post_check.py와 같은 문장 (두 저장소가 같은 저혈당 말 목록)
class RiskSignalRulesTest {

    private static RiskSignalRules.Type type(String text) {
        return RiskSignalRules.match(text, false).map(RiskSignalRules.Hit::type).orElse(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "식은땀이 나고 손이 떨려요. 재 보니 62예요", "방금 혈당 58 나왔어요",
            "방금 혈당 52 나왔어요. 사탕 먹고 기다리는 중이에요.", "인슐린 맞고 한 시간 뒤에 손이 덜덜 떨렸어요.",
            "저혈당 온 것 같아요.", "저혈당인지 손이 떨리고 식은땀이 나면서 정신이 멍해요."})
    void 저혈당_말(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.HYPO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "식후 혈당 140 넘으면 어떡해요?", "혈당 10월 1일부터 보여 줘", "식후 혈당 128 나왔어요.",
            "혈당 60분 뒤에 쟀어요.", "식후 혈당 60분에 쟀는데 128이었어요."})
    void 저혈당이_아닌_숫자(String text) {
        assertThat(type(text)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "피가 많이 나요", "숨이 차고 가슴이 답답해", "배가 계속 뭉쳐", "열이 39도야", "다리가 갑자기 엄청 부었어",
            "식은땀이 나고 의식이 흐려져요"})   // 마지막: 의식이 흐려지면 저혈당보다 위급이 먼저
    void 위급(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.URGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"내가 없으면 다들 편할 텐데", "다 그만두고 싶어", "나를 해칠 것 같아요"})
    void 마음_위기(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.MIND);
    }

    @Test
    void 폭력() {
        assertThat(type("남편이 때려")).isEqualTo(RiskSignalRules.Type.SAFETY);
        assertThat(type("남편이 때려서 피가 나요")).isEqualTo(RiskSignalRules.Type.SAFETY);   // 폭력이 위급보다 먼저 (119 같이 있음)
    }

    @ParameterizedTest
    @ValueSource(strings = {"인슐린 깜빡하고 안 맞았어", "약을 두 번 먹었어요", "인슐린 몇 단위 맞아야 해요?"})
    void 약(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.DOSE);
    }

    // 넓게 잡되 흔한 말은 안 걸리게 (61 7-6 「오탐 막기」)
    @ParameterizedTest
    @ValueSource(strings = {
            "오늘 인슐린 주사 맞았어요", "병원 예약 깜빡했어요", "요즘 정신이 없어서 기록을 못 했어요",
            "진통제 먹어도 돼요?", "일 빨리 끝내고 싶어", "배가 좀 뭉치는 느낌이에요"})
    void 흔한_말은_안_걸린다(String text) {
        assertThat(type(text)).isNull();
    }

    @Test
    void 산후에는_태동_없는_본문() {
        assertThat(RiskSignalRules.match("피가 많이 나요", true).orElseThrow().text()).doesNotContain("태동");
        assertThat(RiskSignalRules.match("피가 많이 나요", false).orElseThrow().text()).contains("태동");
    }

    @Test
    void 저혈당이_약_용량보다_먼저() {   // 지금 몸 상태가 더 급함
        assertThat(type("인슐린 맞고 저혈당 왔는데 몇 단위 줄여요?")).isEqualTo(RiskSignalRules.Type.HYPO);
    }

    @Test
    void 약_용량은_제목과_빨간_줄이_없다() {
        RiskSignalRules.Hit dose = RiskSignalRules.match("인슐린 몇 단위 맞아야 해요?", false).orElseThrow();
        assertThat(dose.title()).isNull();
        assertThat(dose.warning()).isNull();
    }

    @Test
    void AI가_고른_위급_이유는_키워드와_같은_안내() {
        assertThat(RiskSignalRules.forAiReason(ChatUnknownReason.URGENT, false))
                .isEqualTo(RiskSignalRules.match("피가 많이 나요", false).orElseThrow());
        assertThat(RiskSignalRules.forAiReason(ChatUnknownReason.CRISIS, false))
                .isEqualTo(RiskSignalRules.match("사라지고 싶어요", false).orElseThrow());
        assertThat(RiskSignalRules.forAiReason(ChatUnknownReason.SAFETY, false))
                .isEqualTo(RiskSignalRules.match("남편이 때려", false).orElseThrow());
    }
}
