package com.dangdang.domain.community.service;

import com.dangdang.domain.community.exception.CommunityErrorCode;
import com.dangdang.global.exception.CustomException;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class NicknameRule {

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z0-9 ]+$");

    private NicknameRule() {
    }

    static String normalizeAndCheck(String raw) {
        String nickname = Normalizer.normalize(raw, Normalizer.Form.NFC).trim();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 2 || length > 10 || !ALLOWED.matcher(nickname).matches()) {
            throw new CustomException(CommunityErrorCode.INVALID_NICKNAME);
        }
        return nickname;
    }
}
