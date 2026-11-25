package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.community.entity.CommunityProfile;
import com.dangdang.domain.community.repository.CommunityProfileRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 진료 메모를 AI로 보내기 전 가리기 — 스프링 없이 빠르게 (경계: 다른 단어 안의 이름 · 전화번호 모양 · 빈 이름)
class VisitMemoMaskerTest {

    private static final Long USER_ID = 1L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommunityProfileRepository profileRepository = mock(CommunityProfileRepository.class);
    private final CareSiteRepository careSiteRepository = mock(CareSiteRepository.class);
    private final VisitMemoMasker masker = new VisitMemoMasker(userRepository, profileRepository, careSiteRepository);

    private void known(String displayName, String nickname, String... careSites) {
        User user = mock(User.class);
        given(user.getDisplayName()).willReturn(displayName);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        if (nickname == null) {
            given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        } else {
            CommunityProfile profile = mock(CommunityProfile.class);
            given(profile.getNickname()).willReturn(nickname);
            given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        }
        List<CareSite> sites = Arrays.stream(careSites).map(name -> {
            CareSite site = mock(CareSite.class);
            given(site.getName()).willReturn(name);
            return site;
        }).toList();
        given(careSiteRepository.findAllByUserId(USER_ID)).willReturn(sites);
    }

    private String mask(String memo) {
        return masker.mask(USER_ID, memo);
    }

    @Test
    void 내_이름_닉네임_병원_이름을_가린다() {
        known("지수", "튼튼맘", "한빛산부인과");
        assertThat(mask("지수님 한빛산부인과 진료. 튼튼맘 글 보고 물어봄. 공복 95 미만"))
                .isEqualTo("[이름]님 [병원] 진료. [이름] 글 보고 물어봄. 공복 95 미만");
    }

    @Test
    void 이름이_병원_이름의_일부면_병원_이름을_먼저_가린다() {
        known("지수", null, "지수산부인과");
        assertThat(mask("지수산부인과에서 지수가 들은 말")).isEqualTo("[병원]에서 [이름]가 들은 말");
    }

    @Test
    void 이름이_다른_말_안에_있어도_가린다() {   // 한국어는 띄어쓰기로 이름을 못 가려서 넓게 (덜 가리는 것보다 안전)
        known("지수", null);
        assertThat(mask("김지수 산모, 지수씨")).isEqualTo("김[이름] 산모, [이름]씨");
    }

    @Test
    void 두_글자_이름이_낱말_안에_있으면_안_가린다() {   // 앞에 한글이 두 글자 이상 붙으면 낱말의 일부로 본다
        known("지수", null);
        assertThat(mask("혈당지수가 낮은 음식, 지수님")).isEqualTo("혈당지수가 낮은 음식, [이름]님");
    }

    @Test
    void 세_글자_이상_이름은_낱말_안에_있어도_가린다() {
        known("김지수", "튼튼맘");
        assertThat(mask("담당김지수님, 우리튼튼맘")).isEqualTo("담당[이름]님, 우리[이름]");
    }

    @Test
    void 이름에_없는_글자가_섞인_말은_안_가린다() {
        known("지수", null);
        assertThat(mask("지난주 수치가 높았어요")).isEqualTo("지난주 수치가 높았어요");
    }

    @Test
    void 한_글자_이름이나_빈_이름은_안_가린다() {
        known("수", "  ", "");
        assertThat(mask("수요일 수치 확인")).isEqualTo("수요일 수치 확인");
    }

    @Test
    void 이름이_없거나_사람이_없어도_전화번호는_가린다() {
        known(null, null);
        assertThat(mask("문의 010-1234-5678")).isEqualTo("문의 [전화번호]");
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());
        assertThat(mask("문의 010-1234-5678")).isEqualTo("문의 [전화번호]");
    }

    @Test
    void 띄어쓰기가_달라도_같은_병원으로_본다() {
        known("지수", null, "한빛 산부인과");
        assertThat(mask("한빛산부인과, 한빛 산부인과")).isEqualTo("[병원], [병원]");
    }

    @Test
    void 영어_닉네임은_대소문자를_가리지_않는다() {
        known("지수", "Mint");
        assertThat(mask("MINT 맘, mint")).isEqualTo("[이름] 맘, [이름]");
    }

    @Test
    void 이름에_정규식_글자가_있어도_글자_그대로_찾는다() {
        known("지수", "a.b(c)");
        assertThat(mask("a.b(c) axb(c)")).isEqualTo("[이름] axb(c)");
    }

    @Test
    void 바꿔_넣은_자리표시를_다시_바꾸지_않는다() {
        known("병원", null, "한빛병원");   // 이름이 「병원」이어도 [병원] 안의 글자는 안 건드림
        assertThat(mask("한빛병원")).isEqualTo("[병원]");
    }

    @Test
    void 이름이_메모에_흔한_말이면_안_가린다() {   // 닉네임 「혈당」을 가리면 「공복 혈당 95」를 AI가 못 읽는다
        known("하나", "혈당");
        assertThat(mask("공복 혈당 95, 하나 더 물어보기")).isEqualTo("공복 혈당 95, 하나 더 물어보기");
    }

    @Test
    void 영어_이름은_낱말_안에서는_안_가린다() {
        known("지수", "Amy");
        assertThat(mask("amylase 수치, Amy 맘")).isEqualTo("amylase 수치, [이름] 맘");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678", "01012345678", "010 1234 5678", "010.1234.5678", "02-123-4567", "031-123-4567",
            "+82 10-1234-5678", "1588-1234"})
    void 여러_모양의_전화번호(String phone) {
        known("지수", null);
        assertThat(mask("전화 " + phone + " 로")).isEqualTo("전화 [전화번호] 로");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "공복 95 미만, 식후 140 미만", "11/27 금요일", "2026-11-27 진료", "32주 3일", "식후 1시간 120",
            "15881234", "123-4567"})
    void 혈당_날짜_주수_숫자는_전화번호가_아니다(String memo) {
        known("지수", null);
        assertThat(mask(memo)).isEqualTo(memo);
    }
}
