package com.dangdang.domain.user.service;

import com.dangdang.domain.user.dto.response.MeResponse;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(MeResponse::from)
                .orElseThrow(()->new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}
