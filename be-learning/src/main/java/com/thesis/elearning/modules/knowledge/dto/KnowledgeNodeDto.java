package com.thesis.elearning.modules.knowledge.dto;

import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import java.util.List;

/**
 * A knowledge node as the client's graph consumes it.
 *
 * <h2>Why ids are strings</h2>
 * {@code knowledge_nodes.node_id} is a BIGINT, but the client's
 * {@code KnowledgeNodeData.id} is a string, and React Flow keys nodes by it.
 * Serialising the id as a string avoids a lossy conversion in the client and
 * keeps the graph keys stable regardless of how the browser renders them.
 *
 * @param id          node id, stringified.
 * @param title       node name.
 * @param description nullable prose shown in the drawer.
 * @param topic       subject name; there is no topic column, it is derived.
 * @param prerequisites ids of nodes that must be mastered first.
 * @param relatedLessonCount lessons linked via {@code lesson_knowledge_nodes}.
 */
public record KnowledgeNodeDto(
        String id,
        String title,
        String description,
        String topic,
        List<String> prerequisites,
        int relatedLessonCount) {

    public static KnowledgeNodeDto from(final KnowledgeNode node) {
        return new KnowledgeNodeDto(
                String.valueOf(node.getNodeId()),
                node.getNodeName(),
                node.getDescription(),
                // Lazy proxy: touched inside an open transaction by the caller.
                node.getSubject() == null ? null : node.getSubject().getSubjectName(),
                List.of(),
                0);
    }

    /** Full projection once prerequisites and counts have been resolved. */
    public static KnowledgeNodeDto of(
            final KnowledgeNode node,
            final List<String> prerequisites,
            final int relatedLessonCount) {
        return new KnowledgeNodeDto(
                String.valueOf(node.getNodeId()),
                node.getNodeName(),
                node.getDescription(),
                node.getSubject() == null ? null : node.getSubject().getSubjectName(),
                prerequisites,
                relatedLessonCount);
    }
}