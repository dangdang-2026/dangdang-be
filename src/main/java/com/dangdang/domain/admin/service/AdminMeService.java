package com.dangdang.domain.admin.service;

import com.dangdang.domain.admin.dto.response.AdminMeResponse;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 운영자 화면 오른쪽 위 계정 칸. ADMIN인지는 SecurityConfig가 이미 걸렀다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMeService {

    private final UserRepository userRepository;

    public AdminMeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return new AdminMeResponse(user.getId(), user.getDisplayName(), user.getEmail());
    }
}
