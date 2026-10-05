package com.thesis.elearning.modules.question.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable revision of a question's content and answer key.
 *
 * <h2>Immutability is enforced by write pattern, not by the entity</h2>
 * There is deliberately no {@code @PreUpdate} guard and no setter path that
 * mutates a persisted version's payload. Rows are written once by
 * {@code QuestionService} and are thereafter only ever deactivated, which is what
 * lets {@code exam_questions.version_id} stay a meaningful pointer: it identifies
 * the exact bytes a student saw and the grader scored.
 *
 * <h2>One active version per question</h2>
 * Exactly one row carries {@code is_active = 1}; publishing a revision flips the
 * previous row off in the same transaction that inserts the new one. The schema's
 * {@code idx_qv_active (question_id, is_active)} is a plain non-unique index, so
 * the database does NOT enforce the invariant on its own - it is guaranteed by the
 * service taking a pessimistic write lock on the parent question first.
 */
@Entity
@Table(name = "question_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** 1-based and strictly increasing per question; unique with question_id in the schema. */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * What the student sees.
     *
     * <p>Authored through the public half of the payload pair only. The service
     * additionally scans this node for answer-key keys before it is stored, so a
     * teacher cannot smuggle the key in through the "public" channel - see
     * {@code PayloadLeakageGuard}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_payload", nullable = false, columnDefinition = "json")
    private JsonNode contentPayload;

    /**
     * The answer key and rationales.
     *
     * <p>Must never reach a student-facing response. This field exists on exactly
     * one DTO, {@code TeacherQuestionDetailResponse}; {@code StudentQuestionResponse}
     * has no corresponding property at all, so omission is a compile-time fact
     * rather than a runtime filtering step that could be bypassed by a future
     * refactor.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "solution_payload", nullable = false, columnDefinition = "json")
    private JsonNode solutionPayload;

    /** The revision currently offered to students. Exactly one per question. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
