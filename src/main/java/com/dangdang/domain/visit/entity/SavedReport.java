package com.dangdang.domain.visit.entity;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.visit.enums.ReportKind;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// saved_reports 테이블 한 줄 = 저장한 리포트 하나 (기간·병원만. 숫자·표는 볼 때마다 다시 계산함)
@Getter
@Entity
@Table(name = "saved_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportKind kind;

    @Column(nullable = false)
    private LocalDate fromDate;              // 기간 시작 (포함)

    @Column(nullable = false)
    private LocalDate toDate;                // 기간 끝 (포함)

    private Long visitId;                    // 진료용이면 그 진료 일정, 기간 직접이면 null

    private Long careSiteId;                 // 보여 줄 병원 (없어도 됨)

    public static SavedReport of(User user, ReportKind kind, LocalDate from, LocalDate to,
                                 Long visitId, Long careSiteId) {
        SavedReport r = new SavedReport();
        r.user = user;
        r.kind = kind;
        r.fromDate = from;
        r.toDate = to;
        r.visitId = visitId;
        r.careSiteId = careSiteId;
        return r;
    }
}
