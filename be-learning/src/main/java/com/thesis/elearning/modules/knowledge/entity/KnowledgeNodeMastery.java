package com.thesis.elearning.modules.knowledge.entity;

import com.thesis.elearning.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Materialised per-student mastery for one knowledge node.
 *
 * <h2>Why this table exists</h2>
 * The inherited schema had nowhere to record "student S scored 82% on node N".
 * The only path to it would be joining questions to exam_questions to
 * exam_submissions to submission_answers - and there is no exam-to-node link, so
 * there is no way to tell which of a student's attempts belongs to which node.
 * Writing the score when an exam is graded turns that into one indexed read.
 *
 * <p>Written by the grading stage. Stage 3 only reads it.
 */
@Entity
@Table(name = "knowledge_node_mastery")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNodeMastery {

    @EmbeddedId private MasteryId id;

    @MapsId("studentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @MapsId("nodeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private KnowledgeNode node;

    /** Percentage 0..100. At or above 80 counts as mastered. */
    @Column(name = "mastery_score", nullable = false)
    private Integer masteryScore;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Composite key: one mastery row per student per node. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasteryId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "student_id")
        private Long studentId;

        @Column(name = "node_id")
        private Long nodeId;

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MasteryId that)) {
                return false;
            }
            return Objects.equals(studentId, that.studentId)
                    && Objects.equals(nodeId, that.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentId, nodeId);
        }
    }
}