package com.thesis.elearning.modules.course.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto.LessonDto;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto.MaterialDto;
import com.thesis.elearning.modules.course.dto.UpdateProgressRequest;
import com.thesis.elearning.modules.course.service.LearningProgressService;
import com.thesis.elearning.modules.course.service.LessonService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lesson and material endpoints.
 *
 * <p>Split by audience: authoring lives under {@code /teacher/...} and learner
 * progress under {@code /student/...}, so the subtree rules already declared in
 * {@code SecurityConfig} apply without extra configuration.
 */
@RestController
@RequestMapping
public class LessonController {

    private final LessonService lessonService;
    private final LearningProgressService progressService;

    public LessonController(
            final LessonService lessonService,
            final LearningProgressService progressService) {
        this.lessonService = lessonService;
        this.progressService = progressService;
    }

    /* ---------------------------- Authoring ---------------------------- */

    @PostMapping("/teacher/courses/{courseId}/chapters")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> createChapter(
            @PathVariable final Long courseId,
            @Valid @RequestBody final TitleOrderRequest request) {
        lessonService.createChapter(courseId, request.title(), request.orderIndex());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo chương thành công"));
    }

    @PostMapping("/teacher/chapters/{chapterId}/lessons")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LessonDto>> createLesson(
            @PathVariable final Long chapterId,
            @Valid @RequestBody final TitleOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                lessonService.createLesson(
                                        chapterId, request.title(), request.orderIndex()),
                                "Tạo bài học thành công"));
    }

    @PostMapping("/teacher/lessons/{lessonId}/materials")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(
            @PathVariable final Long lessonId,
            @Valid @RequestBody final CreateMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                lessonService.createMaterial(
                                        lessonId,
                                        request.materialType(),
                                        request.title(),
                                        request.fileUrl(),
                                        request.durationSeconds()),
                                "Tạo tài liệu thành công"));
    }

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonDto> get(@PathVariable final Long lessonId) {
        return ApiResponse.success(
                lessonService.findLesson(lessonId), "Lấy chi tiết bài học thành công");
    }

    /** Soft-deletes a lesson; rows and progress are retained for audit. */
    @DeleteMapping("/teacher/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteLesson(@PathVariable final Long lessonId) {
        lessonService.softDeleteLesson(lessonId);
        return ApiResponse.success("Đã xoá bài học");
    }

    /** Soft-deletes a chapter without cascading into its lessons. */
    @DeleteMapping("/teacher/chapters/{chapterId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteChapter(@PathVariable final Long chapterId) {
        lessonService.softDeleteChapter(chapterId);
        return ApiResponse.success("Đã xoá chương");
    }

    /* ----------------------------- Progress ---------------------------- */

    /**
     * Records progress for the signed-in student.
     *
     * <p>The student id comes from the token, never from the request body, so a
     * learner cannot record progress on somebody else's behalf.
     */
    @PutMapping("/student/lessons/{lessonId}/progress")
    public ApiResponse<Void> updateProgress(
            @PathVariable final Long lessonId,
            @Valid @RequestBody final UpdateProgressRequest request,
            final Principal principal) {
        progressService.update(currentUserId(principal), lessonId, request);
        return ApiResponse.success("Cập nhật tiến độ học tập thành công");
    }

    private static Long currentUserId(final Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (final NumberFormatException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** Title plus ordering position, shared by chapter and lesson creation. */
    public record TitleOrderRequest(
            @NotBlank(message = "Vui lòng nhập tiêu đề")
                    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
                    String title,
            @Min(value = 0, message = "Thứ tự không được âm") Integer orderIndex) {}

    /** Body for adding a retrievable material to a lesson. */
    public record CreateMaterialRequest(
            @NotBlank(message = "Vui lòng nhập loại tài liệu")
                    @Pattern(
                            regexp = "VIDEO|PDF|DOCUMENT|SLIDES",
                            message = "Loại tài liệu phải là VIDEO, PDF, DOCUMENT hoặc SLIDES")
                    String materialType,
            @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự") String title,
            @Size(max = 500, message = "Đường dẫn không được vượt quá 500 ký tự") String fileUrl,
            @Min(value = 0, message = "Thời lượng không được âm") Integer durationSeconds) {}
}