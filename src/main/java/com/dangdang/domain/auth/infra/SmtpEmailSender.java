package com.dangdang.domain.auth.infra;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.exception.CustomException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

@Slf4j
@Component
@ConditionalOnProperty(name = "mail.sender", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    // 인증번호는 기다렸다가 결과를 알려 준다 (안 가면 다시 받기를 눌러야 하니까)
    @Override
    public void sendVerificationCode(String email, String code) {
        try {
            send(email, "[당당] 인증번호 " + code, layout("인증번호를 알려 드려요", """
                    <p style="margin:0 0 16px">아래 인증번호를 당당 화면에 입력해 주세요.</p>
                    <div style="background:#FFF6E5;border-radius:12px;padding:20px;text-align:center;font-size:32px;font-weight:800;letter-spacing:8px;color:#191f28">%s</div>
                    <p style="margin:16px 0 0;font-size:13px;color:#8b95a1">인증번호는 <b style="color:#4e5968">10분 동안</b>만 쓸 수 있어요.</p>
                    <div style="margin:24px 0 0;padding:16px 0 0;border-top:1px solid #e5e8eb;font-size:13px;color:#8b95a1;line-height:1.7">
                      직접 요청하지 않았나요?<br>이 메일은 무시해도 괜찮아요.
                    </div>
                    """.formatted(code)));
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("[Mail] 인증번호 메일 실패: {}", e.getMessage());
            throw new CustomException(AuthErrorCode.MAIL_SEND_FAILED);
        }
    }

    // 재설정 링크는 따로 보내고(@Async) 실패해도 응답은 같게 (계정이 있는지 드러나지 않게)
    @Async
    @Override
    public void sendPasswordResetLink(String email, String link) {
        try {
            send(email, "[당당] 비밀번호 재설정", layout("비밀번호를 다시 정해요", """
                    <p style="margin:0 0 20px">아래 버튼을 눌러 새 비밀번호를 정해 주세요.</p>
                    <a href="%s" style="display:block;background:#FBB315;color:#ffffff;border-radius:12px;padding:16px;text-align:center;font-size:16px;font-weight:700;text-decoration:none">비밀번호 다시 정하기</a>
                    <p style="margin:16px 0 0;font-size:13px;color:#8b95a1">링크는 <b style="color:#4e5968">30분 동안 한 번만</b> 쓸 수 있어요.</p>
                    <div style="margin:24px 0 0;padding:16px 0 0;border-top:1px solid #e5e8eb;font-size:13px;color:#8b95a1;line-height:1.7">
                      직접 요청하지 않았나요?<br>이 메일은 무시해도 괜찮아요. 비밀번호는 바뀌지 않아요.
                    </div>
                    """.formatted(link)));
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("[Mail] 재설정 메일 실패: {}", e.getMessage());
        }
    }


    private String layout(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#FFF6E5">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#FFF6E5;padding:32px 16px;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;border:1px solid #e5e8eb">
                      <tr><td align="center" style="padding:32px 28px 0"><img src="cid:wordmark" alt="당당" width="71" height="32" style="display:block;margin:0 auto;border:0"></td></tr>
                      <tr><td align="center" style="padding:20px 28px 0;font-size:20px;font-weight:700;color:#191f28;line-height:1.4;text-align:center">%s</td></tr>
                      <tr><td align="center" style="padding:12px 28px 32px;font-size:15px;color:#4e5968;line-height:1.6;text-align:center">%s</td></tr>
                    </table>
                    <p style="max-width:480px;margin:16px auto 0;font-size:12px;color:#8b95a1;line-height:1.5;text-align:center">이 메일은 보내기 전용이라 답장은 받을 수 없어요.</p>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(title, body);
    }

    private void send(String to, String subject, String html)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");   // true = 그림을 같이 붙일 수 있는 메일
        helper.setFrom(from, "당당");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        helper.addInline("wordmark", new ClassPathResource("mail/wordmark.png"));
        mailSender.send(message);
    }
}