package com.thesis.elearning.modules.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the teacher dashboard shows for one paper.
 *
 * @param examId              the paper analysed.
 * @param examTitle           its title.
 * @param courseId            owning course, so the client can link back.
 * @param courseTitle         owning course's title.
 * @param submissionCount     graded attempts on this paper. Attempts still in
 *                            flight are excluded - they have no answers yet and
 *                            would drag the average down as if they were zeros.
 * @param averageScore        mean score as a percentage over those attempts.
 * @param scoreBuckets        the histogram, all four bands, weakest first.
 * @param questionFailureRates per-question failure rates, hardest first. The top
 *                            row is the question worth reteaching.
 * @param atRiskParticipants  students tripping the risk rule on this paper,
 *                            lowest average first.
 */
public record ExamAnalyticsResponse(
        Long examId,
        String examTitle,
        Long courseId,
        String courseTitle,
        int submissionCount,
        BigDecimal averageScore,
        List<ScoreDistributionBucketDto> scoreBuckets,
        List<QuestionFailureRateDto> questionFailureRates,
        List<AtRiskStudentDto> atRiskParticipants) {}