package com.dangdang.domain.auth;

import com.dangdang.domain.auth.dto.request.SignupRequest;
import com.dangdang.domain.auth.dto.response.AuthRetryInfo;
import com.dangdang.domain.auth.dto.response.EmailCodeSendResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.EmailSender;
import com.dangdang.domain.auth.infra.EmailVerificationStore;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
class SignupFlowTest {

    // 메일 대신 인증번호를 붙잡아 두는 테스트용 발송기
    static final Map<String, String> SENT = new HashMap<>();
    static final Set<String> FAIL_ONCE = ConcurrentHashMap.newKeySet();   // 한 번만 메일 실패 흉내

    @TestConfiguration
    static class FakeMail {
        @Bean
        @Primary
            // LogEmailSender 대신 이걸 쓰게 한다
        EmailSender capturingSender() {
            return new EmailSender() {
                @Override
                public void sendVerificationCode(String email, String code) {
                    if (email.startsWith("fail@") || FAIL_ONCE.remove(email)) {
                        throw new CustomException(AuthErrorCode.MAIL_SEND_FAILED);   // V-12 흉내
                    }
                    SENT.put(email, code);
                }

                @Override
                public void sendPasswordResetLink(String email, String link) {}
            };
        }
    }

    @Autowired EmailVerificationService verificationService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailVerificationStore store;

