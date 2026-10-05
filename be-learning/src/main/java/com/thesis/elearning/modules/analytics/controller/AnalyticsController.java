package com.thesis.elearning.modules.analytics.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.analytics.dto.CourseAnalyticsResponse;
import com.thesis.elearning.modules.analytics.dto.ExamAnalyticsResponse;
import com.thesis.elearning.modules.analytics.service.AnalyticsService;
import com.thesis.elearning.modules.question.domain.ActorContext;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher analytics, read-only.
 *
 * <h2>Why the prefix is {@code /teacher/analytics}</h2>
 * The whole subtree inherits {@code hasAnyRole("TEACHER", "ADMIN")} from
 * {@code SecurityConfig}, so a student holding only ROLE_STUDENT is refused at the
 * filter with a 401 before a handler is ever selected. The {@code @PreAuthorize}
 * annotations below restate that rule rather than relying on it: URL matchers are
 * evaluated before method security, so a handler moved to another prefix would
 * silently lose its authorisation, and restating keeps the annotation truthful.
 *
 * <h2>Why identity comes from the token, not the path</h2>
 * The actor is read from the JWT subject exactly as in {@code QuestionController},
 * so a teacher cannot request another teacher's figures by editing a query
 * parameter. Which courses they may see is decided by the service against
 * {@code courses.teacher_id}.
 */
@RestController
@RequestMapping("/teacher/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Course dashboard: headline metrics, weak competencies, at-risk students. */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ApiResponse<CourseAnalyticsResponse> courseAnalytics(
            @PathVariable final Long courseId, final Authentication authentication) {
        return ApiResponse.success(
                analyticsService.getCourseAnalytics(courseId, actor(authentication)),
                "Lấy dữ liệu thống kê thành công.");
    }

    /** Paper dashboard: score histogram, per-question failure rates, at-risk sitters. */
    @GetMapping("/exams/{examId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ApiResponse<ExamAnalyticsResponse> examAnalytics(
            @PathVariable final Long examId, final Authentication authentication) {
        return ApiResponse.success(
                analyticsService.getExamAnalytics(examId, actor(authentication)),
                "Lấy dữ liệu thống kê thành công.");
    }

    /* ============================== Internals ============================== */

    /**
     * Builds the service's ownership context from the authenticated principal.
     *
     * <p>Mirrors {@code QuestionController} so the two teacher-facing modules read
     * the JWT subject in one recognisable way.
     */
    private static ActorContext actor(final Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        final Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (final NumberFormatException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        final boolean admin =
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new ActorContext(userId, admin);
    }
}