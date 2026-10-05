package com.thesis.elearning.modules.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A directed prerequisite edge: {@code prerequisite -> dependent}.
 *
 * <p>The composite primary key is inherited, so it is modelled with an
 * {@link EmbeddedId}. That key also gives duplicate-edge rejection for free,
 * before any service logic runs.
 *
 * <h2>Direction matters for cycle detection</h2>
 * "A must be learned before B" is stored as
 * {@code prerequisite_node_id = A, dependent_node_id = B}. A cycle exists when
 * following that direction ever returns to a node already on the current path.
 */
@Entity
@Table(name = "knowledge_prerequisites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePrerequisite {

    @EmbeddedId private PrerequisiteId id;

    @MapsId("prerequisiteNodeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_node_id", nullable = false)
    private KnowledgeNode prerequisite;

    @MapsId("dependentNodeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependent_node_id", nullable = false)
    private KnowledgeNode dependent;

    /** Composite key matching the inherited table's primary key. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrerequisiteId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "prerequisite_node_id")
        private Long prerequisiteNodeId;

        @Column(name = "dependent_node_id")
        private Long dependentNodeId;

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PrerequisiteId that)) {
                return false;
            }
            return Objects.equals(prerequisiteNodeId, that.prerequisiteNodeId)
                    && Objects.equals(dependentNodeId, that.dependentNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prerequisiteNodeId, dependentNodeId);
        }

        @Override
        public String toString() {
            return prerequisiteNodeId + "->" + dependentNodeId;
        }
    }
}