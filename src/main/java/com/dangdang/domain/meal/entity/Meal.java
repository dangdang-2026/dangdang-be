package com.dangdang.domain.meal.entity;

import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// meals 테이블 한 줄 = 식사 하나 (뭘 먹었는지가 아니라 언제 먹기 시작했는지 기록함)
@Getter
@Entity
@Table(name = "meals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)             // DB에 "BREAKFAST" 같은 글자로 저장
    @Column(nullable = false, length = 10)
    private MealSlot mealSlot;

    @Column(nullable = false)
    private Instant startedAt;               // 식사 시작 누른 시각

    @Column(nullable = false)
    private boolean startedOffline;          // 오프라인에서 누르고 나중에 올린 기록인지

    @Column(length = 255)
    private String note;                     // 메모 (먹은 음식 등, 없어도 됨)

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 식사 자체(끼니·시작 시각·메모)를 마지막으로 고친 시각. 한 번도 안 고쳤으면 null
    // updatedAt은 음식 항목이 바뀌어도 올라가서(충돌 확인용) "수정됨" 표시는 이 칸으로 판단함
    private Instant editedAt;

    // 새 식사 만들기
    public static Meal of(User user, MealSlot mealSlot, Instant startedAt, boolean startedOffline, String note) {
        Meal m = new Meal();
        m.user = user;
        m.mealSlot = mealSlot;
        m.startedAt = startedAt;
        m.startedOffline = startedOffline;
        m.note = note;
        return m;
    }

    // 수정. null로 온 칸은 그대로 둠. 실제로 값이 바뀐 칸이 있을 때만 고친 시각(editedAt)을 적음
    // startedOffline은 "그때 오프라인이었는지"라는 사실이라 안 바꿈
    public void update(MealSlot mealSlot, Instant startedAt, String note, Instant now) {
        boolean changed = false;
        if (mealSlot != null && mealSlot != this.mealSlot) { this.mealSlot = mealSlot; changed = true; }
        if (startedAt != null && !startedAt.equals(this.startedAt)) { this.startedAt = startedAt; changed = true; }
        if (note != null && !note.equals(this.note)) { this.note = note; changed = true; }
        if (changed) this.editedAt = now;
    }

    // 식사 자체를 고친 적이 있는지 (음식 항목 변경은 안 셈)
    public boolean isEdited() {
        return editedAt != null;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
