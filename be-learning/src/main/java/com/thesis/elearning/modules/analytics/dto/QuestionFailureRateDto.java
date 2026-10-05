package com.thesis.elearning.modules.analytics.dto;

import java.math.BigDecimal;

/**
 * How often one question on a paper was answered wrongly.
 *
 * <p>The highest {@code failurePercentage} on the page is the question to
 * reteach, so it sorts descending.
 *
 * @param questionId       the question this revision belongs to.
 * @param versionId        the exact revision pinned by the paper. Reported
 *                         alongside {@code questionId} because a paper pins a
 *                         version, and a question edited since would otherwise
 *                         make a past failure rate look like it drifted.
 * @param promptSnippet    first line of the stem, truncated for display.
 * @param questionType     renderer name, so the client can group or label.
 * @param totalAttempts    submissions that reached this question. Stage 5 writes
 *                         a row per paper question even when blank, so this is
 *                         the number of attempts, not the number answered.
 * @param failurePercentage wrong answers over {@code totalAttempts}, as a
 *                         percentage. A blank submission counts as wrong, which
 *                         is the reading a teacher wants: skipped and mistaken
 *                         are both "this did not land".
 */
public record QuestionFailureRateDto(
        Long questionId,
        Long versionId,
        String promptSnippet,
        String questionType,
        int totalAttempts,
        BigDecimal failurePercentage) {}