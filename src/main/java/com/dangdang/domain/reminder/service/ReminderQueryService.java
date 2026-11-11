package com.dangdang.domain.reminder.service;

import com.dangdang.domain.reminder.dto.response.NextReminderResponse;
import com.dangdang.domain.reminder.dto.response.ReminderPageResponse;
import com.dangdang.domain.reminder.dto.response.ReminderResponse;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderQueryService {

    private final ReminderRepository reminderRepository;

    // from·to는 한국 달력 날짜, 양쪽 날짜 모두 포함
    public ReminderPageResponse list(Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "from은 to보다 늦을 수 없어요.");
        }
        Instant start = from.atStartOfDay(ClockConfig.KST).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ClockConfig.KST).toInstant();
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("scheduledAt"), Sort.Order.desc("id")));

        Slice<Reminder> slice = reminderRepository.findByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                userId, start, end, pageable);
        return new ReminderPageResponse(slice.map(ReminderResponse::from).getContent(), page, size, slice.hasNext());
    }


    //오늘 API의 nextReminder — 없으면 null
    public NextReminderResponse findNext(Long userId) {
        return reminderRepository.findNextByDueTime(userId, ReminderStatus.SCHEDULED, Limit.of(1)).stream()
                .findFirst()
                .map(NextReminderResponse::from)
                .orElse(null);
    }
}
