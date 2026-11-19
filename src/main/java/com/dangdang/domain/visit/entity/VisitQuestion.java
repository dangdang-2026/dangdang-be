package com.dangdang.domain.visit.entity;

import com.dangdang.domain.visit.enums.QuestionSource;
import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// visit_questions 테이블 한 줄 = 진료 때 물어볼 질문 하나
@Getter
@Entity
@Table(name = "visit_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionSource source;

    @Column(nullable = false)
    private int sortOrder;                   // 작을수록 위, 같으면 먼저 만든 순

    private Instant deletedAt;               // null = 살아 있음, 값 있음 = 삭제됨

    // 사용자가 직접 적은 질문
    public static VisitQuestion ofUser(Visit visit, String content, int sortOrder) {
        VisitQuestion q = new VisitQuestion();
        q.visit = visit;
        q.content = content;
        q.source = QuestionSource.USER;
        q.sortOrder = sortOrder;
        return q;
    }

    // 삭제 (soft delete). 이미 지웠으면 처음 지운 시각을 그대로 둠 (여러 번 지워도 같은 결과)
    // DB(TIMESTAMPTZ)는 마이크로초까지만 저장해서 미리 맞춤 → 첫 응답과 다시 지울 때 응답의 시각이 똑같음
    public void delete(Instant now) {
        if (deletedAt == null) {
            this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }
}
