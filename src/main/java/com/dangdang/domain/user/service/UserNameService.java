package com.dangdang.domain.user.service;

import com.dangdang.domain.user.dto.request.NameUpdateRequest;
import com.dangdang.domain.user.dto.response.NameUpdateResponse;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNameService {

    private final UserRepository userRepository;

    @Transactional
    public NameUpdateResponse change(Long userId, NameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        // 1차: 화면이 들고 있던 version과 지금 version 비교
        if (Long.parseLong(request.version()) != user.getRowVersion()) {
            throw new CustomException(UserErrorCode.VERSION_CONFLICT);
        }
        user.changeDisplayName(request.name());
        try {
            // 지금 바로 UPDATE를 보낸다 -> 올라간 version을 응답에 담을 수 있다
            userRepository.saveAndFlush(user);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 2차: 위 비교와 UPDATE 사이에 다른 요청이 먼저 저장한 경우
            throw new CustomException(UserErrorCode.VERSION_CONFLICT);
        }
        return NameUpdateResponse.from(user);
    }
}
