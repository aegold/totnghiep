package com.thesis.elearning.modules.course.repository;

import com.thesis.elearning.modules.course.entity.LearningProgress;
import com.thesis.elearning.modules.course.entity.LearningProgress.ProgressId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProgressRepository extends JpaRepository<LearningProgress, ProgressId> {

    List<LearningProgress> findByIdStudentId(Long studentId);

    long countByIdStudentIdAndCompletedTrue(Long studentId);
}