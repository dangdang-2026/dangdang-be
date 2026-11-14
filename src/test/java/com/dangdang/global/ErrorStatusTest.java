package com.dangdang.global;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 요청이 잘못된 것(클라이언트 잘못)은 500(서버 고장)이 아니라 4xx로 답한다
@SpringBootTest
@AutoConfigureMockMvc
class ErrorStatusTest {
    @Autowired MockMvc mvc;

    @Test
    void JSON이_아닌_형식으로_보내면_415() throws Exception {
        mvc.perform(post("/api/auth/kakao").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_41501"));
    }

    @Test
    void 없는_메서드로_부르면_405() throws Exception {
        mvc.perform(get("/api/auth/kakao"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40501"));
    }

    @Test
    void 없는_주소면_404() throws Exception {
        mvc.perform(get("/api/auth/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40401"));
    }
}
