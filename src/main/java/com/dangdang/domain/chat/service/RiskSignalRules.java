package com.dangdang.domain.chat.service;

import com.dangdang.domain.chat.enums.ChatUnknownReason;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// 위험 신호·약 용량은 AI를 거치지 않고 규칙으로 정해진 안내 (ASK-06 · Q-07 · Q-07b · Q-07c)
// 화면은 한 틀 「당당/위급 안내」: 제목(title) + 본문(text) + 빨간 줄(warning) + 전화 버튼(actions). 119는 위급 안내에 늘 있음
// 말 목록: 마음·위급은 AI 서버 app/moderation/rules.py(가이드 40)와 같게 + 「심한 두통」·산후 증상
//         저혈당(HYPO)은 AI 서버 LOW_GLUCOSE와 같은 목록 — 한쪽을 고치면 다른 쪽도 (ai app/moderation/rules.py)
// 키워드는 넓게 (놓치는 것보다 과하게 잡는 게 안전). 문구는 간호사·멘토 검토 뒤 확정 (61번 7-8)
public final class RiskSignalRules {

    public enum Type { MIND, SAFETY, URGENT, HYPO, DOSE }

    // title·warning은 DOSE면 null (위급 안내 틀이 아니라 「병원에 물어보세요」 안내)
    public record Hit(Type type, String title, String text, String warning, List<String> actions) {}

