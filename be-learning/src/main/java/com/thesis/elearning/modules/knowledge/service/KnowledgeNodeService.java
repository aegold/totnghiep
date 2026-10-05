package com.thesis.elearning.modules.knowledge.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.knowledge.dto.AddPrerequisiteRequest;
import com.thesis.elearning.modules.knowledge.dto.CreateNodeRequest;
import com.thesis.elearning.modules.knowledge.dto.KnowledgeNodeDto;
import com.thesis.elearning.modules.knowledge.dto.StudentNodeStatusDto;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.entity.KnowledgePrerequisite;
import com.thesis.elearning.modules.knowledge.entity.KnowledgePrerequisite.PrerequisiteId;
import com.thesis.elearning.modules.knowledge.entity.Subject;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeRepository;
import com.thesis.elearning.modules.knowledge.repository.KnowledgePrerequisiteRepository;
import com.thesis.elearning.modules.knowledge.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD over knowledge nodes, prerequisite linking, and student status. */
@Service
public class KnowledgeNodeService {

    /** Percentage at or above which a competency counts as mastered. */
    public static final int MASTERY_THRESHOLD = 80;

    static final String STATUS_LOCKED = "LOCKED";
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_MASTERED = "MASTERED";

    private final KnowledgeNodeRepository nodeRepository;
    private final KnowledgePrerequisiteRepository prerequisiteRepository;
    private final KnowledgeNodeMasteryRepository masteryRepository;
    private final SubjectRepository subjectRepository;
    private final PrerequisiteGraphValidator validator;

    public KnowledgeNodeService(
            final KnowledgeNodeRepository nodeRepository,
            final KnowledgePrerequisiteRepository prerequisiteRepository,
            final KnowledgeNodeMasteryRepository masteryRepository,
            final SubjectRepository subjectRepository,
            final PrerequisiteGraphValidator validator) {
        this.nodeRepository = nodeRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.masteryRepository = masteryRepository;
        this.subjectRepository = subjectRepository;
        this.validator = validator;
    }

    /**
     * Node deletion is deliberately not offered.
     *
     * <p>{@code knowledge_nodes} has no {@code is_deleted} column, so a hard
     * delete would cascade through {@code knowledge_prerequisites} and could
     * re-lock every dependent student. Until a soft-delete column exists, nodes
     * are updated, never removed.
     */
    private KnowledgeNode requireNode(final Long nodeId) {
        return nodeRepository
                .findById(nodeId)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Không tìm thấy nút kiến thức"));
    }

