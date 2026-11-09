package com.dangdang.domain.push.enums;

public enum PushSendResult {
    SENT,       //받아 줬다
    GONE,       //404,410 : 이 구독은 죽었다.(앱 삭제, 권한 끔)
    FAILED      // 그 밖의 실패 -> 구독은 그대로 두기
}
