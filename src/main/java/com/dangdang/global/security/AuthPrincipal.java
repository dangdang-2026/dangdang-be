package com.dangdang.global.security;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.Role;

// 로그인한 사람. 컨트롤러에서 @AuthenticationPrincipal AuthPrincipal 로 받는다
public record AuthPrincipal(Long userId, Role role) {

    public static AuthPrincipal from(User user) {
        return new AuthPrincipal(user.getId(), user.getRole());
    }
}
