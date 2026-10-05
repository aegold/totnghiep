package com.thesis.elearning.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The outcome of grading an attempt.
 *
 * <h2>Why {@code isLate} is derived, not stored</h2>
 * Lateness is a fact about two timestamps and the paper's duration. Persisting
 * a boolean would be a third copy of the same information, free to drift out of
 * agreement with the columns it was derived from - and a stored flag cannot
 * answer "by how much", which is the question a marker actually has. Computing
 * it at read time costs nothing and is always consistent.
 *
 * <h2>Why the per-question results carry no explanations</h2>
 * A student who has just sat the paper may see which questions were right and
 * what they scored. They may not see the rationales, the accepted answer
 * spellings, or the correct pairings - those live in {@code solution_payload}
 * and stay there. {@code result} below is deliberately a bare verdict.
 *
 * @param totalScore     marks earned across the paper.
 * @param maxScore       marks the paper was worth.
 * @param percentage     {@code totalScore / maxScore} as a whole number, or
 *                       null when the paper carries no marks.
 * @param answeredCount  questions the student supplied an answer for.
 * @param questionCount  questions on the paper.
 * @param correctCount   questions at full marks.
 * @param isLate         arrived after {@code serverEndTime + grace}.
 * @param graceSeconds   the network-latency allowance applied before judging
 *                       lateness.
 * @param submittedAt    when the server recorded the hand-in.
 */
public record ExamResultResponse(
        Long submissionId,
        Long examId,
        BigDecimal totalScore,
        BigDecimal maxScore,
        Integer percentage,
        int answeredCount,
        int questionCount,
        int correctCount,
        boolean isLate,
        long graceSeconds,
        LocalDateTime submittedAt,
        List<QuestionResult> results) {

    /**
     * One question's verdict.
     *
     * @param versionId     the revision that was served.
     * @param questionType  renderer, so the client can label the row.
     * @param orderIndex    position on the paper.
     * @param earnedPoints  marks awarded.
     * @param maxPoints     marks the question was worth.
     * @param fullyCorrect  true only for full marks.
     * @param answered      whether anything was submitted.
     */
    public record QuestionResult(
            Long versionId,
            String questionType,
            Integer orderIndex,
            BigDecimal earnedPoints,
            BigDecimal maxPoints,
            boolean fullyCorrect,
            boolean answered) {}
}
