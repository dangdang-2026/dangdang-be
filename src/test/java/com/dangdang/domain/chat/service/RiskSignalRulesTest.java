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

    // ── 11/25 리뷰에서 찾은 말 — ai tests/test_post_check.py와 같은 문장, 같은 결과 ──

    @ParameterizedTest
    @ValueSource(strings = {"피가 너무 많이 나요.", "피 나요.", "갈색 피비침이 있어요."})
    void 출혈은_부사가_둘이어도_피비침도_위급(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.URGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"혈당이 너무 낮아요 어지러워요.", "혈당이 떨어져서 어지러워요.", "혈당 65mg/dL 나왔어요."})
    void 혈당이_낮다_떨어졌다도_저혈당(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.HYPO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "혈당 측정기 15만원이에요.", "혈당 측정 30초면 끝나요.", "혈당 관리하는 50대예요.", "혈당 낮추는 법 알려 주세요.",
            "혈당 떨어뜨리는 음식 있나요?", "커피 나와요.", "피가 나아졌어요.", "회사 다 그만두고 싶어요.", "엄마 없으면 편해요."})
    void 돈_시간_나이와_흔한_말은_안_걸린다(String text) {
        assertThat(type(text)).isNull();
    }

    @Test
    void 혈당_뒤_약_용량은_저혈당이_아니라_약_용량() {
        assertThat(type("혈당 쟀는데 인슐린 12단위 맞아야 해?")).isEqualTo(RiskSignalRules.Type.DOSE);
        assertThat(type("인슐린 12단위 맞아요")).isEqualTo(RiskSignalRules.Type.DOSE);
    }

    @Test
    void 저혈당과_약_용량이_한_문장이면_저혈당_안내가_먼저() {   // ai 글 감지는 둘 다 알리지만 챗봇 답은 하나
        assertThat(type("란투스 10단위 맞았는데 혈당 65 나왔어요.")).isEqualTo(RiskSignalRules.Type.HYPO);
        assertThat(type("인슐린 4단위 맞고 손이 떨려요.")).isEqualTo(RiskSignalRules.Type.HYPO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "고열량 간식 먹어도 돼요?", "열이 나아졌어요", "아기가 배를 발로 때리는 느낌이에요", "아기가 발로 배를 때려요",
            "약간 어지러운데 깜빡했어요", "약해서 두 번 먹었어요"})
    void 챗봇만_보는_말의_오탐(String text) {
        assertThat(type(text)).isNotIn(RiskSignalRules.Type.URGENT, RiskSignalRules.Type.SAFETY, RiskSignalRules.Type.DOSE);
    }

    @Test
    void AI가_고른_저혈당_이유는_산과_문구가_아니라_저혈당_안내() {
        RiskSignalRules.Hit hit = RiskSignalRules.forAiReason(ChatUnknownReason.HYPO, false);
        assertThat(hit).isEqualTo(RiskSignalRules.match("저혈당 온 것 같아요", false).orElseThrow());
        assertThat(hit.text()).doesNotContain("태동", "분만실");
    }

    // ── #143 리뷰 ──

    @ParameterizedTest
    @ValueSource(strings = {"머리를 망치로 때리는 것처럼 아파요", "머리를 망치로 때리는 것 같아요", "머리가 깨질 것 같아요"})
    void 머리를_때리는_듯한_두통은_폭력이_아니라_위급(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.URGENT);
    }

    @Test
    void 남편이_머리를_때리면_폭력() {
        assertThat(type("남편이 머리를 때리는 것 같아요")).isEqualTo(RiskSignalRules.Type.SAFETY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"갈색피가 비쳐요", "속옷에피가 비쳐요", "피가 조금 나와요"})
    void 피_앞에_글자가_붙어도_출혈(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.URGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"레시피가 나와 있어요", "커피가 나와요", "열이 나지는 않는데 기침해요", "피가 나지 않아요", "열이 40분째 안 떨어져서 걱정이에요"})
    void 부정문과_숫자_뒤_시간은_위급이_아니다(String text) {
        assertThat(type(text)).isNotEqualTo(RiskSignalRules.Type.URGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"혈당 62mg 나왔어요", "혈당이 50대로 나와요", "혈당 수치가 60대예요"})
    void mg만_쓰거나_혈당_바로_뒤_대도_저혈당(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.HYPO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"약 2주 동안 두 번 쟀어요", "계약서 깜빡했어요", "요약 두 번 했어요", "약관 깜빡하고 안 읽었어요"})
    void 대략_뜻의_약과_약이_아닌_말은_약_용량이_아니다(String text) {
        assertThat(type(text)).isNull();
    }

    @Test
    void 약_숫자_알은_약() {
        assertThat(type("약 2알 깜빡하고 안 먹었어요")).isEqualTo(RiskSignalRules.Type.DOSE);
    }

    // ── ai 글 감지(app/moderation/rules.py) 평가에서 나온 폭력 말 — ai와 같은 문장, 같은 결과 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "남편이 때려", "남편이 발로 때렸어", "남편한테 맞았어요", "신랑한테 또 맞았어", "누구한테 맞아서 멍이 들었어요",
            "시어머니에게 맞고 있어요", "남자 친구한테 맞았어", "아기가 발로 차요. 남편이 때렸어요"})
    void 사람이_때렸거나_사람한테_맞았으면_폭력(String text) {
        assertThat(type(text)).isEqualTo(RiskSignalRules.Type.SAFETY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "아기가 발로 뻥뻥 때려요", "애기가 발로 때려요", "뱃속에서 아기가 발로 때려요", "아기가 엄마 배를 발로 때려요",
            "나쁜 버릇 때려잡고 싶어요", "회사 때려치우고 싶어", "인슐린 맞았어요", "오늘 주사 맞았어요"})
    void 태동과_때려잡다_주사는_폭력이_아니다(String text) {
        assertThat(type(text)).isNotEqualTo(RiskSignalRules.Type.SAFETY);
    }
}
