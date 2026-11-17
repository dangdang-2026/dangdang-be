package com.dangdang.domain.postpartum.repository;

import com.dangdang.domain.postpartum.entity.PostpartumTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PostpartumTestRepository extends JpaRepository<PostpartumTest, Long> {

    List<PostpartumTest> findByUserIdOrderByTestDateAscIdAsc(Long userId);

    List<PostpartumTest> findByUserIdAndTestDateGreaterThanEqualOrderByTestDateAscIdAsc(Long userId, LocalDate from);

    boolean existsByUserIdAndTestDateGreaterThanEqual(Long userId, LocalDate from);

    Optional<PostpartumTest> findByIdAndUserId(Long id, Long userId);

    // 전날 알림 차례: 내일 검사 · 전날 알림 켬, 아직 처리 안 함 (회원도 같이 읽는다)
    @Query("select t from PostpartumTest t join fetch t.user where t.testDate = :tomorrow"
            + " and t.remindDayBefore = true and t.dayBeforeSentAt is null")
    List<PostpartumTest> findDayBeforeDue(LocalDate tomorrow);

    // 당일 알림 차례
    @Query("select t from PostpartumTest t join fetch t.user where t.testDate = :today"
            + " and t.remindSameDay = true and t.sameDaySentAt is null")
    List<PostpartumTest> findSameDayDue(LocalDate today);
}
