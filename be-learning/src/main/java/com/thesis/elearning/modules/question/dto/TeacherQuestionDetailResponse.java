package com.thesis.elearning.modules.question.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/**
 * Full question detail for an authoring client.
 *
 * <p>The only response in the API that exposes {@code solutionPayload}. Served
 * exclusively from {@code /teacher/questions/**}, whose URL matcher admits
 * TEACHER and ADMIN and whose controller methods additionally carry
 * {@code @PreAuthorize}.
 *
 * @param questionId     stable identity across revisions.
 * @param nodeId         knowledge node assessed.
 * @param nodeName       display name, denormalised to avoid a second round trip.
 * @param difficultyId   difficulty band.
 * @param difficultyCode difficulty band label.
 * @param questionType   renderer discriminator.
 * @param authorId       owning teacher.
 * @param authorName     display name.
 * @param versionId      the revision being shown.
 * @param versionNumber  1-based revision counter.
 * @param active         whether this revision is the one students would receive.
 * @param createdAt      when this revision was written.
 * @param contentPayload student-visible prompt and structure.
 * @param solutionPayload private answer key and rationales.
 */
public record TeacherQuestionDetailResponse(
        Long questionId,
        Long nodeId,
        String nodeName,
        Integer difficultyId,
        String difficultyCode,
        String questionType,
        Long authorId,
        String authorName,
        Long versionId,
        Integer versionNumber,
        boolean active,
        LocalDateTime createdAt,
        JsonNode contentPayload,
        JsonNode solutionPayload) {}
