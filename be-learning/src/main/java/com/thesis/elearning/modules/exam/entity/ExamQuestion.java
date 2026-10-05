package com.thesis.elearning.modules.exam.entity;

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
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One question's place in one paper.
 *
 * <h2>Why marks live here and not on the version</h2>
 * {@code points} answers "what is this worth <em>in this paper</em>", which is
 * not a property of the question: the same revision can sit in a quiz for 1
 * point and a final for 20. Storing it here rather than on {@code
 * question_versions} is also what lets a paper be worth a whole number without
 * touching the question bank.
 *
 * <h2>Composite key</h2>
 * {@code (exam_id, version_id)} is inherited from the schema and doubles as a
 * guarantee that the same revision cannot appear twice in one paper - which would
 * otherwise double-count its marks in the total.
 */
@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestion {

    @EmbeddedId private PaperItemId id;

    @MapsId("examId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @MapsId("versionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private com.thesis.elearning.modules.question.entity.QuestionVersion version;

    /** Marks this item contributes to the paper's total. */
    @Column(name = "points", nullable = false)
    @Builder.Default
    private BigDecimal points = new BigDecimal("1.00");

    /** 1-based position on the paper. */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /** Composite key: one row per revision per exam. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaperItemId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "exam_id")
        private Long examId;

        @Column(name = "version_id")
        private Long versionId;

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PaperItemId that)) {
                return false;
            }
            return Objects.equals(examId, that.examId) && Objects.equals(versionId, that.versionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(examId, versionId);
        }
    }
}
