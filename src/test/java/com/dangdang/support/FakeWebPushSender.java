package com.dangdang.support;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.infra.WebPushSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 진짜 푸시 서버 대신: 보낸 내용을 적어 두고, 주소별로 정해 둔 대답을 돌려준다
public class FakeWebPushSender implements WebPushSender {

    public final List<PushMessage> sent = new ArrayList<>();
    public final Map<String, PushSendResult> answers = new HashMap<>();
    public final Set<String> explodes = new HashSet<>();

    @Override
    public PushSendResult send(PushSubscription subscription, PushMessage message) {
        if (explodes.contains(subscription.getEndpoint())) {
            throw new IllegalStateException("일부러 터뜨림");
        }
        sent.add(message);
        return answers.getOrDefault(subscription.getEndpoint(), PushSendResult.SENT);
    }

    public void clear() {
        sent.clear();
        answers.clear();
        explodes.clear();
    }
}
