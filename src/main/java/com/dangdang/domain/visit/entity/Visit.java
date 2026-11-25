package com.dangdang.domain.visit.entity;

import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

// visits 테이블 한 줄 = 진료 일정 하나
@Getter
@Entity
@Table(name = "visits")
@DynamicUpdate   // 바뀐 칸만 UPDATE → 일정 수정이 읽어 둔 옛 deleted_at(null)으로 병원 지우기를 덮어쓰지 않는다
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
    public void changeVisitAt(Instant visitAt) {
        this.visitAt = visitAt;
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠. 질문·메모는 VisitService.delete에서 같이 지움
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
