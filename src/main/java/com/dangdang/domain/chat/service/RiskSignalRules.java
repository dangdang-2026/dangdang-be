package com.dangdang.domain.chat.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// 위험 신호·약 용량은 AI를 거치지 않고 규칙으로 정해진 안내 (ASK-06 · Q-07)
// 말 목록은 AI 서버 app/moderation/rules.py(가이드 40)와 같게 + 「심한 두통」. 문구는 간호사 검토 뒤 확정
public final class RiskSignalRules {

    public enum Type { MIND, URGENT, DOSE }

    public record Hit(Type type, String text, List<String> actions) {}

    private static final Pattern MIND = Pattern.compile(
            "죽고\\s*싶|사라지고\\s*싶|없어지고\\s*싶|살기\\s*싫|살고\\s*싶지\\s*않|자해|극단적\\s*(?:인\\s*)?선택");
    private static final Pattern URGENT = Pattern.compile(
            "피가\\s*(?:조금\\s*|좀\\s*)?(?:비쳐|비치|비침|나와|났)|출혈|하혈"
                    + "|양수가?\\s*(?:터졌|터진|새|샌|흘러)"
                    + "|태동이?\\s*(?:줄었|줄어|없어|없|안\\s*느껴|약해)"
                    + "|배가\\s*심하게\\s*(?:아파|아프|뭉쳐|뭉치)|(?:심한|극심한|참을\\s*수\\s*없는)\\s*(?:통증|복통|두통)"
                    + "|두통이\\s*(?:너무\\s*|많이\\s*)?심|(?:시야가|눈앞이)\\s*(?:흐려|흐릿|침침)|경련");
    private static final Pattern DRUG = Pattern.compile(
            "인슐린|메트포르민|글루코파지|다이아벡스|노보래피드|휴마로그|란투스|레버미어|트레시바|투제오");
    private static final Pattern DOSE_WORD = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:단위|유닛|IU|mg(?!\\s*/\\s*d[lL])|밀리그램)|몇\\s*단위|용량|얼마나\\s*(?:맞|놓|늘|줄)",
            Pattern.CASE_INSENSITIVE);

    static final String URGENT_TEXT = "말씀하신 증상은 바로 확인이 필요할 수 있어요. 다니는 병원에 먼저 전화해 주세요. "
            + "많이 급하면 119에 전화해 주세요.";
    static final String MIND_TEXT = "많이 힘드셨겠어요. 혼자 견디지 않아도 괜찮아요. "
            + "지금 바로 이야기할 수 있는 곳이 있어요. 109(자살예방 상담전화)나 1577-0199(정신건강 위기상담)에 전화해 주세요.";
    static final String DOSE_TEXT = "약·인슐린 용량은 당당이 정해 드릴 수 없어요. 처방한 병원에 물어봐 주세요. "
            + "물어볼 말로 적어 두면 진료 때 잊지 않아요.";

    private RiskSignalRules() {
    }

    // 여러 개면 위험한 쪽: 마음 위기 > 위급 > 용량 (AI 서버 규칙과 같은 순서)
    public static Optional<Hit> match(String text) {
        if (MIND.matcher(text).find()) {
            return Optional.of(new Hit(Type.MIND, MIND_TEXT, List.of("CALL_109", "CALL_1577_0199")));
        }
        if (URGENT.matcher(text).find()) {
            return Optional.of(new Hit(Type.URGENT, URGENT_TEXT, List.of("CALL_HOSPITAL", "CALL_119")));
        }
        if (DRUG.matcher(text).find() && DOSE_WORD.matcher(text).find()) {
            return Optional.of(new Hit(Type.DOSE, DOSE_TEXT, List.of("CALL_HOSPITAL")));
        }
        return Optional.empty();
    }
}
