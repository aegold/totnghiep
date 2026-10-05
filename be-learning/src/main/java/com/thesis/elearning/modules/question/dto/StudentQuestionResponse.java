package com.thesis.elearning.modules.question.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * The student-facing projection of one question revision.
 *
 * <h2>Why the answer key is absent rather than filtered out</h2>
 * There is no {@code solutionPayload} property on this record, and no
 * {@code @JsonIgnore} and no runtime scrubbing step. A serialisation filter can be
 * forgotten, mis-scoped to the wrong DTO, or bypassed by a mapper that copies
 * whole entities; a field that does not exist cannot be serialised under any
 * code path. {@code StudentQuestionResponseLeakageTest} asserts the absence by
 * reflection so a later edit that adds the property back fails the build.
 *
 * <p>Grading therefore has to happen server-side at submission time. Nothing in
 * this DTO lets a client compute a score.
 *
 * @param versionId      the exact revision the student is being served.
 * @param questionType   renderer discriminator.
 * @param points         marks this revision carries in its exam, or {@code null}
 *                       when the question is fetched outside an exam.
 * @param orderIndex     1-based position within the exam paper, or {@code null}
 *                       when fetched outside an exam.
 * @param contentPayload student-visible prompt and structure.
 */
public record StudentQuestionResponse(
        Long versionId,
        String questionType,
        BigDecimal points,
        Integer orderIndex,
        JsonNode contentPayload) {

    /**
     * Builds the exam-agnostic projection.
     *
     * <p>{@code points} and {@code orderIndex} live on {@code exam_questions},
     * not on the question: the same revision can appear in several papers with
     * different marks, and outside any paper the values are genuinely unknown.
     * They are therefore left null here and populated by the Stage 5 exam service
     * once it knows which paper is being served.
     */
    public static StudentQuestionResponse of(
            final Long versionId,
            final String questionType,
            final JsonNode contentPayload) {
        return new StudentQuestionResponse(versionId, questionType, null, null, contentPayload);
    }
}
