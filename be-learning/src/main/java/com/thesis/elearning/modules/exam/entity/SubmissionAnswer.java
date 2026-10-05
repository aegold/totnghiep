package com.thesis.elearning.modules.exam.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One student's answer to one question on one attempt, with its grade.
 *
 * <h2>What is stored, and what is deliberately not</h2>
 * {@code student_response} keeps exactly what the student sent, unmodified.
 * Nothing is normalised on the way in: the graded outcome is captured alongside
 * it in {@code earned_points} and {@code is_correct}, so a later change to the
 * grading rules would not silently rewrite history, and an examiner can still
 * see the literal answer that was submitted.
 *
 * <p>No correctness flag is stored per blank or per pair. Partial credit is
 * collapsed into the single {@code earned_points} total, which is the only value
 * the schema can hold and the only one a mark sheet needs.
 */
@Entity
@Table(name = "submission_answers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionAnswer {

    @EmbeddedId private AnswerId id;

    @MapsId("submissionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ExamSubmission submission;

    /**
     * The exact revision that was served.
     *
     * <p>Pinning the version rather than the question is what makes a re-grade
     * reproducible: the answer key used is the one that was on screen.
     */
    @MapsId("versionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private QuestionVersion version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "student_response", nullable = false, columnDefinition = "json")
    private JsonNode studentResponse;

    /** Marks awarded, from zero up to the paper's {@code points} for this item. */
    @Column(name = "earned_points", nullable = false)
    @Builder.Default
    private BigDecimal earnedPoints = BigDecimal.ZERO;

    /** True only for full marks, even on a question that allows partial credit. */
    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private boolean correct = false;

    /** Composite key: one row per question per attempt. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "submission_id")
        private Long submissionId;

        @Column(name = "version_id")
        private Long versionId;

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AnswerId that)) {
                return false;
            }
            return Objects.equals(submissionId, that.submissionId)
                    && Objects.equals(versionId, that.versionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(submissionId, versionId);
        }
    }
}
