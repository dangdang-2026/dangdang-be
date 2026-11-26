package com.dangdang.domain.visit.entity;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_card_id")
    private ContentCard sourceCard;          // 카드뉴스에서 담은 질문이면 그 카드 (U-28). 아니면 null

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

    // 당당이 대화(Q-04 물어볼 말 정리)에서 가져온 질문 — 서버가 대화 번호를 확인한 뒤에만 만든다
    public static VisitQuestion ofChatbot(Visit visit, String content, int sortOrder) {
        VisitQuestion q = ofUser(visit, content, sortOrder);
        q.source = QuestionSource.CHATBOT;
        return q;
    }

    // 카드뉴스 「물어볼 것에 적기」(U-28)에서 담은 질문. 서버가 카드 질문과 같은지 확인한 뒤에만 만든다
    public static VisitQuestion ofCard(Visit visit, String content, int sortOrder, ContentCard card) {
        VisitQuestion q = ofUser(visit, content, sortOrder);
        q.source = QuestionSource.CARD;
        q.sourceCard = card;
        return q;
    }

    // 출처 카드가 내려갔는지 (U-26c 「출처 카드가 내려갔어요」). 카드 상태로만 정한다
    // 카드 줄이 지워져 null이 된 CARD 질문도 내려간 것으로 본다. 카드를 읽으니 트랜잭션 안에서 부른다
    public boolean isSourceCardWithdrawn() {
        if (source != QuestionSource.CARD) {
            return false;
        }
        return sourceCard == null || sourceCard.getStatus() == CardStatus.WITHDRAWN;
    }

    // 삭제 (soft delete). 이미 지웠으면 처음 지운 시각을 그대로 둠 (여러 번 지워도 같은 결과)
    // DB(TIMESTAMPTZ)는 마이크로초까지만 저장해서 미리 맞춤 → 첫 응답과 다시 지울 때 응답의 시각이 똑같음
    public void delete(Instant now) {
        if (deletedAt == null) {
            this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }
}
