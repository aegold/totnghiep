package com.thesis.elearning.modules.question.repository;

import com.thesis.elearning.modules.question.entity.DifficultyLevel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DifficultyLevelRepository extends JpaRepository<DifficultyLevel, Integer> {

    Optional<DifficultyLevel> findByLevelCode(String levelCode);
}
