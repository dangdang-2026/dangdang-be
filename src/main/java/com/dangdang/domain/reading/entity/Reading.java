package com.dangdang.domain.reading.entity;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

// readings 테이블 한 줄 = 혈당 기록 하나 (created_at·updated_at은 BaseTimeEntity가 채운다)
@Getter
@Entity
@Table(name = "readings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reading extends BaseTimeEntity {

    // 잰 시각보다 이만큼 넘게 늦게 저장하면 "늦게 적음"
    private static final Duration LATE_AFTER = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long mealId;                     // 어떤 식사의 식후 혈당인지 (없을 수 있음)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criteria_version_id")
    private CriteriaVersion criteriaVersion; // 잴 때 적용 중이던 병원 기준

    @Enumerated(EnumType.STRING)             // DB에 "FASTING" 같은 글자로 저장
    @Column(nullable = false, length = 10)
    private ReadingType readingType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MeasureSlot slot;                // 병원에서 재라고 한 때 중 어디인지 (없을 수 있음)

    private Instant scheduledAt;             // 알림 예정 시각 (알림 기능에서 채운다)

    @Column(nullable = false)
    private Instant measuredAt;              // 실제로 잰 시각

    @JdbcTypeCode(SqlTypes.SMALLINT)         // DB 칸이 SMALLINT라서
    @Column(nullable = false)
    private Integer value;                   // mg/dL

    @Column(nullable = false)
    private boolean enteredLate;             // 30분 넘게 늦게 적었는지 (서버가 계산)

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 새 기록 만들기. now = 저장하는 순간 (늦게 적음 계산용)
    public static Reading of(User user, CriteriaVersion criteriaVersion, ReadingType readingType,
                             MeasureSlot slot, Instant measuredAt, Integer value, Long mealId, Instant now) {
        Reading r = new Reading();
        r.user = user;
        r.mealId = mealId;
        r.criteriaVersion = criteriaVersion;
        r.readingType = readingType;
        r.slot = slot;
        r.measuredAt = measuredAt;
        r.value = value;
        r.enteredLate = Duration.between(measuredAt, now).compareTo(LATE_AFTER) > 0;
        return r;
    }

    // 수정. null로 온 칸은 그대로 둠
    // 잰 시각이 바뀌면 그 날짜의 병원 기준(criteriaVersion)도 같이 받아서 바꾸고, 늦게 적음도 처음 적은 시각 기준으로 다시 계산함
    public void update(Integer value, Instant measuredAt, CriteriaVersion criteriaVersion,
                       ReadingType readingType, MeasureSlot slot, Long mealId) {
        if (value != null) this.value = value;
        if (mealId != null) this.mealId = mealId;
        if (readingType != null) this.readingType = readingType;
        if (slot != null) this.slot = slot;
        if (measuredAt != null) {
            this.measuredAt = measuredAt;
            this.criteriaVersion = criteriaVersion;
            this.enteredLate = Duration.between(measuredAt, getCreatedAt()).compareTo(LATE_AFTER) > 0;
        }
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠 → 조회 쿼리들이 deletedAt is null 로 걸러냄
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
