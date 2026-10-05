package com.thesis.elearning.modules.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One competency in the knowledge graph.
 *
 * <p>Mapped onto the inherited {@code knowledge_nodes} table, whose primary key
 * is {@code node_id} rather than the conventional {@code id}, so every column is
 * named explicitly.
 *
 * <p>There is no {@code is_deleted} column on this table, which is why Stage 3
 * offers no delete for nodes: a hard delete would cascade through
 * {@code knowledge_prerequisites} and silently erase the curriculum graph. Use
 * update-only until a soft-delete column exists.
 */
@Entity
@Table(name = "knowledge_nodes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Long nodeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "node_name", nullable = false, length = 150)
    private String nodeName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /*
     * No createdAt field: the inherited `knowledge_nodes` table has no
     * created_at column. `ddl-auto: validate` fails fast on such a mismatch,
     * which is how it was caught rather than at runtime.
     */
}