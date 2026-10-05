package com.thesis.elearning.modules.question.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for authoring a brand new question.
 *
 * <p>Carries both halves of the payload pair because only an authoring client may
 * supply an answer key. The service splits them into two columns and guarantees
 * the {@code solution_payload} half is reachable only through
 * {@link TeacherQuestionDetailResponse}.
 *
 * @param nodeId          knowledge node the question assesses.
 * @param difficultyId    row in {@code difficulty_levels}.
 * @param questionType    one of the four supported renderers.
 * @param contentPayload  student-visible prompt and structure.
 * @param solutionPayload private answer key and rationales.
 */
public record CreateQuestionRequest(
        @NotNull(message = "Vui lòng chọn nút kiến thức")
                @Positive(message = "Mã nút kiến thức không hợp lệ")
                Long nodeId,

        @NotNull(message = "Vui lòng chọn mức độ khó")
                @Positive(message = "Mã mức độ khó không hợp lệ")
                Integer difficultyId,

        @NotBlank(message = "Vui lòng chọn loại câu hỏi")
                @Size(max = 30, message = "Loại câu hỏi không được vượt quá 30 ký tự")
                String questionType,

        @NotNull(message = "Vui lòng nhập nội dung câu hỏi")
                JsonNode contentPayload,

        @NotNull(message = "Vui lòng nhập đáp án chuẩn")
                JsonNode solutionPayload) {}
