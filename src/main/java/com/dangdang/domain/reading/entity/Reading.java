package com.dangdang.domain.reading.entity;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.MeterLimit;
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

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private MeterLimit meterLimit;           // 혈당계에 HI·LO가 떴으면 그 글자 (value는 끝값 600·20). 숫자면 null

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer originalValue;           // 처음 저장한 값. 값을 안 고쳤거나 처음 값으로 되돌렸으면 null

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private MeterLimit originalMeterLimit;   // 처음 저장 때 HI·LO였으면 그 글자 (originalValue와 같이 채우고 같이 비움)

    // 사용자가 값·시점·잰 때·잰 시각을 마지막으로 고친 시각. 한 번도 안 고쳤으면 null
    // updatedAt은 지우기·되돌리기·식사 붙이기에도 올라가서 "수정됨" 표시는 이 칸으로 판단함
    private Instant editedAt;

    @Column(nullable = false)
    private boolean enteredLate;             // 30분 넘게 늦게 적었는지 (서버가 계산)

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 고칠 때마다 1씩 오름. 두 곳에서 같은 기록을 동시에 고치면 나중에 쓰는 쪽이 실패함 (처음 값·고친 시각이 덮이지 않게)
    @Version
    @Column(nullable = false)
    private Long rowVersion;

    // 새 기록 만들기. now = 저장하는 순간 (늦게 적음 계산용)
    public static Reading of(User user, CriteriaVersion criteriaVersion, ReadingType readingType,
                             MeasureSlot slot, Instant measuredAt, Integer value, MeterLimit meterLimit,
                             Long mealId, Instant now) {
        Reading r = new Reading();
        r.user = user;
        r.mealId = mealId;
        r.criteriaVersion = criteriaVersion;
        r.readingType = readingType;
        r.slot = slot;
        r.measuredAt = measuredAt;
        r.value = value;
        r.meterLimit = meterLimit;
        r.enteredLate = Duration.between(measuredAt, now).compareTo(LATE_AFTER) > 0;
        return r;
    }

    // 수정. null로 온 칸은 그대로 둠. 실제로 바뀐 칸이 있을 때만 고친 시각(editedAt)을 적음
    // 값을 처음 고칠 때 원래 값·HI·LO를 originalValue·originalMeterLimit에 한 번만 남김 (두 번째부터는 안 덮음)
    // 처음 값(숫자·HI·LO 모두 같게)으로 되돌리면 처음 값 칸을 비움. 고친 시각(editedAt)은 마지막으로 고친 시각으로 남김
    // 잰 시각이 바뀌면 그 날짜의 병원 기준(criteriaVersion)도 같이 받아서 바꾸고, 늦게 적음도 처음 적은 시각 기준으로 다시 계산함
    // 식후가 아닌 것(공복·기타)으로 바꾸면서 mealId를 안 보냈으면 식사 연결을 끊음 (D-05 시점 바꾸기)
    // 식사를 붙이거나 바꾸는 것만으로는 "고침"으로 안 셈
    public void update(Integer value, MeterLimit meterLimit, Instant measuredAt, CriteriaVersion criteriaVersion,
                       ReadingType readingType, MeasureSlot slot, Long mealId, Instant now) {
        boolean edited = false;
        // 숫자나 HI·LO로 고침 (둘 다 온 요청은 서비스에서 400). 숫자로 고치면 HI·LO 표시가 풀림
        // HI(600) → 숫자 600처럼 값은 같아도 HI 표시가 바뀌면 고친 것으로 셈
        if (value != null || meterLimit != null) {
            int newValue = meterLimit != null ? meterLimit.value() : value;
            if (newValue != this.value || meterLimit != this.meterLimit) {
                if (this.originalValue == null) {
                    this.originalValue = this.value;
                    this.originalMeterLimit = this.meterLimit;
                }
                this.value = newValue;
                this.meterLimit = meterLimit;
                if (newValue == this.originalValue && meterLimit == this.originalMeterLimit) {
                    this.originalValue = null;
                    this.originalMeterLimit = null;
                }
                edited = true;
            }
        }
        if (readingType != null && readingType != this.readingType) {
            this.readingType = readingType;
            if (readingType != ReadingType.POST_MEAL && mealId == null) this.mealId = null;
            edited = true;
        }
        if (slot != null && slot != this.slot) {
            this.slot = slot;
            edited = true;
        }
        if (measuredAt != null && !measuredAt.equals(this.measuredAt)) {
            this.measuredAt = measuredAt;
            this.criteriaVersion = criteriaVersion;
            this.enteredLate = Duration.between(measuredAt, getCreatedAt()).compareTo(LATE_AFTER) > 0;
            edited = true;
        }
        if (mealId != null) this.mealId = mealId;
        if (edited) this.editedAt = now;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠 → 조회 쿼리들이 deletedAt is null 로 걸러냄
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
