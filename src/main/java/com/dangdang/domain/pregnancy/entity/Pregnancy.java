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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PregnancyState state;

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
}
