package com.dangdang.domain.visit.entity;

import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Objects;

// visits 테이블 한 줄 = 진료 일정 하나
// @DynamicUpdate: 바뀐 칸만 UPDATE함 → 일정을 고치는 동안 스케줄러가 적은 알림 보낸 시각을 옛 값(null)으로 덮지 않게
@Getter
@Entity
@DynamicUpdate
@Table(name = "visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long careSiteId;                 // 내 병원 (없어도 됨). 병원 정보는 병원 API에서 이 id로 찾음

    private Instant visitAt;                 // 진료 날짜·시각. 아직 모르면 null

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    private Instant dayBeforeSentAt;         // 전날 알림 처리한 시각 (null = 아직). 스케줄러가 조건부 UPDATE로만 채움

    private Instant sameDaySentAt;           // 당일 알림 처리한 시각 (null = 아직)

    // 새 일정 만들기
    public static Visit of(User user, Long careSiteId, Instant visitAt) {
        Visit v = new Visit();
        v.user = user;
        v.careSiteId = careSiteId;
        v.visitAt = visitAt;
        return v;
    }

    // 병원 바꾸기 (null이면 병원 지우기)
    public void changeCareSite(Long careSiteId) {
        this.careSiteId = careSiteId;
    }

    // 진료 날짜 바꾸기 (null이면 날짜 미정으로)
    // 날짜가 실제로 바뀌면 보낸 알림 기록을 비워서 새 날짜 기준으로 전날·당일 알림을 다시 보냄
    public void changeVisitAt(Instant visitAt) {
        if (!Objects.equals(this.visitAt, visitAt)) {
            this.dayBeforeSentAt = null;
            this.sameDaySentAt = null;
        }
        this.visitAt = visitAt;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠. 질문·메모는 VisitService.delete에서 같이 지움
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
