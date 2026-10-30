package com.dangdang.global.security;

import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.exception.model.BaseErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(BaseResponse.fail(errorCode)));
    }
}
