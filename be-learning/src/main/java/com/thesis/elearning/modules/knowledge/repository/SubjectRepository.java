package com.thesis.elearning.modules.knowledge.repository;

import com.thesis.elearning.modules.knowledge.entity.Subject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {

    Optional<Subject> findBySubjectCode(String subjectCode);
}