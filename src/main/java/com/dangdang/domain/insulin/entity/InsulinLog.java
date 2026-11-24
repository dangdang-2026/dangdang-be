package com.dangdang.domain.insulin.entity;

import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

// insulin_logs 테이블 한 줄 = 인슐린 맞은 기록 하나 (적은 그대로 저장, 권장량 계산 없음)
@Getter
@Entity
@Table(name = "insulin_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsulinLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InsulinType insulinType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MealSlot mealSlot;               // 식사 때(MEAL)일 때만 아침·점심·저녁. 자기 전이면 null

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal units;                // 맞은 단위 (소수 한 자리)

    @Column(nullable = false)
    private Instant takenAt;                 // 맞은 시각

    private Instant editedAt;                // 마지막으로 고친 시각. 한 번도 안 고쳤으면 null

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 새 기록. mealSlot은 서비스가 종류에 맞게 정리해서 넘김 (자기 전이면 null)
    public static InsulinLog of(User user, InsulinType insulinType, MealSlot mealSlot,
                                BigDecimal units, Instant takenAt) {
        InsulinLog log = new InsulinLog();
        log.user = user;
        log.insulinType = insulinType;
        log.mealSlot = mealSlot;
        log.units = oneDecimal(units);
        log.takenAt = takenAt;
        return log;
    }

    // 수정. 종류·끼니는 서비스가 최종 값을 정해서 넘기고, 단위·시각은 null이면 그대로 둠
    // 실제로 값이 바뀐 칸이 있을 때만 고친 시각(editedAt)을 적음 (식사 수정과 같은 방식)
    public void update(InsulinType insulinType, MealSlot mealSlot, BigDecimal units, Instant takenAt, Instant now) {
        boolean changed = false;
        if (insulinType != this.insulinType) { this.insulinType = insulinType; changed = true; }
        if (mealSlot != this.mealSlot) { this.mealSlot = mealSlot; changed = true; }
        if (units != null && units.compareTo(this.units) != 0) { this.units = oneDecimal(units); changed = true; }
        if (takenAt != null && !takenAt.equals(this.takenAt)) { this.takenAt = takenAt; changed = true; }
        if (changed) this.editedAt = now;
    }

    // 고친 적이 있는지 (기록 탭 modified · 「수정됨」)
    public boolean isEdited() {
        return editedAt != null;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠 → 조회 쿼리들이 deletedAt is null 로 걸러냄
    public void delete(Instant now) {
        this.deletedAt = now;
    }

    // 6 → 6.0 (응답이 저장 직후든 다시 읽은 뒤든 같은 모양이 되게). 소수 둘째 자리는 DTO 검사에서 이미 막힘
    private static BigDecimal oneDecimal(BigDecimal units) {
        return units.setScale(1, RoundingMode.UNNECESSARY);
    }
}
