package com.dangdang.domain.card.service;

import com.dangdang.domain.card.dto.response.CardImageResponse;
import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.domain.card.infra.CardImageStorage;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// 운영자가 피그마나 캔바에서 만든 카드 이미지를 S3에 올린다. 카드에 붙이는 건 만들기·정정하기에서 주소로
@Service
@RequiredArgsConstructor
public class CardImageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;   // 1080×1350 PNG도 보통 2~3MB

    // 파일 이름이나 Content-Type은 믿지 않고, 내용을 열어 본 형식으로 정한다
    private static final Map<String, Format> FORMATS = Map.of(
            "png", new Format("png", "image/png"),
            "jpeg", new Format("jpg", "image/jpeg"));

    private final CardImageStorage storage;

    public CardImageResponse upload(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new CustomException(CardErrorCode.IMAGE_REQUIRED);
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {   // multipart 한도(10MB)보다 작게 한 번 더
            throw new MaxUploadSizeExceededException(MAX_IMAGE_BYTES);
        }
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new CustomException(CardErrorCode.NOT_AN_IMAGE);
        }
        Probe probe = probe(bytes);
        String key = "cards/" + UUID.randomUUID() + "." + probe.format().ext();
        String url = storage.put(key, bytes, probe.format().contentType());
        return new CardImageResponse(url, probe.width(), probe.height());
    }

    // 머리 부분만 읽어 형식과 크기를 본다 (그림 전체를 풀지 않는다)
    private static Probe probe(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new CustomException(CardErrorCode.NOT_AN_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                Format format = FORMATS.get(reader.getFormatName().toLowerCase());
                if (format == null) {   // GIF, BMP 같은 것
                    throw new CustomException(CardErrorCode.NOT_AN_IMAGE);
                }
                reader.setInput(in);
                return new Probe(format, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {   // 머리는 PNG인데 안이 깨진 파일
            throw new CustomException(CardErrorCode.NOT_AN_IMAGE);
        }
    }

    private record Format(String ext, String contentType) {
    }

    private record Probe(Format format, int width, int height) {
    }
}
