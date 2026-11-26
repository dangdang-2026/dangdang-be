package com.dangdang.domain.card;

import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.domain.card.infra.CardImageStorage;
import com.dangdang.domain.card.infra.OffCardImageStorage;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.exception.CustomException;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 카드뉴스를 만든 이미지로 올리기 (05 결정). S3 대신 가짜 저장소
class CardImageFlowTest extends ReminderTestBase {

    static final String URL = "https://cards.example/cards/a.png";

    @MockitoBean CardImageStorage storage;

    Cookie jisu;
    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
        when(storage.put(anyString(), any(), anyString())).thenReturn(URL);
    }

    @AfterEach
    void cleanCards() {
        jdbc.update("DELETE FROM admin_logs");
        jdbc.update("DELETE FROM content_cards");
    }

    private static byte[] image(String format, int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void PNG를_올리면_주소와_크기를_돌려준다() throws Exception {
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "1장.png", "image/png", image("png", 1080, 1350))).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value(URL))
                .andExpect(jsonPath("$.data.width").value(1080))
                .andExpect(jsonPath("$.data.height").value(1350));
        verify(storage).put(startsWith("cards/"), any(), eq("image/png"));
    }

    @Test
    void 이름과_형식이_PNG여도_내용이_JPG면_JPG로_올린다() throws Exception {
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "1장.png", "image/png", image("jpg", 100, 125))).cookie(admin))
                .andExpect(status().isCreated());
        verify(storage).put(endsWith(".jpg"), any(), eq("image/jpeg"));
    }

    @Test
    void 이미지가_아니거나_없거나_크면_올리지_않는다() throws Exception {
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "a.png", "image/png", "그림 아님".getBytes())).cookie(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CARD_40002"));
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "a.gif", "image/gif", image("gif", 10, 10))).cookie(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CARD_40002"));
        mvc.perform(multipart("/api/admin/card-images").cookie(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CARD_40001"));
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1])).cookie(admin))
                .andExpect(status().isContentTooLarge());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void 운영자가_아니면_못_올린다() throws Exception {
        mvc.perform(multipart("/api/admin/card-images").file(
                        new MockMultipartFile("image", "1장.png", "image/png", image("png", 10, 10))).cookie(jisu))
                .andExpect(status().isForbidden());
    }

    @Test
    void 이미지_장은_대체_텍스트가_있어야_만들어지고_앱에_주소와_대체_텍스트가_간다() throws Exception {
        String noAlt = card("{\"title\":\"1장\",\"imageUrl\":\"" + URL + "\"}");
        mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON).content(noAlt))
                .andExpect(status().isBadRequest());

        String ok = card("{\"title\":\"1장\",\"imageUrl\":\"" + URL + "\",\"alt\":\"식후 혈당은 정해 준 시간에 잽니다.\"}");
        String res = mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(res, "$.data.id");
        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());

        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.slides[0].imageUrl").value(URL))
                .andExpect(jsonPath("$.data.slides[0].alt").value("식후 혈당은 정해 준 시간에 잽니다."))
                .andExpect(jsonPath("$.data.slides[0].text").doesNotExist())
                .andExpect(jsonPath("$.data.slides[0].textOrImage").doesNotExist());
    }

    @Test
    void 글_장은_예전처럼_본문이_있어야_하고_http_이미지는_안_된다() throws Exception {
        mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(card("{\"title\":\"1장\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(card("{\"title\":\"1장\",\"imageUrl\":\"http://a.example/a.png\",\"alt\":\"글\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(card("{\"title\":\"1장\",\"text\":\"본문\"}")))
                .andExpect(status().isCreated());
    }

    @Test
    void S3를_켜지_않은_서버는_503() {
        assertThatThrownBy(() -> new OffCardImageStorage().put("cards/a.png", new byte[1], "image/png"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CardErrorCode.IMAGE_STORAGE_OFF);
    }

    private static String card(String slide) {
        return "{\"title\":\"식후 혈당, 언제 재나요?\",\"series\":\"GUIDELINE\",\"body\":[" + slide + "],"
                + "\"sources\":[{\"title\":\"출처 1\",\"url\":\"https://a.example\"}],\"checkedOn\":\"2026-11-09\"}";
    }
}
