package com.thesis.elearning.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.knowledge.dto.AddPrerequisiteRequest;
import com.thesis.elearning.modules.knowledge.dto.CreateNodeRequest;
import com.thesis.elearning.modules.knowledge.dto.StudentNodeStatusDto;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery.MasteryId;
import com.thesis.elearning.modules.knowledge.entity.Subject;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeRepository;
import com.thesis.elearning.modules.knowledge.repository.KnowledgePrerequisiteRepository;
import com.thesis.elearning.modules.knowledge.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for {@link KnowledgeNodeService}.
 *
 * <p>Two behaviours carry the weight: an edge that would close a cycle must be
 * refused with a 400 before anything is persisted, and node status must resolve
 * from scores with mastery taking precedence over prerequisites.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeNodeServiceTest {

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
    }

    private static KnowledgeNode node(final long id, final String name) {
        return KnowledgeNode.builder()
                .nodeId(id)
                .nodeName(name)
                .subject(Subject.builder().subjectId(1).subjectName("Toán học").build())
                .build();
    }

    /** Existing edges as {@code from -> to} pairs, shaped like the repository's return. */
    private static List<Object[]> rows(final Long... pairs) {
        final List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(new Object[] {pairs[i], pairs[i + 1]});
        }
        return out;
    }

    /** Flattens an adjacency map into the same pair list. */
    private static List<Object[]> rows(final Map<Long, List<Long>> adjacency) {
        final List<Object[]> out = new ArrayList<>();
        adjacency.forEach((from, tos) -> tos.forEach(to -> out.add(new Object[] {from, to})));
        return out;
    }

    private void stubNodes(final KnowledgeNode... nodes) {
        lenient().when(nodeRepository.findAll()).thenReturn(List.of(nodes));
        lenient().when(nodeRepository.countRelatedLessons(any())).thenReturn(0L);
        lenient().when(nodeRepository.findPrerequisiteIds(any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("addPrerequisite() cycle rejection")
    class CycleRejection {

        @Test
        @DisplayName("refuses a self-loop with the Vietnamese 400")
        void selfLoop() {
            lenient().when(nodeRepository.findById(1L)).thenReturn(Optional.of(node(1L, "A")));

            assertThatThrownBy(
                            () -> service.addPrerequisite(new AddPrerequisiteRequest(1L, 1L)))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex -> {
                                final AppException app = (AppException) ex;
                                assertThat(app.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                                assertThat(app.getErrorCode().statusValue()).isEqualTo(400);
                                assertThat(app.getMessage())
                                        .startsWith("Phát hiện vòng lặp phụ thuộc");
                            });
            verify(prerequisiteRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an edge that would close a two-node cycle")
        void closesTwoNodeCycle() {
            // Existing: 1 -> 2. Adding 2 -> 1 closes the loop.
            lenient().when(nodeRepository.findById(1L)).thenReturn(Optional.of(node(1L, "A")));
            lenient().when(nodeRepository.findById(2L)).thenReturn(Optional.of(node(2L, "B")));
            lenient().when(prerequisiteRepository.existsById(any())).thenReturn(false);
            lenient().when(nodeRepository.findAllEdges()).thenReturn(rows(1L, 2L));

            assertThatThrownBy(
                            () -> service.addPrerequisite(new AddPrerequisiteRequest(2L, 1L)))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Phát hiện vòng lặp phụ thuộc không hợp lệ")
                    .hasMessageContaining("2 -> 1");

            verify(prerequisiteRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a transitive cycle through an intermediate node")
        void closesTransitiveCycle() {
            // Existing: 1 -> 2 -> 3. Adding 3 -> 1 closes it.
            lenient().when(nodeRepository.findById(1L)).thenReturn(Optional.of(node(1L, "A")));
            lenient().when(nodeRepository.findById(3L)).thenReturn(Optional.of(node(3L, "C")));
            lenient().when(prerequisiteRepository.existsById(any())).thenReturn(false);
            lenient().when(nodeRepository.findAllEdges()).thenReturn(rows(1L, 2L, 2L, 3L));

            assertThatThrownBy(
                            () -> service.addPrerequisite(new AddPrerequisiteRequest(3L, 1L)))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Phát hiện vòng lặp phụ thuộc không hợp lệ");

            verify(prerequisiteRepository, never()).save(any());
        }

        @Test
        @DisplayName("accepts an edge that keeps the graph acyclic")
        void acceptsValidEdge() {
            lenient().when(nodeRepository.findById(1L)).thenReturn(Optional.of(node(1L, "A")));
            lenient().when(nodeRepository.findById(3L)).thenReturn(Optional.of(node(3L, "C")));
            lenient().when(prerequisiteRepository.existsById(any())).thenReturn(false);
            lenient().when(nodeRepository.findAllEdges()).thenReturn(rows(1L, 2L, 2L, 3L));
            stubNodes(node(1L, "A"), node(2L, "B"), node(3L, "C"));

            // 1 -> 3 alongside 1 -> 2 -> 3 is a diamond, not a cycle.
            service.addPrerequisite(new AddPrerequisiteRequest(1L, 3L));

            verify(prerequisiteRepository).save(any());
        }

        @Test
        @DisplayName("refuses a duplicate edge")
        void refusesDuplicate() {
            lenient().when(nodeRepository.findById(1L)).thenReturn(Optional.of(node(1L, "A")));
            lenient().when(nodeRepository.findById(2L)).thenReturn(Optional.of(node(2L, "B")));
            lenient().when(prerequisiteRepository.existsById(any())).thenReturn(true);

            assertThatThrownBy(
                            () -> service.addPrerequisite(new AddPrerequisiteRequest(1L, 2L)))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        }
    }

    @Nested
    @DisplayName("resolveStatuses()")
    class StatusResolution {

        /** Wires an adjacency map plus a flat {@code nodeId, score, ...} list. */
        private void stubGraph(final Map<Long, List<Long>> adjacency, final long... scores) {
            lenient().when(nodeRepository.findAllEdges()).thenReturn(rows(adjacency));

            final List<KnowledgeNodeMastery> mastery = new ArrayList<>();
            for (int i = 0; i + 1 < scores.length; i += 2) {
                mastery.add(
                        KnowledgeNodeMastery.builder()
                                .id(new MasteryId(1L, scores[i]))
                                .masteryScore(Math.toIntExact(scores[i + 1]))
                                .build());
            }
            lenient().when(masteryRepository.findAllForStudent(1L)).thenReturn(mastery);
        }

        private StudentNodeStatusDto statusOf(
                final List<StudentNodeStatusDto> all, final String nodeId) {
            return all.stream()
                    .filter(dto -> dto.nodeId().equals(nodeId))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        @DisplayName("a node at the threshold is MASTERED")
        void masterAtThreshold() {
            stubNodes(node(1L, "A"));
            stubGraph(Map.<Long, List<Long>>of(), 1L, KnowledgeNodeService.MASTERY_THRESHOLD);

            assertThat(statusOf(service.resolveStatuses(1L), "1").status())
                    .isEqualTo("MASTERED");
        }

        @Test
        @DisplayName("one point below the threshold is not MASTERED")
        void notMasteredBelowThreshold() {
            stubNodes(node(1L, "A"));
            stubGraph(Map.<Long, List<Long>>of(), 1L, KnowledgeNodeService.MASTERY_THRESHOLD - 1);

            assertThat(statusOf(service.resolveStatuses(1L), "1").status())
                    .isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("an unmet prerequisite locks the node")
        void lockedByPrerequisite() {
            // 1 -> 2, and node 2 has no mastery record.
            stubNodes(node(1L, "A"), node(2L, "B"));
            stubGraph(Map.of(1L, List.of(2L)));

            final StudentNodeStatusDto blocked = statusOf(service.resolveStatuses(1L), "2");

            assertThat(blocked.status()).isEqualTo("LOCKED");
            assertThat(blocked.blockingPrerequisites()).containsExactly("1");
        }

        @Test
        @DisplayName("a mastered prerequisite unlocks the node")
        void unlockedWhenPrerequisiteMastered() {
            stubNodes(node(1L, "A"), node(2L, "B"));
            stubGraph(Map.of(1L, List.of(2L)), 1L, 90);

            assertThat(statusOf(service.resolveStatuses(1L), "2").status())
                    .isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("mastery survives an unmastered prerequisite - never revoked")
        void masteryNotRevoked() {
            // Node 1 is below threshold, yet node 2 already scored 95.
            stubNodes(node(1L, "A"), node(2L, "B"));
            stubGraph(Map.of(1L, List.of(2L)), 1L, 10, 2L, 95);

            assertThat(statusOf(service.resolveStatuses(1L), "2").status())
                    .isEqualTo("MASTERED");
        }

        @Test
        @DisplayName("a prerequisite that exists but is untouched blocks")
        void untouchedPrerequisiteBlocks() {
            // Edge 99 -> 1 and node 99 has no mastery row. The foreign key means
            // the node must exist, so it reads as IN_PROGRESS, not satisfied.
            stubNodes(node(1L, "A"), node(99L, "Z"));
            stubGraph(Map.of(99L, List.of(1L)));

            assertThat(statusOf(service.resolveStatuses(1L), "1").status()).isEqualTo("LOCKED");
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("rejects a duplicate node name")
        void duplicateName() {
            lenient()
                    .when(subjectRepository.findById(1))
                    .thenReturn(Optional.of(Subject.builder().subjectId(1).build()));
            lenient()
                    .when(nodeRepository.findByNodeNameIgnoreCase("Toán rời rạc"))
                    .thenReturn(Optional.of(node(1L, "Toán rời rạc")));

            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new CreateNodeRequest(1, "Toán rời rạc", null)))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
            verify(nodeRepository, never()).save(any());
        }
    }
}