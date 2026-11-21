package com.dangdang.domain.visit.entity;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.visit.enums.ReportKind;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// saved_reports 테이블 한 줄 = 저장한 리포트 하나 (숫자·표는 볼 때마다 다시 계산함)
// 진료용은 진료 일정만 가리키고 기간·병원은 볼 때 진료 일정에서 계산, 기간 직접은 기간·병원을 저장
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

    private Long visitId;                    // 진료용만

    private LocalDate fromDate;              // 기간 직접만, 기간 시작 (포함)

    private LocalDate toDate;                // 기간 직접만, 기간 끝 (포함)

    private Long careSiteId;                 // 기간 직접만, 보여 줄 병원 (없어도 됨)

    // 진료용
    public static SavedReport ofVisit(User user, Long visitId) {
        SavedReport r = new SavedReport();
        r.user = user;
        r.kind = ReportKind.VISIT;
        r.visitId = visitId;
        return r;
    }

    // 기간 직접
    public static SavedReport ofCustom(User user, LocalDate from, LocalDate to, Long careSiteId) {
        SavedReport r = new SavedReport();
        r.user = user;
        r.kind = ReportKind.CUSTOM;
        r.fromDate = from;
        r.toDate = to;
        r.careSiteId = careSiteId;
        return r;
    }
}
