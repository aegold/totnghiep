package com.thesis.elearning.modules.knowledge.repository;

import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, Long> {

    Optional<KnowledgeNode> findByNodeNameIgnoreCase(String nodeName);

    List<KnowledgeNode> findBySubjectSubjectIdOrderByNodeNameAsc(Integer subjectId);

    /**
     * Prerequisite ids of {@code nodeId}, as plain ids.
     *
     * <p>A projection rather than an entity fetch so building the adjacency map
     * for cycle detection does not pull a node graph into memory.
     */
    @Query("SELECT p.id.prerequisiteNodeId FROM KnowledgePrerequisite p "
            + "WHERE p.id.dependentNodeId = :nodeId")
    List<Long> findPrerequisiteIds(@Param("nodeId") Long nodeId);

    /** Every edge as a flat id pair; the service assembles the adjacency map. */
    @Query("SELECT p.id.prerequisiteNodeId, p.id.dependentNodeId FROM KnowledgePrerequisite p")
    List<Object[]> findAllEdges();

    @Query("SELECT COUNT(l) FROM Lesson l JOIN l.knowledgeNodes n WHERE n.nodeId = :nodeId")
    long countRelatedLessons(@Param("nodeId") Long nodeId);
}