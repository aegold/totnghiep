package com.thesis.elearning.modules.question.repository;

import com.thesis.elearning.modules.question.entity.QuestionVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, Long> {

    /** All revisions of one question, newest first. */
    List<QuestionVersion> findByQuestionQuestionIdOrderByVersionNumberDesc(Long questionId);

    /** The single revision currently offered to students, if the question has one. */
    Optional<QuestionVersion> findByQuestionQuestionIdAndActiveTrue(Long questionId);

    /**
     * Highest revision number recorded so far, or 0 when the question has none.
     *
     * <p>Computed from the full history rather than from the active row so that
     * numbering stays monotonic even if a revision is ever deactivated out of
     * band. Returns a row for the {@code COALESCE}, which is why the service reads
     * {@code int[0]} instead of a boxed {@code Integer}.
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM QuestionVersion v "
            + "WHERE v.question.questionId = :questionId")
    int findMaxVersionNumber(@Param("questionId") Long questionId);

    /**
     * Retires every active revision of a question.
     *
     * <p>A bulk update is required rather than a per-row save: the service must
     * not hold two active versions even momentarily, and leaving it to the
     * persistence context to flush would briefly publish both.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QuestionVersion v SET v.active = false "
            + "WHERE v.question.questionId = :questionId AND v.active = true")
    int deactivateAllForQuestion(@Param("questionId") Long questionId);
}
