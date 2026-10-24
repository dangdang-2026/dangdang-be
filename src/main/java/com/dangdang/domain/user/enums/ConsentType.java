package com.dangdang.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsentType {
    AGE_14(true,"v1"),
    TERMS(true, "v1"),
    PRIVACY(true, "v1"),
    SENSITIVE(true, "v1"),
    MARKETING(false, "v1"),
    AI_PHOTO(false, "v1"),
    AI_CHAT_RECORDS(false, "v1"),
    OVERSEAS_TRANSFER(false, "v1");

    private final boolean required;
    private final String version;
}
