package com.dangdang.domain.postpartum.repository;

import com.dangdang.domain.postpartum.entity.PostpartumTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PostpartumTestRepository extends JpaRepository<PostpartumTest, Long> {

    // 산후이거나 졸업한 사람에게만 검사 알림을 보낸다 — 진행 중인 임신이 산후가 아니면(출산 되돌리기로 임신 중 · 입원 · 다른 상황) 안 보냄
    // 일정은 남아 있어도 엉뚱한 알림이 안 가게. 졸업한 사람(진행 중인 임신 없음)은 산후 검사를 받는 중이라 그대로 보낸다
    // 졸업 뒤 새 임신을 시작하면 지난 산후 검사 알림도 멈춘다 (의도한 것 — 지금 임신과 상관없는 알림이 가지 않게)
    String POSTPARTUM_OR_GRADUATED = """
            NOT EXISTS (SELECT 1 FROM Pregnancy p WHERE p.user = t.user
                AND p.state IN (com.dangdang.domain.pregnancy.enums.PregnancyState.PREGNANT,
                                com.dangdang.domain.pregnancy.enums.PregnancyState.HOSPITALIZED,
                                com.dangdang.domain.pregnancy.enums.PregnancyState.PAUSED))
            """;

    List<PostpartumTest> findByUserIdOrderByTestDateAscIdAsc(Long userId);

    List<PostpartumTest> findByUserIdAndTestDateGreaterThanEqualOrderByTestDateAscIdAsc(Long userId, LocalDate from);

    boolean existsByUserIdAndTestDateGreaterThanEqual(Long userId, LocalDate from);

    Optional<PostpartumTest> findByIdAndUserId(Long id, Long userId);

    // 전날 알림 차례: 내일 검사 · 전날 알림 켬, 아직 처리 안 함 (회원도 같이 읽는다)
    @Query("select t from PostpartumTest t join fetch t.user where t.testDate = :tomorrow"
            + " and t.remindDayBefore = true and t.dayBeforeSentAt is null and " + POSTPARTUM_OR_GRADUATED
            + " order by t.id")
    List<PostpartumTest> findDayBeforeDue(LocalDate tomorrow);

    // 당일 알림 차례
    @Query("select t from PostpartumTest t join fetch t.user where t.testDate = :today"
            + " and t.remindSameDay = true and t.sameDaySentAt is null and " + POSTPARTUM_OR_GRADUATED
            + " order by t.id")
    List<PostpartumTest> findSameDayDue(LocalDate today);

    // 보내기 직전에 「처리함」으로 적는다 — 아직 그 조건 그대로일 때만 1
    // (다른 서버·다음 바퀴가 먼저 잡았거나, 그사이 날짜를 바꿨거나·알림을 껐거나·출산을 되돌렸으면 0 → 보내지 않음. 같은 알림 두 번 방지)
    @Modifying
    @Query("update PostpartumTest t set t.dayBeforeSentAt = :now where t.id = :id and t.testDate = :testDate"
            + " and t.remindDayBefore = true and t.dayBeforeSentAt is null and " + POSTPARTUM_OR_GRADUATED)
    int claimDayBefore(Long id, LocalDate testDate, Instant now);

    @Modifying
    @Query("update PostpartumTest t set t.sameDaySentAt = :now where t.id = :id and t.testDate = :testDate"
            + " and t.remindSameDay = true and t.sameDaySentAt is null and " + POSTPARTUM_OR_GRADUATED)
    int claimSameDay(Long id, LocalDate testDate, Instant now);
}
