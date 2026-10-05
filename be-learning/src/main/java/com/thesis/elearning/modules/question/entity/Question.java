package com.thesis.elearning.modules.question.entity;

import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A question's stable identity, independent of any single revision.
 *
 * <h2>Why the row never changes when the wording does</h2>
 * This table holds only metadata: which knowledge node it targets, who wrote it,
 * how hard it is, and which renderer handles its payload. The actual prompt and
 * the answer key live in {@link QuestionVersion}. Editing a question therefore
 * never UPDATEs a version row - it inserts a new one - so a paper already printed
 * from version 3 keeps pointing at exactly the bytes that were graded, even after
 * the author rewrites the stem.
 *
 * <h2>Soft deletion</h2>
 * {@code is_deleted} hides a question from the authoring surface while preserving
 * the row, because {@code exam_questions} references it. Deleting a question that
 * a past submission still cites would either cascade away a student's record or
 * fail outright. {@link SQLRestriction} appends {@code is_deleted = 0} to every
 * query so a deleted question cannot be loaded by ordinary repository calls,
 * which lets {@code getQuestionForStudent} report the single not-found message
 * for "missing" and "deleted" alike.
 */
@Entity
@Table(name = "questions")
@SQLDelete(
        sql = "UPDATE questions SET is_deleted = 1, deleted_at = CURRENT_TIMESTAMP "
                + "WHERE question_id = ?")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private KnowledgeNode node;

    /**
     * Author of record. Kept even when a version is revised by someone else so
     * ownership checks have a stable answer.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "difficulty_id", nullable = false)
    private DifficultyLevel difficulty;

    /**
     * Discriminator persisted as a {@code String}, not an enum constant.
     *
     * <p>Deliberate: the column is {@code varchar(30)} and new renderers may
     * appear later. Mapping an enum would make an unrecognised stored value
     * blow up at read time, whereas the string form lets the service reject an
     * unsupported type at write time with a Vietnamese message.
     */
    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Database default {@code CURRENT_TIMESTAMP}; never written by the app.
     *
     * <p>{@code @ColumnDefault} matters only for the H2 test profile, whose
     * {@code create-drop} DDL would otherwise emit a bare {@code NOT NULL} column
     * with no default and reject every INSERT.
     */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
