package com.dangdang.domain.push.infra;

import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.enums.PushSendResult;

public interface WebPushSender {
    PushSendResult send(PushSubscription subscription, PushMessage message);
}