    private static final Pattern MIND = Pattern.compile(
            "죽고\\s*싶|사라지고\\s*싶|없어지고\\s*싶|살기\\s*싫|살고\\s*싶지\\s*않|자해|극단적\\s*(?:인\\s*)?선택"
                    + "|(?:다|모든\\s*걸|전부)\\s*그만(?:두|하)고\\s*싶|(?:다|모든\\s*걸|삶을|인생을)\\s*끝내고\\s*싶"
                    + "|없으면\\s*(?:다들|모두)?\\s*편"
                    + "|(?:나를|날|스스로를?)\\s*해(?:치|칠)|해치고\\s*싶");
    // 폭력·위협 (61번 7-6). 「맞았」은 「주사·인슐린 맞았」과 겹쳐서 뺐고, 「때려치우다」도 뺀다
    private static final Pattern SAFETY = Pattern.compile(
            "때려(?!\\s*치)|때렸|때린|때리(?:고|는|면)|폭력|폭행|협박|목을\\s*졸");
    private static final Pattern URGENT = Pattern.compile(
            "피가\\s*(?:조금\\s*|좀\\s*|많이\\s*|계속\\s*)?(?:비쳐|비치|비침|나와|났|나)|출혈|하혈"
                    + "|양수가?\\s*(?:터졌|터진|새|샌|흘러)"
                    + "|태동이?\\s*(?:줄었|줄어|없어|없|안\\s*느껴|약해)"
                    + "|배가\\s*심하게\\s*(?:아파|아프|뭉쳐|뭉치)|배\\s*(?:가|이)?\\s*(?:계속|자꾸)\\s*뭉|진통이\\s*(?:와|오|시작)"
                    + "|(?:심한|극심한|참을\\s*수\\s*없는)\\s*(?:통증|복통|두통)"
                    + "|두통이\\s*(?:너무\\s*|많이\\s*)?심|(?:시야가|눈앞이)\\s*(?:흐려|흐릿|침침)|경련"
                    // 산후에도 위험한 것 (산후 출혈·감염·혈전): 열 · 숨참 · 가슴 통증 · 붓기 · 의식
                    + "|고열|열이\\s*(?:많이\\s*)?(?:나|났|올라)|열\\s*(?:이)?\\s*(?:3[89]|4\\d)"
                    + "|숨이\\s*(?:차|가빠|막혀)|숨\\s*쉬기\\s*(?:힘들|어렵)"
                    + "|가슴이\\s*(?:아파|아프|답답|조여)"
                    + "|(?:갑자기|많이|심하게).{0,6}부(?:었|어)|얼굴.{0,4}부(?:었|어)"
                    + "|의식이\\s*(?:흐려|희미|없)|정신이\\s*혼미|정신을?\\s*잃|기절|쓰러졌|쓰러질");
    // 저혈당 (Q-07b · U-30c) — 증상 말, 또는 혈당 이야기 가까이(8자 안) 70 미만 두 자리 숫자 (「재 보니 62」 「혈당 58 나왔어요」)
    // 날짜·횟수·시간(「10월」 「12번」 「60분」)은 숫자 뒤 단위로 거른다
    private static final Pattern HYPO = Pattern.compile(
            "저혈당|식은\\s*땀|떨림|덜덜|(?:손|몸|다리)이?\\s*(?:덜덜\\s*)?떨(?:려|리|림|렸)"
                    + "|(?:혈당|재\\s*보니|쟀더니|쟀는데|측정)[^0-9]{0,8}[1-6]\\d(?!\\d)"
                    + "(?!\\s*(?:개월|월|일|시|분|주|번|개|회|살|%))");
    private static final Pattern DRUG = Pattern.compile(
            "인슐린|메트포르민|글루코파지|다이아벡스|노보래피드|휴마로그|란투스|레버미어|트레시바|투제오");
    private static final Pattern DOSE_WORD = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:단위|유닛|IU|mg(?!\\s*/\\s*d[lL])|밀리그램)|몇\\s*단위|용량|얼마나\\s*(?:맞|놓|늘|줄)",
            Pattern.CASE_INSENSITIVE);
    // 약을 빠뜨리거나 두 번 썼다는 말도 「처방한 병원에」 (61번 7-6). 「예약」·「약속」은 약이 아니다
    private static final Pattern DOSE_MISSED = Pattern.compile(
            "(?:인슐린|메트포르민|글루코파지|(?<!예)약(?!속)).{0,8}(?:깜빡|안\\s*맞|안\\s*먹|두\\s*번)");

    // ── Q-07 산과 위급 ──
    static final String URGENT_TITLE = "지금은 병원에 먼저 연락해 주세요";
    static final String URGENT_TEXT = "출혈, 태동이 줄어든 느낌, 심한 두통이나 복통이 있으면 "
            + "기다리지 말고 다니는 병원이나 분만실에 바로 연락하세요.";
    static final String URGENT_TEXT_AFTER_BIRTH = "출혈이 많거나, 열이 나거나, 숨이 차거나, 심한 두통이나 복통이 있으면 "
            + "기다리지 말고 다니는 병원에 바로 연락하세요.";
    static final String URGENT_WARNING = "피가 많이 나거나, 어지럽거나, 의식이 흐려지면 바로 119";
    // ── Q-07b 저혈당 ──
    static final String HYPO_TITLE = "병원에서 안내받은 저혈당 대처를 먼저 해 주세요";
    static final String HYPO_TEXT = "식은땀·떨림이 있거나 혈당이 70mg/dL 미만이면 "
            + "병원에서 들은 대처를 먼저 하고, 대처한 뒤 다시 재 주세요.";
    static final String HYPO_WARNING = "어지럽거나 의식이 흐려지면 바로 119";
    // ── Q-07c 마음 ──
    static final String MIND_TITLE = "혼자 견디지 않아도 돼요";
    static final String MIND_TEXT = "지금 마음이 많이 힘들다면 전문 상담사와 바로 이야기할 수 있어요. 24시간 받아요.";
    static final String MIND_WARNING = "나를 해칠 것 같거나 위급하면 바로 119";
    // ── 폭력 (61번 7-4 SAFETY 초안 — 디자인 없음, Q-07 틀 그대로) ──
    static final String SAFETY_TITLE = "지금 안전한 게 가장 중요해요";
    static final String SAFETY_TEXT = "위험하면 112에 바로 전화해 주세요. 1366(여성긴급전화)은 24시간 상담해 줘요.";
    static final String SAFETY_WARNING = "다쳤거나 지금 위험하면 바로 112·119";
    // ── 약 용량 (위급 틀 아님) ──
    static final String DOSE_TEXT = "약·인슐린 용량은 당당이 정해 드릴 수 없어요. 처방한 병원에 물어봐 주세요. "
            + "물어볼 말로 적어 두면 진료 때 잊지 않아요.";

    private RiskSignalRules() {
    }

    // 여러 개면 위험한 쪽: 마음 위기 > 폭력 > 위급 > 저혈당 > 용량
    // afterBirth = 산후·다른 상황·졸업 → 「태동」 없는 본문 (임신 중이 아닌 사람에게 태동 이야기를 하지 않음)
    public static Optional<Hit> match(String text, boolean afterBirth) {
        if (MIND.matcher(text).find()) {
            return Optional.of(mind());
        }
        if (SAFETY.matcher(text).find()) {
            return Optional.of(safety());
        }
        if (URGENT.matcher(text).find()) {
            return Optional.of(urgent(afterBirth));
        }
        if (HYPO.matcher(text).find()) {
            return Optional.of(new Hit(Type.HYPO, HYPO_TITLE, HYPO_TEXT, HYPO_WARNING,
                    List.of("CALL_HOSPITAL", "CALL_119")));
        }
        if ((DRUG.matcher(text).find() && DOSE_WORD.matcher(text).find()) || DOSE_MISSED.matcher(text).find()) {
            return Optional.of(new Hit(Type.DOSE, null, DOSE_TEXT, null, List.of("CALL_HOSPITAL")));
        }
        return Optional.empty();
    }

    // AI가 「답할 수 없음」 이유로 위급·마음 위기·폭력을 고르면 키워드로 잡혔을 때와 같은 안내 (61번 7-4 · 7-9)
    public static Hit forAiReason(ChatUnknownReason reason, boolean afterBirth) {
        return switch (reason) {
            case URGENT -> urgent(afterBirth);
            case CRISIS -> mind();
            case SAFETY -> safety();
            default -> throw new IllegalArgumentException("위급 안내 이유가 아님: " + reason);
        };
    }

    private static Hit mind() {
        return new Hit(Type.MIND, MIND_TITLE, MIND_TEXT, MIND_WARNING,
                List.of("CALL_109", "CALL_1577_0199", "CALL_119"));
    }

    private static Hit safety() {
        return new Hit(Type.SAFETY, SAFETY_TITLE, SAFETY_TEXT, SAFETY_WARNING,
                List.of("CALL_112", "CALL_1366", "CALL_119"));
    }

    private static Hit urgent(boolean afterBirth) {
        return new Hit(Type.URGENT, URGENT_TITLE, afterBirth ? URGENT_TEXT_AFTER_BIRTH : URGENT_TEXT,
                URGENT_WARNING, List.of("CALL_HOSPITAL", "CALL_119"));
    }
}
