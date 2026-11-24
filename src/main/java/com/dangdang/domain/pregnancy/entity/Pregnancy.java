package com.dangdang.domain.pregnancy.entity;

import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

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

    private LocalDate deliveryDate;          // 출산일 — 적으면 산후 모드 (PP-01)

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
        p.state = PregnancyState.PREGNANT;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void changeDueDate(LocalDate dueDate, Instant now) {
        this.dueDate = dueDate;
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
