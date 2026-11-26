package com.dangdang.domain.admin.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// admin_logs.action. 신고 처리(가리기·되돌리기·문제없음)는 admin_actions 에 따로 있다
@Getter
@RequiredArgsConstructor
public enum AdminLogAction {
    SANCTION_CREATE(AdminLogTarget.SANCTION),
    SANCTION_LIFT(AdminLogTarget.SANCTION),
    ANNOUNCEMENT_CREATE(AdminLogTarget.ANNOUNCEMENT),
    ANNOUNCEMENT_END(AdminLogTarget.ANNOUNCEMENT),
    CARD_CREATE(AdminLogTarget.CARD),
    CARD_PUBLISH(AdminLogTarget.CARD),
    CARD_CORRECT(AdminLogTarget.CARD),
    CARD_REVIEW(AdminLogTarget.CARD),
    CARD_WITHDRAW(AdminLogTarget.CARD);

    private final AdminLogTarget target;
}
