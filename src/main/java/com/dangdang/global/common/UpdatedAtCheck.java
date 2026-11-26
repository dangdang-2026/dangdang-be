package com.dangdang.global.common;

import java.time.Duration;
import java.time.Instant;

// 「내가 본 버전」 확인: 수정 요청의 expectedUpdatedAt이 지금 기록의 updatedAt과 같은지 (#172)
// 다른 기기에서 먼저 고쳤으면 다름 → 부르는 쪽이 409로 막음 (말없이 덮어쓰지 않게)
public final class UpdatedAtCheck {

    private UpdatedAtCheck() {
    }

    // 안 보냈으면(null) 확인하지 않음 → 이미 배포된 앱은 지금처럼 동작
    public static boolean isStale(Instant expected, Instant current) {
        return expected != null && !sameMoment(expected, current);
    }

    // 두 시각이 같은지 (1밀리초 안쪽 차이는 같다고 봄)
    // 수정 응답의 updatedAt은 메모리 값(나노초)이고 DB는 마이크로초로 반올림해 저장해서, 아래 자릿수가 조금 다를 수 있음
    //   밀리초로 잘라 비교하면 반올림이 밀리초 경계를 넘을 때 멀쩡한 요청도 409가 남
    public static boolean sameMoment(Instant a, Instant b) {
        return a != null && b != null && Duration.between(a, b).abs().compareTo(Duration.ofMillis(1)) < 0;
    }
}
