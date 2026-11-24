package com.dangdang.domain.auth.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// K-07에 보일 가린 이메일: 앞부분이 두 글자 이하면 하나도 남기지 않는다 (리뷰 #128)
class KakaoLoginResponseTest {

    @Test
    void 앞부분이_세_글자_이상이면_두_글자만_남긴다() {
        assertThat(KakaoLoginResponse.mask("dahyun@gmail.com")).isEqualTo("da***@gmail.com");
        assertThat(KakaoLoginResponse.mask("abc@x.com")).isEqualTo("ab***@x.com");
    }

    @Test
    void 앞부분이_두_글자_이하면_다_가린다() {
        assertThat(KakaoLoginResponse.mask("a@x.com")).isEqualTo("***@x.com");
        assertThat(KakaoLoginResponse.mask("ab@x.com")).isEqualTo("***@x.com");
    }
}
