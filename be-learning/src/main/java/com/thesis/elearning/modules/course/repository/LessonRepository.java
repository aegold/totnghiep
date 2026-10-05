package com.thesis.elearning.modules.course.repository;

import com.thesis.elearning.modules.course.entity.Lesson;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByChapterChapterIdOrderByOrderIndexAsc(Long chapterId);

    /**
     * Loads a lesson only if it belongs to the given course.
     *
     * <p>The property is {@code lessonId}, not {@code id}: Spring Data parses
     * {@code Id} in a derived name as a property path and fails at bootstrap.
     */
    Optional<Lesson> findByLessonIdAndChapterCourseCourseId(Long lessonId, Long courseId);
}