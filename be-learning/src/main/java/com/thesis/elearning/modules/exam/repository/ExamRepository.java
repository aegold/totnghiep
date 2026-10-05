package com.thesis.elearning.modules.exam.repository;

import com.thesis.elearning.modules.exam.entity.Exam;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    /** Published, non-deleted papers attached to a course. */
    List<Exam> findByCourseCourseIdAndPublishedTrue(Long courseId);

    /**
     * Papers a student may sit right now.
     *
     * <p>Publication is in the WHERE clause rather than left to the entity's
     * {@code is_deleted = 0} restriction alone: an unpublished exam must be
     * invisible to students but still reachable by the teacher editing it, so
     * "visible" is a per-role notion and cannot live in the entity.
     */
    @Query("SELECT e FROM Exam e WHERE e.published = true ORDER BY e.examId DESC")
    List<Exam> findPublished();
}
