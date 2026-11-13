package com.dangdang.domain.push.infra;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushMessage(String title, String body, String url, Long reminderId) {

    private static final String HIDDEN_BODY = "확인할 일이 있어요";

    //잠금화면 문구 가리기 -> 임신 혈당이 그대로 보이면 안되는 사람이 있어서 위와 같이 설정하기!
    public PushMessage masked() {
        return new PushMessage(title, HIDDEN_BODY, url, reminderId);
    }
}
