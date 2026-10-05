package com.thesis.elearning.modules.analytics.dto;

import java.math.BigDecimal;

/**
 * Headline numbers for one course's dashboard.
 *
 * <p>Every field is produced by a single SQL aggregate rather than by counting
 * entities in Java: each of these would otherwise be a full scan of
 * {@code exam_submissions} and {@code learning_progress} per dashboard load.
 *
 * @param totalStudents distinct students with any recorded activity in the
 *                     course - either a submission on one of its papers or a
 *                     lesson-progress row. There is no enrolment table in the
 *                     schema, so activity is the only evidence of participation.
 * @param averageScore  mean score across submitted attempts, as a percentage.
 *                      {@code null} when nobody has submitted anything yet,
 *                      which is different from a real average of zero.
 * @param completionRate mean completed-lessons ratio over the students who have
 *                      progress rows, as a percentage.
 * @param atRiskStudentCount how many of those students trip {@link
 *                      com.thesis.elearning.modules.analytics.domain.AtRiskRule}.
 */
public record CourseOverviewMetricsDto(
        long totalStudents,
        BigDecimal averageScore,
        BigDecimal completionRate,
        long atRiskStudentCount) {}