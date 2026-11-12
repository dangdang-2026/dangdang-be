package com.dangdang.domain.user.service;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.user.dto.request.DueDateUpdateRequest;
import com.dangdang.domain.user.dto.response.DueDateResponse;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DueDateService {

    private final UserRepository userRepository;
    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;

    @Transactional
    public DueDateResponse save(Long userId, DueDateUpdateRequest request) {
        // 잠그고 읽기: 저장 버튼을 두 번 눌러도 두 번째 요청은 첫 번째가 끝난 뒤에 진행한다
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Instant now = clock.instant();

        Optional<Pregnancy> active = pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED);
        if (active.isPresent()) {
            active.get().changeDueDate(request.dueDate(), now);   // 있으면 그 줄을 고친다
        } else {
            pregnancyRepository.save(Pregnancy.start(user, request.dueDate(), now));   // 없으면 새 줄
        }
        return new DueDateResponse(request.dueDate());
    }
}
