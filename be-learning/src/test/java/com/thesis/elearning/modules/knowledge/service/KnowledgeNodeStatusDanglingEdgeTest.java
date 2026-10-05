package com.thesis.elearning.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.thesis.elearning.modules.knowledge.dto.StudentNodeStatusDto;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.knowledge.service.PrerequisiteGraphValidator;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeRepository;
import com.thesis.elearning.modules.knowledge.repository.KnowledgePrerequisiteRepository;
import com.thesis.elearning.modules.knowledge.repository.SubjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Status resolution when the graph no longer matches the table.
 *
 * <p>Deleting a knowledge node does not cascade into {@code knowledge_prerequisites},
 * so the adjacency map can name a prerequisite whose row is gone. Such an edge must
 * not block anybody: it points at something the student can neither see nor act
 * on, so honouring it produces a LOCKED state that no amount of work can clear.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeNodeStatusDanglingEdgeTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long LIVE_NODE = 2L;
    private static final Long DELETED_NODE = 99L;

    @Mock private KnowledgeNodeRepository nodeRepository;
    @Mock private KnowledgePrerequisiteRepository prerequisiteRepository;
    @Mock private KnowledgeNodeMasteryRepository masteryRepository;
    @Mock private SubjectRepository subjectRepository;

    private KnowledgeNodeService service;

    @BeforeEach
    void setUp() {
        service =
                new KnowledgeNodeService(
                        nodeRepository,
                        prerequisiteRepository,
                        masteryRepository,
                        subjectRepository,
                        new PrerequisiteGraphValidator());

        // Only the live node exists; the edge still points at the deleted one.
        when(nodeRepository.findAll())
                .thenReturn(List.of(KnowledgeNode.builder().nodeId(LIVE_NODE).nodeName("Live").build()));
        when(nodeRepository.findAllEdges())
                .thenReturn(List.<Object[]>of(new Object[] {DELETED_NODE, LIVE_NODE}));
        when(masteryRepository.findAllForStudent(STUDENT_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("a prerequisite whose row is gone does not lock the dependent")
    void danglingPrerequisiteDoesNotLock() {
        final List<StudentNodeStatusDto> statuses = service.resolveStatuses(STUDENT_ID);

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).status())
                .as("the only live node must stay reachable")
                .isNotEqualTo("LOCKED");
        assertThat(statuses.get(0).blockingPrerequisites())
                .as("a dangling edge must not appear as a blocker")
                .isEmpty();
    }

    @Test
    @DisplayName("a genuinely unmet live prerequisite still locks the dependent")
    void liveUnmetPrerequisiteStillLocks() {
        when(nodeRepository.findAllEdges())
                .thenReturn(List.<Object[]>of(new Object[] {DELETED_NODE, LIVE_NODE}));
        when(nodeRepository.findAll())
                .thenReturn(
                        List.of(
                                KnowledgeNode.builder().nodeId(LIVE_NODE).nodeName("Live").build(),
                                KnowledgeNode.builder().nodeId(DELETED_NODE).nodeName("Still here").build()));

        final List<StudentNodeStatusDto> statuses = service.resolveStatuses(STUDENT_ID);

        final StudentNodeStatusDto dependent =
                statuses.stream()
                        .filter(s -> s.nodeId().equals(String.valueOf(LIVE_NODE)))
                        .findFirst()
                        .orElseThrow();
        assertThat(dependent.status()).isEqualTo("LOCKED");
        assertThat(Set.copyOf(dependent.blockingPrerequisites()))
                .containsExactly(String.valueOf(DELETED_NODE));
    }
}