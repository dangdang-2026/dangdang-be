package com.dangdang.domain.auth;

import com.dangdang.domain.auth.dto.request.SignupRequest;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.EmailSender;
import com.dangdang.domain.auth.service.EmailVerificationService;
import com.dangdang.domain.auth.service.SignupService;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
class SignupFlowTest {

    // 메일 대신 인증번호를 붙잡아 두는 테스트용 발송기
    static final Map<String, String> SENT = new HashMap<>();

    @TestConfiguration
    static class FakeMail {
        @Bean
        @Primary
            // LogEmailSender 대신 이걸 쓰게 한다
        EmailSender capturingSender() {
            return SENT::put;
        }
    }

    @Autowired EmailVerificationService verificationService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() { // Redis 는 @Transactional 로 되돌릴 수 없어서 직접 지운다
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        SENT.clear();
    }

    private AuthErrorCode codeOf(Throwable e) {
        return (AuthErrorCode) ((CustomException) e).getErrorCode();
    }

    @Test
    void 인증번호를_맞히면_가입되고_알림설정도_생긴다() {
        verificationService.sendCode("Jisu@X.com");
        verificationService.verify("jisu@x.com", SENT.get("jisu@x.com"));

        Long id = signupService.signup(new SignupRequest("jisu@x.com", "지수", "abcd1234"));

        assertThat(userRepository.findById(id).orElseThrow().getPasswordHash()).startsWith("$2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 인증_안_하고_가입하면_막힌다() {
        assertThatThrownBy(() -> signupService.signup(new SignupRequest("a@x.com", "지수", "abcd1234")))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void 다섯_번_틀리면_번호가_사라져_다시_받아야_한다() {
        verificationService.sendCode("a@x.com");
        String code = SENT.get("a@x.com");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verificationService.verify("a@x.com", wrong))
                    .isInstanceOf(CustomException.class);
        }
        assertThatThrownBy(() -> verificationService.verify("a@x.com", code))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_EXPIRED));
    }

    @Test
    void 하루에_여섯_번째_발송은_막힌다() {
        for (int i = 0; i < 5; i++) {
            verificationService.sendCode("a@x.com");
        }
        assertThatThrownBy(() -> verificationService.sendCode("a@x.com"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_SEND_LIMIT));
    }

    @Test
    void 이미_가입한_이메일은_인증번호를_보내지_않는다() {
        verificationService.sendCode("a@x.com");
        verificationService.verify("a@x.com", SENT.get("a@x.com"));
        signupService.signup(new SignupRequest("a@x.com", "지수", "abcd1234"));

        assertThatThrownBy(() -> verificationService.sendCode("A@X.COM"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_ALREADY_USED));
    }
}
