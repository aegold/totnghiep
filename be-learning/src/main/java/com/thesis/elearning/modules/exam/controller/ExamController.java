package com.thesis.elearning.modules.exam.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.exam.dto.ClockSyncResponse;
import com.thesis.elearning.modules.exam.dto.ExamResultResponse;
import com.thesis.elearning.modules.exam.dto.ExamSessionResponse;
import com.thesis.elearning.modules.exam.dto.SubmitExamRequest;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.exam.service.ExamService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exam-taking, for students.
 *
 * <h2>Why there is no class-level {@code @RequestMapping}</h2>
 * Every handler here answers at two URLs - the canonical {@code /student/**} one
 * and an alias the exam-runner client was written against - and a single class
 * prefix cannot express a per-method set of paths. {@code QuestionController}
 * solves the same problem the same way.
 *
 * <h2>Why the aliases are safe to expose</h2>
 * The alias prefixes are added to {@code SecurityConfig}'s
 * {@code hasRole("STUDENT")} matcher, so they are not a way around the student
 * gate. Both spellings of every route resolve to the same handler, which reads
 * the candidate from the JWT subject, so ownership cannot be influenced by which
 * URL was used.
 *
 * <h2>Why this needs no {@code @PreAuthorize}</h2>
 * The candidate's identity is taken from the token rather than the request body,
 * so a student cannot sit an attempt on somebody else's behalf by editing a
 * field. Ownership of the attempt is re-checked inside the service against the
 * submission row. The URL matcher answers "is this a student"; only the service
 * can answer "is this <em>their</em> attempt".
 */
@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamRepository examRepository;

    /** Papers currently open to students. */
    @GetMapping({"/student/exams", "/exam-runs"})
    public ApiResponse<List<ExamSummary>> listPublished() {
        final List<ExamSummary> summaries =
                examRepository.findPublished().stream()
                        .map(
                                exam ->
                                        new ExamSummary(
                                                exam.getExamId(),
                                                exam.getCourse().getCourseId(),
                                                exam.getTitle(),
                                                exam.getDurationMinutes(),
                                                exam.getMaxAttempts()))
                        .toList();
        return ApiResponse.success(summaries, "Lấy danh sách bài thi thành công");
    }

    /**
     * Opens an attempt and returns the paper with the server's clock.
     *
     * <p>Idempotent: calling it again while an attempt is open returns that
     * attempt rather than starting a second one.
     */
    @PostMapping({"/student/exams/{examId}/start", "/exam-runs/{examId}/start"})
    public ResponseEntity<ApiResponse<ExamSessionResponse>> start(
            @PathVariable final Long examId, final Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                examService.startExam(examId, actorId(principal)),
                                "Bắt đầu làm bài thành công"));
    }

    /**
     * Closes the attempt, grading it server-side.
     *
     * <p>The alias carries the submission id, not the exam id - that is the shape
     * the client was built against - so it is mapped onto the same path variable
     * and reconciled against the graded result below, exactly as the canonical
     * route does.
     */
    @PostMapping({"/student/exams/{examId}/submit", "/exam-submissions/{examId}/submit"})
    public ApiResponse<ExamResultResponse> submit(
            @PathVariable final Long examId,
            @Valid @RequestBody final SubmitExamRequest request,
            final Principal principal) {

        final ExamResultResponse result =
                examService.submitExam(request.submissionId(), request, actorId(principal));

        // The paper named in the path must be the one the attempt belongs to;
        // catching it here gives a clear message instead of grading silently.
        if (!examId.equals(result.examId())) {
            throw AppException.badRequest("Bài thi không khớp với lần thi này.");
        }
        return ApiResponse.success(result, "Nộp bài thành công");
    }

    /**
     * Re-reads the server clock for an attempt still in flight.
     *
     * <p>Exists because a suspended tab stops the client's own countdown without
     * the server noticing; polling this is how the client recovers. Returning an
     * absolute deadline rather than a corrected offset means one response is
     * enough to resynchronise, however long the stall was.
     */
    @GetMapping({"/student/submissions/{submissionId}/sync", "/exam-submissions/{submissionId}/sync"})
    public ApiResponse<ClockSyncResponse> sync(
            @PathVariable final Long submissionId, final Principal principal) {
        return ApiResponse.success(
                examService.syncClock(submissionId, actorId(principal)),
                "Đồng bộ thời gian thi thành công.");
    }

    /** The numeric user id carried as the JWT subject. */
    private static Long actorId(final Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (final NumberFormatException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** Enough to render a list; deliberately carries no attempt state. */
    public record ExamSummary(
            Long examId,
            Long courseId,
            String title,
            Integer durationMinutes,
            Integer maxAttempts) {}
}