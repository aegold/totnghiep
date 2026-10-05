package com.thesis.elearning.modules.exam.repository;

import com.thesis.elearning.modules.exam.entity.SubmissionAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionAnswerRepository
        extends JpaRepository<SubmissionAnswer, SubmissionAnswer.AnswerId> {

    /** Every graded answer on one attempt, in the order the client sent them. */
    List<SubmissionAnswer> findByIdSubmissionId(Long submissionId);

    long countByIdSubmissionId(Long submissionId);
}
