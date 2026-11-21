package com.dangdang.domain.insulin.entity;

import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal units;                // 맞은 단위 (소수 한 자리)

    @Column(nullable = false)
    private Instant takenAt;                 // 맞은 시각

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    public static InsulinLog of(User user, InsulinType insulinType, BigDecimal units, Instant takenAt) {
        InsulinLog log = new InsulinLog();
        log.user = user;
        log.insulinType = insulinType;
        log.units = units;
        log.takenAt = takenAt;
        return log;
    }

    // 수정. null로 온 칸은 그대로 둠
    public void update(InsulinType insulinType, BigDecimal units, Instant takenAt) {
        if (insulinType != null) this.insulinType = insulinType;
        if (units != null) this.units = units;
        if (takenAt != null) this.takenAt = takenAt;
    }
}