    /* ------------------------------------------------------------------ */
    /*  CRUD                                                                */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<KnowledgeNodeDto> findAll() {
        return nodeRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeNodeDto findById(final Long nodeId) {
        return toDto(requireNode(nodeId));
    }

    @Transactional
    public KnowledgeNodeDto create(final CreateNodeRequest request) {
        final Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy môn học"));

        if (nodeRepository.findByNodeNameIgnoreCase(request.nodeName().trim()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tên nút kiến thức đã tồn tại");
        }

        final KnowledgeNode saved =
                nodeRepository.save(
                        KnowledgeNode.builder()
                                .subject(subject)
                                .nodeName(request.nodeName().trim())
                                .description(
                                        request.description() == null
                                                ? null
                                                : request.description().trim())
                                .build());

        return toDto(saved);
    }

    @Transactional
    public KnowledgeNodeDto update(final Long nodeId, final CreateNodeRequest request) {
        final KnowledgeNode node = requireNode(nodeId);

        if (request.subjectId() != null
                && !request.subjectId().equals(node.getSubject().getSubjectId())) {
            node.setSubject(
                    subjectRepository
                            .findById(request.subjectId())
                            .orElseThrow(
                                    () ->
                                            new AppException(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Không tìm thấy môn học")));
        }
        if (request.nodeName() != null && !request.nodeName().isBlank()) {
            node.setNodeName(request.nodeName().trim());
        }
        if (request.description() != null) {
            node.setDescription(request.description().trim());
        }
        return toDto(nodeRepository.save(node));
    }

    /** Resolves a node id or fails with a Vietnamese message. */
    private KnowledgeNodeDto toDto(final KnowledgeNode node) {
        final Long nodeId = node.getNodeId();
        final List<String> prerequisites =
                nodeRepository.findPrerequisiteIds(nodeId).stream()
                        .map(String::valueOf)
                        .toList();
        return KnowledgeNodeDto.of(
                node,
                prerequisites,
                Math.toIntExact(nodeRepository.countRelatedLessons(nodeId)));
    }

    /* ------------------------------------------------------------------ */
    /*  Prerequisite edges                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Links a prerequisite, rejecting any change that would create a cycle.
     *
     * <p>The candidate edge is added to a COPY of the adjacency map and the
     * WHOLE graph re-walked, rather than only checking the path between the two
     * endpoints. A targeted check is cheaper but easy to get subtly wrong when
     * an unrelated cycle already exists; one in-memory traversal is obviously
     * correct and the graph is small enough that the cost is irrelevant.
     */
    @Transactional
    public KnowledgeNodeDto addPrerequisite(final AddPrerequisiteRequest request) {
        final Long prerequisiteId = request.prerequisiteNodeId();
        final Long dependentId = request.dependentNodeId();

        if (validator.isSelfLoop(prerequisiteId, dependentId)) {
            throw cycleException(List.of(prerequisiteId, prerequisiteId));
        }

        requireNode(prerequisiteId);
        requireNode(dependentId);

        final PrerequisiteId edgeId = new PrerequisiteId(prerequisiteId, dependentId);
        if (prerequisiteRepository.existsById(edgeId)) {
            throw new AppException(
                    ErrorCode.DUPLICATE_RESOURCE, "Quan hệ tiên quyết này đã tồn tại");
        }

        final Optional<List<Long>> cycle = validator.findCycle(adjacencyWith(prerequisiteId, dependentId));
        if (cycle.isPresent()) {
            // @Transactional rolls back, so nothing is persisted.
            throw cycleException(cycle.get());
        }

        final KnowledgePrerequisite edge = new KnowledgePrerequisite();
        edge.setId(edgeId);
        edge.setPrerequisite(requireNode(prerequisiteId));
        edge.setDependent(requireNode(dependentId));
        prerequisiteRepository.save(edge);

        return toDto(requireNode(dependentId));
    }

    @Transactional
    public void removePrerequisite(final Long prerequisiteId, final Long dependentId) {
        prerequisiteRepository.deleteById(new PrerequisiteId(prerequisiteId, dependentId));
    }

    /** Existing graph as {@code prerequisite -> [dependents]}. */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> currentAdjacency() {
        final Map<Long, List<Long>> edges = new LinkedHashMap<>();
        for (final Object[] row : nodeRepository.findAllEdges()) {
            edges.computeIfAbsent((Long) row[0], key -> new ArrayList<>())
                    .add((Long) row[1]);
        }
        return edges;
    }

    /** A copy of the current graph with one hypothetical edge added. */
    private Map<Long, List<Long>> adjacencyWith(final Long from, final Long to) {
        final Map<Long, List<Long>> candidate = new HashMap<>(currentAdjacency());
        candidate.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
        // Ensure the new endpoint is traversable even if it had no outgoing edge.
        candidate.computeIfAbsent(to, key -> new ArrayList<>());
        return candidate;
    }

    /** The Vietnamese 400 the specification requires, with the offending path. */
    private static AppException cycleException(final List<Long> cycleIds) {
        return new AppException(
                ErrorCode.BAD_REQUEST,
                "Phát hiện vòng lặp phụ thuộc không hợp lệ: Node kiến thức này không thể làm điều kiện "
                        + "tiên quyết cho chính nó hoặc các node phụ thuộc trước đó. Chuỗi gây vòng lặp: "
                        + cycleIds.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(" -> ")));
    }

    /* ------------------------------------------------------------------ */
    /*  Student status resolution                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Resolves the status of every node for one student.
     *
     * <h2>Why mastery is checked before prerequisites</h2>
     * A node at or above the threshold is {@code MASTERED} even if a
     * prerequisite later dropped below it. Mastery already earned is not
     * revoked by a curriculum edit, which matches how the client derives status
     * and is the fairer rule for a learner.
     *
     * <h2>Why dangling prerequisites count as satisfied</h2>
     * If a prerequisite id no longer resolves, locking on it would trap the
     * student behind a row that no longer exists and that they cannot act on.
     */
    @Transactional(readOnly = true)
    public List<StudentNodeStatusDto> resolveStatuses(final Long studentId) {

        final List<KnowledgeNode> nodes = nodeRepository.findAll();

        // One query for every score, then index in memory: a lookup per node
        // would make opening the graph O(nodes) round-trips.
        final Map<Long, Integer> scores = new HashMap<>();
        for (final KnowledgeNodeMastery mastery : masteryRepository.findAllForStudent(studentId)) {
            scores.put(
                    mastery.getId().getNodeId(),
                    mastery.getMasteryScore() == null ? 0 : mastery.getMasteryScore());
        }

        /*
         * Nodes that still exist. An edge can outlive its endpoint - deleting a node
         * does not cascade into knowledge_prerequisites - so an adjacency map may
         * name a prerequisite that no longer resolves. Treating such an id as
         * unmet would lock every dependent on it permanently, behind a row the
         * student can neither see nor act on.
         */
        final Set<Long> knownNodeIds = new HashSet<>();
        for (final KnowledgeNode node : nodes) {
            knownNodeIds.add(node.getNodeId());
        }

        final Map<Long, List<Long>> adjacency = currentAdjacency();

        final List<StudentNodeStatusDto> result = new ArrayList<>(nodes.size());
        for (final KnowledgeNode node : nodes) {
            final Long nodeId = node.getNodeId();
            final int score = scores.getOrDefault(nodeId, 0);

            if (score >= MASTERY_THRESHOLD) {
                result.add(
                        new StudentNodeStatusDto(
                                String.valueOf(nodeId), STATUS_MASTERED, score, List.of()));
                continue;
            }

            // Prerequisite ids come from the reverse edges of this node.
            final List<Long> prerequisites = prerequisitesOf(nodeId, adjacency);
            final List<String> blocking = new ArrayList<>();
            for (final Long prerequisiteId : prerequisites) {
                    // Dangling edge: the endpoint row is gone, so it can no longer
                    // block anyone. Skipping it is what prevents a permanent LOCKED.
                    if (!knownNodeIds.contains(prerequisiteId)) {
                        continue;
                    }
                if (!isMastered(prerequisiteId, scores)) {
                    blocking.add(String.valueOf(prerequisiteId));
                }
            }

            result.add(
                    new StudentNodeStatusDto(
                            String.valueOf(nodeId),
                            blocking.isEmpty() ? STATUS_IN_PROGRESS : STATUS_LOCKED,
                            score,
                            List.copyOf(blocking)));
        }
        return result;
    }

    /**
     * Whether a prerequisite has been mastered.
     *
     * <p>A node with no mastery row scores zero, so an untouched prerequisite
     * correctly blocks its dependents - the same rule the client's
     * {@code deriveNodeStatus} applies, where a node below the threshold is
     * {@code IN_PROGRESS} and therefore not {@code MASTERED}.
     */
    private boolean isMastered(final Long nodeId, final Map<Long, Integer> scores) {
        return scores.getOrDefault(nodeId, 0) >= MASTERY_THRESHOLD;
    }

    /** Reverse lookup: which nodes must be mastered before {@code nodeId}. */
    private static List<Long> prerequisitesOf(
            final Long nodeId, final Map<Long, List<Long>> adjacency) {
        final List<Long> result = new ArrayList<>();
        adjacency.forEach(
                (prerequisite, dependents) -> {
                    if (dependents.contains(nodeId)) {
                        result.add(prerequisite);
                    }
                });
        return result;
    }
}
