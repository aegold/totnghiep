package com.thesis.elearning.modules.exam.repository;

import com.thesis.elearning.modules.exam.entity.ExamSubmission;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {

    /** Every attempt by one student on one paper, newest first. */
    List<ExamSubmission> findByExamExamIdAndStudentUserIdOrderBySubmissionIdDesc(
            Long examId, Long studentId);

    /** The attempt currently in flight, if the student has one open. */
    Optional<ExamSubmission> findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
            Long examId, Long studentId);

    long countByExamExamIdAndStudentUserId(Long examId, Long studentId);

    /**
     * Takes a write lock on the attempt while it is being graded.
     *
     * <p>Without it, a student who double-clicks submit - or a client that
     * retries on a timeout - would send two requests that both observe
     * {@code submitted_at IS NULL}, both grade the paper, and the second would
     * overwrite the first's answers. Serialising on this row makes
     * "already submitted" a decision the second request actually sees.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ExamSubmission s WHERE s.submissionId = :submissionId")
    Optional<ExamSubmission> findByIdForUpdate(@Param("submissionId") Long submissionId);
}
