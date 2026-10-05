package com.thesis.elearning.modules.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.exam.dto.ClockSyncResponse;
import com.thesis.elearning.modules.exam.dto.ExamResultResponse;
import com.thesis.elearning.modules.exam.dto.ExamSessionResponse;
import com.thesis.elearning.modules.exam.dto.SubmitExamRequest;
import com.thesis.elearning.modules.exam.entity.Exam;
import com.thesis.elearning.modules.exam.entity.ExamQuestion;
import com.thesis.elearning.modules.exam.entity.ExamSubmission;
import com.thesis.elearning.modules.exam.entity.SubmissionAnswer;
import com.thesis.elearning.modules.exam.repository.ExamQuestionRepository;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.exam.repository.ExamSubmissionRepository;
import com.thesis.elearning.modules.exam.repository.SubmissionAnswerRepository;
import com.thesis.elearning.modules.exam.service.grading.GradingResult;
import com.thesis.elearning.modules.exam.service.grading.GradingStrategyFactory;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.question.domain.QuestionType;
import com.thesis.elearning.modules.question.dto.StudentQuestionResponse;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sitting an exam: opening an attempt, and grading a hand-in.
 *
 * <h2>The server owns the clock</h2>
 * An attempt's deadline is {@code started_at + duration_minutes}, recomputed on
 * every request from columns that already exist. Nothing is scheduled, no
 * countdown is trusted from the client, and a restart mid-exam loses nothing -
 * there is no in-memory timer to lose. Each student's window is their own,
 * because each {@code started_at} is their own.
 *
 * <h2>Late work is graded, never discarded</h2>
 * A submission arriving after the deadline is still marked, and the result
 * reports {@code isLate}. Throwing it away would destroy a student's work over a
 * slow connection, a policy the platform should not be making by accident.
 * Lateness is surfaced, not enforced.
 *
 * <h2>One transaction covers grading, persistence and mastery</h2>
 * {@link #submitExam} is a single transaction, so a paper can never be half
 * marked: either every answer, the submission's timestamps and the derived
 * mastery rows are committed together, or none of them are.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    /**
     * Network-latency allowance before a hand-in counts as late.
     *
     * <p>Deliberately generous. The purpose is not to catch cheating - a client
     * that wants to overrun can simply not send until much later, and this does
     * nothing about that - but to absorb the ordinary case of a submission
     * pressed at the buzzer that is still travelling. Anything longer is a
     * genuine overrun, and a marker should know.
     */
    static final Duration GRACE = Duration.ofSeconds(30);

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final KnowledgeNodeMasteryRepository masteryRepository;
    private final GradingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;

    /** Scale the {@code decimal(4,2)} mark columns hold. */
    private static final int SCORE_SCALE = 2;

    /* ------------------------------------------------------------------ */
    /*  Start                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Opens an attempt, or resumes the one already in flight.
     *
     * <p>Resuming rather than refusing is deliberate: a student who reloads the
     * page mid-exam, or whose network drops and reconnects, must land back in
     * the same attempt with the same remaining time. Starting a second attempt
     * would silently double their allowance and - with {@code max_attempts = 1} -
     * lock them out of the exam entirely.
     *
     * @param examId    the paper.
     * @param studentId the candidate, from the JWT subject.
     * @return the paper plus this attempt's authoritative clock.
     */
    @Transactional
    public ExamSessionResponse startExam(final Long examId, final Long studentId) {
        final Exam exam =
                examRepository
                        .findById(examId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy bài thi với mã " + examId + "."));
        if (!exam.isPublished()) {
            throw new AppException(
                    ErrorCode.FORBIDDEN, "Bài thi chưa được công bố nên bạn chưa thể làm bài.");
        }

        final Optional<ExamSubmission> inFlight =
                submissionRepository
                        .findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
                                examId, studentId);
        if (inFlight.isPresent()) {
            return buildSession(exam, inFlight.get(), LocalDateTime.now());
        }

        final long used = submissionRepository.countByExamExamIdAndStudentUserId(examId, studentId);
        if (used >= exam.getMaxAttempts()) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Bạn đã sử dụng hết số lần làm bài được phép của bài thi này.");
        }

        final User student =
                userRepository
                        .findById(studentId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy tài khoản học sinh."));

        final ExamSubmission submission =
                submissionRepository.save(
                        ExamSubmission.builder()
                                .exam(exam)
                                .student(student)
                                .startedAt(LocalDateTime.now())
                                .submittedAt(null)
                                .tabSwitchCount(0)
                                .build());

        return buildSession(exam, submission, LocalDateTime.now());
    }


    /* ------------------------------------------------------------------ */
    /*  Submit                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Grades a hand-in and closes the attempt.
     *
     * <p>Locked on the attempt row, so a double-clicked submit - or a client
     * retrying after a timeout - cannot grade the same paper twice.
     *
     * @param submissionId the attempt being closed.
     * @param request      the student's answers and proctoring counters.
     * @param studentId    the caller, verified against the attempt's owner.
     */
    @Transactional
    public ExamResultResponse submitExam(
            final Long submissionId, final SubmitExamRequest request, final Long studentId) {

        final ExamSubmission submission =
                submissionRepository
                        .findByIdForUpdate(submissionId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy lần thi này."));
        if (!submission.getStudent().getUserId().equals(studentId)) {
            throw AppException.forbidden();
        }
        if (submission.isSubmitted()) {
            throw new AppException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bài thi này đã được nộp trước đó và không thể nộp lại.");
        }

        final Exam exam = submission.getExam();
        final List<ExamQuestion> paper =
                examQuestionRepository.findPaperOrdered(exam.getExamId());

        final Map<Long, JsonNode> submitted = indexAnswers(request, paper);

        /*
         * A question the client never mentioned has no map entry at all, so
         * reading it with get() would yield null and defeat the empty-document
         * substitution indexAnswers performs. Substituting here as well is what
         * makes "every paper question gets a row" actually hold, which in turn is
         * what keeps a mark sheet complete rather than full of holes.
         */
        final JsonNode noAnswer = objectMapper.createObjectNode();

        /*
         * The deadline is derived, never stored, and compared against the
         * server's clock. The client's submittedAt is deliberately ignored: it
         * is the one field in the request a student fully controls.
         */
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime deadline = deadlineOf(submission, exam);
        final boolean late = now.isAfter(deadline.plus(GRACE));

        final List<SubmissionAnswer> toSave = new java.util.ArrayList<>();
        final List<ExamResultResponse.QuestionResult> perQuestion = new java.util.ArrayList<>();
        final Map<Long, NodeTally> byNode = new LinkedHashMap<>();

        BigDecimal earnedTotal = BigDecimal.ZERO;
        BigDecimal maxTotal = BigDecimal.ZERO;
        int answered = 0;
        int fullyCorrect = 0;

        for (final ExamQuestion item : paper) {
            final QuestionVersion version = item.getVersion();
            final Long versionId = item.getId().getVersionId();
            final QuestionType type =
                    QuestionType.require(version.getQuestion().getQuestionType());
            final BigDecimal points = item.getPoints().setScale(SCORE_SCALE, RoundingMode.HALF_UP);
            final JsonNode response = submitted.getOrDefault(versionId, noAnswer);

            final GradingResult outcome =
                    strategyFactory.forType(type).grade(version.getSolutionPayload(), response, points);

            toSave.add(
                    SubmissionAnswer.builder()
                            /*
                             * The composite key is set explicitly rather than
                             * left for @MapsId to derive. With a
                             * save-all, the insert is issued before any id has
                             * been read back, so a null key reaches the driver
                             * and the batch fails as a type error on
                             * AnswerId.submissionId.
                             */
                            .id(
                                    new SubmissionAnswer.AnswerId(
                                            submission.getSubmissionId(), versionId))
                            .submission(submission)
                            .version(version)
                            .studentResponse(response)
                            .earnedPoints(outcome.earnedPoints())
                            .correct(outcome.fullyCorrect())
                            .build());

            perQuestion.add(
                    new ExamResultResponse.QuestionResult(
                            versionId,
                            type.name(),
                            item.getOrderIndex(),
                            outcome.earnedPoints(),
                            points,
                            outcome.fullyCorrect(),
                            outcome.answered()));

            maxTotal = maxTotal.add(points);
            earnedTotal = earnedTotal.add(outcome.earnedPoints());
            if (outcome.answered()) {
                answered++;
            }
            if (outcome.fullyCorrect()) {
                fullyCorrect++;
            }
            byNode.computeIfAbsent(
                            version.getQuestion().getNode().getNodeId(),
                            key -> new NodeTally(version.getQuestion().getNode()))
                    .add(points, outcome.earnedPoints());
        }

        answerRepository.saveAll(toSave);
        submission.setSubmittedAt(now);
        // Both spellings of the client''s proctoring counter are reconciled in the
        // DTO, so a client still sending only violationCount is not silently
        // recorded as a clean session.
        submission.setTabSwitchCount(request.resolvedTabSwitchCount());
        submissionRepository.save(submission);

        /*
         * Mastery is written last and inside the same transaction: a rollback
         * anywhere above must not leave a student's skill profile reflecting a
         * paper that was never marked.
         */
        recordMastery(submission.getStudent(), byNode);

        final BigDecimal totalScore = earnedTotal.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        final BigDecimal maxScore = maxTotal.setScale(SCORE_SCALE, RoundingMode.HALF_UP);

        return new ExamResultResponse(
                submission.getSubmissionId(),
                exam.getExamId(),
                totalScore,
                maxScore,
                percentage(totalScore, maxScore),
                answered,
                paper.size(),
                fullyCorrect,
                late,
                GRACE.getSeconds(),
                now,
                perQuestion);
    }


    /**
     * Re-reads the server clock for an attempt in flight.
     *
     * <p>Read-only and deliberately lock-free: this is called whenever the client
     * suspects its own timer has stalled, and taking a write lock here would make
     * a polling client contend with the very hand-in it is trying to avoid racing.
     *
     * <p>Ownership is enforced here rather than by the URL matcher, because the
     * alias route sits outside the {@code /student/**} subtree, so the path alone
     * cannot be trusted to have come from a candidate.
     *
     * @throws AppException 404 when the attempt is unknown, 403 when it belongs
     *                      to somebody else.
     */
    @Transactional(readOnly = true)
    public ClockSyncResponse syncClock(final Long submissionId, final Long studentId) {
        final ExamSubmission submission =
                submissionRepository
                        .findById(submissionId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy lần thi này."));
        if (!submission.getStudent().getUserId().equals(studentId)) {
            throw AppException.forbidden();
        }
        final LocalDateTime now = LocalDateTime.now();
        return ClockSyncResponse.of(now, deadlineOf(submission, submission.getExam()), GRACE);
    }

    /* ------------------------------------------------------------------ */
    /*  Internals                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Projects an attempt into the paper the client renders.
     *
     * <p>Questions come out as {@link StudentQuestionResponse}, so the exam path
     * has no route to {@code solution_payload} even by accident: the type has no
     * property to put it in. {@code points} and {@code orderIndex} are populated
     * from {@code exam_questions}, which is where a mark belongs.
     */
    private ExamSessionResponse buildSession(
            final Exam exam, final ExamSubmission submission, final LocalDateTime now) {
        final List<StudentQuestionResponse> questions = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (final ExamQuestion item : examQuestionRepository.findPaperOrdered(exam.getExamId())) {
            final QuestionVersion version = item.getVersion();
            final BigDecimal points =
                    item.getPoints().setScale(SCORE_SCALE, RoundingMode.HALF_UP);

            questions.add(
                    new StudentQuestionResponse(
                            item.getId().getVersionId(),
                            version.getQuestion().getQuestionType(),
                            points,
                            item.getOrderIndex(),
                            version.getContentPayload()));
            total = total.add(points);
        }

        return ExamSessionResponse.of(
                submission.getSubmissionId(),
                exam.getExamId(),
                exam.getTitle(),
                exam.getDurationMinutes(),
                submission.getStartedAt(),
                deadlineOf(submission, exam),
                now,
                total.setScale(SCORE_SCALE, RoundingMode.HALF_UP),
                questions);
    }

    /**
     * The attempt's deadline: when it began plus the paper's duration.
     *
     * <p>Computed rather than stored, so there is no clock to keep in sync and
     * nothing to repair after a restart.
     */
    private static LocalDateTime deadlineOf(final ExamSubmission submission, final Exam exam) {
        return submission
                .getStartedAt()
                .plusMinutes(exam.getDurationMinutes() == null ? 0L : exam.getDurationMinutes());
    }


    /**
     * Turns the request's free-form answers into a lookup keyed by version id.
     *
     * <p>An answer for a version that is not on this paper is rejected rather
     * than ignored: it means the client is holding a stale paper, and silently
     * dropping it would hide a real bug behind a plausible-looking mark sheet.
     *
     * <p>An omitted answer becomes an empty JSON object rather than a null node.
     * {@code submission_answers.student_response} is {@code NOT NULL}, and
     * Hibernate binds a Jackson {@code NullNode} as SQL {@code NULL} rather than
     * as the JSON literal {@code null} - so a single skipped question would abort
     * the whole hand-in. Every strategy already reads an empty document as "not
     * answered", and the grading columns are what tell a blank from a wrong
     * answer.
     */
    private Map<Long, JsonNode> indexAnswers(
            final SubmitExamRequest request, final List<ExamQuestion> paper) {
        final Set<Long> onPaper = new LinkedHashSet<>();
        for (final ExamQuestion item : paper) {
            onPaper.add(item.getId().getVersionId());
        }

        final Map<Long, JsonNode> byVersion = new LinkedHashMap<>();
        final List<SubmitExamRequest.AnswerPayload> submittedAnswers = request.answers();
        for (final SubmitExamRequest.AnswerPayload answer :
                (submittedAnswers == null ? List.<SubmitExamRequest.AnswerPayload>of() : submittedAnswers)) {
            if (answer == null || answer.versionId() == null) {
                continue;
            }
            if (!onPaper.contains(answer.versionId())) {
                throw AppException.badRequest(
                        "Câu trả lời có mã phiên bản "
                                + answer.versionId()
                                + " không thuộc bài thi này.");
            }
            byVersion.put(
                    answer.versionId(),
                    answer.studentResponse() == null
                            ? objectMapper.createObjectNode()
                            : objectMapper.valueToTree(answer.studentResponse()));
        }
        return byVersion;
    }

    /**
     * Writes per-node mastery for the nodes this paper actually assessed.
     *
     * <p>A further attempt upserts rather than replaces: {@code attempts}
     * accumulates and {@code mastery_score} becomes the running mean, so one
     * strong sitting does not erase a weak one and one weak sitting does not
     * erase a strong one. Averaging is what makes the number mean "how well does
     * this student know this", which is the only reading under which the 80%
     * unlock threshold in the knowledge graph can be trusted.
     */
    private void recordMastery(final User student, final Map<Long, NodeTally> byNode) {
        for (final NodeTally tally : byNode.values()) {
            if (tally.maxPoints().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            final int score =
                    tally.earnedPoints()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(tally.maxPoints(), 0, RoundingMode.HALF_UP)
                            .intValue();

            masteryRepository
                    .findById(
                            new KnowledgeNodeMastery.MasteryId(
                                    student.getUserId(), tally.node().getNodeId()))
                    .ifPresentOrElse(
                            existing -> {
                                final long total =
                                        (long) existing.getMasteryScore() * existing.getAttempts()
                                                + score;
                                existing.setAttempts(existing.getAttempts() + 1);
                                existing.setMasteryScore(
                                        (int) Math.round((double) total / existing.getAttempts()));
                                masteryRepository.save(existing);
                            },
                            () ->
                                    masteryRepository.save(
                                            KnowledgeNodeMastery.builder()
                                                    .id(
                                                            new KnowledgeNodeMastery.MasteryId(
                                                                    student.getUserId(),
                                                                    tally.node().getNodeId()))
                                                    .student(student)
                                                    .node(tally.node())
                                                    .masteryScore(score)
                                                    .attempts(1)
                                                    .build()));
        }
    }

    /** Whole-number percentage, or null when the paper carries no marks. */
    private static Integer percentage(final BigDecimal score, final BigDecimal max) {
        if (max.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return score.multiply(BigDecimal.valueOf(100))
                .divide(max, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Marks seen for one knowledge node, plus the node they belong to. */
    private static final class NodeTally {

        private final KnowledgeNode node;
        private BigDecimal maxPoints = BigDecimal.ZERO;
        private BigDecimal earnedPoints = BigDecimal.ZERO;

        NodeTally(final KnowledgeNode node) {
            this.node = node;
        }

        void add(final BigDecimal max, final BigDecimal earned) {
            maxPoints = maxPoints.add(max);
            earnedPoints = earnedPoints.add(earned);
        }

        KnowledgeNode node() {
            return node;
        }

        BigDecimal maxPoints() {
            return maxPoints;
        }

        BigDecimal earnedPoints() {
            return earnedPoints;
        }
    }
}

