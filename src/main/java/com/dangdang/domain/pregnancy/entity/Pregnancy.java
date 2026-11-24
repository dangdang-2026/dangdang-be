package com.dangdang.domain.pregnancy.entity;

import com.dangdang.domain.pregnancy.enums.CareMode;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Entity
@Table(name = "pregnancies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pregnancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate dueDate;               // 출산 예정일

    private LocalDate diagnosisDate;         // 임신성 당뇨 진단일 (M-02, 안 적었으면 null)

    private LocalDate deliveryDate;          // 출산일 — 적으면 산후 모드 (PP-01)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CareMode careMode;               // 관리 모드 (M-03). 처음엔 식사·운동

    @Column(length = 100)
    private String contactMemo;              // 연락 기준 메모 (M-03 · G-04). 없으면 null

    private Instant contactMemoUpdatedAt;    // 연락 기준 메모를 마지막으로 적은 시각 (G-05). 메모가 없으면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PregnancyState state;

    private Instant hospitalizedAt;          // 입원 누른 시각 (E-03·E-04)

    private LocalDate postpartumAskOn;       // 산후 검사 날짜를 다시 물어볼 날 (E-09)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // 처음 예정일을 적을 때 새 임신 한 줄
    public static Pregnancy start(User user, LocalDate dueDate, Instant now) {
        Pregnancy p = new Pregnancy();
        p.user = user;
        p.dueDate = dueDate;
        p.careMode = CareMode.DIET;
        p.state = PregnancyState.PREGNANT;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void changeDueDate(LocalDate dueDate, Instant now) {
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    // 진단일 (M-02 「바꾸기」). 날짜 검사는 서비스에서 끝냈다
    public void changeDiagnosisDate(LocalDate diagnosisDate, Instant now) {
        this.diagnosisDate = diagnosisDate;
        this.updatedAt = now;
    }

    // 관리 모드 (M-03)
    public void changeCareMode(CareMode careMode, Instant now) {
        this.careMode = careMode;
        this.updatedAt = now;
    }

    // 연락 기준 메모 (M-03). null이면 지운다
    // 적은 시각(G-05 「10월 1일에 적은 메모」)은 글이 바뀔 때만 바꾼다 — 같은 글을 다시 저장해도 날짜는 그대로
    public void changeContactMemo(String contactMemo, Instant now) {
        if (!Objects.equals(this.contactMemo, contactMemo)) {
            this.contactMemo = contactMemo;
            this.contactMemoUpdatedAt = contactMemo == null ? null : now;
        }
        this.updatedAt = now;
    }

    // 입원했어요 — 측정 알림만 멈춘다 (기록은 그대로)
    public void hospitalize(Instant now) {
        this.state = PregnancyState.HOSPITALIZED;
        this.hospitalizedAt = now;
        this.updatedAt = now;
    }

    // 퇴원(다시 켜기) — 출산 전이면 임신 중으로
    public void leaveHospital(Instant now) {
        this.state = PregnancyState.PREGNANT;
        this.hospitalizedAt = null;
        this.updatedAt = now;
    }

    // 출산했어요 — 산후 모드
    public void deliver(LocalDate deliveryDate, Instant now) {
        this.deliveryDate = deliveryDate;
        this.state = PregnancyState.POSTPARTUM;
        this.hospitalizedAt = null;
        this.updatedAt = now;
    }

    // 다른 상황이에요 — 측정 알림만 멈춘다 (E-03 · E-03b). 입원 중에 눌러도 이쪽으로 바뀐다
    public void pause(Instant now) {
        this.state = PregnancyState.PAUSED;
        this.hospitalizedAt = null;
        this.updatedAt = now;
    }

    // 다시 켜기 — 임신 중으로
    public void resume(Instant now) {
        this.state = PregnancyState.PREGNANT;
        this.updatedAt = now;
    }

    // 출산 되돌리기 (E-06 잘못 눌렀을 때) — 임신 중으로. 출산일·다시 물어볼 날을 지운다
    public void undoDelivery(Instant now) {
        this.deliveryDate = null;
        this.postpartumAskOn = null;
        this.state = PregnancyState.PREGNANT;
        this.updatedAt = now;
    }

    public void askLater(LocalDate askOn, Instant now) {
        this.postpartumAskOn = askOn;
        this.updatedAt = now;
    }

    // 임신 중 관리 졸업 (E-12) — 이 줄은 끝난 임신이 된다
    public void graduate(Instant now) {
        this.state = PregnancyState.ENDED;
        this.postpartumAskOn = null;
        this.updatedAt = now;
    }
}
