package com.dangdang.domain.user.service;

import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 건강 기록을 저장하기 전 문지기 — 건강정보(SENSITIVE) 동의가 없으면 저장하지 않는다 (M-07b 철회 뒤 저장 막기)
// 부르는 쪽 트랜잭션 안에서 이 사람 줄을 FOR SHARE로 잠그고 본다 → 철회(FOR UPDATE)와 겹치면 철회가 끝난 뒤에 보고 403,
// 저장이 먼저 잡았으면 철회가 저장 끝까지 기다렸다가 같이 지운다 (철회 도중에 저장돼서 남거나, 외래키로 철회가 실패하지 않게)
// 다른 줄을 잠그기 전에 맨 먼저 부른다 (식사·메모 줄을 먼저 잡고 여기서 기다리면 철회와 서로 기다릴 수 있음)
// 지우기·읽기에는 안 부른다
@Component
@RequiredArgsConstructor
public class SensitiveConsentGuard {

    private final UserRepository userRepository;
    private final ConsentService consentService;

    @Transactional(propagation = Propagation.MANDATORY)   // 잠금이 저장 끝까지 가야 해서 트랜잭션 안에서만
    public void require(Long userId) {
        userRepository.findByIdForShare(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (!consentService.isGranted(userId, ConsentType.SENSITIVE)) {
            throw new CustomException(UserErrorCode.SENSITIVE_CONSENT_REQUIRED);
        }
    }
}
