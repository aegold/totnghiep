package com.thesis.elearning.modules.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a course.
 *
 * @param subjectId owning subject, used to group the curriculum.
 * @param title     course title.
 * @param description optional long-form summary.
 * @param published whether learners can see it immediately.
 */
public record CreateCourseRequest(
        @NotNull(message = "Vui lòng chọn môn học")
                @Positive(message = "Mã môn học không hợp lệ")
                Integer subjectId,

        @NotBlank(message = "Vui lòng nhập tên môn học")
                @Size(max = 200, message = "Tên môn học không được vượt quá 200 ký tự")
                String title,

        @Size(max = 4_000, message = "Mô tả không được vượt quá 4000 ký tự")
                String description,

        boolean published) {}