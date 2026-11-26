package com.dangdang.domain.food.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// foods 테이블 한 줄 = 식약처 음식 하나. 마이그레이션으로만 넣는 공공 데이터라 앱에서는 읽기만 함
// 영양값은 기준량(100g 또는 100ml)당 값이고, 모르는 값은 null (0과 구분)
@Getter
@Entity
@Immutable
@Table(name = "foods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food {

    @Id
    @Column(length = 30)
    private String foodCode;                 // 식약처 식품코드 (예: D102-053000000-0001)

    @Column(nullable = false, length = 100)
    private String name;                     // 식품명 (예: 가래떡_흑미)

    @Column(length = 100)
    private String repName;                  // 대표식품명 (예: 가래떡)

    @Column(length = 30)
    private String category;                 // 식품대분류명

    @Column(nullable = false)
    private Short origin;                    // 식품기원코드 1 가정식 / 2 프랜차이즈 / 3·4 외식 / 5~7 급식

    @Column(length = 100)
    private String maker;                    // 업체명 (프랜차이즈만)

    @Column(nullable = false, length = 2)
    private String baseUnit;                 // g / ml

    @Column(precision = 7, scale = 1)
    private BigDecimal servingAmount;        // 1인분 양 (baseUnit 단위). 모르면 null

    @Column(precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "carb_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal carbG;

    @Column(name = "sugar_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal sugarG;

    @Column(name = "protein_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 7, scale = 2)   // 이름 끝 대문자 하나는 자동으로 _g가 안 돼서 직접 적음
    private BigDecimal fatG;

    @Column(nullable = false)
    private LocalDate sourceVersion;         // 데이터기준일

    private static final short FRANCHISE = 2;
    private static final String MEAL_KIT = "간편조리세트";
    // 이름 뒤 조각 중 이것들은 재료가 아니라 양념이라 괄호로 뺌 (쑥갓나물_고춧가루 → 쑥갓나물 (고춧가루))
    private static final Set<String> SEASONINGS = Set.of("간장", "소금", "된장", "고추장", "고춧가루", "양념");

    // 주식 여부 (밥·면·빵 칩, 「1공기」 같은 단위 말). 식품대분류만으로는 안 돼서 대표식품명을 같이 봄
    // 이 분류는 주식 (단, 그 안의 반찬·국물(스프·만두·월남쌈)은 isSideInStapleCategory로 뺌)
    private static final Set<String> STAPLE_CATEGORIES = Set.of("밥류", "면 및 만두류", "죽 및 스프류");
    // 「빵 및 과자류」는 피자·버거·식빵 같은 끼니와 케이크·도넛·쿠키 같은 간식이 섞여 있어서 대표식품명으로 나눔
    private static final String BREAD_CATEGORY = "빵 및 과자류";
    private static final Set<String> STAPLE_BREADS = Set.of(
            "피자", "버거", "햄버거", "식빵", "토스트", "토스트(식빵)", "베이글", "바게트", "번", "모닝빵", "롤빵",
            "또띠아", "치아바타", "호밀빵", "핫도그", "시리얼");   // 시리얼은 아침 끼니로 먹음 (프론트 요청)

    // 주식 여부 (분류·대표식품명이 없으면 false). 식약처 동기화로 분류 값이 늘면 위 목록에 더함
    public boolean isStaple() {
        return isStaple(category, repName);
    }

    static boolean isStaple(String category, String repName) {
        if (category == null) {
            return false;
        }
        if (STAPLE_CATEGORIES.contains(category)) {
            return repName == null || !isSideInStapleCategory(repName);
        }
        if (BREAD_CATEGORY.equals(category) && repName != null) {
            return STAPLE_BREADS.contains(repName) || repName.contains("샌드위치");   // 햄샌드위치·소고기 샌드위치 등
        }
        return false;
    }

    // 주식 분류 안의 반찬·국물: 스프, 만두(군만두·물만두·만두탕수 등, 만두국·떡만두국은 끼니라 주식), 월남쌈
    private static boolean isSideInStapleCategory(String repName) {
        return repName.contains("스프")
                || (repName.contains("만두") && !repName.contains("만두국"))
                || repName.equals("월남쌈");
    }

    // ---------- 표시 단위 (「밥 1공기」 「국 1그릇」) ----------
    // 1인분 양(servingAmount)을 그 단위 하나로 보여 줘도 맞는 분류만 단위를 정함. 영양값 계산(1인분 × 먹은 양)은 그대로
    // 분류 기준: 로컬·개발 DB 식약처 분류 25개 (2026-11-27 조회, 분류별 1인분 중앙값을 보고 정함)
    public static final String DEFAULT_UNIT = "인분";
    private static final Map<String, String> UNIT_BY_CATEGORY = Map.ofEntries(
            Map.entry("밥류", "그릇"),               // 볶음밥·비빔밥·덮밥·국밥 (흰밥·잡곡밥은 아래에서 공기)
            Map.entry("죽 및 스프류", "그릇"),
            Map.entry("국 및 탕류", "그릇"),
            Map.entry("찌개 및 전골류", "그릇"),     // 전골·샤브샤브는 같이 먹는 냄비라 인분
            Map.entry("면 및 만두류", "그릇"),       // 라면·국수·떡국 (만두는 여러 개라 인분)
            Map.entry("전·적 및 부침류", "접시"),    // 1인분 120~150g = 전 여러 조각이라 「조각」이 아니라 접시
            Map.entry("볶음류", "접시"),
            Map.entry("생채·무침류", "접시"),
            Map.entry("나물·숙채류", "접시"),
            Map.entry("조림류", "접시"),
            Map.entry("김치류", "접시"),
            Map.entry("장아찌·절임류", "접시"),
            Map.entry("젓갈류", "접시"),
            Map.entry("음료 및 차류", "잔"));        // g·ml은 바꾸지 않고 그대로 (servingUnit)
    // 찜류·구이류·튀김류·빵·유제품·양념 등은 한 분류에 생선 한 마리·고기·치킨·피자 한 판이 섞여 있어 인분

    // 밥류 중 흰밥·잡곡밥 (공기). 묵밥·연잎밥·자장밥·잡탕밥·채소밥은 그릇
    private static final Set<String> PLAIN_RICE = Set.of(
            "쌀밥", "찰밥", "콩밥", "팥밥", "밤밥", "감자밥", "귀리밥", "기장밥", "보리밥", "수수밥", "오곡밥", "잡곡밥",
            "차조밥", "현미밥", "흑미밥", "강낭콩밥", "검정콩밥", "고구마밥", "옥수수밥", "완두콩밥", "혼합잡곡밥", "순 현미밥");
    private static final Set<String> FRIED_EGGS = Set.of("달걀후라이", "달걀부침(달걀후라이)");   // 1인분 60~100g = 1개

    public String unitLabel() {
        return unitLabelOf(category, repName, servingAmount);
    }

    // 1인분 양까지 본 최종 단위 (PR #178 리뷰)
    // - 1인분 양을 모르면 servings 1이 기준량(100g·ml)이라 「1그릇」이 100g이 됨 → 인분
    // - 공기·줄·개는 개수라서, 1인분 양이 한 공기·한 줄·한 개와 많이 다르면 「1공기」를 눌렀을 때 기록 양이 틀어짐 → 인분
    //   범위는 DB 분포로 정함 (2026-11-27): 공기 중앙값 280 · 100g 3줄, 370~450ml 17줄은 뺌 / 김밥 일반 230~400 · 100g, 168ml는 뺌
    //   / 삼각김밥·달걀후라이 60~100 · 200g은 뺌. ml로 적힌 밥도 숫자만 봄 (g·ml은 바꾸지 않음)
    static String unitLabelOf(String category, String repName, BigDecimal servingAmount) {
        if (servingAmount == null) {
            return DEFAULT_UNIT;
        }
        String unit = unitLabelOf(category, repName);
        double s = servingAmount.doubleValue();
        boolean fits = switch (unit) {
            case "공기" -> s >= 150 && s <= 350;
            case "줄" -> s >= 200 && s <= 450;
            case "개" -> s >= 40 && s <= 150;
            default -> true;
        };
        return fits ? unit : DEFAULT_UNIT;
    }

    // 분류가 없거나 빈 값·모르는 분류면 인분. 대표식품명으로 분류 안의 예외를 먼저 봄
    static String unitLabelOf(String category, String repName) {
        if (category == null || category.isBlank()) {
            return DEFAULT_UNIT;
        }
        String unit = UNIT_BY_CATEGORY.getOrDefault(category, DEFAULT_UNIT);
        if (repName == null || repName.isBlank()) {
            return unit;
        }
        if (FRIED_EGGS.contains(repName)) {
            return "개";
        }
        if (repName.contains("달걀찜")) {      // 찜류(인분) 안의 달걀찜·두부 달걀찜은 뚝배기 한 그릇 (1인분 110g)
            return "그릇";
        }
        return switch (category) {
            case "밥류" -> riceUnit(repName);
            case "찌개 및 전골류" -> repName.contains("전골") || repName.contains("샤브샤브") ? DEFAULT_UNIT : unit;
            case "면 및 만두류" -> isSideInStapleCategory(repName) ? DEFAULT_UNIT : unit;   // 만두·월남쌈 (만두국은 그릇)
            case "전·적 및 부침류" -> repName.equals("달걀말이") ? DEFAULT_UNIT : unit;      // 1인분 50g, 조각 수가 안 정해짐
            default -> unit;
        };
    }

    // 흰밥·잡곡밥은 공기, 김밥은 줄(1인분 400g = 한 줄), 삼각김밥은 개, 주먹밥·초밥·충무김밥·모듬 김밥은 여러 개라 인분
    private static String riceUnit(String repName) {
        if (PLAIN_RICE.contains(repName)) {
            return "공기";
        }
        return switch (repName) {
            case "김밥" -> "줄";
            case "삼각김밥" -> "개";
            case "주먹밥", "초밥", "충무김밥", "모듬 김밥" -> DEFAULT_UNIT;
            default -> "그릇";
        };
    }

    // 화면에 보여 줄 이름. 식약처 이름은 "종류_재료" 순서라서 사람이 부르는 순서로 바꿈
    //   재료 하나: 볶음밥_김치 → 김치볶음밥 (붙여 씀. 원래 이름에 띄어쓰기가 있으면 띄움: 김치 볶음밥_모듬 → 모듬 김치 볶음밥)
    //   재료 둘 이상: 샌드위치_햄_치즈 → 샌드위치 (햄, 치즈) (앞에 다 붙이면 "햄치즈채소샌드위치"처럼 길어져서)
    //   뒤 조각에 종류가 들어 있으면 그 조각이 이름, 나머지는 괄호: 식빵_통밀식빵 → 통밀식빵, 냉면_회냉면_홍어 → 회냉면 (홍어)
    //   종류에 이미 든 재료는 뺌: 부추무침_부추_채소 → 채소부추무침
    //     단, 재료가 전부 빠지면 괄호로 남김 (김밥_김 → 김밥 (김)). 안 그러면 그냥 "김밥"과 이름이 같아짐
    //   2글자 이상 겹치면 한 단어로: 버섯볶음_새송이버섯 → 새송이버섯볶음
    //   양념은 괄호로: 쑥갓나물_고춧가루 → 쑥갓나물 (고춧가루)
    //   간편조리세트는 뒤가 상품명이라 상품명만 + 표시: 칼국수_간편조리세트_원주식장칼국수 → 원주식장칼국수 (간편조리세트)
    //   프랜차이즈는 뒤가 상품명이라 상품명만: 커피_아메리카노 핫(HOT) → 아메리카노 핫(HOT)
    public String displayName() {
        List<String> parts = Arrays.stream(name.split("_")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (parts.size() < 2) {
            return name.trim();
        }
        String kind = parts.get(0);
        List<String> rest = parts.subList(1, parts.size());

        boolean mealKit = rest.contains(MEAL_KIT);
        if (origin == FRANCHISE || mealKit) {
            List<String> product = rest.stream().filter(p -> !p.equals(MEAL_KIT)).toList();
            String shown = product.isEmpty() ? kind : String.join(" ", product);
            return mealKit ? shown + " (" + MEAL_KIT + ")" : shown;
        }

        List<String> seasonings = rest.stream().filter(SEASONINGS::contains).toList();
        List<String> inKind = rest.stream().filter(p -> !SEASONINGS.contains(p) && kind.contains(p)).toList();
        List<String> others = rest.stream().filter(p -> !SEASONINGS.contains(p) && !kind.contains(p)).toList();

        if (others.isEmpty()) {
            // 종류가 "파이/만주"처럼 여러 개고 뒤 조각이 그중 하나면 그게 이름 (파이/만주_만주 → 만주)
            if (inKind.size() == 1 && kind.contains("/")
                    && Arrays.stream(kind.split("/")).map(String::trim).anyMatch(inKind.get(0)::equals)) {
                return withDetails(inKind.get(0), List.of(), seasonings);
            }
            // 재료가 전부 종류 이름에 들어 있음 (굴무침_무, 감자볶음_감자). 지우면 그냥 "굴무침"과 같아지니 괄호로 남김
            return withDetails(kind, inKind, seasonings);
        }

        // 뒤 조각에 종류가 들어 있으면 그 조각이 진짜 이름 (식빵_통밀식빵 → 통밀식빵, 샌드위치_햄샌드위치_치즈 → 햄샌드위치 (치즈))
        int named = indexContainingKind(others, kind);
        if (named >= 0) {
            List<String> extra = new ArrayList<>(others);
            String main = extra.remove(named);
            return withDetails(main, extra, seasonings);
        }

        if (others.size() >= 2) {
            // 재료가 2개 이상이면 앞에 다 붙이면 길고 어색해서 메뉴판처럼 종류 (재료, 양념)
            // 골뱅이무침_채소_소면 → 골뱅이무침 (채소, 소면), 달걀_삶은것_노른자 → 달걀 (삶은것, 노른자)
            return withDetails(kind, others, seasonings);
        }

        // 재료 하나: 사람이 부르는 순서로 붙임
        return withDetails(joinIngredient(others.get(0), kind), List.of(), seasonings);
    }

    // 이름 (재료들, 양념들). 괄호에 넣을 게 없으면 이름만
    private static String withDetails(String main, List<String> details, List<String> seasonings) {
        List<String> all = new ArrayList<>(details);
        all.addAll(seasonings);
        return all.isEmpty() ? main : main + " (" + String.join(", ", all) + ")";
    }

    // 재료 + 종류를 한 이름으로. 조각끼리는 붙여 씀 (김치볶음밥)
    // 단, 조각 안에 원래 띄어쓰기가 있으면 반만 붙어 어색해서 띄어 씀 (아이스 카페모카_카라멜 → 카라멜 아이스 카페모카)
    // 재료 끝과 종류 앞이 2글자 이상 겹치면 겹친 부분을 한 번만 씀 (버섯볶음_새송이버섯 → 새송이버섯볶음)
    // 1글자 겹침은 "미음_현미 → 현미음"처럼 틀린 말이 돼서 안 겹침
    private static String joinIngredient(String ingredient, String kind) {
        for (int len = Math.min(ingredient.length() - 1, kind.length() - 1); len >= 2; len--) {
            if (kind.startsWith(ingredient.substring(ingredient.length() - len))) {
                return ingredient + kind.substring(len);
            }
        }
        String sep = kind.contains(" ") || ingredient.contains(" ") ? " " : "";
        return ingredient + sep + kind;
    }

    // 뒤 조각 중 종류 이름이 들어 있는 첫 조각의 위치 (없으면 -1). 종류가 "파이/만주"처럼 여러 개면 하나라도 들어 있으면 됨
    private static int indexContainingKind(List<String> others, String kind) {
        String[] kinds = kind.split("/");
        for (int i = 0; i < others.size(); i++) {
            String p = others.get(i);
            if (Arrays.stream(kinds).anyMatch(k -> !k.isBlank() && p.contains(k.trim()))) {
                return i;
            }
        }
        return -1;
    }
}
