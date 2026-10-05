package com.thesis.elearning.modules.analytics.dto;

import java.util.List;

/**
 * Everything the teacher dashboard shows for one course.
 *
 * <h2>Why the three lists travel together</h2>
 * The page asks one question - "how is this cohort doing" - and needs the
 * headline numbers, the competencies dragging them down, and the named students
 * to contact. Splitting them across three endpoints would make the teacher click
 * through to correlate figures that describe the same cohort at the same moment.
 *
 * <h2>Why ownership is settled before this record exists</h2>
 * Nothing here is filtered by viewer. The service proves the caller authored the
 * course first and then returns the whole picture, so a partially-filtered
 * response can never leak that a second teacher has cohorts at all.
 *
 * @param courseId          the course analysed.
 * @param courseTitle       its title, so the client header needs no second call.
 * @param overview          headline aggregates.
 * @param weakKnowledgeNodes competencies with the lowest mean mastery, weakest
 *                          first. May be empty when nothing has been graded.
 * @param atRiskStudents    students tripping the risk rule, lowest average
 *                          first. May be empty.
 */
public record CourseAnalyticsResponse(
        Long courseId,
        String courseTitle,
        CourseOverviewMetricsDto overview,
        List<WeakKnowledgeNodeDto> weakKnowledgeNodes,
        List<AtRiskStudentDto> atRiskStudents) {}