    @AfterEach
    void clean() { // Redis 는 @Transactional 로 되돌릴 수 없어서 직접 지운다
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        SENT.clear();
        FAIL_ONCE.clear();
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
    void 하루에_여섯_번째_발송은_막힌다() {
        for (int i = 0; i < 5; i++) {
            verificationService.sendCode("a@x.com");
            redis.delete("email:wait:a@x.com");   // 60초 대기는 이 테스트에서 안 봄
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

    @Test
    void 일분_안에_다시_받으면_막히고_하루_횟수는_안_센다() {
        verificationService.sendCode("a@x.com");
        assertThatThrownBy(() -> verificationService.sendCode("a@x.com"))
                .satisfies(e -> {
                    assertThat(codeOf(e)).isEqualTo(AuthErrorCode.RESEND_TOO_SOON);
                    assertThat(((AuthRetryInfo) ((CustomException) e).getData()).retryAfterSeconds()).isBetween(1L, 60L);
                });
        assertThat(redis.opsForValue().get("email:sends:a@x.com:" + LocalDate.now(ZoneId.of("Asia/Seoul")))).isEqualTo("1");
    }

    @Test
    void 틀리면_남은_횟수를_주고_다섯_번째부터는_다시_받으라고_한다() {
        verificationService.sendCode("a@x.com");
        String code = SENT.get("a@x.com");
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> verificationService.verify("a@x.com", wrong))      // 1번째
                .satisfies(e -> {
                    assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_MISMATCH);
                    assertThat(e.getMessage()).endsWith("4번 더 틀리면 인증번호를 다시 받아야 해요.");
                    assertThat(((AuthRetryInfo) ((CustomException) e).getData()).remainingAttempts()).isEqualTo(4);
                });
        for (int i = 0; i < 3; i++) {                                                 // 2~4번째
            assertThatThrownBy(() -> verificationService.verify("a@x.com", wrong))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_MISMATCH));
        }
        assertThatThrownBy(() -> verificationService.verify("a@x.com", wrong))      // 5번째 → V-07
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES));
        assertThatThrownBy(() -> verificationService.verify("a@x.com", code))       // 맞는 번호여도 → V-07
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES));
    }

    @Test
    void 번호를_한_번도_안_받았으면_시간_지남() {
        assertThatThrownBy(() -> verificationService.verify("a@x.com", "123456"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_EXPIRED));
    }

    @Test
    void 인증된_뒤_같은_확인이_또_와도_성공한다() {
        verificationService.sendCode("a@x.com");
        String code = SENT.get("a@x.com");
        verificationService.verify("a@x.com", code);
        verificationService.verify("a@x.com", code);   // V-13 두 번째 요청 — 예외 없음
        assertThat(redis.hasKey("email:verified:a@x.com")).isTrue();
    }

    @Test
    void 보내면_오늘_남은_횟수와_시간을_준다() {
        EmailCodeSendResponse res = verificationService.sendCode("a@x.com");
        assertThat(res.remainingSends()).isEqualTo(4);
        assertThat(res.expiresInSeconds()).isEqualTo(600);
        assertThat(res.resendAfterSeconds()).isEqualTo(60);
    }

    @Test
    void 메일을_못_보내면_오늘_횟수에_안_센다() {
        assertThatThrownBy(() -> verificationService.sendCode("fail@x.com"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.MAIL_SEND_FAILED));
        assertThat(redis.opsForValue().get("email:sends:fail@x.com:" + LocalDate.now(ZoneId.of("Asia/Seoul"))))
                .isEqualTo("0");
        assertThat(redis.hasKey("email:wait:fail@x.com")).isTrue();   // 리뷰 #126: 실제로 갔을 수 있어서 60초 대기는 유지
    }

    @Test   // 리뷰: 오늘 횟수를 다 쓴 뒤엔 60초 타이머(42904)가 아니라 계속 「오늘 다 씀」(42901)
    void 오늘_횟수를_다_쓰면_바로_다시_눌러도_오늘_다_씀() {
        for (int i = 0; i < 5; i++) {
            verificationService.sendCode("a@x.com");
            redis.delete("email:wait:a@x.com");
        }
        assertThatThrownBy(() -> verificationService.sendCode("a@x.com"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_SEND_LIMIT));
        assertThatThrownBy(() -> verificationService.sendCode("a@x.com"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_SEND_LIMIT));
    }

    @Test   // 리뷰: 다시 받기 메일이 실패하면 전에 받은 번호가 그대로 살아 있어야 함
    void 다시_받기_메일이_실패해도_전에_받은_번호로_인증된다() {
        verificationService.sendCode("a@x.com");
        String first = SENT.get("a@x.com");
        redis.delete("email:wait:a@x.com");
        FAIL_ONCE.add("a@x.com");
        assertThatThrownBy(() -> verificationService.sendCode("a@x.com"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.MAIL_SEND_FAILED));

        verificationService.verify("a@x.com", first);   // 예외 없음
        assertThat(redis.hasKey("email:verified:a@x.com")).isTrue();
    }

    @Test   // 리뷰: 돌려줄 키가 이미 없으면(만료·자정) -1로 남지 않음
    void 돌려줄_횟수_키가_없으면_음수로_남지_않는다() {
        store.giveBackSend("email:sends:none@x.com:2026-01-01");
        assertThat(redis.hasKey("email:sends:none@x.com:2026-01-01")).isFalse();
    }

    @Test   // 리뷰: 확인 요청이 동시에 몰려도 비교는 5번까지만 → 틀린 번호 답은 4번까지
    void 동시에_많이_확인해도_5번_넘게_맞혀_볼_수_없다() throws Exception {
        verificationService.sendCode("a@x.com");
        String code = SENT.get("a@x.com");
        String wrong = code.equals("000000") ? "111111" : "000000";

        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<AuthErrorCode>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> {
                try {
                    verificationService.verify("a@x.com", wrong);
                    return null;
                } catch (CustomException e) {
                    return codeOf(e);
                }
            }));
        }
        int mismatch = 0;
        for (Future<AuthErrorCode> f : results) {
            if (f.get() == AuthErrorCode.EMAIL_CODE_MISMATCH) mismatch++;
        }
        pool.shutdown();

        assertThat(mismatch).isLessThanOrEqualTo(4);
        assertThatThrownBy(() -> verificationService.verify("a@x.com", code))   // 맞는 번호여도 이제 다시 받아야 함
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES));
    }

    @Test   // 리뷰 #126: 메일 실패로 돌려주는 건 하루 2번까지 (메일 폭탄 막기)
    void 메일_실패로_돌려주는_횟수는_하루_2번까지() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> verificationService.sendCode("fail@x.com"))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.MAIL_SEND_FAILED));
            redis.delete("email:wait:fail@x.com");
        }
        assertThat(redis.opsForValue().get("email:sends:fail@x.com:" + LocalDate.now(ZoneId.of("Asia/Seoul"))))
                .isEqualTo("1");   // 3번 실패 중 2번만 돌려받음
    }

    @Test   // 리뷰 #126: 인증된 뒤 다른 번호로 확인하면 성공이 아니라 시간 지남 (인증 여부를 안 알려 줌)
    void 인증된_뒤_다른_번호로_확인하면_시간_지남() {
        verificationService.sendCode("a@x.com");
        String code = SENT.get("a@x.com");
        verificationService.verify("a@x.com", code);
        String other = code.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> verificationService.verify("a@x.com", other))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(AuthErrorCode.EMAIL_CODE_EXPIRED));
    }
}
