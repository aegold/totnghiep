package com.thesis.elearning.modules.analytics.dto;

import java.math.BigDecimal;

/**
 * One column of an exam score histogram.
 *
 * <h2>Why all four bands are always present</h2>
 * The query LEFT JOINs its counts onto a fixed four-row skeleton, so an empty
 * band arrives as a zero rather than being absent. Recharts sizes its axis from
 * the data it is given: a missing first category makes the remaining bars shift
 * left and the teacher reads the wrong distribution.
 *
 * @param bucketLabel  Vietnamese band label, e.g. {@code Khá [6.0 - 8.0 điểm]}.
 * @param studentCount submissions in this band; counts attempts, so a student
 *                     who sat a retake twice contributes twice.
 * @param percentage   this band's share of all submissions, as a percentage.
 */
public record ScoreDistributionBucketDto(
        String bucketLabel, int studentCount, BigDecimal percentage) {}