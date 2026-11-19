package com.dangdang.domain.visit.entity;

import com.dangdang.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// visit_notes 테이블 한 줄 = 진료 후 메모 하나 (병원에서 들은 내용 원문 그대로)
// AI 정리 결과(ai_extract)는 AI 정리 기능에서 채움. 메모 저장만으로는 AI를 돌리지 않음
@Getter
@Entity
@Table(name = "visit_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitNote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @Column(nullable = false, length = 5000)
    private String content;                  // 사용자가 적은 그대로 (앞뒤 공백·줄바꿈도 안 건드림)

    @JdbcTypeCode(SqlTypes.JSON)             // DB 칸이 JSONB라서
    private VisitNoteExtract aiExtract;      // AI 정리 결과. 정리한 적 없으면 null

    private Instant deletedAt;               // null = 살아 있음 (메모 삭제 API는 아직 없음)

    public static VisitNote of(Visit visit, String content) {
        VisitNote n = new VisitNote();
        n.visit = visit;
        n.content = content;
        return n;
    }

    // 내용 고치기
    public void changeContent(String content) {
        this.content = content;
    }

    // AI 정리 결과 저장 (다시 정리하면 새 결과로 바꿈)
    public void saveExtract(VisitNoteExtract extract) {
        this.aiExtract = extract;
    }
}
