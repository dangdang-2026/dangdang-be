package com.dangdang.domain.visit.entity;

import com.dangdang.domain.user.entity.User;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

// visits 테이블 한 줄 = 진료 일정 하나
@Getter
@Entity
@Table(name = "visits")
// 바뀐 칸만 UPDATE → 일정 수정이 읽어 둔 옛 deleted_at(null)으로 병원 지우기를 덮어쓰지 않고,
// 스케줄러가 적은 진료 알림 보낸 시각도 옛 값(null)으로 덮지 않는다
@DynamicUpdate
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
        v.visitAt = visitAt == null ? null : visitAt.truncatedTo(ChronoUnit.MICROS);   // DB와 같은 정밀도
        return v;
    }

    // 병원 바꾸기 (null이면 병원 지우기)
    public void changeCareSite(Long careSiteId) {
        this.careSiteId = careSiteId;
    }

    // 진료 날짜 바꾸기 (null이면 날짜 미정으로). 한국 날짜가 바뀌었으면 true
    // 날짜가 바뀌면 부르는 쪽(VisitService.update)이 보낸 알림 기록을 비워서 새 날짜 기준으로 전날·당일 알림을 다시 보냄
    // 같은 날 안에서 시각만 바꾸면 그대로 둠 (「내일 진료가 있어요」를 또 보내지 않게)
    public boolean changeVisitAt(Instant visitAt) {
        visitAt = visitAt == null ? null : visitAt.truncatedTo(ChronoUnit.MICROS);   // DB와 같은 정밀도
        boolean dateChanged = !Objects.equals(dateOf(this.visitAt), dateOf(visitAt));
        this.visitAt = visitAt;
        return dateChanged;
    }

    // 진료의 한국 날짜 (날짜 미정이면 null)
    private static LocalDate dateOf(Instant at) {
        return at == null ? null : KoreanTime.dateOf(at);
    }

    // 삭제 (soft delete). 줄을 지우지 않고 지운 시각만 적어 둠. 질문·메모는 VisitService.delete에서 같이 지움
    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
