package com.thesis.elearning.modules.knowledge.repository;

import com.thesis.elearning.modules.knowledge.entity.KnowledgePrerequisite;
import com.thesis.elearning.modules.knowledge.entity.KnowledgePrerequisite.PrerequisiteId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePrerequisiteRepository
        extends JpaRepository<KnowledgePrerequisite, PrerequisiteId> {

    boolean existsById(PrerequisiteId id);

    List<KnowledgePrerequisite> findAllByIdDependentNodeId(Long dependentNodeId);
}