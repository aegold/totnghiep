package com.thesis.elearning.modules.question.repository;

import com.thesis.elearning.modules.question.entity.Question;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Takes a row-level write lock on the question before publishing a revision.
     *
     * <p>Two teachers editing the same question at once would otherwise both read
     * version {@code n}, both insert {@code n + 1}, and the second would violate
     * {@code uq_question_version} - or worse, if the unique constraint were absent,
     * both rows would stay active. Serialising on the parent row makes
     * read-max-then-insert atomic, which is the only place the lock can be taken
     * that every writer already passes through.
     *
     * <p>Rows hidden by the entity's {@code is_deleted = 0} restriction are
     * excluded, so a deleted question cannot be revived by this path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Question q WHERE q.questionId = :questionId")
    Optional<Question> findByIdForUpdate(@Param("questionId") Long questionId);

    /**
     * Soft-deletes a question.
     *
     * <p>Written as an explicit UPDATE rather than {@code delete()} so the row
     * count is returned: the service turns zero affected rows into the
     * not-found message, making a repeated delete idempotent from the caller's
     * point of view instead of surfacing a stale-entity error.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Question q SET q.deleted = true, q.deletedAt = CURRENT_TIMESTAMP "
            + "WHERE q.questionId = :questionId AND q.deleted = false")
    int softDelete(@Param("questionId") Long questionId);
}
