package com.thesis.elearning.modules.course.repository;

import com.thesis.elearning.modules.course.entity.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByLessonLessonIdOrderByMaterialIdAsc(Long lessonId);
}