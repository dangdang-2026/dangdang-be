package com.dangdang.domain.push.infra;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.global.config.WebPushConfig;
import com.dangdang.global.config.property.VapidProperties;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicHttpResponse;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Spring 을 띄우지 않고 가짜 PushService 로 확인 — 인터넷으로는 아무것도 안 나간다
class VapidWebPushSenderTest {

    static final String ENDPOINT = "https://web.push.apple.com/device-a";
    static final PushMessage MESSAGE = new PushMessage("당당", "혈당 잴 시간이에요", "/today", 30L);

    PushService pushService = mock(PushService.class);
    VapidWebPushSender sender = new VapidWebPushSender(pushService, JsonMapper.builder().build());

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    // 키를 파일에 적지 않으려고 테스트할 때마다 새로 만든다
    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String publicKeyOf(KeyPair pair) {
        return encode(Utils.encode((ECPublicKey) pair.getPublic()));
    }

    private static String privateKeyOf(KeyPair pair) {
        return encode(Utils.encode((ECPrivateKey) pair.getPrivate()));
    }

    private static PushSubscription subscription() throws Exception {
        String auth = encode(new byte[16]);
        return PushSubscription.of(null, ENDPOINT, publicKeyOf(newKeyPair()), auth, null, Instant.now());
    }

    private void pushServerAnswers(int status) throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "");
        when(pushService.sendAsync(any(Notification.class), eq(Encoding.AES128GCM)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }

    @Test
    void 푸시_서버가_201이면_SENT() throws Exception {
        pushServerAnswers(201);
        assertThat(sender.send(subscription(), MESSAGE)).isEqualTo(PushSendResult.SENT);
    }

    @Test
    void 푸시_서버가_404나_410이면_GONE() throws Exception {
        pushServerAnswers(404);
        assertThat(sender.send(subscription(), MESSAGE)).isEqualTo(PushSendResult.GONE);
        pushServerAnswers(410);
        assertThat(sender.send(subscription(), MESSAGE)).isEqualTo(PushSendResult.GONE);
    }

    @Test
    void 푸시_서버가_500이면_FAILED() throws Exception {
        pushServerAnswers(500);
        assertThat(sender.send(subscription(), MESSAGE)).isEqualTo(PushSendResult.FAILED);
    }

    @Test
    void 키가_망가져_있어도_예외를_던지지_않고_FAILED() {
        PushSubscription broken = PushSubscription.of(null, ENDPOINT, "not-a-key", "x", null, Instant.now());
        assertThat(sender.send(broken, MESSAGE)).isEqualTo(PushSendResult.FAILED);
    }

    @Test
    void 진짜_키로_암호화한_요청이_만들어진다() throws Exception {
        pushServerAnswers(201);
        sender.send(subscription(), MESSAGE);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(pushService).sendAsync(captor.capture(), eq(Encoding.AES128GCM));
        Notification notification = captor.getValue();
        assertThat(new String(notification.getPayload(), StandardCharsets.UTF_8))
                .isEqualTo("{\"title\":\"당당\",\"body\":\"혈당 잴 시간이에요\",\"url\":\"/today\",\"reminderId\":30}");

        KeyPair vapid = newKeyPair();
        PushService real = new WebPushConfig().pushService(
                new VapidProperties(publicKeyOf(vapid), privateKeyOf(vapid), "mailto:test@x.com"));
        HttpPost post = real.preparePost(notification, Encoding.AES128GCM);   // 만들기만 하고 보내지 않는다

        assertThat(post.getURI().toString()).isEqualTo(ENDPOINT);
        assertThat(post.getFirstHeader("TTL").getValue()).isEqualTo("3600");
        assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid t=");
    }

    @Test
    void VAPID_값이_비어_있으면_서버가_뜰_때_바로_알려_준다() {
        assertThatThrownBy(() -> new WebPushConfig().pushService(new VapidProperties("", "", "")))
                .isInstanceOf(IllegalStateException.class);
    }
}
