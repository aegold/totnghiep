package com.thesis.elearning.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a knowledge node.
 *
 * @param subjectId   owning subject; drives the graph's grouping.
 * @param nodeName    display name.
 * @param description optional prose.
 */
public record CreateNodeRequest(
        @NotNull(message = "Vui lòng chọn môn học")
                @Positive(message = "Mã môn học không hợp lệ")
                Integer subjectId,

        @NotBlank(message = "Vui lòng nhập tên nút kiến thức")
                @Size(max = 150, message = "Tên nút kiến thức không được vượt quá 150 ký tự")
                String nodeName,

        @Size(max = 4_000, message = "Mô tả không được vượt quá 4000 ký tự")
                String description) {}