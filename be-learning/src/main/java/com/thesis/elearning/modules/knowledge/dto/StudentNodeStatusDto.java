package com.thesis.elearning.modules.knowledge.dto;

import java.util.List;

/**
 * One node's state for one student.
 *
 * <h2>Derivation rules</h2>
 * <ul>
 *   <li>{@code MASTERED} when the score is at or above the threshold. Evaluated
 *       FIRST, so mastery already earned is never revoked because a prerequisite
 *       later changed - the same rule the client applies.</li>
 *   <li>{@code LOCKED} when any prerequisite is not mastered.</li>
 *   <li>{@code IN_PROGRESS} otherwise.</li>
 * </ul>
 *
 * <p>A prerequisite id that no longer resolves is treated as satisfied. Locking a
 * student behind a row that was deleted upstream is a trap they can never
 * escape, and there is nothing they could do about it.
 *
 * @param nodeId      node id, stringified.
 * @param status      LOCKED, IN_PROGRESS or MASTERED.
 * @param masteryScore percentage 0..100.
 * @param blockingPrerequisites prerequisite ids that are not yet mastered.
 */
public record StudentNodeStatusDto(
        String nodeId,
        String status,
        int masteryScore,
        List<String> blockingPrerequisites) {}