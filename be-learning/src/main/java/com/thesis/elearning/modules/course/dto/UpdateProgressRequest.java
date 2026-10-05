package com.thesis.elearning.modules.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for recording a student's progress through a lesson.
 *
 * <p>The grain is the LESSON, matching the inherited
 * {@code learning_progress(student_id, lesson_id)} key. The client currently
 * caches progress per material; that divergence is recorded and needs
 * reconciling when the learning endpoints are wired.
 *
 * @param lastWatchedSeconds resume position; clamped to non-negative by the service.
 * @param completed         marks the lesson finished.
 */
public record UpdateProgressRequest(
        @NotNull(message = "Vui lòng nhập thời điểm đã xem")
                @Min(value = 0, message = "Thời điểm xem không được âm")
                Integer lastWatchedSeconds,

        @Min(value = 0, message = "Cờ đã hoàn thành không được âm")
                Integer completed) {}