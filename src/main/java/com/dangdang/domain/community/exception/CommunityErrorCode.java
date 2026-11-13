package com.dangdang.domain.community.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements BaseErrorCode {
    INVALID_NICKNAME("COMMUNITY_40001", "닉네임은 2~10자, 한글·영문·숫자로 적어 주세요.", HttpStatus.BAD_REQUEST),
    EMPTY_PROFILE_UPDATE("COMMUNITY_40002", "바꿀 내용을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    CANNOT_BLOCK_SELF("COMMUNITY_40003", "자기 자신은 안 보기 할 수 없어요.", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("COMMUNITY_40004", "페이지 정보가 올바르지 않아요. 처음부터 다시 불러와 주세요.", HttpStatus.BAD_REQUEST),
    PROFILE_NOT_FOUND("COMMUNITY_40401", "커뮤니티 프로필을 먼저 만들어 주세요.", HttpStatus.NOT_FOUND),
    BLOCK_TARGET_NOT_FOUND("COMMUNITY_40402", "안 보기 할 사람을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    NICKNAME_DUPLICATED("COMMUNITY_40901", "이미 누가 쓰고 있는 이름이에요.", HttpStatus.CONFLICT),
    PROFILE_ALREADY_EXISTS("COMMUNITY_40902", "이미 커뮤니티 프로필이 있어요.", HttpStatus.CONFLICT),
    NICKNAME_SUGGESTION_FAILED("COMMUNITY_40903", "추천할 이름을 찾지 못했어요. 직접 적어 주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
