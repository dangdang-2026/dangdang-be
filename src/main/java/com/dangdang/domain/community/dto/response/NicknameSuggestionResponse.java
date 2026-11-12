package com.dangdang.domain.community.dto.response;

// 추천 이름 하나. 저장된 건 아님 -> 마음에 들면 POST /api/community/profiles 로 생성
public record NicknameSuggestionResponse(String nickname) {
}
