package com.dangdang.domain.auth.infra;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.exception.CustomException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Spring 을 띄우지 않고 가짜 JavaMailSender 로 확인
class SmtpEmailSenderTest {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    SmtpEmailSender sender = new SmtpEmailSender(mailSender);

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        ReflectionTestUtils.setField(sender, "from", "Ddangdang2026@gmail.com");
    }

    @Test
    void 인증번호_메일에_받는사람_제목_로고가_들어간다() throws Exception {
        sender.sendVerificationCode("jisu@x.com", "012345");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("jisu@x.com");
        assertThat(sent.getSubject()).isEqualTo("[당당] 인증번호 012345");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();   // 실제로 나갈 메일 원문
        sent.writeTo(raw);
        assertThat(raw.toString()).contains("Content-ID: <wordmark>");   // 로고 그림이 붙었는지
    }

    @Test
    void 인증번호_메일이_안_가면_502() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendVerificationCode("jisu@x.com", "012345"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.MAIL_SEND_FAILED);
    }

    @Test
    void 재설정_메일은_안_가도_에러를_던지지_않는다() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> sender.sendPasswordResetLink("jisu@x.com", "http://localhost:5173/reset-password?token=abc"))
                .doesNotThrowAnyException();
    }

}
