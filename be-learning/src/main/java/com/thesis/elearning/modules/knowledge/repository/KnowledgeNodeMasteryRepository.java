package com.thesis.elearning.modules.knowledge.repository;

import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery.MasteryId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeNodeMasteryRepository
        extends JpaRepository<KnowledgeNodeMastery, MasteryId> {

    Optional<KnowledgeNodeMastery> findByIdStudentIdAndIdNodeId(Long studentId, Long nodeId);

    /**
     * Every mastery row for a student, in one query.
     *
     * <p>Resolving the whole graph must not issue a lookup per node, so the
     * scores are fetched once and indexed by node id in memory.
     */
    @Query("SELECT m FROM KnowledgeNodeMastery m WHERE m.id.studentId = :studentId")
    List<KnowledgeNodeMastery> findAllForStudent(@Param("studentId") Long studentId);
}