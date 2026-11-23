package com.dangdang.domain.card.entity;

import com.dangdang.domain.card.enums.CardStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 당당 소식 카드뉴스 (COM-12). 표는 V20261112_3에 이미 있다
@Getter
@Entity
@Table(name = "content_cards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 30)
    private String series;                   // BEFORE_VISIT / GUIDELINE / POSTPARTUM / MIND

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Slide> body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<CardSource> sources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> questions;          // U-28 「물어볼 것에 적기」 질문 (없으면 빈 목록)

    @Column(nullable = false)
    private LocalDate checkedOn;             // 원문 확인일

    @Column(length = 50)
    private String reviewer;                 // 검수자 (검수한 뒤에 적음)

    private LocalDate reviewedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardStatus status;

    private Instant correctedAt;

    @Column(length = 60)
    private String correctionSummary;        // 정정 요약 한 줄 (카드 위 띠)

    private String correctionNote;           // 정정 자세한 내용 (펼치면 보임)

    private Instant withdrawnAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static ContentCard draft(String title, String series, List<Slide> body, List<CardSource> sources,
                                    List<String> questions, LocalDate checkedOn, Instant now) {
        ContentCard c = new ContentCard();
        c.title = title;
        c.series = series;
        c.body = body;
        c.sources = sources;
        c.questions = questions == null ? List.of() : questions;
        c.checkedOn = checkedOn;
        c.status = CardStatus.DRAFT;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public void publish(Instant now) {
        this.status = CardStatus.PUBLISHED;
        this.updatedAt = now;
    }

    // 정정 (U-26b): 고친 내용 + 요약 한 줄 + 자세한 내용(선택)
    public void correct(List<Slide> body, List<String> questions, String summary, String note, LocalDate checkedOn,
                        Instant now) {
        if (body != null) this.body = body;
        if (questions != null) this.questions = questions;
        if (checkedOn != null) this.checkedOn = checkedOn;
        this.correctionSummary = summary;
        this.correctionNote = note;
        this.correctedAt = now;
        this.status = CardStatus.CORRECTED;
        this.updatedAt = now;
    }

    // 내리기 (U-26c): 저장한 사람에게는 「내려간 카드」로 남는다
    public void withdraw(Instant now) {
        this.status = CardStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    public void review(String reviewer, LocalDate reviewedOn, Instant now) {
        this.reviewer = reviewer;
        this.reviewedOn = reviewedOn;
        this.updatedAt = now;
    }

    public boolean isVisible() {
        return status == CardStatus.PUBLISHED || status == CardStatus.CORRECTED
                || status == CardStatus.RECHECK || status == CardStatus.WITHDRAWN;
    }
}
