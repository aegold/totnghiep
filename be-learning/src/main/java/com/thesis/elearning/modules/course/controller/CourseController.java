package com.thesis.elearning.modules.course.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.course.dto.CreateCourseRequest;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto;
import com.thesis.elearning.modules.course.service.CourseService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Course endpoints, mounted under {@code /teacher/courses} so the existing
 * {@code /teacher/**} -> {@code hasRole("TEACHER")} rule applies. Writes repeat
 * the requirement with {@link PreAuthorize} so the guarantee survives a future
 * remount elsewhere.
 */
@RestController
@RequestMapping("/teacher/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(final CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseOutlineDto>> create(
            @Valid @RequestBody final CreateCourseRequest request, final Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                courseService.create(request, currentUserId(principal)),
                                "Tạo môn học thành công"));
    }

    /** Full outline: course -> chapters -> lessons -> materials. */
    @GetMapping("/{courseId}/outline")
    public ApiResponse<CourseOutlineDto> outline(@PathVariable final Long courseId) {
        return ApiResponse.success(
                courseService.outline(courseId), "Lấy cấu trúc môn học thành công");
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<CourseOutlineDto>> mine(final Principal principal) {
        return ApiResponse.success(
                courseService.findByTeacher(currentUserId(principal)),
                "Lấy danh sách môn học thành công");
    }

    /**
     * Soft-deletes a course.
     *
     * <p>Flags the row rather than erasing it; chapters, lessons, materials and
     * learner progress remain resolvable, and the shared knowledge graph is
     * never touched.
     */
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable final Long courseId) {
        courseService.softDelete(courseId);
        return ApiResponse.success("Đã xoá môn học");
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
}