package com.dangdang.domain.visit.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 진료 일정 수정 요청 (PATCH) — 보낸 칸만 바뀜, 안 보낸 칸은 그대로 둠
// 두 칸 모두 "안 보냄(그대로)"과 "null을 보냄(비우기)"을 구분해야 해서 record 대신 class로 둠
//   careSiteId: null을 보내면 병원 지우기 / visitAt: null을 보내면 날짜 미정으로
@Getter
@NoArgsConstructor
public class VisitUpdateRequest {

    private Long careSiteId;
    private Instant visitAt;

    @JsonIgnore   // 서버 내부 표시라 JSON으로 받지 않음
    private boolean careSiteIdSent;
    @JsonIgnore
    private boolean visitAtSent;

    @JsonProperty("careSiteId")
    public void setCareSiteId(Long careSiteId) {
        this.careSiteId = careSiteId;
        this.careSiteIdSent = true;
    }

    @JsonProperty("visitAt")
    public void setVisitAt(Instant visitAt) {
        this.visitAt = visitAt;
        this.visitAtSent = true;
    }

    // 바꿀 값이 하나도 없는 요청인지. Swagger·JSON에 "empty" 칸으로 안 보이게 뺌
    @JsonIgnore
    public boolean isEmpty() {
        return !careSiteIdSent && !visitAtSent;
    }
}
