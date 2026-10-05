package com.thesis.elearning.modules.exam.repository;

import com.thesis.elearning.modules.exam.entity.ExamQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestion.PaperItemId> {

    /**
     * The paper in presentation order.
     *
     * <p>Written as an explicit JPQL query rather than a derived name. Spring
     * Data parses {@code ...OrderByIdOrderIndex} by taking {@code Id} as the
     * entity's own identifier property and {@code OrderIndex} as a sub-path of it,
     * then fails because {@code PaperItemId} has no {@code orderIndex} - the same
     * trap as {@code findByIdAndSomething} in the knowledge module. Naming the
     * sort column explicitly removes the ambiguity.
     */
    @Query("SELECT eq FROM ExamQuestion eq WHERE eq.id.examId = :examId ORDER BY eq.orderIndex ASC")
    List<ExamQuestion> findPaperOrdered(@Param("examId") Long examId);

    long countByIdExamId(Long examId);
}
