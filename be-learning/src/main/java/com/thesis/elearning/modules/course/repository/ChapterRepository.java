package com.thesis.elearning.modules.course.repository;

import com.thesis.elearning.modules.course.entity.Chapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByCourseCourseIdOrderByOrderIndexAsc(Long courseId);
}