package com.thesis.elearning.modules.question.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.question.domain.ActorContext;
import com.thesis.elearning.modules.question.dto.CreateQuestionRequest;
import com.thesis.elearning.modules.question.dto.StudentQuestionResponse;
import com.thesis.elearning.modules.question.dto.TeacherQuestionDetailResponse;
import com.thesis.elearning.modules.question.dto.UpdateQuestionRequest;
import com.thesis.elearning.modules.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Question authoring and the student-facing projection.
 *
 * <h2>Why there is no class-level {@code @RequestMapping}</h2>
 * The two tiers need different path prefixes: authoring lives under
 * {@code /teacher/**} so the URL matcher denies students before any handler
 * runs, while the exam-time read must be reachable by a student holding only
 * ROLE_STUDENT. A class-level prefix would force one tier to inherit the other's
 * gate, so each method declares its own full path.
 *
 * <h2>Why the annotation stays even though the URL matcher already covers it</h2>
 * {@code SecurityConfig} matches {@code /teacher/**} with
 * {@code hasAnyRole("TEACHER", "ADMIN")}, which is defence in depth rather than
 * redundancy: relisting the rule here means moving a handler to another prefix
 * cannot silently drop its authorisation.
 */
@RestController
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(final QuestionService questionService) {
        this.questionService = questionService;
    }

    /* ----------------------------- Authoring ---------------------------- */

    /** Creates a question at version 1. */
    @PostMapping("/teacher/questions")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeacherQuestionDetailResponse>> create(
            @Valid @RequestBody final CreateQuestionRequest request,
            final Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                questionService.createQuestion(request, actorId(authentication)),
                                "Tạo câu hỏi thành công"));
    }

    /** Authoring detail, including the answer key. */
    @GetMapping("/teacher/questions/{questionId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ApiResponse<TeacherQuestionDetailResponse> getForTeacher(
            @PathVariable final Long questionId) {
        return ApiResponse.success(
                questionService.getQuestionForTeacher(questionId),
                "Lấy chi tiết câu hỏi thành công");
    }

    /**
     * Publishes a new revision.
     *
     * <p>Returns 200 with the new revision rather than 201: a row was created,
     * but the caller's intent was to modify {@code /questions/{id}} and that id
     * does not change.
     */
    @PutMapping("/teacher/questions/{questionId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ApiResponse<TeacherQuestionDetailResponse> update(
            @PathVariable final Long questionId,
            @Valid @RequestBody final UpdateQuestionRequest request,
            final Authentication authentication) {
        return ApiResponse.success(
                questionService.updateQuestion(questionId, request, actor(authentication)),
                "Cập nhật câu hỏi thành công");
    }

    /** Soft-deletes; the row and its versions survive for historical papers. */
    @DeleteMapping("/teacher/questions/{questionId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable final Long questionId, final Authentication authentication) {
        questionService.softDeleteQuestion(questionId, actor(authentication));
        return ApiResponse.success("Xoá câu hỏi thành công");
    }


    /* ------------------------------ Student ----------------------------- */

    /**
     * Serves one revision to a candidate.
     *
     * <p>Deliberately outside {@code /teacher/**} so a student can reach it, and
     * consequently outside the authoring gate. The protection here is the DTO
     * itself, which has no {@code solutionPayload} property; {@code anyRequest()
     * .authenticated()} is sufficient, because an anonymous caller gets 401 and a
     * student receives the public payload and nothing more.
     */
    @GetMapping("/questions/{versionId}")
    public ApiResponse<StudentQuestionResponse> getForStudent(
            @PathVariable final Long versionId) {
        return ApiResponse.success(
                questionService.getQuestionForStudent(versionId),
                "Lấy nội dung câu hỏi thành công");
    }

    /* ----------------------------- Internals ---------------------------- */

    /** Builds the service's ownership context from the authenticated principal. */
    private static ActorContext actor(final Authentication authentication) {
        return new ActorContext(actorId(authentication), isAdmin(authentication));
    }

    /** The numeric user id carried as the JWT subject. */
    private static Long actorId(final Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (final NumberFormatException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private static boolean isAdmin(final Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
