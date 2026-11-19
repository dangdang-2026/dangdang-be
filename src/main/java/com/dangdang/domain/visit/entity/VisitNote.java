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

    // 내용 고치기. 내용이 실제로 바뀌면 AI 정리 결과를 비움 (옛 메모 기준 결과라 근거 문장이 안 맞음)
    // 앞뒤 공백·줄바꿈만 달라진 건 같은 메모로 봄 (AI 정리에도 앞뒤 공백을 뺀 메모를 보내서)
    public void changeContent(String content) {
        if (!hasSameContent(content)) {
            this.aiExtract = null;
        }
        this.content = content;
    }

    // 앞뒤 공백을 뺀 내용이 같은지 (메모 수정과 AI 정리 저장이 같은 기준으로 "바뀌었는지"를 봄)
    public boolean hasSameContent(String other) {
        return other != null && this.content.strip().equals(other.strip());
    }

    // AI 정리 결과 저장 (다시 정리하면 새 결과로 바꿈)
    public void saveExtract(VisitNoteExtract extract) {
        this.aiExtract = extract;
    }
}
