package com.dangdang.domain.criteria.entity;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Entity
@Table(name = "criteria_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriteriaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long careSiteId;

    @JdbcTypeCode(SqlTypes.SMALLINT)         // DB 칸이 SMALLINT라서
    private Integer postMealMinutes;         // null = 아직 몰라요

    @JdbcTypeCode(SqlTypes.JSON)             // DB 칸이 JSONB라서
    private List<MeasureSlot> measureSlots;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer fastingTarget;           // 공복 목표(미만), null이면 비교 안 함

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer postMealTarget;          // 식후 목표(미만)

    @Column(length = 255)
    private String sourceNote;               // "10/1 진료 때 들은 대로" 같은 메모

    @Column(nullable = false)
    private LocalDate effectiveFrom;         // 적용 시작일 (한국 날짜)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;               // 저장한 순간

    public static CriteriaVersion of(User user, Integer postMealMinutes, List<MeasureSlot> measureSlots,
                                     Integer fastingTarget, Integer postMealTarget, String sourceNote,
                                     LocalDate effectiveFrom, Instant now) {
        CriteriaVersion c = new CriteriaVersion();
        c.user = user;
        c.postMealMinutes = postMealMinutes;
        c.measureSlots = measureSlots;
        c.fastingTarget = fastingTarget;
        c.postMealTarget = postMealTarget;
        c.sourceNote = sourceNote;
        c.effectiveFrom = effectiveFrom;
        c.createdAt = now;
        return c;
    }

    // 기준이 하나도 없는 사람용 "빈 기준" (목표·시간 다 비움)
    public static CriteriaVersion empty(User user, LocalDate today, Instant now) {
        return of(user, null, null, null, null, null, today, now);
    }
}
