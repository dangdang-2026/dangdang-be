package com.dangdang.domain.push.dto.response;

//보낸 결과 -> 성공, 끊긴 구독, 잠깐 실패 개수
public record PushDeliveryResponse(int sentCount, int revokedCount, int failedCount) {

    //보낼 기기가 하나도 없었나 (get is로 시작하지 않아서 JSON 칸이 되지 않는다)
    public boolean hadNoTarget(){
        return sentCount + revokedCount + failedCount == 0;
    }
}
