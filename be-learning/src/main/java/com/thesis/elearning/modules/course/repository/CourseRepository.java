package com.thesis.elearning.modules.course.repository;

import com.thesis.elearning.modules.course.entity.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByTeacherUserIdOrderByTitleAsc(Long teacherId);

    /**
     * Published courses.
     *
     * <p>Written as an explicit query: the field is {@code published} but the
     * column is {@code is_published}, so the derived name
     * {@code findByIsPublishedTrue...} would not resolve.
     */
    @Query("SELECT c FROM Course c WHERE c.published = true ORDER BY c.title ASC")
    List<Course> findAllPublished();

    Optional<Course> findByCourseIdAndTeacherUserId(Long courseId, Long teacherId);
}