package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.community.repository.CommunityProfileRepository;
import com.dangdang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 진료 메모를 AI 서버(해외)로 보내기 전에 내 이름·병원 이름·전화번호를 자리표시로 바꾼다 (C-11 「내 이름·병원 이름은 빼고 보내요」)
// 원문은 DB에 그대로 두고, AI로 보내는 글만 가린다
// 서버가 아는 것만 가린다 — 남의 이름(의사·가족)이나 줄여 쓴 병원 이름(서울대학교병원 → 서울대병원)은 못 가림
@Component
@RequiredArgsConstructor
public class VisitMemoMasker {

    static final String NAME = "[이름]";
    static final String HOSPITAL = "[병원]";
    static final String PHONE = "[전화번호]";

    // 한 글자는 안 가린다 — 「수」를 가리면 「수치」「수요일」까지 바뀌어 AI가 메모를 못 읽는다
    private static final int MIN_LENGTH = 2;
    // 두 글자 이름은 앞에 한글이 두 글자 이상 붙어 있으면 낱말의 일부로 보고 안 가린다 (「혈당지수」의 「지수」)
    // 성 한 글자(「김지수」)나 뒤에 붙는 말(「지수님」「지수가」)은 그대로 가린다. 세 글자 이상은 늘 가린다
    private static final int SHORT_LENGTH = 2;
    private static final String NOT_INSIDE_WORD = "(?<![가-힣]{2})";
    // 영문은 낱말 경계를 본다 — 「Amy」가 「amylase」 안에서 가려지지 않게
    private static final String NO_LATIN_BEFORE = "(?<![A-Za-z])";
    private static final String NO_LATIN_AFTER = "(?![A-Za-z])";
    // 이름·닉네임이 진료 메모에 흔한 말과 같으면 안 가린다 — 닉네임 「혈당」이면 「공복 혈당 95」가 「공복 [이름] 95」가 돼 AI가 못 읽는다
    private static final Set<String> COMMON_WORDS = Set.of(
            "혈당", "공복", "식전", "식후", "아침", "점심", "저녁", "간식", "야식", "취침", "하나",
            "당뇨", "임신", "산모", "아기", "태아", "엄마", "아빠", "남편", "병원", "의사", "선생님", "간호사",
            "진료", "검사", "수치", "기준", "정상", "체중", "몸무게", "혈압", "소변", "단백", "단백질", "케톤",
            "인슐린", "주사", "식단", "운동", "산책", "탄수", "탄수화물", "과일", "우유", "오늘", "내일", "어제",
            "다음", "초음파", "태동", "주수", "당화혈색소");
    // 전화번호: 0으로 시작하는 9~11자리(010-1234-5678 · 01012345678 · 02-123-4567 · 010 1234 5678 · 010.1234.5678 · +82 10-1234-5678)
    // 또는 대표번호(1588-1234, 가운데 구분 글자가 있어야). 앞뒤에 숫자가 붙으면 안 봄
    // → 혈당(95)·날짜(11/27)·주수(32주)와 안 겹친다
    private static final String PHONE_NUMBER = "(?<![0-9])(?:(?:\\+82[-.\\s]?|0)\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}"
            + "|1[5-9]\\d{2}[-.\\s]\\d{4})(?![0-9])";

    private final UserRepository userRepository;
    private final CommunityProfileRepository communityProfileRepository;
    private final CareSiteRepository careSiteRepository;

    @Transactional(readOnly = true)
    public String mask(Long userId, String memo) {
        Map<String, String> tags = new LinkedHashMap<>();   // 가릴 말 → 자리표시
        // 지운 병원도 가린다 — 지우기 전에 쓴 메모에 그 이름이 남아 있을 수 있어서 (soft delete를 일부러 안 거름)
        careSiteRepository.findAllByUserId(userId).forEach(site -> add(tags, site.getName(), HOSPITAL));
        // 같은 말이 병원 이름이기도 하면 이름으로 본다 (나중에 넣은 쪽이 덮어씀)
        userRepository.findById(userId).ifPresent(user -> add(tags, user.getDisplayName(), NAME));
        communityProfileRepository.findByUserId(userId).ifPresent(profile -> add(tags, profile.getNickname(), NAME));

        Map<String, String> tagByKey = new HashMap<>();
        tags.forEach((word, tag) -> tagByKey.put(key(word), tag));
        // 긴 말부터 — 「지수산부인과」가 먼저 [병원]이 돼야 이름 「지수」만 바뀌어 「[이름]산부인과」가 되는 일이 없다
        String words = tags.keySet().stream()
                .sorted(Comparator.comparingInt((String word) -> key(word).length()).reversed())
                .map(VisitMemoMasker::wordPattern)
                .collect(Collectors.joining("|"));
        // 전화번호와 말을 한 번에 훑는다 — 바꿔 넣은 자리표시([병원] 등)를 다른 말이 다시 바꾸는 일이 없게
        Pattern pattern = Pattern.compile("(" + PHONE_NUMBER + ")" + (words.isEmpty() ? "" : "|" + words),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(memo).replaceAll(m -> Matcher.quoteReplacement(
                m.group(1) != null ? PHONE : tagByKey.getOrDefault(key(m.group()), NAME)));
    }

    // 비었거나 한 글자거나 흔한 말이면 안 넣는다. 띄어쓰기는 하나로 모아 둔다
    private static void add(Map<String, String> tags, String word, String tag) {
        if (word == null) {
            return;
        }
        String w = word.strip().replaceAll("\\s+", " ");
        if (key(w).length() >= MIN_LENGTH && !COMMON_WORDS.contains(key(w))) {
            tags.put(w, tag);
        }
    }

    private static String wordPattern(String word) {
        String before = key(word).length() <= SHORT_LENGTH ? NOT_INSIDE_WORD : "";
        if (isLatin(word.charAt(0))) {
            before += NO_LATIN_BEFORE;
        }
        String after = isLatin(word.charAt(word.length() - 1)) ? NO_LATIN_AFTER : "";
        return before + spaceInsensitive(word) + after;
    }

    private static boolean isLatin(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    // 띄어 쓴 곳은 붙여 써도 같은 말로 본다 (등록은 「한빛 산부인과」, 메모는 「한빛산부인과」)
    private static String spaceInsensitive(String word) {
        return Arrays.stream(word.split(" ")).map(Pattern::quote).collect(Collectors.joining("\\s*"));
    }

    // 찾은 글을 어느 자리표시로 바꿀지 고를 때 쓰는 열쇠 (띄어쓰기·대소문자 무시)
    private static String key(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
